# Release checklist

Automated on every build (CI): JUnit, GameTests on both loaders (anti-dupe table, quick trade, ore and its processing
tags, rewards, trades saved with their chunk, Common Economy API), lang key parity and coverage.

Automated, but run by hand before a release (they need a display or kill processes):

| Command | What it checks | Last run |
|---|---|---|
| `./gradlew :fabric:runClientGameTest` | HUD (GUI scale 1-4), vending screens, hint, quick buy/sell with held keys, ores; **dedicated server over the network** (`NetworkClientTest`: join sync, `/eco give`, `/pay` to a second player, quick buy, buyer screen, the owner's sale notification); **50 vending blocks** (`PerformanceClientTest`) | 2026-10-06 ✅ |
| `./gradlew :fabric:runClientGameTest -Pcompat=true` | the same with JEI, REI, Jade, Placeholder API (Jade tooltip, hint hidden) | 2026-09-30 ✅ |
| `./gradlew :neoforge:runClientTest` | NeoForge client: hint, held sneak + use with a block in hand (buys, places nothing), sneak + attack on a selling block (refused), held sneak + attack on a buyback block (sells), the owner's sneak + attack breaks the own block | 2026-10-06 ✅ |
| `tools/crash-test.sh` | dedicated NeoForge server: trades, save, more trades, real `kill -9`, restart → stock, balances and money supply from one moment | 2026-10-06 ✅ |

Results: screenshots in `fabric/build/run/clientGameTest/screenshots` and `neoforge/build/run/clientTest/screenshots`,
`fabric/build/run/clientGameTest/tradery-performance.txt`, `neoforge/build/run/clientTest/tradery-client-test.txt`,
`neoforge/build/run/crashTest/crash-result.txt`.

Last measured (2026-10-06, Apple Silicon): server tick ~1 ms with or without 50 stocked vending blocks (they never
tick); client render cost of the 50 blocks within noise (< 1 ms per frame).

Before tagging `v<version>`:

1. `./gradlew clean build` is green.
2. Run the four commands above; look at the screenshots.
3. Optional sanity pass with two real players on a dedicated server (the network test covers one real client plus a
   server-side second player): HUDs after `/pay`, B buys ×1 / ×8 / max and A gets the notification, A breaks the
   block while B has the buyer screen open, B can't break A's block, TNT next to it.
4. Create and Mekanism: no 26.3 builds yet (2026-10-06). When they exist, check in game that coin ore can't be crushed
   or enriched; until then the GameTest `coin_ore_not_processable` guards the tags and furnace recipes they rely on.
5. Update `CHANGELOG.md` (move "Unreleased" to the date), set `modrinth_project_id` / `curseforge_project_id` in
   `gradle.properties`, add `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` to the `release` environment, then tag
   `v<version>` and push the tag.
