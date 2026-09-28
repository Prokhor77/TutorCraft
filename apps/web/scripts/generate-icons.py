#!/usr/bin/env python3
"""Renders PNG app icons (PWA, NFR-COMP-02) from the Stitch logo (public/icons/logo.svg, 40×40 grid). Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

BRAND = (79, 70, 229, 255)  # #4F46E5
CAP_TOP = (238, 242, 255, 255)  # #EEF2FF
CAP_BODY = (199, 210, 254, 255)  # #C7D2FE
TASSEL = (16, 185, 129, 255)  # #10B981
GRID = 40
CORNER = 10
SUPERSAMPLE = 16
MASKABLE_SCALE = 0.7  # keeps the mark inside the maskable safe zone
BEZIER_STEPS = 24
OUT = Path(__file__).resolve().parent.parent / "public" / "icons"


def cubic(p0, p1, p2, p3):
    points = []
    for step in range(1, BEZIER_STEPS + 1):
        t = step / BEZIER_STEPS
        u = 1 - t
        points.append(tuple(u**3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t**3 * d for a, b, c, d in zip(p0, p1, p2, p3)))
    return points


def cap_body():
    # M14 18.5 V24 C14 26.5 17 28.5 20 28.5 C23 28.5 26 26.5 26 24 V18.5 L20 22.25 Z
    points = [(14, 18.5), (14, 24)]
    points += cubic((14, 24), (14, 26.5), (17, 28.5), (20, 28.5))
    points += cubic((20, 28.5), (23, 28.5), (26, 26.5), (26, 24))
    points += [(26, 18.5), (20, 22.25)]
    return points


def draw_mark(draw: ImageDraw.ImageDraw, unit: float, offset: float) -> None:
    def p(point):
        return (offset + point[0] * unit, offset + point[1] * unit)

    draw.polygon([p(pt) for pt in [(12, 15), (20, 10), (28, 15), (20, 20)]], fill=CAP_TOP)
    draw.polygon([p(pt) for pt in cap_body()], fill=CAP_BODY)
    draw.line([p((28, 24.5)), p((28, 28))], fill=TASSEL, width=round(1.5 * unit))
    for y in (24.5, 28):  # round line caps
        cx, cy = p((28, y))
        r = 0.75 * unit
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=TASSEL)
    cx, cy = p((28, 22))
    r = 2.5 * unit
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=TASSEL)


def render(size: int, maskable: bool) -> Image.Image:
    big = size * SUPERSAMPLE
    unit = big / GRID
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if maskable:
        draw.rectangle([0, 0, big, big], fill=BRAND)
        draw_mark(draw, unit * MASKABLE_SCALE, big * (1 - MASKABLE_SCALE) / 2)
    else:
        draw.rounded_rectangle([0, 0, big, big], radius=CORNER * unit, fill=BRAND)
        draw_mark(draw, unit, 0)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (192, 512):
        render(size, False).save(OUT / f"icon-{size}.png")
        render(size, True).save(OUT / f"icon-maskable-{size}.png")
    render(180, False).save(OUT / "apple-touch-icon.png")


if __name__ == "__main__":
    main()
