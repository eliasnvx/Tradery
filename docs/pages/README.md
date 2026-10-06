# Project pages

- `curseforge.md`: the main page (CurseForge and Modrinth, EN). Images use absolute `raw.githubusercontent.com/.../26.3-dev/...`
  URLs, so they show up once the images are on the `26.3-dev` branch.
- `description-ru.md`: the Russian page (link it in RU posts: Modrinth is blocked in Russia, CurseForge isn't).
- `gallery.txt`: gallery titles and descriptions, in order; the files are `docs/images/gallery/`.
- Download badges point at `modrinth.com/mod/tradery` and `curseforge.com/minecraft/mc-mods/tradery`: check the slugs
  when the projects exist.
- Modrinth tags: Economy, Storage, Utility, Game Mechanics; environment: client + server (required on both).
- CurseForge categories: Server Utility, Storage, Miscellaneous; loaders Fabric + NeoForge, MC 26.3.

## Images

```bash
TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest   # in-game docs_* screenshots (DocsShotsClientTest)
python3 tools/docs/make_page_images.py                       # banner, section headers, page/ and gallery/
```

- Banner background: `art/page/banner_bg.png`, picked from `tools/docs/gen_banner_art.py` drafts (OpenAI image model,
  needs `OPENAI_API_KEY` and the `openai` package).
- Section icons: `art/page/icons/*.png`, AI drafts from `tools/texgen/prompts.yaml` (the texgen tool from Femboy Mod:
  `texgen --root <Tradery> batch --tag header`), pixelized to 32x32.
- Text, chips and frames are drawn by `make_page_images.py`; screenshots are real. Keep the AI note in "Credits & Notes"
  in sync if AI art changes.
