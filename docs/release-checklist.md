# Release checklist

Automated on every build (CI): JUnit, GameTests on both loaders (anti-dupe table, quick trade, ore and its processing
tags, rewards, trades saved with their chunk, Common Economy API), lang key parity and coverage.

Automated, but run by hand before a release (they need a display or kill processes):

| Command | What it checks | Last run |
|---|---|---|
| `./gradlew :fabric:runClientTest` | Fabric client in a dev-only test mod (1.21.1 has no client GameTests): HUD mixin, key mappings, networking, render layers; hint, held sneak + use with a block in hand (buys, places nothing), sneak + attack on a selling block (refused), held sneak + attack on a buyback block (sells), the owner's sneak + attack breaks the own block, buyer and owner screens | 2026-10-09 ✅ |
| `./gradlew :neoforge:runClientTest` | the same scenario on a NeoForge client | 2026-10-09 ✅ |
| `./gradlew :fabric:runClientTest -Pcompat=true`, `./gradlew :neoforge:runClientTest -Pcompat=true` | the same with JEI, REI, Jade (and Placeholder API on Fabric) installed: their Tradery plugins load without errors, the scenario still passes | 2026-10-09 ✅ |
| `tools/crash-test.sh` | dedicated NeoForge server: trades, save, more trades, real `kill -9`, restart → stock, balances and money supply from one moment | 2026-10-09 ✅ |

Results: screenshots in `{fabric,neoforge}/build/run/clientTest/screenshots`, reports in
`{fabric,neoforge}/build/run/clientTest/tradery-client-test.txt` and `neoforge/build/run/crashTest/crash-result.txt`.
The network and performance client GameTests (`NetworkClientTest`, `PerformanceClientTest`) exist on the 26.3 branch only.

Last measured on 26.3 (2026-10-06, Apple Silicon): server tick ~1 ms with or without 50 stocked vending blocks (they
never tick); client render cost of the 50 blocks within noise (< 1 ms per frame).

Before tagging `v<version>+1.21.1` (one tag per Minecraft version branch):

1. `./gradlew clean build` is green.
2. Run the commands above; look at the screenshots.
3. Optional sanity pass with two real players on a dedicated server (the network test covers one real client plus a
   server-side second player): HUDs after `/pay`, B buys ×1 / ×8 / max and A gets the notification, A breaks the
   block while B has the buyer screen open, B can't break A's block, TNT next to it.
4. Create and Mekanism exist for 1.21.1: optionally check in game that coin ore can't be crushed or enriched (the
   GameTest `coin_ore_not_processable` guards the tags and furnace recipes they rely on).
5. Update `CHANGELOG.md` (move "Unreleased" to the date), set `modrinth_project_id` / `curseforge_project_id` in
   `gradle.properties`, add `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` to the `release` environment, then tag
   `v<version>+1.21.1` on this branch and push the tag. Repeat on the other version branches.
