#!/usr/bin/env python3
"""
Builds the emoji texture atlas used by the mod from the emoji referenced in the rule files.

Graphics: Twemoji (https://github.com/jdecked/twemoji), (c) Twitter, Inc and other contributors,
licensed CC-BY 4.0, taken from the `emoji-datasource-twitter` npm package (MIT metadata).

Usage:
    npm install --prefix tools/.cache emoji-datasource-twitter
    python3 tools/build_emoji_atlas.py tools/.cache/node_modules/emoji-datasource-twitter

Needs Pillow. Re-run whenever emoji are added to / removed from assets/lyricsworld/emoji/rules_*.json.
"""
import glob
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "core", "src", "main", "resources", "assets", "lyricsworld")
CELL = 64
COLS = 32
SIZE = 2048


def key(s):
    """Same normalisation as EmojiAtlas.key() in Java: code points, VS16/VS15 removed."""
    cps = [c for c in (ord(ch) for ch in s) if c not in (0xFE0F, 0xFE0E)]
    return "-".join("%x" % c for c in cps)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    pkg = sys.argv[1]
    data = json.load(open(os.path.join(pkg, "emoji.json"), encoding="utf-8"))
    by_key = {}
    for e in data:
        if not e.get("has_img_twitter"):
            continue
        by_key[key("".join(chr(int(x, 16)) for x in e["unified"].split("-")))] = e["image"]

    used = set()
    for f in sorted(glob.glob(os.path.join(RES, "emoji", "rules_*.json"))):
        d = json.load(open(f, encoding="utf-8"))
        for rules in d["categories"].values():
            for r in rules:
                used.update(r["e"])
    keys = sorted({key(e) for e in used})
    missing = [k for k in keys if k not in by_key]
    if missing:
        sys.exit("No Twemoji image for: " + ", ".join(missing))

    atlas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    index = {}
    for i, k in enumerate(keys):
        img = Image.open(os.path.join(pkg, "img", "twitter", "64", by_key[k])).convert("RGBA")
        if img.size != (CELL, CELL):
            img = img.resize((CELL, CELL), Image.LANCZOS)
        atlas.paste(img, ((i % COLS) * CELL, (i // COLS) * CELL))
        index[k] = i
    assert len(keys) <= COLS * (SIZE // CELL)

    tex_dir = os.path.join(RES, "textures")
    os.makedirs(tex_dir, exist_ok=True)
    out_png = os.path.join(tex_dir, "emoji_atlas.png")
    atlas.save(out_png, optimize=True)
    with open(os.path.join(RES, "emoji", "atlas.json"), "w", encoding="utf-8") as fh:
        json.dump({"cell": CELL, "cols": COLS, "width": SIZE, "height": SIZE, "emoji": index}, fh, separators=(",", ":"))
    print("%d emoji -> %s (%d KiB)" % (len(keys), out_png, os.path.getsize(out_png) // 1024))


if __name__ == "__main__":
    main()
