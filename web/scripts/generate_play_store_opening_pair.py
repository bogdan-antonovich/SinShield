#!/usr/bin/env python3
"""Generate SinShield's two-card conversational opening for Google Play.

The two exports are slices of one continuous comic scene. The standard product
screenshots that follow them live in ``source-media/play-store-updated`` and are
deliberately not recreated or modified here.
"""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


WEB_ROOT = Path(__file__).resolve().parents[1]
SOURCE_MEDIA_DIR = WEB_ROOT / "source-media"
DEFAULT_OUTPUT_DIR = SOURCE_MEDIA_DIR / "play-store-v2"

PANEL_WIDTH = 1242
HEIGHT = 2688
PANEL_COUNT = 2
MASTER_WIDTH = PANEL_WIDTH * PANEL_COUNT

NAVY = (8, 45, 72)
BLUE = (8, 124, 240)
BG_TOP = (239, 246, 255)
BG_BOTTOM = (215, 231, 251)
WHITE = (255, 255, 255)

SWITZER_FONT = WEB_ROOT.parent / "app" / "src" / "main" / "res" / "font" / "switzer.ttf"
FONT_FALLBACK = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
FONT_FALLBACK_BOLD = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    """Load the product typeface at the requested variable-font weight."""
    if SWITZER_FONT.is_file():
        result = ImageFont.truetype(SWITZER_FONT, size)
        variation = {
            "body": "Regular",
            "semibold": "Semibold",
            "headline": "Extrabold",
        }[weight]
        result.set_variation_by_name(variation)
        return result

    fallback = FONT_FALLBACK if weight == "body" else FONT_FALLBACK_BOLD
    if fallback.is_file():
        return ImageFont.truetype(fallback, size)
    raise FileNotFoundError(f"No usable {weight} font found")


def gradient_panel() -> Image.Image:
    gradient = Image.new("RGB", (1, HEIGHT))
    gradient.putdata(
        [
            tuple(
                round(start + (end - start) * y / (HEIGHT - 1))
                for start, end in zip(BG_TOP, BG_BOTTOM)
            )
            for y in range(HEIGHT)
        ]
    )
    return gradient.resize((PANEL_WIDTH, HEIGHT)).convert("RGBA")


def build_background() -> Image.Image:
    master = Image.new("RGBA", (MASTER_WIDTH, HEIGHT))
    panel = gradient_panel()
    for index in range(PANEL_COUNT):
        master.alpha_composite(panel, (index * PANEL_WIDTH, 0))

    atmosphere = Image.new("RGBA", master.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(atmosphere)

    # One pool of light crosses the export seam, making the cards read as two
    # windows onto the same conversation instead of duplicated layouts.
    draw.ellipse((260, 650, MASTER_WIDTH - 220, 2670), fill=(*WHITE, 96))
    draw.ellipse((610, 820, MASTER_WIDTH - 430, 2440), fill=(*BLUE, 20))
    master.alpha_composite(atmosphere.filter(ImageFilter.GaussianBlur(190)))
    return master


def draw_centered_lines(
    draw: ImageDraw.ImageDraw,
    lines: tuple[tuple[str, tuple[int, int, int]], ...],
    center_x: int,
    y: int,
    text_font: ImageFont.FreeTypeFont,
    line_height: int,
) -> int:
    for text, color in lines:
        bounds = draw.textbbox((0, 0), text, font=text_font)
        width = bounds[2] - bounds[0]
        draw.text((center_x - width / 2, y), text, font=text_font, fill=color)
        y += line_height
    return y


def draw_speech_bubble(
    master: Image.Image,
    box: tuple[int, int, int, int],
    tail: tuple[tuple[int, int], tuple[int, int], tuple[int, int]],
    lines: tuple[tuple[str, tuple[int, int, int]], ...],
    text_size: int = 50,
    line_height: int = 62,
) -> None:
    def bubble_points(
        bounds: tuple[int, int, int, int],
        offset: tuple[int, int] = (0, 0),
    ) -> list[tuple[int, int]]:
        """Build a subtly hand-inked squircle for a modern manga balloon."""
        left, top, right, bottom = bounds
        center_x = (left + right) / 2
        center_y = (top + bottom) / 2
        radius_x = (right - left) / 2
        radius_y = (bottom - top) / 2
        points: list[tuple[int, int]] = []
        exponent = 2 / 3.35

        for index in range(120):
            angle = math.tau * index / 120
            cosine = math.cos(angle)
            sine = math.sin(angle)
            wobble = 1 + 0.007 * math.sin(angle * 7 + 0.4)
            x = center_x + radius_x * math.copysign(abs(cosine) ** exponent, cosine) * wobble
            y = center_y + radius_y * math.copysign(abs(sine) ** exponent, sine) * wobble
            points.append((round(x + offset[0]), round(y + offset[1])))
        return points

    points = bubble_points(box)
    shadow = Image.new("RGBA", master.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.polygon(tail, fill=(*NAVY, 70))
    shadow_draw.polygon(points, fill=(*NAVY, 70))
    master.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22)), (0, 18))

    draw = ImageDraw.Draw(master)
    accent_offset = (12, 14)
    accent_tail = tuple(
        (point[0] + accent_offset[0], point[1] + accent_offset[1]) for point in tail
    )
    draw.polygon(accent_tail, fill=BLUE)
    draw.polygon(bubble_points(box, accent_offset), fill=BLUE)

    draw.polygon(tail, fill=WHITE)
    draw.line((tail[0], tail[2], tail[1]), fill=NAVY, width=7, joint="curve")
    draw.polygon(points, fill=WHITE)
    draw.line(points + [points[0]], fill=NAVY, width=7, joint="curve")

    bubble_font = font("semibold", text_size)
    total_height = line_height * len(lines)
    y = box[1] + (box[3] - box[1] - total_height) / 2 - 2
    center_x = (box[0] + box[2]) // 2
    draw_centered_lines(draw, lines, center_x, round(y), bubble_font, line_height)


def draw_story_dialogue(master: Image.Image) -> None:
    # The outer bubbles point inward to the two people sharing the center seam.
    # Reading left-to-right across the exports gives one uninterrupted exchange.
    draw_speech_bubble(
        master,
        box=(45, 80, 790, 720),
        tail=((740, 450), (760, 570), (925, 720)),
        lines=(
            ("Did you know most", NAVY),
            ("blockers have", NAVY),
            ("a blind spot?", BLUE),
            ("They block porn sites—", NAVY),
            ("but explicit content", NAVY),
            ("inside apps can still", NAVY),
            ("get through.", BLUE),
        ),
        text_size=57,
        line_height=69,
    )
    draw_speech_bubble(
        master,
        box=(30, 1110, 760, 1630),
        tail=((710, 1400), (735, 1530), (920, 1740)),
        lines=(
            ("Wait, really?", NAVY),
            ("I want porn out of", NAVY),
            ("my life for good.", BLUE),
            ("How do I stay", NAVY),
            ("protected?", BLUE),
        ),
        text_size=58,
        line_height=70,
    )

    # Card two completes the same conversation from the opposite side.
    left = PANEL_WIDTH
    draw_speech_bubble(
        master,
        box=(left + 440, 80, left + 1190, 740),
        tail=((left + 475, 470), (left + 450, 590), (left + 325, 730)),
        lines=(
            ("SinShield detects", BLUE),
            ("and covers", NAVY),
            ("sexual content inside", NAVY),
            ("supported apps.", NAVY),
            ("Plus, it blocks", NAVY),
            ("70K+ porn domains—", BLUE),
            ("just like a regular", NAVY),
            ("blocker.", NAVY),
        ),
        text_size=55,
        line_height=65,
    )
    draw_speech_bubble(
        master,
        box=(left + 555, 1250, left + 1210, 1590),
        tail=((left + 590, 1400), (left + 570, 1515), (left + 360, 1740)),
        lines=(("Cool.", NAVY), ("I'm trying it", BLUE), ("right now!", BLUE)),
        text_size=64,
        line_height=76,
    )


def rasterize_svg(svg_path: Path, width: int) -> Image.Image:
    """Rasterize an SVG with ImageMagick while preserving transparency."""
    convert = shutil.which("convert")
    if not convert:
        raise RuntimeError("ImageMagick's 'convert' command is required for SVG artwork")

    with tempfile.TemporaryDirectory(prefix="sinshield-play-store-") as temp_dir:
        png_path = Path(temp_dir) / f"{svg_path.stem}.png"
        subprocess.run(
            [convert, "-background", "none", str(svg_path), "-resize", f"{width}x", str(png_path)],
            check=True,
        )
        with Image.open(png_path) as image:
            return image.convert("RGBA")


def add_soft_shadow(
    master: Image.Image,
    item: Image.Image,
    position: tuple[int, int],
    opacity: float,
    blur: int,
    y_offset: int,
) -> None:
    shadow = Image.new("RGBA", master.size, (0, 0, 0, 0))
    alpha = item.getchannel("A").point(lambda value: round(value * opacity))
    silhouette = Image.new("RGBA", item.size, (*NAVY, 255))
    silhouette.putalpha(alpha)
    shadow.paste(silhouette, (position[0], position[1] + y_offset), silhouette)
    master.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))


def add_story_character(
    master: Image.Image,
    svg_path: Path,
    position: tuple[int, int],
    width: int,
) -> None:
    character = rasterize_svg(svg_path, width=width)
    add_soft_shadow(master, character, position, opacity=0.12, blur=28, y_offset=22)
    master.alpha_composite(character, position)


def add_story_scenes(
    master: Image.Image,
    teller: Path,
    listener_satisfied: Path,
) -> None:
    # There is one speaker pair, not a duplicate pair per card. The teller
    # crosses the upper seam and the satisfied listener crosses the lower seam.
    add_story_character(master, teller, (875, 180), width=760)
    add_story_character(master, listener_satisfied, (845, 1480), width=980)


def build_master(
    teller: Path,
    listener_satisfied: Path,
) -> Image.Image:
    master = build_background()
    draw_story_dialogue(master)
    add_story_scenes(master, teller, listener_satisfied)
    return master


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--teller", type=Path, default=SOURCE_MEDIA_DIR / "teller2.svg")
    parser.add_argument(
        "--listener-satisfied",
        type=Path,
        default=SOURCE_MEDIA_DIR / "listener2.svg",
    )
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    source_paths = (
        args.teller,
        args.listener_satisfied,
    )
    for path in source_paths:
        if not path.is_file():
            parser.error(f"Missing source asset: {path}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    master = build_master(
        args.teller,
        args.listener_satisfied,
    )

    outputs = (
        ("01-blind-spot.png", 0),
        ("02-goes-further.png", PANEL_WIDTH),
    )
    for filename, left in outputs:
        path = args.output_dir / filename
        panel = master.crop((left, 0, left + PANEL_WIDTH, HEIGHT)).convert("RGB")
        panel.save(path, "PNG", optimize=True)
        print(f"Wrote {path} ({panel.width}x{panel.height})")

    preview_path = args.output_dir / "00-opening-pair-preview.png"
    preview = master.convert("RGB")
    preview.save(preview_path, "PNG", optimize=True)
    print(f"Wrote {preview_path} ({preview.width}x{preview.height})")


if __name__ == "__main__":
    main()
