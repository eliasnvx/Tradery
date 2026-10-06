# Changelog

All notable changes. Mod versions follow SemVer; the Economy API has its own version (`api_version`).

## [1.0.0] - Unreleased

First release for Minecraft 26.3 (Fabric and NeoForge). Economy API 1.0.0.

### Economy
- One configurable currency (name, symbol, decimals, thousands separator), starting balance, balance ceiling.
- Atomic transfers with fees, deposits and withdrawals; an infinite server account; locked accounts.
- Transaction log per day (JSON lines), per-player history, money supply statistics.
- Commands `/bal`, `/balance`, `/pay`, `/baltop`, `/tradery history|withdraw|hud|vendors|reload`, `/eco give|take|set|lock|unlock|stats`.
- Permission nodes for LuckPerms and other permission mods, with op-level defaults.

### Balance HUD
- Any corner, offset and scale, full or short format, count-up animation, gain/loss popups; key binding and `/tradery hud`.

### Vending
- Vending block: sells goods for money or items, buyback mode, facades, showcase animations, owner notifications and an offline summary.
- Quick trade without the window: sneak + right-click buys, sneak + left-click sells; hold to repeat. A look-at hint shows the owner, the offer and the keys.
- Display block: shows an item in a glass case.
- Vendor key: admin vendors with infinite stock, burned payment, no fee, server ownership.
- Protection from strangers, explosions, pistons and hoppers; no item slots in the buyer screen.
- Crash-safe trades: when a vending block's chunk is saved, the money and the traders' inventories are saved with it.

### Money sources
- Copper, silver and gold coin ore in stone and deepslate; payout straight to the balance or as coins; daily cap and inflation damping; data-pack worldgen.
- Coins: deposit by right-click or pickup, `/tradery withdraw`.
- Rewards for mobs, mining, crafting, fishing and advancements, with spawner, fake-player, diminishing-returns and daily-cap rules.

### Integrations
- Common Economy API provider (Fabric, included), Text Placeholder API placeholders, Jade tooltips, JEI and REI drag-and-drop and information pages.

### Languages
- English, Russian.
