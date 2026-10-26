# Project pages

- `curseforge.md`: the main page (CurseForge and Modrinth, EN). Images use absolute `raw.githubusercontent.com/.../26.3-dev/...`
  URLs, so they show up once the images are on the `26.3-dev` branch.
- `description-ru.md`: the Russian page (link it in RU posts: Modrinth is blocked in Russia, CurseForge isn't).
- `gallery.txt`: gallery titles and descriptions, in order; the files are `docs/images/gallery/`.
- Download badges point at `modrinth.com/mod/tradery` and `curseforge.com/minecraft/mc-mods/tradery`: check the slugs
  when the projects exist.
- Modrinth tags: Economy, Game Mechanics, Utility, Storage, Worldgen; environment: client + server (required on both).
- CurseForge (no "Economy" category there): main **Server Utility**; additional **Miscellaneous** (where the original
  Vending Block and Lightman's Currency are), **Utility & QoL**, **Ores and Resources** (World Gen), **Storage**.
  Loaders Fabric + NeoForge + Forge; game versions 26.3 (Fabric, NeoForge), 1.21.1 (Fabric, NeoForge), 1.20.1
  (Fabric, Forge): each file is uploaded from its branch (`26.3-dev`, `1.21.1-dev`, `1.20.1-dev`) with its own
  Minecraft version and loader. Checked against the category list on 2026-10-06.

## Images

The image scripts live in `tools/`, which is local and not in the repository (`.gitignore`); only their output is.

```bash
TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest     # in-game docs_* screenshots (DocsShotsClientTest)
TRADERY_MARKET_SHOTS=village ./gradlew :fabric:runClientGameTest  # village board, street lights, well, festival, caravan
python3 tools/docs/make_page_images.py                         # banner, section headers, page/ and gallery/
```

- 1.1 images: `docs/images/village/` (README and pages, windows cropped to their frame) and `docs/images/gallery/10_…20_`
  (full 1920x1080 frames). Section headers `header_villagers.png` (a villager's head) and `header_villages.png` (the bell)
  come from the glyphs in `make_icon.py`.

- Banner: `art/page/banner_shot.png`, a real in-game screenshot (1.21.1 NeoForge with Complementary Reimagined, from the
  `:neoforge:runShots` harness on the `1.21.1-dev` branch); `make_page_images.py` crops it, draws the title and the chips.
- Mod icon: `python3 tools/docs/make_icon.py stall` draws it pixel by pixel (32x32, scaled with nearest-neighbour) and
  installs `assets/tradery/icon.png` (128 px); `art/icon/icon_512.png` / `icon_1024.png` are the same picture for Modrinth
  and CurseForge. The other variants (`villager`, `vending`) stay in `art/icon/` for comparison.
- Section icons: `art/page/icons/*.png`, 16x16 glyphs drawn in `make_icon.py` from the same palette.
- Text, chips and frames are drawn by `make_page_images.py`; screenshots are real. There are no AI-generated images: Modrinth
  bans them on project pages (icon, gallery, description) since August 2026, and the README's "Credits and notes" says so.
