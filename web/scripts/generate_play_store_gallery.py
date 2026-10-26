#!/usr/bin/env python3
"""Generate SinShield's portrait Google Play gallery artwork.

The layout intentionally mirrors the original temporary Play Store generator:
1242x2688 output, the website palette, a two-line Switzer-style headline,
and the real framed app screenshot rising from the bottom edge.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont


WEB_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = WEB_ROOT / "public" / "images"
DEFAULT_OUTPUT_DIR = WEB_ROOT / "source-media" / "play-store-updated"
DEFAULT_LEGACY_SOURCE_DIR = WEB_ROOT / "source-media" / "play-store-assets"
DEFAULT_LEGACY_OUTPUT_DIR = WEB_ROOT / "source-media" / "play-store"

NAVY = (8, 45, 72)
BLUE = (8, 124, 240)
BG_TOP_LEFT = (234, 242, 255)
BG_BOTTOM_RIGHT = (214, 230, 251)
WHITE = (255, 255, 255)

WIDTH = 1242
HEIGHT = 2688
# Every product card uses the width required by the longest headline so the
# device scale stays consistent throughout the gallery.
PHONE_WIDTH = 972
HEADLINE_Y = 170
HEADLINE_SIZE = 96
HEADLINE_LINE_HEIGHT = 112
PHONE_GAP = 70
# The device must begin on the exact same horizontal line on every card,
# regardless of whether its headline uses two or three lines.
PHONE_TOP = 546
PHONE_BOTTOM_MARGIN = 39
LEGACY_PHONE_WIDTH = 1050
LEGACY_PHONE_HEIGHT = 2213

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
        (
            (("Meet SinShield—", NAVY),),
            (("more than just", NAVY),),
            (("a website blocker", BLUE),),
        ),
    ),
    (
        "Screenshot_20261009_005117.png",
        "02-streak.png",
        ((("Build your streak", NAVY),), (("one day at a time", BLUE),)),
    ),
    (
        "Screenshot_20261009_005144.png",
        "03-reliability.png",
        (
            (("70K+", BLUE), (" domains blocked", NAVY)),
            (("add more anytime", BLUE),),
        ),
    ),
    (
        "Screenshot_20261009_005201.png",
        "04-tuning.png",
        ((("Tune detection", NAVY),), (("to ", NAVY), ("your standard", BLUE))),
    ),
)

LEGACY_SLIDES = (
    (
        "04-cover-source.png",
        "04-cover.png",
        ((('Covers explicit', NAVY),), (("content", NAVY), (" instantly", BLUE))),
    ),
    (
        "06-pause-source.png",
        "06-pause.png",
        ((('A built-in pause', NAVY),), (("to break ", NAVY), ("the pull", BLUE))),
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
    phone_y = PHONE_TOP
    phone_width = PHONE_WIDTH
    phone_height = round(phone.height * phone_width / phone.width)

    # Keep the complete device frame visible even when a longer headline uses
    # more vertical space. A small bottom margin makes that fit unambiguous.
    available_height = HEIGHT - phone_y - PHONE_BOTTOM_MARGIN
    if phone_height > available_height:
        phone_height = available_height
        phone_width = round(phone.width * phone_height / phone.height)

    phone = phone.resize((phone_width, phone_height), Image.Resampling.LANCZOS)
    phone_x = (WIDTH - phone_width) // 2

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


def render_resized_legacy_slide(source: Path, destination: Path, headline) -> None:
    """Rebuild an older card while matching the current gallery's phone width."""
    original = Image.open(source).convert("RGBA")
    original_background = gradient_background()
    add_glow(original_background)

    source_x = (WIDTH - LEGACY_PHONE_WIDTH) // 2
    source_y = HEADLINE_Y + 2 * HEADLINE_LINE_HEIGHT + PHONE_GAP
    crop_box = (
        source_x,
        source_y,
        source_x + LEGACY_PHONE_WIDTH,
        source_y + LEGACY_PHONE_HEIGHT,
    )
    phone = original.crop(crop_box)
    background_crop = original_background.crop(crop_box).convert("RGB")

    # Recover the device silhouette from the rendered card. The solid inner
    # mask keeps the screen opaque; the difference mask retains the frame,
    # buttons, antialiasing, and soft exterior shadow.
    difference = ImageChops.difference(phone.convert("RGB"), background_crop)
    red, green, blue = difference.split()
    difference_mask = ImageChops.lighter(ImageChops.lighter(red, green), blue).point(
        lambda value: min(255, max(0, (value - 1) * 20))
    )
    inner_mask = Image.new("L", phone.size, 0)
    ImageDraw.Draw(inner_mask).rounded_rectangle(
        (28, 28, phone.width - 29, phone.height - 29),
        radius=112,
        fill=255,
    )
    phone.putalpha(ImageChops.lighter(difference_mask, inner_mask))

    target_height = round(phone.height * PHONE_WIDTH / phone.width)
    phone = phone.resize((PHONE_WIDTH, target_height), Image.Resampling.LANCZOS)

    canvas = gradient_background()
    add_glow(canvas)
    draw_headline(ImageDraw.Draw(canvas), headline)
    phone_x = (WIDTH - PHONE_WIDTH) // 2
    phone_y = PHONE_TOP
    canvas.alpha_composite(phone, (phone_x, phone_y))

    destination.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(destination, "PNG", optimize=True)
    print(f"Wrote {destination} ({WIDTH}x{HEIGHT})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--legacy-source-dir",
        type=Path,
        default=DEFAULT_LEGACY_SOURCE_DIR,
    )
    parser.add_argument(
        "--legacy-output-dir",
        type=Path,
        default=DEFAULT_LEGACY_OUTPUT_DIR,
    )
    args = parser.parse_args()

    missing = [name for name, _, _ in SLIDES if not (args.source_dir / name).is_file()]
    if missing:
        parser.error("Missing source screenshot(s): " + ", ".join(missing))

    missing_legacy = [
        name for name, _, _ in LEGACY_SLIDES if not (args.legacy_source_dir / name).is_file()
    ]
    if missing_legacy:
        parser.error("Missing legacy source card(s): " + ", ".join(missing_legacy))

    for source_name, output_name, headline in SLIDES:
        render_slide(
            args.source_dir / source_name,
            args.output_dir / output_name,
            headline,
        )

    for source_name, output_name, headline in LEGACY_SLIDES:
        render_resized_legacy_slide(
            args.legacy_source_dir / source_name,
            args.legacy_output_dir / output_name,
            headline,
        )


if __name__ == "__main__":
    main()
