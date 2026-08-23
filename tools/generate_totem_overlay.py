"""从原版图腾生成具有清晰工件材质、局部能量脉冲和短促故障的动画遮罩。"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
import zipfile
from pathlib import Path

from PIL import Image


VANILLA_TEXTURE = "assets/minecraft/textures/item/totem_of_undying.png"
FRAME_SIZE = 16
FRAME_COUNT = 40

# 三次不等间隔、仅持续一帧的局部故障；不存在连续滚动扫描线。
GLITCH_FRAMES = {
    8: (5, 1, (25, 225, 240)),
    25: (10, -1, (145, 241, 52)),
    34: (3, 1, (255, 214, 58)),
}
DROPOUT_FRAMES = {
    16: ((6, 4), (9, 5)),
    29: ((7, 11), (9, 12)),
}
FLASH_FRAME = 35

EYE_PIXELS = {
    (6, 4), (9, 4),
    (5, 5), (6, 5), (9, 5), (10, 5),
}
CORE_INNER = {
    (7, 10), (8, 10),
    (7, 11), (8, 11),
    (7, 12), (8, 12),
}
CORE_OUTER = {
    (6, 10), (9, 10),
    (6, 11), (9, 11),
    (6, 12), (9, 12),
    (6, 13), (9, 13),
}
ENERGY_CRACKS = {
    (5, 7), (10, 7),
    (4, 8), (11, 8),
    (5, 9), (10, 9),
    (5, 11), (10, 11),
    (5, 13), (10, 13),
}


def mix(first: float, second: float, amount: float) -> float:
    return first * (1.0 - amount) + second * amount


def mix_color(first: tuple[int, int, int], second: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(round(mix(a, b, amount)) for a, b in zip(first, second))


def quantize(channel: float) -> int:
    """使用 17 级硬色阶，避免光滑渐变造成圆润感。"""
    return max(0, min(255, round(channel / 17.0) * 17))


def source_luminance(pixel: tuple[int, int, int, int]) -> float:
    red, green, blue, _ = pixel
    return (red * 0.2126 + green * 0.7152 + blue * 0.0722) / 255.0


def material_color(x: int, y: int, luminance: float) -> tuple[tuple[int, int, int], float]:
    """给头部、骨/石面板、金属框和躯干侧板分配可辨识的低饱和材质。"""
    oxidized_teal = (45, 105, 102)
    dark_blue_gray = (33, 54, 68)
    old_gold = (174, 132, 55)
    pale_stone = (207, 205, 174)
    bone_highlight = (231, 225, 190)

    # 原版最暗一档统一成为蓝灰结构线，先把头、手臂和躯干切割成清楚的工件面板。
    if luminance < 0.28:
        return dark_blue_gray, 0.78
    if y <= 6:
        if x <= 5 or x >= 10:
            color, alpha_scale = oxidized_teal, 0.70
        elif luminance >= 0.72:
            color, alpha_scale = bone_highlight, 0.32
        elif 6 <= x <= 9 and 2 <= y <= 3:
            color, alpha_scale = pale_stone, 0.40
        else:
            color, alpha_scale = old_gold, 0.55
    elif y <= 9:
        if x <= 3 or x >= 12:
            color, alpha_scale = oxidized_teal, 0.74
        elif luminance >= 0.72:
            color, alpha_scale = pale_stone, 0.34
        else:
            color, alpha_scale = old_gold, 0.56
    else:
        if x in (4, 5, 10, 11):
            color, alpha_scale = dark_blue_gray, 0.76
        elif x in (6, 9):
            color, alpha_scale = old_gold, 0.58
        elif x in (7, 8):
            color, alpha_scale = pale_stone, 0.40
        else:
            color, alpha_scale = oxidized_teal, 0.68

    # 完整保留原版明暗阶；材质遮罩不再把所有像素压成同一高饱和度。
    if luminance < 0.28:
        shade = 0.68
    elif luminance < 0.52:
        shade = 0.82
    elif luminance < 0.76:
        shade = 0.94
    else:
        shade = 1.0
    return tuple(quantize(channel * shade) for channel in color), alpha_scale


def pulse_amount(frame: int) -> float:
    """带停顿感的呼吸曲线：能量快速点亮，短暂停留，再缓慢回落。"""
    phase = frame / FRAME_COUNT
    base = 0.5 - 0.5 * math.cos(math.tau * phase)
    return base ** 1.35


def energy_color(pulse: float, offset: float = 0.0) -> tuple[int, int, int]:
    # 静息为青色，峰值经过翡翠/酸橙到少量黄白；只有能量像素使用高饱和色。
    hue = mix(0.50, 0.18, min(1.0, max(0.0, pulse + offset)))
    saturation = mix(0.82, 0.68, pulse)
    value = mix(0.58, 1.0, pulse)
    red, green, blue = colorsys.hsv_to_rgb(hue, saturation, value)
    return quantize(red * 255), quantize(green * 255), quantize(blue * 255)


def render_pixel(source: Image.Image, x: int, y: int, frame: int) -> tuple[int, int, int, int]:
    source_pixel = source.getpixel((x, y))
    source_alpha = source_pixel[3]
    if source_alpha == 0:
        return 0, 0, 0, 0

    luminance = source_luminance(source_pixel)
    color, alpha_scale = material_color(x, y, luminance)
    pulse = pulse_amount(frame)

    if (x, y) in EYE_PIXELS:
        # 两眼有轻微相位差；第 18 帧单眼短暂熄灭，避免机械同步闪烁。
        left_eye = x < FRAME_SIZE // 2
        eye_pulse = min(1.0, max(0.0, pulse + (0.10 if left_eye else -0.06)))
        color = energy_color(eye_pulse, 0.04 if left_eye else -0.02)
        alpha_scale = 0.62 + eye_pulse * 0.36
        if frame == 18 and left_eye:
            color, alpha_scale = (17, 34, 42), 0.88
    elif (x, y) in CORE_INNER:
        color = energy_color(pulse, 0.10)
        alpha_scale = 0.44 + pulse * 0.52
        if frame == FLASH_FRAME:
            color, alpha_scale = (238, 255, 204), 1.0
    elif (x, y) in CORE_OUTER:
        expansion = max(0.0, (pulse - 0.18) / 0.82)
        color = energy_color(expansion, -0.04)
        alpha_scale = 0.20 + expansion * 0.70
    elif (x, y) in ENERGY_CRACKS:
        crack_wave = max(0.0, pulse - 0.48) / 0.52
        # 相邻裂纹错开点亮，产生局部能量流动而不是整图统一变色。
        stagger = ((x * 3 + y * 5 + frame // 2) % 7) / 6.0
        active = max(0.0, crack_wave - stagger * 0.35)
        if active > 0.08:
            color = energy_color(active, -0.08)
            alpha_scale = 0.18 + active * 0.70

    return color[0], color[1], color[2], round(source_alpha * min(1.0, alpha_scale))


def apply_glitch(frame_image: Image.Image, source: Image.Image, frame: int) -> None:
    glitch = GLITCH_FRAMES.get(frame)
    if glitch is not None:
        row, shift, echo_color = glitch
        original_row = [frame_image.getpixel((x, row)) for x in range(FRAME_SIZE)]
        source_row = [source.getpixel((x, row)) for x in range(FRAME_SIZE)]
        occupied = [x for x in range(FRAME_SIZE) if source_row[x][3] > 0]
        # 只错位行中间一小段，绝不把整行做成连续扫描条。
        if occupied:
            start = occupied[len(occupied) // 3]
            end = occupied[min(len(occupied) - 1, len(occupied) * 2 // 3)]
            for x in range(start, end + 1):
                if source_row[x][3] == 0:
                    continue
                frame_image.putpixel((x, row), (*echo_color, 220))
                shifted_x = x + shift
                if 0 <= shifted_x < FRAME_SIZE:
                    frame_image.putpixel((shifted_x, row), original_row[x])
            # 单个轮廓外色差像素让故障一眼可见，但不会形成横向长条。
            echo_x = (max(occupied) + 1) if shift > 0 else (min(occupied) - 1)
            if 0 <= echo_x < FRAME_SIZE:
                frame_image.putpixel((echo_x, row), (*echo_color, 210))

    # 两个不相邻帧出现局部“像素断电”，下一帧立即恢复。
    for position in DROPOUT_FRAMES.get(frame, ()):
        if source.getpixel(position)[3] > 0:
            frame_image.putpixel(position, (12, 25, 31, 235))


def generate(source_jar: Path, output: Path) -> None:
    with zipfile.ZipFile(source_jar) as archive, archive.open(VANILLA_TEXTURE) as source_file:
        source = Image.open(source_file).convert("RGBA")

    if source.size != (FRAME_SIZE, FRAME_SIZE):
        raise ValueError(f"Expected vanilla totem texture to be 16x16, got {source.size!r}")

    overlay = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT))
    for frame in range(FRAME_COUNT):
        frame_image = Image.new("RGBA", source.size)
        for y in range(FRAME_SIZE):
            for x in range(FRAME_SIZE):
                frame_image.putpixel((x, y), render_pixel(source, x, y, frame))
        apply_glitch(frame_image, source, frame)
        overlay.paste(frame_image, (0, frame * FRAME_SIZE))

    output.parent.mkdir(parents=True, exist_ok=True)
    overlay.save(output, format="PNG", optimize=True)
    metadata = {
        "animation": {
            "frametime": 2,
            "interpolate": False,
        }
    }
    output.with_suffix(output.suffix + ".mcmeta").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_jar", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    generate(args.source_jar, args.output)


if __name__ == "__main__":
    main()
