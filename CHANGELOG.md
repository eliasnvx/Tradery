# Changelog

All notable changes. Mod versions follow SemVer; the Economy API has its own version (`api_version`).

## [Unreleased]

### Added
- Economy core: accounts, one configurable currency, atomic transfers with fees, deposits and withdrawals, an infinite server account, locked accounts, a balance ceiling.
- Public Economy API (`tradery-api` 0.1.0) with a loader-independent event bus: transaction, balance, account, vending, coin ore, coin pickup and reward events.
- Balance HUD with count-up animation and popups; key binding and `/tradery hud` to hide it.
- Commands `/bal`, `/balance`, `/pay`, `/baltop`, `/tradery history|hud|reload`, `/eco give|take|set|lock|unlock|stats`.
- Permission nodes through Fabric's permission API and NeoForge's PermissionAPI (LuckPerms-compatible).
- JSON5 configs with comments and error reporting; daily transaction log.
- English and Russian translations.
