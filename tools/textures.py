#!/usr/bin/env python3
"""Tradery textures as code: small ASCII pixel maps rendered to PNG (no dependencies).

Run from the repo root:  python3 tools/textures.py
Every texture in common/src/main/resources/assets/tradery/textures is generated here, so the art stays
reviewable in diffs and consistent in palette. Edit a map, re-run, commit both.
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "tradery", "textures")


def png(path, rows, palette):
    height = len(rows)
    width = len(rows[0])
    raw = bytearray()
    for row in rows:
        if len(row) != width:
            raise ValueError(f"{path}: ragged row {row!r}")
        raw.append(0)
        for ch in row:
            raw.extend(palette[ch])
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += chunk(b"IEND", b"")
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as f:
        f.write(data)
    print("wrote", os.path.relpath(full))


def rgba(hex_color, alpha=255):
    hex_color = hex_color.lstrip("#")
    return (int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16), alpha)


CLEAR = (0, 0, 0, 0)

GOLD = {
    ".": CLEAR,
    "o": rgba("6b4300"),
    "s": rgba("c98d10"),
    "b": rgba("f2c230"),
    "h": rgba("fff09a"),
    "t": rgba("a86f00"),
}

# 9x9 HUD coin with an embossed T
HUD_COIN = [
    "..ooooo..",
    ".ohhbbbo.",
    "ohbtttbso",
    "ohbbtbbso",
    "obbbtbbso",
    "obbbtbbso",
    "osbbbbsso",
    ".ossssso.",
    "..ooooo..",
]


# ---------------------------------------------------------------- vending block (red machine, brass trim)

MACHINE = {
    ".": CLEAR,
    "k": rgba("2b1414"),  # outline
    "r": rgba("a3302b"),  # body
    "R": rgba("c8483d"),  # highlight
    "d": rgba("7a211d"),  # shadow
    "b": rgba("c9962c"),  # brass
    "B": rgba("f0c95a"),  # brass highlight
    "n": rgba("7d5a14"),  # brass shadow
    "s": rgba("1a1a1a"),  # coin slot
    "g": rgba("3a3a3a"),  # slot rim
}


def grid(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    return rows


def vending_side():
    """Rows 10-15 are the 6 px base: brass band on top, red body, shadow. Rows 0-9 only fill unused UV space."""
    body = ["kRRRRRRRRRRRRRRk"] + ["krrrrrrrrrrrrrdk"] * 9
    base = ["BBBBBBBBBBBBBBBB", "bRRRRRRRRRRRRRRb", "brrrrrrrrrrrrrdb", "brrrrrrrrrrrrrdb", "bddddddddddddddb", "nnnnnnnnnnnnnnnn"]
    return grid(body + base)


def vending_front():
    rows = vending_side()
    rows = rows[:]
    # coin slot at x 11..13, rows 11..13 (y 2..4 from the bottom)
    rows[11] = rows[11][:10] + "ggg" + rows[11][13:]
    rows[12] = rows[12][:10] + "gsg" + rows[12][13:]
    rows[13] = rows[13][:10] + "gsg" + rows[13][13:]
    return grid(rows)


def vending_top():
    rows = ["BBBBBBBBBBBBBBBB"]
    for y in range(1, 15):
        if y in (1, 14):
            rows.append("bnbbbbbbbbbbbbnb")
        else:
            rows.append("bRrrrrrrrrrrrrdb")
    rows.append("nnnnnnnnnnnnnnnn")
    # rivets
    rows[2] = "bRBrrrrrrrrrrBdb"
    rows[13] = "bRBrrrrrrrrrrBdb"
    return grid(rows)


def vending_metal():
    return grid(["BbbbbbbbbbbbbbbB" if y % 5 == 0 else "bBbbbbbbbbbbbbnb" for y in range(16)])


LIGHT_ON = {".": CLEAR, "g": rgba("59ff5f"), "G": rgba("b8ffb0")}
LIGHT_OFF = {".": CLEAR, "g": rgba("3b3b3b"), "G": rgba("555555")}


def light():
    return grid(["GgggGggggggggggg"] + ["gggggggggggggggg"] * 15)


# ---------------------------------------------------------------- display block (quartz base, silver trim)

DISPLAY = {
    ".": CLEAR,
    "k": rgba("6f6a64"),
    "q": rgba("e8e3da"),
    "Q": rgba("f7f4ee"),
    "d": rgba("c9c2b6"),
    "m": rgba("a9b0b8"),
    "M": rgba("d9dee3"),
    "n": rgba("7c848c"),
}


def display_base():
    rows = ["qqqqqqqqqqqqqqqq"] * 12 + ["MMMMMMMMMMMMMMMM", "QqqqqqqqqqqqqqqQ", "qddddddddddddddq", "kkkkkkkkkkkkkkkk"]
    return grid(rows)


def display_top():
    rows = ["MMMMMMMMMMMMMMMM"] + ["mQqqqqqqqqqqqqdm"] * 14 + ["nnnnnnnnnnnnnnnn"]
    return grid(rows)


def display_metal():
    return grid(["MmmmmmmmmmmmmmmM" if y % 5 == 0 else "mMmmmmmmmmmmmmnm" for y in range(16)])


# ---------------------------------------------------------------- vendor key (admin item)

KEY = {
    ".": CLEAR,
    "o": rgba("3d2a00"),
    "b": rgba("d9a51f"),
    "B": rgba("ffe27a"),
    "n": rgba("8a6300"),
    "r": rgba("c0392b"),
}

VENDOR_KEY = [
    "................",
    "..........oooo..",
    ".........oBBbbo.",
    "........oBboobno",
    "........obo..ono",
    "........obno.bno",
    "........onbbbnbo",
    ".......oonnnnno.",
    "......obno......",
    ".....obno.......",
    "....obno........",
    "...obnooo.......",
    "..obnobno.......",
    ".obnoobo........",
    ".onno.o.........",
    "..oo............",
]


# ---------------------------------------------------------------- coins and coin ore overlays

def coin_palette(outline, dark, body, light, emboss):
    return {".": CLEAR, "o": rgba(outline), "s": rgba(dark), "b": rgba(body), "h": rgba(light), "t": rgba(emboss)}


COIN_PALETTES = {
    "copper": coin_palette("4a2410", "9c4f24", "d47a3f", "f2b27e", "7a3a18"),
    "silver": coin_palette("3a3f46", "8a9199", "c4cad1", "f2f5f7", "6d747c"),
    "gold": coin_palette("6b4300", "c98d10", "f2c230", "fff09a", "a86f00"),
}

# 16x16 coin: round, rim light top-left, embossed T
COIN = [
    "................",
    "................",
    ".....oooooo.....",
    "....ohhhhbbo....",
    "...ohhbbbbbso...",
    "..ohbbttttbbso..",
    "..ohbbbttbbbso..",
    "..ohbbbttbbbso..",
    "..ohbbbttbbbso..",
    "..obbbbttbbbso..",
    "..obbbbbbbbsso..",
    "...obbbbbbsso...",
    "....osssssso....",
    ".....oooooo.....",
    "................",
    "................",
]

# Coin specks on transparent background, drawn over vanilla stone / deepslate by the block model
ORE_OVERLAY = [
    "................",
    "..oo.......oo...",
    ".ohbo.....ohbo..",
    ".obso.....obso..",
    "..oo.........oo.",
    "............ohbo",
    "......oo....obso",
    ".....ohbo....oo.",
    ".....obso.......",
    "......oo........",
    ".oo.........oo..",
    "ohbo.......ohbo.",
    "obso.......obso.",
    ".oo....oo...oo..",
    ".......ohbo.....",
    ".......obso.....",
]


def main():
    png("gui/sprites/hud/coin.png", HUD_COIN, GOLD)
    png("block/vending_side.png", vending_side(), MACHINE)
    png("block/vending_front.png", vending_front(), MACHINE)
    png("block/vending_top.png", vending_top(), MACHINE)
    png("block/vending_metal.png", vending_metal(), MACHINE)
    png("block/vending_light_on.png", light(), LIGHT_ON)
    png("block/vending_light_off.png", light(), LIGHT_OFF)
    png("block/display_base.png", display_base(), DISPLAY)
    png("block/display_top.png", display_top(), DISPLAY)
    png("block/display_metal.png", display_metal(), DISPLAY)
    png("item/vendor_key.png", VENDOR_KEY, KEY)
    for tier, palette in COIN_PALETTES.items():
        png(f"item/{tier}_coin.png", COIN, palette)
        png(f"block/{tier}_coin_ore_overlay.png", ORE_OVERLAY, palette)


if __name__ == "__main__":
    main()
