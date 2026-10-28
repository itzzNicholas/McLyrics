#!/usr/bin/env python3
"""
Builds the glyph atlas for the in-world lyrics font (Rock Salt, Apache-2.0) used by LyricsWorld.

    npm pack @fontsource/rock-salt && tar xzf fontsource-rock-salt-*.tgz
    pip install pillow fonttools
    python3 tools/build_font_atlas.py package/files/rock-salt-latin-400-normal.woff

Output (committed): core/src/main/resources/assets/lyricsworld/textures/font_atlas.png and
                    core/src/main/resources/assets/lyricsworld/font/glyphs.json
The atlas holds, for every character, a white fill glyph and (in a second block) a dilated outline mask;
the renderer tints both. Rock Salt: (c) Font Diner, Inc DBA Sideshow, Apache License 2.0
(docs/fonts/LICENSE-RockSalt-Apache-2.0.txt).
"""
import json
import os
import sys

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "core", "src", "main", "resources", "assets", "lyricsworld")
SIZE = 42          # font size in atlas pixels ("base size" used by the layout code)
CELL = 104
PAD = 10
COLS = 19
OUTLINE = 11       # MaxFilter size (odd) -> ~5px outline at base size
BOLD = 3           # MaxFilter size used to thicken Rock Salt's thin marker strokes


def charset():
    chars = [chr(c) for c in range(32, 127)] + [chr(c) for c in range(0xA1, 0x100)]
    chars += list("‘’“”–—…•♪")
    return chars


def main():
    src = sys.argv[1]
    tt = TTFont(src)
    tt.flavor = None
    buf = io.BytesIO()
    tt.save(buf)
    buf.seek(0)
    font = ImageFont.truetype(buf, SIZE)
    cmap = tt.getBestCmap()
    chars = [c for c in charset() if ord(c) in cmap]

    n = len(chars)
    rows = (2 * n + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL, rows * CELL), (255, 255, 255, 0))
    meta = {}
    ascent, descent = font.getmetrics()
    for i, ch in enumerate(chars):
        adv = font.getlength(ch)
        if ch == " ":
            meta[ch] = {"c": -1, "a": round(adv, 2)}
            continue
        l, t, r, b = font.getbbox(ch, anchor="ls")
        w, h = r - l, b - t
        if w <= 0 or h <= 0:  # blank glyph (nbsp, soft hyphen...)
            meta[ch] = {"c": -1, "a": round(adv, 2)}
            continue
        # very tall glyphs (accented capitals in Rock Salt) are rendered smaller and scaled back up at draw time
        fit = min(1.0, (CELL - 2 * PAD) / float(max(w, h)))
        gfont = font
        if fit < 1.0:
            fit = int(fit * 100) / 100.0
            buf.seek(0)
            gfont = ImageFont.truetype(buf, max(4, int(SIZE * fit)))
            fit = gfont.size / float(SIZE)
            l, t, r, b = gfont.getbbox(ch, anchor="ls")
        px, py = PAD - l, PAD - t
        mask = Image.new("L", (CELL, CELL), 0)
        ImageDraw.Draw(mask).text((px, py), ch, font=gfont, fill=255, anchor="ls")
        mask = mask.filter(ImageFilter.MaxFilter(BOLD)).filter(ImageFilter.GaussianBlur(0.6))
        outline = mask.filter(ImageFilter.MaxFilter(OUTLINE)).filter(ImageFilter.GaussianBlur(1.2))
        for block, m in ((0, mask), (n, outline)):
            cell = Image.new("RGBA", (CELL, CELL), (255, 255, 255, 0))
            cell.putalpha(m)
            k = block + i
            atlas.paste(cell, ((k % COLS) * CELL, (k // COLS) * CELL))
        q = 1.0 / fit
        meta[ch] = {"c": i, "a": round(adv, 2), "ox": round((l - PAD) * q, 2), "oy": round((t - PAD) * q, 2), "q": round(q, 3)}

    os.makedirs(os.path.join(RES, "textures"), exist_ok=True)
    os.makedirs(os.path.join(RES, "font"), exist_ok=True)
    out_png = os.path.join(RES, "textures", "font_atlas.png")
    atlas.save(out_png, optimize=True)
    with open(os.path.join(RES, "font", "glyphs.json"), "w", encoding="utf-8") as fh:
        json.dump({"size": SIZE, "cell": CELL, "cols": COLS, "width": atlas.width, "height": atlas.height,
                   "count": n, "ascent": ascent, "descent": descent, "glyphs": meta},
                  fh, ensure_ascii=False, separators=(",", ":"))
    print("%d glyphs -> %s %dx%d (%d KiB)" % (n, out_png, atlas.width, atlas.height, os.path.getsize(out_png) // 1024))


if __name__ == "__main__":
    main()
