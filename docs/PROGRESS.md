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

## Phase 2 — Vending (active)
Not started.

## Phase 3 — Money sources
Not started.

## Phase 4 — Integrations
Not started.

## Phase 5 — Release
Not started.
