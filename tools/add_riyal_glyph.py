"""Adds the Saudi riyal sign (U+20C1) to the app's bundled Arabic font.

Why this exists: the sign was encoded in Unicode 17.0 (September 2025) and font
support is still thin. IBM Plex Sans Arabic 1.005 - the family this app ships -
has no glyph for it, and neither did any Noto build checked in September 2026. A
missing glyph is not a fallback, it is a box, and the app prints a currency on
every screen it has.

So the glyph is drawn into the font from the Saudi Central Bank's own published
outline. The result is not IBM Plex any more, and the OFL's reserved-name clause
says so: the family is renamed on the way out.

    python3 tools/add_riyal_glyph.py

Reads tools/upstream/plex_arabic_*.ttf plus tools/saudi_riyal_symbol.svg, and
writes app/src/main/res/font/masrouf_arabic_*.ttf. The upstream files live outside
the resource directory so the build ships one family rather than two. Idempotent;
run it again after any font upgrade.
"""

import re
import sys
from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path.parser import parse_path
from fontTools.ttLib import TTFont

RIYAL = 0x20C1
GLYPH_NAME = "saudiriyalsign"

# The sign sits at the height of the digits it stands beside - measured from the
# font rather than guessed, because a currency mark that is taller or shorter than
# the figures reads as a different typeface.
DIGIT = "zero"
SIDE_BEARING = 55

FAMILY = "Masrouf Arabic"
POSTSCRIPT = "MasroufArabic"
FONTS = {
    "plex_arabic_regular.ttf": ("masrouf_arabic_regular.ttf", "Regular"),
    "plex_arabic_medium.ttf": ("masrouf_arabic_medium.ttf", "Medium"),
    "plex_arabic_semibold.ttf": ("masrouf_arabic_semibold.ttf", "SemiBold"),
    "plex_arabic_bold.ttf": ("masrouf_arabic_bold.ttf", "Bold"),
}


def svg_paths(svg: str) -> list[str]:
    paths = re.findall(r'<path[^>]*\bd="([^"]+)"', svg)
    if not paths:
        raise SystemExit("no <path d=...> in the SVG")
    return paths


def view_box(svg: str) -> tuple[float, float]:
    m = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg)
    if not m:
        raise SystemExit("the SVG must carry a viewBox starting at the origin")
    return float(m.group(1)), float(m.group(2))


def draw(font: TTFont, paths: list[str], box: tuple[float, float]):
    """The SVG outline, scaled to the digits and flipped into font coordinates."""
    width, height = box
    glyphs = font.getGlyphSet()
    from fontTools.pens.boundsPen import BoundsPen

    bounds = BoundsPen(glyphs)
    glyphs[DIGIT].draw(bounds)
    target = bounds.bounds[3]

    scale = target / height
    # SVG's y grows downwards and a font's grows upwards, so the outline is
    # flipped about the baseline as it is scaled.
    transform = Transform(scale, 0, 0, -scale, SIDE_BEARING, height * scale)

    pen = TTGlyphPen(glyphs)
    for d in paths:
        parse_path(d, TransformPen(Cu2QuPen(pen, max_err=1.0), transform))
    return pen.glyph(), round(width * scale) + 2 * SIDE_BEARING


def rename(font: TTFont, style: str):
    """OFL 1.1: a modified font may not carry the reserved name."""
    name = font["name"]
    full = f"{FAMILY} {style}"
    for record in list(name.names):
        text = str(record)
        if record.nameID in (1, 16):
            value = FAMILY
        elif record.nameID == 4:
            value = full
        elif record.nameID == 6:
            value = f"{POSTSCRIPT}-{style}"
        elif record.nameID == 3:
            value = f"{POSTSCRIPT}-{style};masrouf"
        elif record.nameID == 0:
            value = text + " Modified for Masrouf: U+20C1 added."
        else:
            continue
        name.setName(value, record.nameID, record.platformID, record.platEncID, record.langID)


def patch(source: Path, target: Path, style: str, paths: list[str], box) -> str:
    font = TTFont(source)
    if RIYAL in font.getBestCmap():
        return f"{source.name}: already carries U+20C1, nothing to do"

    glyph, advance = draw(font, paths, box)
    font["glyf"].glyphs[GLYPH_NAME] = glyph
    font["hmtx"].metrics[GLYPH_NAME] = (advance, SIDE_BEARING)
    font.setGlyphOrder(font.getGlyphOrder() + [GLYPH_NAME])
    font["maxp"].numGlyphs = len(font.getGlyphOrder())
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[RIYAL] = GLYPH_NAME
    rename(font, style)
    font.save(target)
    return f"{target.name}: U+20C1 added, advance {advance}"


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    svg = (root / "tools" / "saudi_riyal_symbol.svg").read_text(encoding="utf-8")
    paths, box = svg_paths(svg), view_box(svg)
    upstream = root / "tools" / "upstream"
    fonts = root / "app" / "src" / "main" / "res" / "font"
    for source, (target, style) in FONTS.items():
        print(patch(upstream / source, fonts / target, style, paths, box))
    return 0


if __name__ == "__main__":
    sys.exit(main())
