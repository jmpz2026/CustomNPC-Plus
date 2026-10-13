#!/usr/bin/env python3
"""Icon of the "Dialogs" tab of the N menu: cell (80, 0) of textures/gui/icons.png.

icons.png is upstream art (colored 16x16 icons with a dark outline), so this one
follows that style instead of the white icons of the dialog style. Only this cell
is written; the rest of the sheet is left as it is.

    python tools/gen_tab_icon.py                # paints the cell into icons.png
    python tools/gen_tab_icon.py --sheet x.png  # the tab icons at x8, to look at
"""

import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHEET = os.path.join(HERE, "..", "src", "main", "resources", "assets", "customnpcs", "textures", "gui", "icons.png")
CELL_X, CELL_Y = 80, 0

OUTLINE = (58, 42, 16, 255)
FILL = (255, 230, 168, 255)
SHADE = (224, 176, 80, 255)
DOT = (107, 74, 16, 255)

# o outline, f fill, s shade, d dot
GRID = [
    "................",
    "...oooooooooo...",
    "..offfffffffso..",
    ".offfffffffffso.",
    ".offfffffffffso.",
    ".offfffffffffso.",
    ".offddfddfddfso.",
    ".offddfddfddfso.",
    ".offfffffffffso.",
    ".offfffffffffso.",
    "..osssssssssso..",
    "...ossooooooo...",
    "...oso..........",
    "..oso...........",
    "..oo............",
    "................",
]

COLORS = {"o": OUTLINE, "f": FILL, "s": SHADE, "d": DOT}


def paint(img):
    assert len(GRID) == 16 and all(len(r) == 16 for r in GRID)
    for y, row in enumerate(GRID):
        for x, ch in enumerate(row):
            img.putpixel((CELL_X + x, CELL_Y + y), COLORS.get(ch, (0, 0, 0, 0)))
    return img


def main():
    img = paint(Image.open(SHEET).convert("RGBA"))
    if len(sys.argv) > 2 and sys.argv[1] == "--sheet":
        img.crop((0, 0, 96, 16)).resize((96 * 8, 16 * 8), Image.NEAREST).save(sys.argv[2])
        print("sheet:", sys.argv[2])
        return
    img.save(os.path.normpath(SHEET))
    print("tab icon -> %s" % os.path.normpath(SHEET))


if __name__ == "__main__":
    main()
