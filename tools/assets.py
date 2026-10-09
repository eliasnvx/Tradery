#!/usr/bin/env python3
"""Generates Tradery's JSON assets and data (blockstates, block and item models, loot, recipes, tags, worldgen).

Run from the repo root: python3 tools/assets.py. Output goes to common/src/main/resources. Hand edits to generated
files are overwritten: change this script instead.

Formats are Minecraft 1.20.1's (pack format 15): plural data folders (loot_tables, recipes, tags/blocks, tags/items),
item models in models/item, configured features in worldgen/configured_feature, loot "functions"/"conditions", recipe
ingredients as {"item"}/{"tag"} objects and results as {"item", "count"}, match_tool predicates as
{"enchantments": [{"enchantment", "levels"}]}. Translucent and cutout parts use Forge's model "render_type" (vanilla and
Fabric ignore it; Fabric gets the same layers from BlockRenderLayerMap in code). Emissive faces use Forge's "forge_data"
block light (Fabric: no glow). Coin ores reach the biomes through a Forge biome modifier (forge/biome_modifier) or
Fabric's BiomeModifications in code.
"""
import json
import os

RES = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources")
NS = "tradery"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
# Forge render types (model "render_type"): the case glass is translucent, the ore overlay is cut out like grass
TRANSLUCENT = "minecraft:translucent"
CUTOUT_MIPPED = "minecraft:cutout_mipped"


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
        # 1.20.1 has no per-element "light_emission"; Forge reads the face block light from "forge_data" (ForgeFaceData)
        element["forge_data"] = {"block_light": emission}
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

# Base heights in pixels; VendingBlock.BASE_HEIGHT and DisplayBlock.BASE_HEIGHT must match
VENDING_BASE = 3
DISPLAY_BASE = 2
BASE = ([0, 0, 0], [16, VENDING_BASE, 16])


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
    """Glass case above a base of height `bottom`: glass walls, corner posts, and a metal frame holding a glass top."""
    glass = box([1, bottom, 1], [15, 15, 15], lambda s: None if s in ("up", "down") else "#glass",
                lambda s: [1, 1, 15, 16 - bottom])
    # A flat pane one pixel below the frame's top; the down face keeps it visible through the walls
    glass_top = box([2, 15, 2], [14, 15, 14], lambda s: "#glass" if s in ("up", "down") else None, lambda s: [2, 2, 14, 14])
    posts = []
    for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
        frm, to = [x, bottom, z], [x + 1, 15, z + 1]
        posts.append(box(frm, to, lambda s: "#metal", lambda s, f=frm, t=to: [0, 0, 1, 15 - bottom] if s not in ("up", "down") else [0, 0, 1, 1]))
    frame = []
    for frm, to, hidden in (([0, 15, 0], [16, 16, 2], ()), ([0, 15, 14], [16, 16, 16], ()),
                            ([0, 15, 2], [2, 16, 14], ("north", "south")), ([14, 15, 2], [16, 16, 14], ("north", "south"))):
        frame.append(box(frm, to, lambda s, h=hidden: None if s in h else "#metal", uv_box(frm, to), cull=True))
    return [glass, glass_top] + posts + frame


def vending_case():
    # Its own multipart model, so on Forge only the case renders translucent and the base stays solid
    return {
        "parent": "minecraft:block/block",
        "render_type": TRANSLUCENT,
        "textures": {"particle": f"{NS}:block/vending_metal", "glass": f"{NS}:block/case_glass",
                     "metal": f"{NS}:block/vending_metal"},
        "elements": case_elements(VENDING_BASE),
    }


def vending_light(on):
    # Seen from the front the face is mirrored: x 8-13 is the left side, clear of the coin slot at texture x 10-13
    frm, to = [8, 1, -0.05], [13, 2, 0]
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
    return {"parent": "minecraft:block/block", "render_type": TRANSLUCENT, "textures": textures,
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
    frm, to = [0, 0, 0], [16, DISPLAY_BASE, 16]
    base = box(frm, to, lambda s: "#top" if s in ("up", "down") else "#base", uv_box(frm, to), cull=True)
    # One model for base and case: the whole block renders translucent (opaque pixels still draw as opaque)
    return {
        "parent": "minecraft:block/block",
        "render_type": TRANSLUCENT,
        "textures": {"particle": f"{NS}:block/display_base", "base": f"{NS}:block/display_base", "top": f"{NS}:block/display_top",
                     "glass": f"{NS}:block/case_glass",
                     "metal": f"{NS}:block/display_metal"},
        "elements": [base] + case_elements(DISPLAY_BASE),
        "display": block_item_display(),
    }


def display_blockstate():
    return {"variants": {f"facing={f}": ({"model": f"{NS}:block/display_block", "y": y} if y else {"model": f"{NS}:block/display_block"})
                         for f, y in FACINGS.items()}}


# ---------------------------------------------------------------- items, loot, recipes, tags

def block_item_model(model):
    """models/item/<block>.json: the block item shows a block model (its "display" transforms and render type carry over)."""
    return {"parent": model}


def generated_item(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def ingredient(item_id):
    """A recipe ingredient: 1.20.1 takes {"item": id} or {"tag": id} objects, not plain strings ("#tag")."""
    return {"tag": item_id[1:]} if item_id.startswith("#") else {"item": item_id}


def result(item_id, count=1):
    """A recipe result: 1.20.1 names the item "item" (1.20.5+ renamed it to "id")."""
    return {"item": item_id, "count": count}


def block_loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{"conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}], "rolls": 1}],
        "random_sequence": f"{NS}:blocks/{name}",
    }


# Same check as vanilla 1.20.1 ore loot (1.21.1: item sub-predicates, 26.x: the predicate minecraft:tool/can_silk_touch)
SILK_TOUCH = {
    "condition": "minecraft:match_tool",
    "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]},
}


# ---------------------------------------------------------------- coins and coin ore

TIERS = {
    # tier: (vein size, veins per chunk, min y, max y, coins min, coins max, harder tool tag)
    "copper": (5, 6, 0, 96, 1, 3, "minecraft:needs_stone_tool"),
    "silver": (4, 3, -32, 32, 1, 2, "minecraft:needs_iron_tool"),
    "gold": (3, 1, -64, -16, 1, 1, "minecraft:needs_iron_tool"),
}


def ore_model(base_texture, overlay_texture):
    """Vanilla stone/deepslate with our transparent coin overlay on top (like grass block sides): no copied pixels."""
    faces = lambda tex: {side: {"uv": [0, 0, 16, 16], "texture": tex, "cullface": side}
                         for side in ("down", "up", "north", "south", "west", "east")}
    return {
        "parent": "minecraft:block/block",
        # 1.20.1 picks the layer per block, not per sprite: the overlay's clear pixels need a cutout layer (like grass)
        "render_type": CUTOUT_MIPPED,
        "textures": {"particle": base_texture, "base": base_texture, "overlay": overlay_texture},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#base")},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#overlay")},
        ],
    }


def ore_loot(block, coin, lo, hi):
    count = {"function": "minecraft:set_count", "count": lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}}
    return {
        "type": "minecraft:block",
        "pools": [{
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {"type": "minecraft:item", "conditions": [SILK_TOUCH], "name": f"{NS}:{block}"},
                    # No apply_bonus: Fortune doesn't multiply money. A data pack can add it here.
                    {"type": "minecraft:item", "functions": [count, {"function": "minecraft:explosion_decay"}], "name": f"{NS}:{coin}"},
                ],
            }],
            "rolls": 1,
        }],
        "random_sequence": f"{NS}:blocks/{block}",
    }


def ore_feature(tier, size):
    """A configured feature (worldgen/configured_feature): the feature type plus its "config"; states are {"Name"} objects."""
    return {
        "type": "minecraft:ore",
        "config": {
            "discard_chance_on_air_exposure": 0.0,
            "size": size,
            "targets": [
                {"state": {"Name": f"{NS}:{tier}_coin_ore"},
                 "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
                {"state": {"Name": f"{NS}:deepslate_{tier}_coin_ore"},
                 "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}},
            ],
        },
    }


def ore_placed(tier, count, lo, hi):
    return {
        "feature": f"{NS}:{tier}_coin_ore",  # the configured feature of the same name
        "placement": [
            {"type": "minecraft:count", "count": count},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range",
             "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"},
            {"type": f"{NS}:enabled_in_config"},
        ],
    }


def coins_and_ores():
    tool_tags = {}
    ores = []
    for tier, (size, count, lo, hi, cmin, cmax, tool) in TIERS.items():
        coin = f"{tier}_coin"
        write(f"assets/{NS}/models/item/{coin}.json", generated_item(f"{NS}:item/{coin}"))
        for deepslate in (False, True):
            block = f"deepslate_{tier}_coin_ore" if deepslate else f"{tier}_coin_ore"
            base = "minecraft:block/deepslate" if deepslate else "minecraft:block/stone"
            write(f"assets/{NS}/models/block/{block}.json", ore_model(base, f"{NS}:block/{tier}_coin_ore_overlay"))
            write(f"assets/{NS}/blockstates/{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})
            write(f"assets/{NS}/models/item/{block}.json", block_item_model(f"{NS}:block/{block}"))
            write(f"data/{NS}/loot_tables/blocks/{block}.json", ore_loot(block, coin, cmin, cmax))
            tool_tags.setdefault(tool, []).append(f"{NS}:{block}")
            ores.append(f"{NS}:{block}")
        write(f"data/{NS}/worldgen/configured_feature/{tier}_coin_ore.json", ore_feature(tier, size))
        write(f"data/{NS}/worldgen/placed_feature/{tier}_coin_ore.json", ore_placed(tier, count, lo, hi))
    for tag, blocks in tool_tags.items():
        ns, path = tag.split(":")
        write(f"data/{ns}/tags/blocks/{path}.json", {"values": blocks})
    write(f"data/{NS}/tags/blocks/coin_ores.json", {"values": ores})
    write(f"data/{NS}/tags/items/coin_ores.json", {"values": ores})
    write(f"data/{NS}/tags/items/coins.json", {"values": [f"{NS}:{t}_coin" for t in TIERS]})
    write(f"data/{NS}/tags/worldgen/biome/has_coin_ore.json", {"values": ["#minecraft:is_overworld"]})
    # Forge 47 reads its biome_modifier registry from data/<ns>/forge/biome_modifier (Fabric ignores the file)
    write(f"data/{NS}/forge/biome_modifier/coin_ores.json", {
        "type": "forge:add_features",
        "biomes": f"#{NS}:has_coin_ore",
        "features": [f"{NS}:{t}_coin_ore" for t in TIERS],
        "step": "underground_ores",
    })
    return ores


def main():
    write(f"assets/{NS}/blockstates/vending_block.json", vending_blockstate())
    write(f"assets/{NS}/models/block/vending_base.json", vending_base())
    write(f"assets/{NS}/models/block/vending_case.json", vending_case())
    write(f"assets/{NS}/models/block/vending_light_on.json", vending_light(True))
    write(f"assets/{NS}/models/block/vending_light_off.json", vending_light(False))
    write(f"assets/{NS}/models/block/vending_block_item.json", vending_item_model())
    write(f"assets/{NS}/models/item/vending_block.json", block_item_model(f"{NS}:block/vending_block_item"))

    write(f"assets/{NS}/blockstates/display_block.json", display_blockstate())
    write(f"assets/{NS}/models/block/display_block.json", display_model())
    write(f"assets/{NS}/models/item/display_block.json", block_item_model(f"{NS}:block/display_block"))

    write(f"assets/{NS}/models/item/vendor_key.json", generated_item(f"{NS}:item/vendor_key"))

    for name in ("vending_block", "display_block"):
        write(f"data/{NS}/loot_tables/blocks/{name}.json", block_loot(name))
    ores = coins_and_ores()
    write("data/minecraft/tags/blocks/mineable/pickaxe.json", {"values": [f"{NS}:vending_block", f"{NS}:display_block"] + ores})

    write(f"data/{NS}/recipes/vending_block.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": ingredient("minecraft:glass"), "C": ingredient("minecraft:chest"), "O": ingredient("minecraft:gold_ingot")},
        "pattern": [" G ", "GCG", "OOO"],
        "result": result(f"{NS}:vending_block"),
    })
    write(f"data/{NS}/recipes/display_block.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"G": ingredient("minecraft:glass"), "S": ingredient("#minecraft:slabs")},
        "pattern": ["GGG", "G G", "SSS"],
        "result": result(f"{NS}:display_block"),
    })


if __name__ == "__main__":
    main()
