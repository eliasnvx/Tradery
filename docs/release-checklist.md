# Release checklist

Automated on every build (CI): JUnit, GameTests on both loaders (anti-dupe table, ore, rewards, Common Economy API), lang key parity and coverage. Client GameTests (`./gradlew :fabric:runClientGameTest`, needs a display) take screenshots for a look.

Before tagging `v<version>`:

1. `./gradlew clean build` is green.
2. `./gradlew :fabric:runClientGameTest` and `-Pcompat=true`: look at `fabric/build/run/clientGameTest/screenshots` (HUD at GUI scale 1-4, vending screens, Jade tooltip, ores).
3. Dedicated server, **two clients**, once on Fabric and once on NeoForge:
   - [ ] Join: HUD shows the starting balance; `/pay` between the players updates both HUDs.
   - [ ] Player A sets up a vending block (money price), B buys ×1 / ×8 / max; A gets the notification.
   - [ ] A logs out, B buys; A joins and gets "While you were away: …".
   - [ ] Item price: revenue fills up → buyer is refused, A is told; A empties revenue → sales resume.
   - [ ] Buyback: B sells, A's balance pays; A broke → refused.
   - [ ] Quick trade (the NeoForge side has no client test): B holds sneak + right-click with a block in hand → buys lots
         about 5 a second, the block in hand is never placed; sneak + left-click on a buyback block sells; the wrong
         button says which keys to use; A's sneak + left-click still mines A's own block. The look-at hint shows, and
         hides with Jade installed.
   - [ ] A breaks the block while B has the buyer screen open → B's screen closes, contents drop once.
   - [ ] B can't break A's block (survival and creative); TNT next to it does nothing.
   - [ ] Mine coin ore in survival (balance goes up), blow one up with TNT (coins drop), pick coins up with a full inventory.
   - [ ] `/tradery withdraw 111`, right-click the coins back.
   - [ ] Kill a zombie (reward), kill one from a spawner (no reward).
4. Crash test: make a few trades, `kill -9` the server, restart → balances and stock match the last save (nothing duplicated).
5. Performance: 50 vending blocks in one chunk, all stocked, two players watching → TPS stays ≥ 19.5 (`/tick query` or a profiler), FPS doesn't drop noticeably. Vending blocks never tick; only the renderer runs per frame.
6. With Create and Mekanism: coin ore has no crushing/enriching recipes and isn't in `c:ores`.
7. Without any optional mod, both loaders start with no Tradery errors in the log.
8. Update `CHANGELOG.md` (move "Unreleased" to the date), set `modrinth_project_id` / `curseforge_project_id` in `gradle.properties`, add `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` to the `release` environment, then tag `v<version>` and push the tag.
