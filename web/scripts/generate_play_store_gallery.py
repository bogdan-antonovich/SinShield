#!/usr/bin/env python3
"""Generate SinShield's portrait Google Play gallery artwork.

The layout intentionally mirrors the original temporary Play Store generator:
1242x2688 output, the website palette, a two-line Switzer-style headline,
and the real framed app screenshot rising from the bottom edge.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


WEB_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = WEB_ROOT / "public" / "images"
DEFAULT_OUTPUT_DIR = WEB_ROOT / "source-media" / "play-store-updated"

NAVY = (8, 45, 72)
BLUE = (8, 124, 240)
BG_TOP_LEFT = (234, 242, 255)
BG_BOTTOM_RIGHT = (214, 230, 251)
WHITE = (255, 255, 255)

WIDTH = 1242
HEIGHT = 2688
PHONE_WIDTH = 1050
HEADLINE_Y = 170
HEADLINE_SIZE = 96
HEADLINE_LINE_HEIGHT = 112
PHONE_GAP = 70

FONT_CANDIDATES = {
    "bold": (
        WEB_ROOT / "scripts" / "fonts" / "Switzer-Bold.ttf",
        Path("/usr/share/fonts/opentype/inter/Inter-Bold.otf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    ),
    "extra_bold": (
        WEB_ROOT / "scripts" / "fonts" / "Switzer-ExtraBold.ttf",
        Path("/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    ),
}


SLIDES = (
    (
        "Screenshot_20261009_005038.png",
        "01-protection.png",
        ((("Set up once,", NAVY),), (("always protected", BLUE),)),
    ),
    (
        "Screenshot_20261009_005117.png",
        "02-streak.png",
        ((("Build your streak", NAVY),), (("one day at a time", BLUE),)),
    ),
    (
        "Screenshot_20261009_005144.png",
        "03-reliability.png",
        ((("Reliable protection", NAVY),), (("under your control", BLUE),)),
    ),
    (
        "Screenshot_20261009_005201.png",
        "04-tuning.png",
        ((("Tune detection", NAVY),), (("to ", NAVY), ("your standard", BLUE))),
    ),
)


def load_font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    for candidate in FONT_CANDIDATES[weight]:
        if candidate.is_file():
            return ImageFont.truetype(candidate, size)
    raise FileNotFoundError(f"No usable {weight} font found")


def gradient_background() -> Image.Image:
    base = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = base.load()
    for y in range(HEIGHT):
        y_ratio = y / HEIGHT
        for x in range(WIDTH):
            amount = (x / WIDTH + y_ratio) / 2.0
            pixels[x, y] = tuple(
                int(start + (end - start) * amount)
                for start, end in zip(BG_TOP_LEFT, BG_BOTTOM_RIGHT)
            )
    return base.convert("RGBA")


def add_glow(image: Image.Image) -> None:
    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    center_x, center_y, radius = WIDTH // 2, 1180, 720
    draw.ellipse(
        (
            center_x - radius,
            center_y - radius,
            center_x + radius,
            center_y + radius,
        ),
        fill=(*WHITE, 60),
    )
    image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(radius // 3)))


def draw_headline(
    draw: ImageDraw.ImageDraw,
    lines: tuple[tuple[tuple[str, tuple[int, int, int]], ...], ...],
) -> int:
    headline_font = load_font("extra_bold", HEADLINE_SIZE)
    y = HEADLINE_Y
    for segments in lines:
        width = sum(draw.textlength(text, font=headline_font) for text, _ in segments)
        x = (WIDTH - width) / 2
        for text, color in segments:
            draw.text((x, y), text, font=headline_font, fill=color)
            x += draw.textlength(text, font=headline_font)
        y += HEADLINE_LINE_HEIGHT
    return y


def render_slide(source: Path, destination: Path, headline) -> None:
    canvas = gradient_background()
    add_glow(canvas)
    headline_bottom = draw_headline(ImageDraw.Draw(canvas), headline)

    phone = Image.open(source).convert("RGBA")
    phone_height = round(phone.height * PHONE_WIDTH / phone.width)
    phone = phone.resize((PHONE_WIDTH, phone_height), Image.Resampling.LANCZOS)

    phone_x = (WIDTH - PHONE_WIDTH) // 2
    phone_y = headline_bottom + PHONE_GAP

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = phone.getchannel("A").point(lambda value: round(value * 0.35))
    silhouette = Image.new("RGBA", phone.size, (*NAVY, 255))
    silhouette.putalpha(alpha)
    shadow.paste(silhouette, (phone_x, phone_y + 26), silhouette)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(34)))
    canvas.alpha_composite(phone, (phone_x, phone_y))

    destination.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(destination, "PNG", optimize=True)
    print(f"Wrote {destination} ({WIDTH}x{HEIGHT})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    missing = [name for name, _, _ in SLIDES if not (args.source_dir / name).is_file()]
    if missing:
        parser.error("Missing source screenshot(s): " + ", ".join(missing))

    for source_name, output_name, headline in SLIDES:
        render_slide(
            args.source_dir / source_name,
            args.output_dir / output_name,
            headline,
        )


if __name__ == "__main__":
    main()
