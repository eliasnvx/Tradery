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
- [x] GameTests (both loaders): ore pays the miner, coins without a player, daily cap, pickup with a full inventory, withdraw/use, kill reward vs spawner mobs, mine reward vs placed blocks, ore feature places ore, all mixins apply; JUnit for the rewards config and diminishing
- [x] Client check: ores and coins screenshot

## Phase 4 — Integrations (active)
Not started.

## Phase 5 — Release
Not started.
