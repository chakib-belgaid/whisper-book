#!/usr/bin/env python3
"""Split the combined theatre raster into a responsive arch and title plaque."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


PLAQUE_OUTLINE = [
    (439, 44),
    (1097, 44),
    (1115, 72),
    (1116, 113),
    (1136, 137),
    (1128, 179),
    (1114, 224),
    (1090, 250),
    (1000, 232),
    (900, 218),
    (768, 208),
    (636, 218),
    (536, 232),
    (446, 254),
    (420, 238),
    (406, 205),
    (396, 140),
    (415, 116),
    (413, 78),
]
PLAQUE_CROP = (368, 20, 1168, 306)
MASK_SCALE = 4


def render_plaque_mask(size: tuple[int, int]) -> Image.Image:
    mask = Image.new("L", (size[0] * MASK_SCALE, size[1] * MASK_SCALE), 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon(
        [(x * MASK_SCALE, y * MASK_SCALE) for x, y in PLAQUE_OUTLINE],
        fill=255,
    )
    return mask.resize(size, Image.Resampling.LANCZOS)


def split_frame(source_path: Path, output_directory: Path) -> None:
    source = Image.open(source_path).convert("RGBA")
    if source.size != (1536, 1024):
        raise ValueError(f"Expected a 1536x1024 source, got {source.size}")

    plaque_mask = render_plaque_mask(source.size)
    source_alpha = source.getchannel("A")

    arch = source.copy()
    arch.putalpha(ImageChops.multiply(source_alpha, ImageChops.invert(plaque_mask)))

    plaque = source.crop(PLAQUE_CROP)
    plaque_alpha = ImageChops.multiply(
        source_alpha.crop(PLAQUE_CROP),
        plaque_mask.crop(PLAQUE_CROP),
    )
    plaque.putalpha(plaque_alpha)

    output_directory.mkdir(parents=True, exist_ok=True)
    arch_path = output_directory / "theatre_arch.png"
    plaque_path = output_directory / "theatre_plaque.png"
    arch.save(arch_path, optimize=True)
    plaque.save(plaque_path, optimize=True)

    print(f"Wrote {arch_path} ({arch.width}x{arch.height})")
    print(f"Wrote {plaque_path} ({plaque.width}x{plaque.height})")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "source",
        nargs="?",
        type=Path,
        default=Path("art/source/theatre_frame.png"),
    )
    parser.add_argument(
        "output_directory",
        nargs="?",
        type=Path,
        default=Path("app/src/main/res/drawable-nodpi"),
    )
    arguments = parser.parse_args()
    split_frame(arguments.source, arguments.output_directory)


if __name__ == "__main__":
    main()
