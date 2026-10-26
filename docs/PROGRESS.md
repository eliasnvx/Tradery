# Progress

Phases and gates: `SPEC.md` → "Этапы разработки".

## Phase 1 — Core ✅ (closed 2026-09-30)
- [x] Build: MultiLoader-Template layout, fabric-loom + ModDevGradle, JDK 25, both loaders build and start
- [x] `:api` — `TraderyEconomy`, `Currency`, `Account`, `AccountId`, `Reason`/`Reasons`, `TransactionResult`, `Event<T>` bus with priorities, all 11 events
- [x] Ledger (atomic moves, overflow, `maxBalance`, locks, infinite server account) + JUnit
- [x] `EconomyService`: events, saved data (`accounts`, `history`, `stats`), async JSONL log, client sync
- [x] JSON5 configs (`server.json5`, `client.json5`) with comments and problem reporting + JUnit
- [x] Permissions: Fabric permission API / NeoForge PermissionAPI with op-level fallback
- [x] Commands: `/bal` (`/balance`), `/pay`, `/baltop`, `/eco give|take|set|lock|unlock|stats`, `/tradery history|hud|reload`
- [x] Payloads: `CurrencyInfo`, `BalanceSync`, `BalanceDelta`, `Notification`, `HudToggle`
- [x] HUD: corner/offset/scale, full/short format, count-up, popups, hotbar avoidance, key + `/tradery hud`
- [x] EN + RU lang for everything above (key sets checked by JUnit)
- [x] GameTests on both loaders: atomic transfer, Pre event tax/cancel, server-thread contract, `/pay`
- [x] Visual HUD check: Fabric client GameTest `HudClientTest` (sync on join, /eco give/take, corner/scale/short format, /tradery hud), screenshots reviewed
- [x] Balance survives a restart: NBT round-trip of accounts/history/stats (JUnit); both dedicated servers start and create the data files

## Phase 2 — Vending ✅ (closed 2026-09-30)
- [x] Registration through `Platform.register` (Fabric immediate, NeoForge DeferredRegister), creative tab
- [x] `vending_block` (facing, stocked light, facade), `display_block`, `vendor_key`; recipes, loot, pickaxe tag
- [x] Block entity: owner, settings, 27 stock + 9 revenue slots, facade, admin flags; never ticks; not a `Container`
- [x] Trades: sale (money or items), buyback (money), fee, burn, infinite stock, all limits before any change
- [x] Menus: buyer (no slots, ×1/×8/×max via menu buttons), owner (stock, take-only revenue, sample slots, draft + Save), admin toggles, display
- [x] Screens drawn with vanilla-style panels; owner/admin/buyer/display checked in a Fabric client test with screenshots
- [x] Renderer: goods with STATIC/SPIN/BOB/SPIN_BOB/NONE, facade squeezed into the base; client animation override
- [x] Owner notifications (sale, empty, no room) and the offline summary on join
- [x] Vendor index (`tradery:vendors`, self-healing), per-player limit, `VendingPlacedEvent`/`VendingConfiguredEvent`/`VendingPurchaseEvent`
- [x] Protection: strangers can't break (also creative, via loader events), explosions, pistons, hoppers
- [x] `/tradery vendors [player]`
- [x] GameTests (both loaders): sale, last item once, no room, item price + full revenue, buyback, menu rules, break with open menus, protection, rate limit, offline summary, price change closes buyer screens

## Phase 3 — Money sources ✅ (closed 2026-09-30)
- [x] Coins (copper/silver/gold): values from config, synced to clients for tooltips; use = deposit, sneak-use = all coins
- [x] Coin ore × 3 tiers × stone/deepslate: overlay models on vanilla stone, loot tables (silk touch = ore block, no Fortune), tool tags, not in `c:ores`
- [x] Payout in `CoinOreBlock#getDrops` (every drop path): survival player → balance (`tradery:ore/mined`), machines/explosions → coin items (counted as cash in the world); `CoinOreMinedEvent`; `ore.dailyCap`; `ore.inflationDamping`
- [x] Coin pickup straight to the balance even with a full inventory (`CoinPickedUpEvent`), `/tradery withdraw` (exact coins, only if they fit)
- [x] Worldgen: configured + placed features (data-pack editable), `tradery:enabled_in_config` placement filter, `#tradery:has_coin_ore`, NeoForge biome modifier, Fabric `BiomeModifications`
- [x] Rewards (`rewards.json5`): kill/mine/craft/fish/advancement, tags and `*`, chance, spawner mobs skipped, fake players skipped, diminishing returns, daily cap, `RewardGrantedEvent`
- [x] Player-placed blocks marked per chunk (data attachment) so `mine` rewards can't be farmed by placing
- [x] GameTests (both loaders): ore pays the miner, coins without a player, daily cap, pickup with a full inventory, withdraw/use, kill reward vs spawner mobs, mine reward vs placed blocks, ore feature places ore, coin ore added to overworld biomes (not the Nether), all mixins apply; JUnit for the rewards config and diminishing
- [x] Client check: ores and coins screenshot

## Phase 4 — Integrations ✅ (closed 2026-09-30)
- [x] Common Economy API 2.0.0 (Fabric, jar-in-jar): Tradery is a provider; runtime-checked on 26.3 by a Fabric GameTest (read, increase, decrease, dry runs, formatting)
- [x] Text Placeholder API (Fabric, optional): `%tradery:balance%`, `balance_short`, `balance_raw`, `top_name N`, `top_balance N`, `currency`
- [x] Jade (both): owner, offer, stock (server data provider), display item
- [x] JEI (both): drag items onto sample slots, info pages for vending/display blocks, coin ore, coins
- [x] REI (both; NeoForge through an annotated subclass): drag onto sample slots, info pages
- [x] `-Pcompat=true` dev runs: Fabric client tests with JEI + REI + Jade + Placeholder API (Jade tooltip screenshot), NeoForge GameTests and client start with JEI + REI + Jade
- [ ] EMI / WTHIT: no 26.3 builds yet (SPEC)

## Phase 5 — Release (active: manual checks left)
- [x] Version 1.0.0, Economy API 1.0.0 (`ApiVersionTest` keeps the constant in sync)
- [x] Mod icon (`tools/textures.py`), in fabric.mod.json and neoforge.mods.toml
- [x] Publishing: `mod-publish-plugin` 2.2.1 (`gradle/publish-mod.gradle`), dry run until project ids and tokens exist
- [x] CI: `.github/workflows/build.yml` (build + JUnit + GameTests), `release.yml` (tag `v*` → checks → build → Modrinth/CurseForge → API maven → GitHub release)
- [x] README with screenshots, `docs/api/README.md`, CHANGELOG, `docs/release-checklist.md`
- [x] Lang coverage test: every translation key used in the code exists
- [x] HUD at GUI scale 1-4 on 1280x720 (client test screenshots)
- [x] Polish after review: glass tops, thinner bases, coin icon instead of the symbol in the HUD, vending screens and Jade
- [x] Quick trade (sneak + use buys, sneak + attack sells, hold to repeat) and the look-at hint; GameTests on both loaders, Fabric client test holds the keys
- [x] Release checks automated (2026-10-06): NeoForge client harness (`runClientTest`), dedicated-server network test, 50-vendor performance test, real `kill -9` crash test (`tools/crash-test.sh`), coin ore processing GameTest. Found and fixed: trades saved apart from their chunk (crash dupe/loss), NeoForge `logoFile` warning screen
- [ ] Optional: two real players on a dedicated server; Create/Mekanism in game once they exist for 26.3
- [ ] Create the Modrinth/CurseForge projects, set their ids, add tokens, tag `v1.0.0`

## Ports (2026-10-09)
- [x] 1.21.1 (Fabric + NeoForge) on branch `1.21.1-dev`, 1.20.1 (Fabric + Forge) on branch `1.20.1-dev`: same features, all GameTests, client tests on every loader (also with JEI/REI/Jade) and the kill -9 crash test pass; details in each branch's `docs/PROGRESS.md` and `docs/decisions.md`

## Phase 6 — v1.1 Living Market (active, 2026-10-18)
- [x] Design: SPEC "Живой рынок (v1.1)", docs/decisions.md 2026-10-18; config sections `villagers` and `market`
- [x] Villager customers: manager, wallets, needs/prices data packs, demand, reputation, owner toggle, notifications (VillagerGameTests 8/8)
- [x] Village market: stall structures, processor, pool injection, changer (MarketGameTests 3/3)
- [x] GameTests (gate): Fabric 45/45, NeoForge 43/43; 15 languages; live client check in a generated village (farmer bought bread)
- [x] Villager trading for money (author's choice): Tradery trade window via a `Villager#startTrading` mixin, wallet in and out, config `villagerTrading`, 15 languages (VillagerTradeGameTests 5/5)
- [x] Live check of the villager trade window (`TRADERY_MARKET_SHOTS=trade`): a village butcher's own offers in coins, sold 4 rabbits for 3.00 from its wallet, bought rabbit stew for 3.00 into it; GameTests Fabric 50/50, NeoForge 48/48
- [x] Village economy core (author's choice 2026-10-20): `villages` config, `VillageEconomy`/`VillageState`, `Villages` (census, prosperity and levels, treasury, trade tax, wages by level), `VillageProjects` loader, `VillageCommerce` API (VillageGameTests 6/6)
- [x] Village board: orders, projects, bell hint, board on market stalls (VillageBoardGameTests 12/12)
- [x] Villager suppliers to buyback blocks (SupplierGameTests 8/8)
- [x] Commerce: regional prices, market days, trading places, caravans, raids (VillageCommerceGameTests 8/8); Fabric GameTests 84/84, 15 languages
- [x] Server conveniences: shop ledger, multi-offer counter, villagers at counters (MarketCounterGameTests 12/12, ShopLedgerGameTests 6/6)
- [x] Live check of the village economy (`TRADERY_MARKET_SHOTS=village|shops`): board tabs, an order delivered, bell hint, a caravan and its window, counter with six offers, buyer/owner screens, shop ledger; 15 languages; GameTests Fabric 102/102, NeoForge 100/100
- [x] Review round 2026-10-23: performance (coalesced saves, spread customer scan, bell cache, cached priced items, bounded log queue), robustness (untrusted sample codec + 2 KB cap, Save rate limit, pruning keeps funded villages, lenient settings codecs, wallet vs tax), client render paths allocation-free; RobustnessGameTests 4/4, JUnit StackMath/TradeTerms; Fabric 106/106, NeoForge 104/104
- [x] Treasury donations from the board and a 7-day treasury history on the Village tab (2026-10-23; VillageBoardGameTests 14/14, `Reasons.VILLAGE_DONATION`, 15 languages)
- [x] Projects build visibly (lamp posts, granary, workshops, trading yard templates), village title on entering, wrapped board tooltips, SDL mouse buttons (2026-10-24; VillageBoardGameTests 16/16)
- [x] Placement like village houses (real ground, outside houses, beside streets), villages build a well / watchtower / trade hall from a savings fund, festivals on market days, Jade on bells (2026-10-24; VillageBuildingGameTests 3/3)
- [x] Streets: paths from new buildings to the street, "Paved Streets" project over the whole village with daily repaving; the watch post builds a lookout; street lights with arms along every dark street (2026-10-24; VillageBoardGameTests 18/18)
- [ ] Ports to 1.21.1 and 1.20.1 (needs a day counter that only advances: `/time set day` resets the clock there)

