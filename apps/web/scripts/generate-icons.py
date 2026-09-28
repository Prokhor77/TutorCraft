#!/usr/bin/env python3
"""Renders PNG app icons (PWA, NFR-COMP-02) matching public/icons/icon.svg. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

BRAND = (79, 70, 229, 255)
WHITE = (255, 255, 255, 255)
SOFT = (199, 210, 254, 255)
BASE = 512
SUPERSAMPLE = 4
OUT = Path(__file__).resolve().parent.parent / "public" / "icons"


def draw_mark(draw: ImageDraw.ImageDraw, scale: float, offset: float) -> None:
    def p(x: float, y: float) -> tuple[float, float]:
        return (offset + x * scale, offset + y * scale)

    draw.polygon([p(256, 128), p(76, 212), p(256, 296), p(402, 228), p(402, 324), p(438, 324), p(438, 212)], fill=WHITE)
    draw.polygon([p(148, 268), p(148, 338), p(256, 400), p(364, 338), p(364, 268), p(256, 318)], fill=SOFT)


def render(size: int, maskable: bool) -> Image.Image:
    big = BASE * SUPERSAMPLE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if maskable:
        draw.rectangle([0, 0, big, big], fill=BRAND)
        draw_mark(draw, SUPERSAMPLE * 0.703, 76 * SUPERSAMPLE)
    else:
        draw.rounded_rectangle([0, 0, big, big], radius=112 * SUPERSAMPLE, fill=BRAND)
        draw_mark(draw, SUPERSAMPLE, 0)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (192, 512):
        render(size, False).save(OUT / f"icon-{size}.png")
        render(size, True).save(OUT / f"icon-maskable-{size}.png")
    render(180, False).save(OUT / "apple-touch-icon.png")


if __name__ == "__main__":
    main()
