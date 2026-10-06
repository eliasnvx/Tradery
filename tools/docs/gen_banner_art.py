#!/usr/bin/env python3
"""Background art for the mod page banner, drawn by an OpenAI image model (a draft we pick from; text, chips and
the real in-game screenshot are put on top by make_page_images.py).

    OPENAI_API_KEY=... python tools/docs/gen_banner_art.py [--n 3] [--model gpt-image-2.5-flare]
    -> art/generated/banner_bg/banner_bg_00N.png (git-ignored); copy the pick to art/page/banner_bg.png

The key is read from the environment only and never printed. Needs the `openai` package.
"""
import argparse
import base64
import datetime
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "art", "generated", "banner_bg")

PROMPT = (
    "Wide cinematic key art in the visual style of Minecraft Java Edition: a blocky voxel world built from "
    "one-metre cubes with chunky 16x16 pixel textures, rendered at high resolution with soft warm lighting and gentle "
    "depth of field. A cozy village marketplace street at golden hour. Along a dirt path stands a row of small shop "
    "vending blocks: each one is a short deep-red base with a brass trim band and a tall glass display case with thin "
    "brass corner posts on top, and inside each glass case a different item floats and glows softly (a loaf of bread, "
    "a diamond sword, a golden apple, a blue potion bottle). Small shiny gold coins float and sparkle in the air above "
    "the stalls. Oak fences with hanging lanterns, flower pots, spruce and oak trees, a warm orange and pink sunset sky "
    "with soft blocky clouds. Composition: the vending blocks and coins are in the right two thirds; the left third is "
    "calm, mostly sky and soft shadow, darker and uncluttered, leaving room for a title. No people, no characters, no "
    "animals. No text, no letters, no numbers, no logos, no watermark, no user interface."
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--n", type=int, default=3)
    parser.add_argument("--model", default=os.environ.get("OPENAI_IMAGE_MODEL", "gpt-image-2.5-flare"))
    parser.add_argument("--size", default="1536x1024")
    parser.add_argument("--quality", default="high")
    args = parser.parse_args()
    if not os.environ.get("OPENAI_API_KEY"):
        raise SystemExit("OPENAI_API_KEY is not set")
    from openai import OpenAI

    client = OpenAI()
    os.makedirs(OUT, exist_ok=True)
    result = client.images.generate(model=args.model, prompt=PROMPT, n=args.n, size=args.size, quality=args.quality)
    files = []
    start = len([f for f in os.listdir(OUT) if f.endswith(".png")])
    for i, image in enumerate(result.data, start=start + 1):
        name = f"banner_bg_{i:03d}.png"
        with open(os.path.join(OUT, name), "wb") as f:
            f.write(base64.b64decode(image.b64_json))
        files.append(name)
    meta_path = os.path.join(OUT, "meta.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {"runs": []}
    meta["runs"].append({"created": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
                         "model": args.model, "size": args.size, "quality": args.quality, "prompt": PROMPT, "files": files})
    json.dump(meta, open(meta_path, "w"), indent=2)
    print("wrote", ", ".join(files))


if __name__ == "__main__":
    main()
