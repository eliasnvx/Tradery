#!/usr/bin/env python3
"""Generates Tradery's JSON assets and data (blockstates, models, item definitions, loot, recipes, tags).

Run from the repo root: python3 tools/assets.py. Output goes to common/src/main/resources. Hand edits to generated
files are overwritten: change this script instead.
"""
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources")
NS = "tradery"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, data):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote", path)


def face(tex, uv, cull=None, emission=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, tex_for_side, uv_for_side, cull=False, emission=None):
    """An element; tex_for_side(side) -> texture ref, uv_for_side(side) -> uv."""
    faces = {}
    for side in ("down", "up", "north", "south", "west", "east"):
        t = tex_for_side(side)
        if t is None:
            continue
        cullface = None
        if cull:
            cullface = {"down": "down" if frm[1] == 0 else None, "up": "up" if to[1] == 16 else None,
                        "north": "north" if frm[2] == 0 else None, "south": "south" if to[2] == 16 else None,
                        "west": "west" if frm[0] == 0 else None, "east": "east" if to[0] == 16 else None}[side]
        faces[side] = face(t, uv_for_side(side), cullface)
    element = {"from": frm, "to": to, "faces": faces}
    if emission is not None:
        element["light_emission"] = emission
    return element


def uv_box(frm, to):
    """Vanilla-style UVs for a box: each face uses the matching area of a 16x16 texture."""
    x1, y1, z1 = frm
    x2, y2, z2 = to

    def uv(side):
        if side in ("down", "up"):
            return [x1, z1, x2, z2]
        if side in ("north", "south"):
            return [x1, 16 - y2, x2, 16 - y1]
        return [z1, 16 - y2, z2, 16 - y1]
    return uv


# ---------------------------------------------------------------- vending block

BASE = ([0, 0, 0], [16, 6, 16])


def vending_base():
    frm, to = BASE
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/vending_side", "side": f"{NS}:block/vending_side",
                     "front": f"{NS}:block/vending_front", "top": f"{NS}:block/vending_top"},
        "elements": [box(frm, to, lambda s: "#front" if s == "north" else "#top" if s in ("up", "down") else "#side",
                         uv_box(frm, to), cull=True)],
    }


def case_elements(bottom):
    """Glass case, corner posts and lid above a base of height `bottom`."""
    glass = box([1, bottom, 1], [15, 15, 15], lambda s: None if s in ("up", "down") else "#glass",
                lambda s: [1, 1, 15, 16 - bottom])
    posts = []
    for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
        frm, to = [x, bottom, z], [x + 1, 15, z + 1]
        posts.append(box(frm, to, lambda s: "#metal", lambda s, f=frm, t=to: [0, 0, 1, 15 - bottom] if s not in ("up", "down") else [0, 0, 1, 1]))
    lid = box([0, 15, 0], [16, 16, 16], lambda s: "#lid" if s in ("up", "down") else "#metal", uv_box([0, 15, 0], [16, 16, 16]), cull=True)
    return [glass] + posts + [lid]


def vending_case():
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/vending_metal", "glass": {"force_translucent": True, "sprite": "minecraft:block/glass"},
                     "metal": f"{NS}:block/vending_metal", "lid": f"{NS}:block/vending_top"},
        "elements": case_elements(6),
    }


def vending_light(on):
    frm, to = [3, 2, -0.05], [8, 3, 0]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/vending_light_{'on' if on else 'off'}", "light": f"{NS}:block/vending_light_{'on' if on else 'off'}"},
        "elements": [box(frm, to, lambda s: "#light" if s == "north" else None, lambda s: [0, 0, 5, 1], emission=12 if on else None)],
    }


def vending_item_model():
    base = vending_base()
    case = vending_case()
    light = vending_light(True)
    textures = {}
    textures.update(base["textures"])
    textures.update(case["textures"])
    textures["light"] = light["textures"]["light"]
    return {"parent": "minecraft:block/block", "textures": textures,
            "elements": base["elements"] + case["elements"] + light["elements"],
            "display": block_item_display()}


def block_item_display():
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
    }


def vending_blockstate():
    parts = []
    for facing, y in FACINGS.items():
        rot = {"y": y} if y else {}
        parts.append({"when": {"facing": facing, "facade": "false"}, "apply": {"model": f"{NS}:block/vending_base", **rot}})
        parts.append({"when": {"facing": facing}, "apply": {"model": f"{NS}:block/vending_case", **rot}})
        parts.append({"when": {"facing": facing, "stocked": "true"}, "apply": {"model": f"{NS}:block/vending_light_on", **rot}})
        parts.append({"when": {"facing": facing, "stocked": "false"}, "apply": {"model": f"{NS}:block/vending_light_off", **rot}})
    return {"multipart": parts}


# ---------------------------------------------------------------- display block

def display_model():
    frm, to = [0, 0, 0], [16, 4, 16]
    base = box(frm, to, lambda s: "#top" if s in ("up", "down") else "#base", uv_box(frm, to), cull=True)
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/display_base", "base": f"{NS}:block/display_base", "top": f"{NS}:block/display_top",
                     "glass": {"force_translucent": True, "sprite": "minecraft:block/glass"},
                     "metal": f"{NS}:block/display_metal", "lid": f"{NS}:block/display_top"},
        "elements": [base] + case_elements(4),
        "display": block_item_display(),
    }


def display_blockstate():
    return {"variants": {f"facing={f}": ({"model": f"{NS}:block/display_block", "y": y} if y else {"model": f"{NS}:block/display_block"})
                         for f, y in FACINGS.items()}}


# ---------------------------------------------------------------- items, loot, recipes, tags

def item_definition(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def generated_item(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def block_loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}], "rolls": 1}],
        "random_sequence": f"{NS}:blocks/{name}",
    }


def main():
    write(f"assets/{NS}/blockstates/vending_block.json", vending_blockstate())
    write(f"assets/{NS}/models/block/vending_base.json", vending_base())
    write(f"assets/{NS}/models/block/vending_case.json", vending_case())
    write(f"assets/{NS}/models/block/vending_light_on.json", vending_light(True))
    write(f"assets/{NS}/models/block/vending_light_off.json", vending_light(False))
    write(f"assets/{NS}/models/block/vending_block_item.json", vending_item_model())
    write(f"assets/{NS}/items/vending_block.json", item_definition(f"{NS}:block/vending_block_item"))

    write(f"assets/{NS}/blockstates/display_block.json", display_blockstate())
    write(f"assets/{NS}/models/block/display_block.json", display_model())
    write(f"assets/{NS}/items/display_block.json", item_definition(f"{NS}:block/display_block"))

    write(f"assets/{NS}/models/item/vendor_key.json", generated_item(f"{NS}:item/vendor_key"))
    write(f"assets/{NS}/items/vendor_key.json", item_definition(f"{NS}:item/vendor_key"))

    for name in ("vending_block", "display_block"):
        write(f"data/{NS}/loot_table/blocks/{name}.json", block_loot(name))
    write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": [f"{NS}:vending_block", f"{NS}:display_block"]})

    write(f"data/{NS}/recipe/vending_block.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": "minecraft:glass", "C": "minecraft:chest", "O": "minecraft:gold_ingot"},
        "pattern": [" G ", "GCG", "OOO"],
        "result": {"id": f"{NS}:vending_block"},
    })
    write(f"data/{NS}/recipe/display_block.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": "minecraft:glass", "S": "#minecraft:slabs"},
        "pattern": ["GGG", "G G", "SSS"],
        "result": {"id": f"{NS}:display_block"},
    })


if __name__ == "__main__":
    main()
