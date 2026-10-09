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

```bash
TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest   # in-game docs_* screenshots (DocsShotsClientTest)
python3 tools/docs/make_page_images.py                       # banner, section headers, page/ and gallery/
```

- Banner background: `art/page/banner_bg.png`, picked from `tools/docs/gen_banner_art.py` drafts (OpenAI image model,
  needs `OPENAI_API_KEY` and the `openai` package).
- Mod icon: `art/icon/icon_1024.png` / `icon_512.png` (upload the 512 one to Modrinth and CurseForge); the game's
  `assets/tradery/icon.png` is the same picture at 128 px. AI draft (`mod_icon` in `tools/texgen/prompts.yaml`, 4 drafts,
  #4 picked), the rounded white corners filled with the background so the platforms can round it themselves.
- Section icons: `art/page/icons/*.png`, AI drafts from `tools/texgen/prompts.yaml` (the texgen tool from Femboy Mod:
  `texgen --root <Tradery> batch --tag header`), pixelized to 32x32.
- Text, chips and frames are drawn by `make_page_images.py`; screenshots are real. Keep the AI note in "Credits & Notes"
  in sync if AI art changes.
