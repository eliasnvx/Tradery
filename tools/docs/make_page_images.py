#!/usr/bin/env python3
"""Mod page images (CurseForge / Modrinth / README): banner, section headers, page screenshots and the gallery.

    TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest   # in-game docs_* screenshots
    python3 tools/docs/make_page_images.py                       # writes docs/images/{banner,header_*}.png, page/, gallery/

Inputs: art/page/banner_bg.png (AI background, tools/docs/gen_banner_art.py), art/page/icons/*.png (AI icons via
texgen, pixelized to 32x32), the mod's own HUD coin, and the real screenshots. Text, chips and frames are drawn here
with a 5x7 pixel font (the same one as Femboy Mod's page).
"""
import glob
import os

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SHOTS = os.path.join(ROOT, "fabric", "build", "run", "clientGameTest", "screenshots")
ART = os.path.join(ROOT, "art", "page")
OUT = os.path.join(ROOT, "docs", "images")
COIN = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "tradery", "textures", "gui", "sprites", "hud", "coin.png")

GOLD = (240, 201, 90)
GOLD_DARK = (125, 90, 20)
INK = (40, 18, 12)
CREAM = (255, 240, 215)
RED = (163, 48, 43)

# ------------------------------------------------------------------------------------------------ pixel font (5x7)

GLYPHS = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["111", "010", "010", "010", "010", "010", "111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10011", "10101", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    " ": ["000", "000", "000", "000", "000", "000", "000"],
    ".": ["0", "0", "0", "0", "0", "0", "1"],
    ",": ["00", "00", "00", "00", "00", "01", "10"],
    ":": ["0", "1", "0", "0", "0", "1", "0"],
    "!": ["1", "1", "1", "1", "1", "0", "1"],
    "'": ["1", "1", "0", "0", "0", "0", "0"],
    "-": ["000", "000", "000", "111", "000", "000", "000"],
    "+": ["000", "010", "010", "111", "010", "010", "000"],
    "/": ["00001", "00010", "00010", "00100", "01000", "01000", "10000"],
    "&": ["01100", "10010", "10100", "01000", "10101", "10010", "01101"],
    "%": ["11001", "11010", "00010", "00100", "01000", "01011", "10011"],
    "(": ["01", "10", "10", "10", "10", "10", "01"],
    ")": ["10", "01", "01", "01", "01", "01", "10"],
    "*": ["01010", "11011", "11111", "11111", "01110", "00100", "00000"],  # heart
}


def text_width(text, scale):
    return sum((len(GLYPHS.get(ch, GLYPHS[" "])[0]) + 1) * scale for ch in text.upper()) - scale


def draw_text(img, text, x, y, scale, fill, shadow=INK, outline=None):
    """Pixel text; `*` draws a heart. Shadow one font pixel down-right, optional 1px outline."""
    def blit(ox, oy, color):
        cx = ox
        for ch in text.upper():
            glyph = GLYPHS.get(ch, GLYPHS[" "])
            for gy, row in enumerate(glyph):
                for gx, bit in enumerate(row):
                    if bit == "1":
                        ImageDraw.Draw(img).rectangle([cx + gx * scale, oy + gy * scale,
                                                       cx + (gx + 1) * scale - 1, oy + (gy + 1) * scale - 1], fill=color)
            cx += (len(glyph[0]) + 1) * scale
    if outline:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            blit(x + dx * max(1, scale // 3), y + dy * max(1, scale // 3), outline)
    if shadow:
        blit(x + scale, y + scale, shadow)
    blit(x, y, fill)


# ------------------------------------------------------------------------------------------------ helpers


def pixel_scale(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def chip(img, x, y, text, scale=4, fill=(30, 14, 10, 205), border=GOLD, color=CREAM):
    """A rounded label; returns its width."""
    pad_x, pad_y = 18, 12
    w = text_width(text, scale) + pad_x * 2
    h = 7 * scale + pad_y * 2
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([x, y, x + w, y + h], radius=h // 2, fill=fill, outline=border, width=3)
    img.alpha_composite(layer)
    draw_text(img, text, x + pad_x, y + pad_y, scale, color, shadow=INK)
    return w


def horizontal_shade(size, color, alpha_left, alpha_right, until):
    """A colour layer that fades from alpha_left at x=0 to alpha_right at x=until (and stays there)."""
    w, h = size
    layer = Image.new("RGBA", size, color + (0,))
    px = layer.load()
    for x in range(w):
        t = min(1.0, x / until)
        a = int(alpha_left + (alpha_right - alpha_left) * (t * t * (3 - 2 * t)))
        for y in range(h):
            px[x, y] = color + (a,)
    return layer


def background():
    bg = Image.open(os.path.join(ART, "banner_bg.png")).convert("RGB")
    return bg


# ------------------------------------------------------------------------------------------------ banner


def banner():
    """The whole scene on the right; on the left its sky, stretched and blurred, under the title."""
    bg = background()
    art = bg.resize((bg.width * 700 // bg.height, 700), Image.LANCZOS).crop((0, 40, bg.width * 700 // bg.height, 680))
    left_w = 1920 - art.width + 240
    img = Image.new("RGBA", (1920, 640))
    img.paste(art.crop((0, 0, 240, 640)).resize((left_w, 640), Image.LANCZOS).filter(ImageFilter.GaussianBlur(24)), (0, 0))
    # The art fades in over its first 240 px, so the stretched sky continues it without a seam
    full = Image.new("L", art.size, 255)
    ramp = full.load()
    for x in range(240):
        a = int(255 * (x / 239) ** 1.5)
        for y in range(art.height):
            ramp[x, y] = a
    img.paste(art.convert("RGBA"), (1920 - art.width, 0), full)
    img.alpha_composite(horizontal_shade(img.size, (20, 9, 6), 200, 0, 1000))
    img.alpha_composite(vertical_shade(img.size, (20, 9, 6), 0, 90))

    coin = pixel_scale(Image.open(COIN).convert("RGBA"), 12)
    img.alpha_composite(coin, (70, 84))
    draw_text(img, "TRADERY", 70 + coin.width + 28, 84 + (coin.height - 7 * 15) // 2, 15, GOLD, shadow=INK, outline=INK)
    draw_text(img, "VENDING BLOCKS & ECONOMY", 74, 230, 5, CREAM, shadow=INK)

    rows = [
        [("PLAYER SHOPS", None), ("QUICK TRADE", None), ("BALANCE HUD", None)],
        [("COIN ORE", None), ("ECONOMY API", None), ("15 LANGUAGES", None)],
        [("MC 26.3", (24, 70, 40, 215)), ("FABRIC", (70, 55, 35, 215)), ("NEOFORGE", (90, 45, 15, 215))],
    ]
    y = 310
    for i, row in enumerate(rows):
        x = 74
        for text, fill in row:
            x += chip(img, x, y, text, scale=3, **({"fill": fill} if fill else {})) + 14
        y += 64 if i < 1 else 84
    img.convert("RGB").save(os.path.join(OUT, "banner.png"), optimize=True)


def vertical_shade(size, color, alpha_top, alpha_bottom):
    w, h = size
    layer = Image.new("RGBA", size, color + (0,))
    px = layer.load()
    for y in range(h):
        a = int(alpha_top + (alpha_bottom - alpha_top) * (y / (h - 1)) ** 2)
        for x in range(w):
            px[x, y] = color + (a,)
    return layer


# ------------------------------------------------------------------------------------------------ section headers

HEADERS = [
    ("vending", "VENDING BLOCKS"),
    ("quick_trade", "QUICK TRADE & HINT"),
    ("hud", "BALANCE HUD"),
    ("money", "WHERE MONEY COMES FROM"),
    ("commands", "COMMANDS"),
    ("servers", "FOR SERVERS"),
    ("developers", "FOR DEVELOPERS"),
    ("compat", "COMPATIBILITY"),
    ("install", "INSTALLATION"),
]


def headers():
    bg = background()
    # The sunset sky by the sun, blurred and darkened: the banner's warm light, quiet enough for text
    sky = bg.crop((0, int(bg.height * 0.36), int(bg.width * 0.42), int(bg.height * 0.36) + 64))
    strip = sky.resize((1600, 132), Image.LANCZOS).filter(ImageFilter.GaussianBlur(6))
    strip = ImageEnhance.Brightness(strip).enhance(0.62).convert("RGBA")
    strip.alpha_composite(horizontal_shade(strip.size, (20, 9, 6), 170, 30, 900))
    for key, title in HEADERS:
        img = strip.copy()
        icon = pixel_scale(Image.open(os.path.join(ART, "icons", key + ".png")).convert("RGBA"), 3)
        img.alpha_composite(icon, (34, (126 - icon.height) // 2))
        draw_text(img, title, 34 + icon.width + 30, (126 - 7 * 7) // 2, 7, GOLD, shadow=INK, outline=INK)
        ImageDraw.Draw(img).rectangle([0, 126, 1600, 131], fill=GOLD)
        img.convert("RGB").save(os.path.join(OUT, "header_" + key + ".png"), optimize=True)


# ------------------------------------------------------------------------------------------------ screenshots

# docs_* screenshot -> (page image name, gallery file name)
SHOT_USES = [
    ("docs_market_day", "market", "01_market"),
    ("docs_market_sunset", None, "02_market_sunset"),
    ("docs_hint", "hint", "03_hint"),
    ("docs_quick_buy", "quick_buy", "04_quick_buy"),
    ("docs_hint_sneaking", "hint_sneaking", "05_hint_sneaking"),
    ("docs_buyer_screen", "buyer_screen", "06_buyer_screen"),
    ("docs_owner_screen", "owner_screen", "07_owner_screen"),
    ("docs_market_side", "market_side", "08_market_side"),
    ("docs_ore_wall", "ore_wall", "09_ore_wall"),
]


def latest_shot(name):
    files = sorted(glob.glob(os.path.join(SHOTS, "*_" + name + ".png")))
    return files[-1] if files else None


def screenshots():
    os.makedirs(os.path.join(OUT, "page"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "gallery"), exist_ok=True)
    for shot, page, gallery in SHOT_USES:
        path = latest_shot(shot)
        if path is None:
            print("missing screenshot", shot, "- run TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest")
            continue
        img = Image.open(path).convert("RGB")
        if page:
            img.resize((1280, 720), Image.LANCZOS).save(os.path.join(OUT, "page", page + ".jpg"), quality=90, optimize=True)
        img.save(os.path.join(OUT, "gallery", gallery + ".jpg"), quality=92, optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    banner()
    headers()
    screenshots()
    print("wrote docs/images: banner, headers, page/, gallery/")


if __name__ == "__main__":
    main()
