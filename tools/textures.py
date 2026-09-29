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


def main():
    png("gui/sprites/hud/coin.png", HUD_COIN, GOLD)


if __name__ == "__main__":
    main()
