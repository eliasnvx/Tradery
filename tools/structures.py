#!/usr/bin/env python3
"""Generates the GameTest structure data/tradery/structure/empty.nbt (5x5x5 of air) without dependencies.

Run from the repo root: python3 tools/structures.py. DATA_VERSION is Minecraft 26.3's world version.
"""
import gzip
import os
import struct

DATA_VERSION = 5023
SIZE = (5, 5, 5)
OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "data", "tradery", "structure", "empty.nbt")

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(text):
    data = text.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def int_tag(key, value):
    return bytes([TAG_INT]) + name(key) + struct.pack(">i", value)


def string_tag(key, value):
    return bytes([TAG_STRING]) + name(key) + name(value)


def list_tag(key, element_type, payloads):
    return bytes([TAG_LIST]) + name(key) + bytes([element_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def compound_payload(*tags):
    return b"".join(tags) + bytes([TAG_END])


def main():
    root = compound_payload(
        int_tag("DataVersion", DATA_VERSION),
        list_tag("size", TAG_INT, [struct.pack(">i", v) for v in SIZE]),
        list_tag("palette", TAG_COMPOUND, [compound_payload(string_tag("Name", "minecraft:air"))]),
        list_tag("blocks", TAG_COMPOUND, []),
        list_tag("entities", TAG_COMPOUND, []),
    )
    data = bytes([TAG_COMPOUND]) + name("") + root
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(data)
    print("wrote", os.path.relpath(OUT))


if __name__ == "__main__":
    main()
