#!/usr/bin/env python3
"""Icons of the cinematic dialog style (GuiDialogCinematic).

This script is the source of the art: to change an icon, edit its grid here and
run it again. Never edit the PNG by hand.

    python tools/gen_dialog_icons.py                # writes the mod texture
    python tools/gen_dialog_icons.py --sheet x.png  # numbered contact sheet, x8

White on transparent, binary alpha: the Java side tints each icon with
glColor, so one icon works for normal and hover. Cells are 9x9 because that is
the size they are drawn at.

The index IS the cell: a new icon goes at the END of ICONS, never in between,
and DialogIcons.java gets the matching constant.
"""

import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "customnpcs", "textures", "gui", "dialog_icons.png")

CELL = 9
COLS = 7
SHEET = 64

# (name, grid). '#' = ink, '.' = empty. 9 rows of 9.
ICONS = [
    ("talk", [
        ".........",
        ".#######.",
        "#########",
        "##.#.#.##",
        "#########",
        ".#######.",
        "..##.....",
        ".#.......",
        ".........",
    ]),
    ("quest", [
        "..#####..",
        ".###.###.",
        "####.####",
        "####.####",
        "####.####",
        "#########",
        "####.####",
        ".#######.",
        "..#####..",
    ]),
    ("quit", [
        "##.....##",
        "###...###",
        ".###.###.",
        "..#####..",
        "...###...",
        "..#####..",
        ".###.###.",
        "###...###",
        "##.....##",
    ]),
    ("command", [
        "....#....",
        "....#....",
        "...###...",
        "..#####..",
        "#########",
        "..#####..",
        "...###...",
        "....#....",
        "....#....",
    ]),
    ("accept", [
        ".........",
        "........#",
        ".......##",
        "......##.",
        "#....##..",
        "##..##...",
        ".####....",
        "..##.....",
        ".........",
    ]),
    ("role_trader", [
        "..#####..",
        ".#######.",
        "###...###",
        "###.#####",
        "###...###",
        "#####.###",
        "###...###",
        ".#######.",
        "..#####..",
    ]),
    ("role_bank", [
        ".........",
        ".#######.",
        "#########",
        "#.......#",
        "#########",
        "####.####",
        "#########",
        "#########",
        ".........",
    ]),
    ("role_follower", [
        "....#....",
        "...###...",
        "...###...",
        "...###...",
        "...###...",
        "...###...",
        ".#######.",
        "....#....",
        "...###...",
    ]),
    ("role_transporter", [
        ".........",
        "#.......#",
        "##.....##",
        "###.#.###",
        "#########",
        ".###.###.",
        "..#...#..",
        ".........",
        ".........",
    ]),
    ("role_postman", [
        ".........",
        "#########",
        "##.....##",
        "#.#...#.#",
        "#..#.#..#",
        "#...#...#",
        "#.......#",
        "#########",
        ".........",
    ]),
    ("role_companion", [
        ".........",
        ".##...##.",
        "####.####",
        "#########",
        "#########",
        ".#######.",
        "..#####..",
        "...###...",
        "....#....",
    ]),
    ("role_mount", [
        ".........",
        "##.....##",
        "##.....##",
        "##.....##",
        "##.....##",
        "###...###",
        ".#######.",
        "..#####..",
        ".........",
    ]),
    ("role_none", [
        "....#....",
        ".#.###.#.",
        "..#####..",
        ".##...##.",
        "###...###",
        ".##...##.",
        "..#####..",
        ".#.###.#.",
        "....#....",
    ]),
]


def render():
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    for i, (name, grid) in enumerate(ICONS):
        assert len(grid) == CELL and all(len(r) == CELL for r in grid), name
        ox, oy = (i % COLS) * CELL, (i // COLS) * CELL
        assert oy + CELL <= SHEET, "the sheet is full"
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch == "#":
                    img.putpixel((ox + x, oy + y), (255, 255, 255, 255))
    return img


def sheet(img, path, zoom=8):
    """Numbered contact sheet on a dark background, to look at before shipping."""
    n = len(ICONS)
    cols = COLS
    rows = (n + cols - 1) // cols
    pad = 14
    w = cols * (CELL * zoom + pad) + pad
    h = rows * (CELL * zoom + pad + 12) + pad
    out = Image.new("RGB", (w, h), (24, 26, 34))
    d = ImageDraw.Draw(out)
    for i, (name, _) in enumerate(ICONS):
        cx = pad + (i % cols) * (CELL * zoom + pad)
        cy = pad + (i // cols) * (CELL * zoom + pad + 12)
        cell = img.crop(((i % COLS) * CELL, (i // COLS) * CELL, (i % COLS) * CELL + CELL, (i // COLS) * CELL + CELL))
        big = cell.resize((CELL * zoom, CELL * zoom), Image.NEAREST)
        d.rectangle([cx - 1, cy - 1, cx + CELL * zoom, cy + CELL * zoom], outline=(60, 64, 80))
        out.paste(big, (cx, cy), big)
        d.text((cx, cy + CELL * zoom + 2), "%d %s" % (i, name), fill=(200, 200, 210))
    out.save(path)


def main():
    img = render()
    if len(sys.argv) > 2 and sys.argv[1] == "--sheet":
        sheet(img, sys.argv[2])
        print("sheet:", sys.argv[2])
        return
    out = os.path.normpath(OUT)
    img.save(out)
    print("%d icons -> %s" % (len(ICONS), out))


if __name__ == "__main__":
    main()
