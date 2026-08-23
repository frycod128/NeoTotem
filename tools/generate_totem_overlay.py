"""从 Minecraft sources JAR 中的原版图腾 alpha 轮廓生成青紫换色遮罩。"""

from __future__ import annotations

import argparse
import colorsys
import zipfile
from pathlib import Path

from PIL import Image


VANILLA_TEXTURE = "assets/minecraft/textures/item/totem_of_undying.png"


def recolor_pixel(red: int, green: int, blue: int, alpha: int, x: int, y: int) -> tuple[int, int, int, int]:
    if alpha == 0:
        return 0, 0, 0, 0

    # 保留原图亮度与像素轮廓，只使用青/紫相位遮罩；左右微变色让 16×16 图标仍有层次。
    luminance = (red * 0.2126 + green * 0.7152 + blue * 0.0722) / 255.0
    horizontal = x / 15.0
    vertical = y / 15.0
    hue = 0.49 + 0.30 * ((horizontal * 0.65 + vertical * 0.35) % 1.0)
    saturation = 0.78 - 0.28 * luminance
    value = 0.48 + 0.52 * luminance
    out_red, out_green, out_blue = colorsys.hsv_to_rgb(hue, saturation, value)

    # 半透明 layer1 与原版 layer0 混合，视觉仍明确保留“不死图腾”本体。
    overlay_alpha = round(alpha * (0.58 + 0.12 * (1.0 - luminance)))
    return round(out_red * 255), round(out_green * 255), round(out_blue * 255), overlay_alpha


def generate(source_jar: Path, output: Path) -> None:
    with zipfile.ZipFile(source_jar) as archive, archive.open(VANILLA_TEXTURE) as source:
        image = Image.open(source).convert("RGBA")

    if image.size != (16, 16):
        raise ValueError(f"Expected vanilla totem texture to be 16x16, got {image.size!r}")

    overlay = Image.new("RGBA", image.size)
    for y in range(image.height):
        for x in range(image.width):
            overlay.putpixel((x, y), recolor_pixel(*image.getpixel((x, y)), x, y))

    output.parent.mkdir(parents=True, exist_ok=True)
    overlay.save(output, format="PNG", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_jar", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    generate(args.source_jar, args.output)


if __name__ == "__main__":
    main()
