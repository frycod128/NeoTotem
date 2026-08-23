"""从原版图腾生成缓慢呼吸、轻故障、低噪声炫彩的动画遮罩。"""

from __future__ import annotations

import argparse
import json
import math
import zipfile
from pathlib import Path

from PIL import Image


VANILLA_TEXTURE = "assets/minecraft/textures/item/totem_of_undying.png"
FRAME_SIZE = 16
FRAME_COUNT = 32
GLITCH_FRAMES = {
    7: (5, 1),
    23: (10, -1),
}
PALETTE = (
    (34, 111, 238),   # electric blue
    (28, 158, 255),   # bright blue
    (22, 222, 240),   # cyan
    (20, 210, 145),   # emerald
    (55, 227, 82),    # vivid green
    (145, 241, 52),   # lime
    (235, 239, 55),   # yellow-lime
    (255, 214, 58),   # warm yellow accent
)
PALE_HIGHLIGHT = (220, 255, 205)


def mix(first: float, second: float, amount: float) -> float:
    return first * (1.0 - amount) + second * amount


def quantize(channel: float) -> int:
    """把颜色压到 17 的硬色阶，避免连续明暗塑造出圆润感。"""
    return max(0, min(255, round(channel / 17.0) * 17))


def recolor_pixel(
    red: int,
    green: int,
    blue: int,
    alpha: int,
    x: int,
    y: int,
    frame: int,
) -> tuple[int, int, int, int]:
    if alpha == 0:
        return 0, 0, 0, 0

    phase = math.tau * frame / FRAME_COUNT
    breath = 0.5 - 0.5 * math.cos(phase)
    luminance = (red * 0.2126 + green * 0.7152 + blue * 0.0722) / 255.0
    # 固定的斜向宽色块保持原版物品辨识度；动态只交给呼吸、窄扫描带和两帧故障。
    band_index = int(x * 0.45 + y * 0.28) % len(PALETTE)
    base_red, base_green, base_blue = PALETTE[band_index]

    # 原版明暗只保留四档；亮部混入浅黄绿能量色，暗部也不压成黑紫。
    if luminance < 0.24:
        shade = 0.72
    elif luminance < 0.48:
        shade = 0.84
    elif luminance < 0.72:
        shade = 0.94
    else:
        shade = 1.0
        base_red = mix(base_red, PALE_HIGHLIGHT[0], 0.36)
        base_green = mix(base_green, PALE_HIGHLIGHT[1], 0.36)
        base_blue = mix(base_blue, PALE_HIGHLIGHT[2], 0.36)

    breath_brightness = 0.90 + breath * 0.10
    out_red = base_red * shade * breath_brightness
    out_green = base_green * shade * breath_brightness
    out_blue = base_blue * shade * breath_brightness

    # 一条窄的蓝绿黄扫描带慢慢穿过；它替换单行颜色，不制造柔和光晕。
    scan_y = (frame / FRAME_COUNT) * (FRAME_SIZE + 4) - 2
    if abs(y - scan_y) < 0.55:
        scan_color = PALETTE[(x // 2 + frame // 3) % len(PALETTE)]
        out_red, out_green, out_blue = scan_color

    # layer1 与原版 layer0 混合：呼吸主要靠亮度和透明度变化，而不是夸张闪烁。
    overlay_alpha = round(alpha * min(0.95, 0.80 + breath * 0.11 + luminance * 0.03))
    return quantize(out_red), quantize(out_green), quantize(out_blue), overlay_alpha


def apply_glitch(frame_image: Image.Image, source: Image.Image, frame: int) -> None:
    glitch = GLITCH_FRAMES.get(frame)
    if glitch is None:
        return

    row, shift = glitch
    original_row = [frame_image.getpixel((x, row)) for x in range(FRAME_SIZE)]
    source_row = [source.getpixel((x, row)) for x in range(FRAME_SIZE)]

    # 只错位一个扫描行并留下低透明色差残影；静态 layer0 仍保证主体轮廓完整。
    for x in range(FRAME_SIZE):
        if source_row[x][3] == 0:
            continue
        echo = (255, 221, 51, min(76, original_row[x][3])) if shift > 0 else (17, 238, 221, min(76, original_row[x][3]))
        frame_image.putpixel((x, row), echo)
        shifted_x = x + shift
        if 0 <= shifted_x < FRAME_SIZE:
            frame_image.putpixel((shifted_x, row), original_row[x])


def generate(source_jar: Path, output: Path) -> None:
    with zipfile.ZipFile(source_jar) as archive, archive.open(VANILLA_TEXTURE) as source:
        image = Image.open(source).convert("RGBA")

    if image.size != (FRAME_SIZE, FRAME_SIZE):
        raise ValueError(f"Expected vanilla totem texture to be 16x16, got {image.size!r}")

    overlay = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT))
    for frame in range(FRAME_COUNT):
        frame_image = Image.new("RGBA", image.size)
        for y in range(image.height):
            for x in range(image.width):
                frame_image.putpixel((x, y), recolor_pixel(*image.getpixel((x, y)), x, y, frame))
        apply_glitch(frame_image, image, frame)
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
