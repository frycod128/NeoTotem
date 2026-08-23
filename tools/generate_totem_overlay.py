"""生成从原版图腾右眼喷向右上方的抽帧像素火焰遮罩。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image


# 使用原版纹理两倍分辨率，让火焰颗粒比图腾本体像素更细，同时保持准确对位。
FRAME_SIZE = 32
FRAME_COUNT = 8

# 原版 16×16 图腾右眼的四个像素；每格映射为遮罩中的 2×2 区域。
RIGHT_EYE_PIXELS = ((9, 5), (10, 5), (9, 6), (10, 6))
FLAME_ROOTS = ((19, 11), (21, 11), (19, 13), (21, 13))

TRANSPARENT = (0, 0, 0, 0)
DEEP_BLUE = (8, 17, 126)
ELECTRIC_BLUE = (16, 54, 255)
CYAN = (0, 226, 255)
PALE_CYAN = (205, 255, 250)
VIOLET = (112, 20, 255)
MAGENTA = (247, 0, 232)


# 每一帧都是独立设计的关键姿势，而不是在同一路径上缓慢平移。
# 四条火舌分别从右眼四格起步，并在离开眼眶后合流、分叉或抽断。
FLAME_KEYFRAMES = (
    (
        ((19, 11), (20, 9), (22, 8), (23, 6)),
        ((21, 11), (22, 9), (24, 8), (25, 6)),
        ((19, 13), (20, 11), (22, 10), (24, 9)),
        ((21, 13), (22, 11), (24, 10), (26, 8)),
    ),
    (
        ((19, 11), (21, 9), (22, 7), (25, 6), (26, 3)),
        ((21, 11), (23, 9), (25, 8), (27, 6), (29, 5)),
        ((19, 13), (21, 11), (23, 10), (25, 8)),
        ((21, 13), (23, 11), (26, 10), (28, 8), (31, 7)),
    ),
    (
        ((19, 11), (21, 9), (23, 8), (24, 5), (27, 3), (28, 0)),
        ((21, 11), (23, 10), (26, 8), (28, 6), (31, 5)),
        ((19, 13), (21, 11), (24, 10), (26, 7), (29, 6)),
        ((21, 13), (23, 11), (25, 9), (27, 5), (30, 3), (31, 1)),
    ),
    (
        ((19, 11), (20, 9), (23, 7), (23, 4), (26, 2), (25, 0)),
        ((21, 11), (22, 9), (25, 8), (27, 5), (30, 4)),
        ((19, 13), (21, 11), (22, 8), (25, 6), (27, 3)),
        ((21, 13), (23, 11), (26, 9), (28, 6), (31, 3)),
    ),
    (
        ((19, 11), (22, 10), (24, 8), (27, 7), (29, 4), (31, 3)),
        ((21, 11), (23, 9), (26, 7), (28, 4), (30, 1)),
        ((19, 13), (21, 11), (24, 9), (25, 6)),
        ((21, 13), (23, 11), (26, 10), (29, 8), (31, 6)),
    ),
    (
        ((19, 11), (20, 9), (23, 8), (25, 6)),
        ((21, 11), (23, 10), (25, 8), (26, 5), (28, 3)),
        ((19, 13), (21, 11), (23, 10)),
        ((21, 13), (23, 11), (25, 10), (27, 8)),
    ),
    (
        ((19, 11), (21, 9), (24, 8), (25, 5), (28, 3), (30, 0)),
        ((21, 11), (23, 9), (24, 6), (27, 4), (29, 2)),
        ((19, 13), (21, 11), (24, 10), (27, 8), (30, 7)),
        ((21, 13), (24, 11), (26, 9), (29, 6), (31, 4)),
    ),
    (
        ((19, 11), (21, 9), (23, 8)),
        ((21, 11), (23, 10), (25, 7)),
        ((19, 13), (21, 11), (23, 10)),
        ((21, 13), (23, 11), (26, 9)),
    ),
)


# 火星位置每帧大步跃迁，故意不补齐中间运动轨迹。
SPARK_KEYFRAMES = (
    ((26, 5, VIOLET), (28, 4, ELECTRIC_BLUE)),
    ((28, 2, VIOLET), (31, 4, MAGENTA), (26, 1, ELECTRIC_BLUE)),
    ((30, 0, MAGENTA), (31, 7, VIOLET), (27, 1, ELECTRIC_BLUE)),
    ((28, 0, VIOLET), (31, 1, MAGENTA), (29, 7, ELECTRIC_BLUE)),
    ((31, 0, MAGENTA), (30, 5, VIOLET), (27, 2, ELECTRIC_BLUE)),
    ((30, 1, VIOLET), (31, 4, MAGENTA), (28, 6, ELECTRIC_BLUE)),
    ((31, 2, MAGENTA), (29, 0, VIOLET), (31, 7, ELECTRIC_BLUE)),
    ((27, 5, ELECTRIC_BLUE), (30, 2, VIOLET), (31, 0, MAGENTA)),
)


def composite_pixel(
    image: Image.Image,
    position: tuple[int, int],
    color: tuple[int, int, int],
    alpha: int,
) -> None:
    x, y = position
    if not (0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE) or alpha <= 0:
        return

    old_red, old_green, old_blue, old_alpha = image.getpixel(position)
    source_alpha = alpha / 255.0
    destination_alpha = old_alpha / 255.0
    result_alpha = source_alpha + destination_alpha * (1.0 - source_alpha)
    channels = tuple(
        round(
            (source * source_alpha + destination * destination_alpha * (1.0 - source_alpha))
            / result_alpha
        )
        for source, destination in zip(color, (old_red, old_green, old_blue))
    )
    image.putpixel(position, (*channels, round(result_alpha * 255)))


def rasterize_line(start: tuple[int, int], end: tuple[int, int]) -> list[tuple[int, int]]:
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


def flame_colors(progress: float) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    if progress < 0.24:
        return ELECTRIC_BLUE, PALE_CYAN
    if progress < 0.55:
        return DEEP_BLUE, CYAN
    if progress < 0.80:
        return VIOLET, ELECTRIC_BLUE
    return MAGENTA, VIOLET


def draw_eye_roots(image: Image.Image, frame: int) -> None:
    """完整覆盖原版右眼四格，使所有火舌确实从这四个像素中喷出。"""
    pulse = (PALE_CYAN, CYAN, PALE_CYAN, CYAN, PALE_CYAN, CYAN, PALE_CYAN, CYAN)[frame]
    for cell_index, (source_x, source_y) in enumerate(RIGHT_EYE_PIXELS):
        color = PALE_CYAN if cell_index == frame % len(RIGHT_EYE_PIXELS) else pulse
        for y in range(source_y * 2, source_y * 2 + 2):
            for x in range(source_x * 2, source_x * 2 + 2):
                composite_pixel(image, (x, y), color, 238)


def draw_flame_strand(
    image: Image.Image,
    vertices: tuple[tuple[int, int], ...],
    frame: int,
    strand_index: int,
) -> None:
    points = rasterize_polyline(vertices)
    last_index = max(1, len(points) - 1)

    # 先画不规则外焰；刻意留洞，避免变成四条发光管线。
    for point_index, (x, y) in enumerate(points):
        progress = point_index / last_index
        outer, _ = flame_colors(progress)
        for direction_index, (offset_x, offset_y) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
            if (point_index * 3 + direction_index + frame + strand_index) % 5 == 0:
                continue
            composite_pixel(image, (x + offset_x, y + offset_y), outer, 112 if progress < 0.75 else 156)

    # 内焰在部分帧主动断点，抽掉的像素会让高速甩动更有力量。
    for point_index, position in enumerate(points):
        progress = point_index / last_index
        _, core = flame_colors(progress)
        if 0.28 < progress < 0.82 and (point_index + frame * 2 + strand_index) % 7 == 0:
            continue
        alpha = 250 if progress < 0.58 else 224
        composite_pixel(image, position, core, alpha)


def render_frame(frame: int) -> Image.Image:
    image = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), TRANSPARENT)
    draw_eye_roots(image, frame)

    for strand_index, vertices in enumerate(FLAME_KEYFRAMES[frame]):
        draw_flame_strand(image, vertices, frame, strand_index)

    for spark_index, (x, y, color) in enumerate(SPARK_KEYFRAMES[frame]):
        composite_pixel(image, (x, y), color, 230 if spark_index == 0 else 178)
        if (frame + spark_index) % 3 == 0:
            composite_pixel(image, (x - 1, y + 1), DEEP_BLUE, 92)

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
