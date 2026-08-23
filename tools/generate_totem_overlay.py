"""生成与底层图标材质无关的永生图腾动画特效遮罩。"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

from PIL import Image


FRAME_SIZE = 16
FRAME_COUNT = 120

# 每帧持续两个游戏刻：完整循环 12 秒。
LIGHTNING_START = 50
LIGHTNING_GROW_FRAMES = 9
LIGHTNING_HOLD_FRAMES = 1
LIGHTNING_FADE_FRAMES = 4
OUTLINE_STARTS = (4, 24, 44, 64, 84, 104)
OUTLINE_DURATION = 7

TRANSPARENT = (0, 0, 0, 0)
INK_GREEN = (3, 21, 16)
DARK_GREEN = (10, 47, 35)
MUTED_GREEN = (74, 119, 91)
PALE_GREEN = (199, 222, 205)
GREEN_WHITE = (241, 248, 243)


def composite_pixel(
    image: Image.Image,
    position: tuple[int, int],
    color: tuple[int, int, int],
    alpha: int,
) -> None:
    """按标准 alpha 规则将一个特效像素叠到当前帧。"""
    x, y = position
    if not (0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE) or alpha <= 0:
        return

    old_red, old_green, old_blue, old_alpha = image.getpixel(position)
    source_alpha = alpha / 255.0
    destination_alpha = old_alpha / 255.0
    result_alpha = source_alpha + destination_alpha * (1.0 - source_alpha)
    if result_alpha == 0.0:
        image.putpixel(position, TRANSPARENT)
        return

    channels = tuple(
        round(
            (source * source_alpha + destination * destination_alpha * (1.0 - source_alpha))
            / result_alpha
        )
        for source, destination in zip(color, (old_red, old_green, old_blue))
    )
    image.putpixel(position, (*channels, round(result_alpha * 255)))


def rasterize_line(start: tuple[int, int], end: tuple[int, int]) -> list[tuple[int, int]]:
    """用 Bresenham 算法生成连续且保持像素硬边的线段。"""
    x, y = start
    end_x, end_y = end
    delta_x = abs(end_x - x)
    delta_y = -abs(end_y - y)
    step_x = 1 if x < end_x else -1
    step_y = 1 if y < end_y else -1
    error = delta_x + delta_y
    points: list[tuple[int, int]] = []

    while True:
        points.append((x, y))
        if (x, y) == (end_x, end_y):
            return points
        doubled_error = 2 * error
        if doubled_error >= delta_y:
            error += delta_y
            x += step_x
        if doubled_error <= delta_x:
            error += delta_x
            y += step_y


def rasterize_polyline(vertices: tuple[tuple[int, int], ...]) -> list[tuple[int, int]]:
    points: list[tuple[int, int]] = []
    for start, end in zip(vertices, vertices[1:]):
        segment = rasterize_line(start, end)
        if points and segment[0] == points[-1]:
            segment = segment[1:]
        points.extend(segment)
    return points


# 一条从左下向右上贯穿图标的折线；转折有意不规则，避免再次成为平直扫光。
LIGHTNING_PATH = rasterize_polyline((
    (0, 15), (1, 13), (3, 12), (2, 10), (5, 9), (4, 7), (7, 6),
    (6, 4), (9, 5), (9, 3), (12, 2), (11, 0), (14, 1), (15, 0),
))

LIGHTNING_BRANCHES = (
    (0.30, rasterize_polyline(((2, 10), (1, 8), (0, 7)))),
    (0.54, rasterize_polyline(((7, 6), (9, 7), (11, 6)))),
    (0.73, rasterize_polyline(((9, 3), (8, 1), (9, 0)))),
)


def draw_bolt_pixels(
    image: Image.Image,
    points: list[tuple[int, int]],
    visible_count: int,
    alpha_scale: float,
    breakup: int | None = None,
) -> None:
    visible_points = points[:visible_count]

    # 偏移一格的墨绿阴影先落笔，随后才画带微绿相的近白电芯。
    for index, (x, y) in enumerate(visible_points):
        if breakup is not None and (index + breakup) % 4 == 0:
            continue
        composite_pixel(image, (x + 1, y), INK_GREEN, round(220 * alpha_scale))
        composite_pixel(image, (x, y + 1), DARK_GREEN, round(150 * alpha_scale))

    for index, position in enumerate(visible_points):
        if breakup is not None and (index + breakup) % 4 == 0:
            continue
        distance_from_head = visible_count - index - 1
        if distance_from_head <= 1:
            color, alpha = GREEN_WHITE, 255
        elif distance_from_head <= 4:
            color, alpha = PALE_GREEN, 238
        else:
            color, alpha = GREEN_WHITE, 218
        composite_pixel(image, position, color, round(alpha * alpha_scale))


def draw_lightning(image: Image.Image, frame: int) -> None:
    """让电光依次经历生长、贯穿定格、分段熄灭，而不是整条平移。"""
    local_frame = frame - LIGHTNING_START
    grow_end = LIGHTNING_GROW_FRAMES
    hold_end = grow_end + LIGHTNING_HOLD_FRAMES

    if local_frame < grow_end:
        progress = (local_frame + 1) / LIGHTNING_GROW_FRAMES
        visible_count = max(1, math.ceil(len(LIGHTNING_PATH) * progress))
        alpha_scale = 1.0
        breakup = None
    elif local_frame < hold_end:
        progress = 1.0
        visible_count = len(LIGHTNING_PATH)
        alpha_scale = 1.0
        breakup = None
    else:
        fade_frame = local_frame - hold_end
        progress = 1.0
        visible_count = len(LIGHTNING_PATH)
        alpha_scale = (0.72, 0.46, 0.25, 0.10)[fade_frame]
        breakup = fade_frame

    draw_bolt_pixels(image, LIGHTNING_PATH, visible_count, alpha_scale, breakup)

    for branch_progress, branch in LIGHTNING_BRANCHES:
        if progress < branch_progress:
            continue
        branch_amount = min(1.0, (progress - branch_progress) / 0.16)
        branch_count = max(1, math.ceil(len(branch) * branch_amount))
        draw_bolt_pixels(image, branch, branch_count, alpha_scale * 0.82, breakup)


def build_perimeter() -> tuple[tuple[int, int], ...]:
    """按顺时针顺序返回贴图最外圈；它位于常规图标轮廓之外。"""
    points = [(x, 0) for x in range(FRAME_SIZE)]
    points.extend((FRAME_SIZE - 1, y) for y in range(1, FRAME_SIZE))
    points.extend((x, FRAME_SIZE - 1) for x in range(FRAME_SIZE - 2, -1, -1))
    points.extend((0, y) for y in range(FRAME_SIZE - 2, 0, -1))
    return tuple(points)


PERIMETER = build_perimeter()
TRACER_LENGTH = 8


def draw_tracer(
    image: Image.Image,
    head: int,
    direction: int,
    alpha_scale: float,
) -> None:
    """绘制一段有亮头和衰减尾迹的框线游标。"""
    trail_colors = (
        (GREEN_WHITE, 246),
        (PALE_GREEN, 226),
        (MUTED_GREEN, 205),
        (DARK_GREEN, 188),
        (DARK_GREEN, 146),
        (INK_GREEN, 112),
        (INK_GREEN, 76),
        (INK_GREEN, 42),
    )
    for trail_index, (color, alpha) in enumerate(trail_colors):
        perimeter_index = (head - direction * trail_index) % len(PERIMETER)
        composite_pixel(
            image,
            PERIMETER[perimeter_index],
            color,
            round(alpha * alpha_scale),
        )


def draw_moving_outline(image: Image.Image, frame: int, start: int) -> None:
    """两段残缺框线沿外圈相向奔跑，全程不组成静态矩形。"""
    local_frame = frame - start
    progress = local_frame / (OUTLINE_DURATION - 1)
    alpha_curve = (0.48, 0.82, 1.0, 1.0, 0.88, 0.64, 0.34)
    distance = round(progress * (len(PERIMETER) * 0.72))

    clockwise_head = distance % len(PERIMETER)
    counterclockwise_head = (len(PERIMETER) // 2 - distance) % len(PERIMETER)
    draw_tracer(image, clockwise_head, 1, alpha_curve[local_frame])
    draw_tracer(image, counterclockwise_head, -1, alpha_curve[local_frame] * 0.88)


def render_frame(frame: int) -> Image.Image:
    image = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), TRANSPARENT)

    for start in OUTLINE_STARTS:
        if start <= frame < start + OUTLINE_DURATION:
            draw_moving_outline(image, frame, start)

    lightning_duration = LIGHTNING_GROW_FRAMES + LIGHTNING_HOLD_FRAMES + LIGHTNING_FADE_FRAMES
    if LIGHTNING_START <= frame < LIGHTNING_START + lightning_duration:
        draw_lightning(image, frame)

    return image


def generate(output: Path) -> None:
    overlay = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE * FRAME_COUNT), TRANSPARENT)
    for frame in range(FRAME_COUNT):
        overlay.paste(render_frame(frame), (0, frame * FRAME_SIZE))

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
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    generate(args.output)


if __name__ == "__main__":
    main()
