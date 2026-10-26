# Changelog

All notable changes. Mod versions follow SemVer; the Economy API has its own version (`api_version`).

## [1.1.0] - Unreleased

Living Market: villagers become customers and suppliers, villages get an economy of their own (treasury, prosperity, orders, projects, caravans, market days) and markets to spend money at. Economy API 1.1.0.

### Added
- Villagers walk up to players' vending blocks and buy with their own money. Only villagers with a bed and a job site shop; their wallets refill every in-game day by profession level (novice 3 to master 18 by default) and hold up to 3 days of wages, so villagers you level up by trading become richer customers.
- They buy what they need (food for everyone, tools and materials by profession; data pack `tradery/villager_wants`) at a fair price (data pack `tradery/prices`). Above 1.5 times the fair price they don't buy; the more of one item you sell in a village, the less it wants it, and demand recovers every day.
- Fair prices build your reputation in the village through vanilla villager gossip: more customers, and better prices in normal villager trades too. Overpricing hurts it.
- "Villagers can buy" toggle in the owner screen (on by default). Owner notifications and the offline summary include villager sales.
- Village markets: newly generated plains, desert, savanna, taiga and snowy villages can have market stalls with server vending blocks selling enchanted books, name tags, saddles, rare seeds and more for coins, and a changer stall that sells and buys emeralds.
- Villager trading for money: the villager trade window shows prices in coins instead of emeralds (1 emerald = 3.00 by default). Buying takes money from your balance and puts it in the villager's wallet; selling is paid from the villager's wallet, and when it's empty the villager asks you to come back tomorrow. Same offers, levels, discounts and experience as vanilla; "Trade all" and double-click to trade. Emeralds stay an item you can exchange at the market changer; wandering traders still take emeralds. Can be turned off (`villagerTrading.enabled`).
- Village economy: every village (its bell) has a treasury, filled daily per villager and by a 10% tax on what players pay villagers, and a prosperity that grows with the money moving through it and fades 5% a day. Prosperity raises it from Hamlet to Village, Town and Trade city: higher wages, richer orders, more market days.
- Village board (crafted, and on market stalls): the village's orders, paid from its treasury (deliver plain items, partly or all at once); its level, people, treasury and your reputation; projects to fund with your money (street lamps, granary, workshops, a watch post with an iron golem, a new market stall built by the bell, a trading yard); what's cheap and dear there, market days, trading places and caravans. Looking at a bell shows the village's level.
- Donations: the first row of the board's projects is a donation to the treasury of any amount, so you can put money straight into a village's orders. The Village tab shows the treasury's last seven days as bars (money in and out, with a per-day tooltip and the week's totals).
- Projects build what they promise: street lamps put lamp posts along the streets, the granary, the workshops (with job site blocks) and the trading yard are built by the bell in the village type's style, like the new market stall. Data packs can add buildings with `"action": "structure"` and a template under `structure/village/<type>/`.
- Entering a village (or being there when it reaches a new level) shows its name and level at the top of the screen for a few seconds; `villageTitle` in `client.json5` turns it off or moves it. Jade shows a bell's village: name, level, treasury and what it saves for.
- Villages build for themselves: a Village saves half its daily income for a well (with a sign bearing its name and a banner in its colour), a Town for a watchtower, a Trade city for a trade hall with server vendors; the board shows "Saving for…". Data pack `tradery/village_buildings`.
- Festivals: on a market day of a Town or bigger, at dusk the bell rings, villagers get a day of wages on top to spend at the stalls, fireworks go up by the bell and players there see "Festival!".
- Buildings are placed like village houses: on real ground (three solid blocks deep, never on a roof), under the open sky, outside existing houses, beside a street when there is one. Every new building gets a path to the nearest street.
- Street lamps light the whole village: along every dark street a lamp post in the village's style with an arm over the road and a hanging lantern, ten blocks apart, skipping streets already lit; new dark streets get theirs the next day.
- "Paved Streets" project: every dirt path of the village (the whole generated village, not just around the bell) becomes stone in its style, one-block dips and grass gaps are filled, and parts loaded later are paved the next day. The watch post now builds a wooden lookout with its iron golem beside it.
- Villager suppliers: villagers produce goods by profession (farmers crops, fishermen fish, masons stone…; data pack `tradery/villager_supplies`) and walk up to players' buyback blocks to sell them for the owner's money, at a fair price or better.
- Trade between villages: each village type sells some goods cheap and pays dear for others (data pack `tradery/village_types`). Prosperous villages send caravans to villages of another type: a trader with llamas at the market who trades in coins.
- Market days: every few days (more often in bigger villages) villagers flock to the stalls. Trading places: rent one through the board and the village's villagers come to your blocks more often and from farther away; the rent goes to the treasury.
- Raids hit the economy: a lost raid costs prosperity, half the treasury and the villagers' money; a won one brings prosperity and restoration orders.
- Market counter: up to six offers in one block, each with its own goods and price, sold from one shared stock; villagers come for the offer they need.
- Shop ledger (book + gold ingot, or `/tradery shops`): all your vending blocks and counters with their stock, waiting revenue and alerts, the last known state of far ones, and a light beam to find one.
- Limits so villagers don't print money: wallets, a daily cap per village, the shared inflation damping, loaded chunks only.
- Config sections `villagers`, `market`, `villagerTrading` and `villages`; Economy API: `Reasons.VILLAGER_PURCHASE`, `VILLAGER_TRADE`, `VILLAGER_SUPPLY`, `VILLAGE_ORDER`, `VILLAGE_PROJECT`, `VILLAGE_DONATION`, `VILLAGE_RENT`, `VILLAGE_CARAVAN`.

### Changed
- Less work on the server tick: stalls are checked for customers a few at a time instead of all at once, the bell of each stall is remembered, saves after trades are batched per tick, and a crafted stack pays one reward instead of one per item. The HUD and the look-at hints no longer allocate per frame.
- New mod icon: a villager at a market stall, pixel art drawn in the repository's scripts. The page banner is an in-game screenshot and the section icons are pixel glyphs; there are no AI-generated images any more.

### Fixed
- A client can no longer park kilobytes of item data in a vending block's sample through a recipe viewer, nor hammer the owner's Save: samples are limited to 2 KB of data and both go through the trade rate limit.
- Villages with a treasury, funded projects, open orders or trading licences are never forgotten after a clock jump or a long absence.
- A vending block or counter whose goods came from a removed mod keeps its price and settings; only the sample is lost.
- In the villager trade window, a villager could pay up to the village's trade tax more than it had; the wallet check now counts the tax.
- A vending block's facade no longer looks squashed: the block is cut at the height of the base, like a slab, instead of squeezed into it, so its texture keeps its scale.

## [1.0.0] - 2026-10-17

First release, for Minecraft 26.3 (Fabric and NeoForge). Economy API 1.0.0. The same release exists for 1.21.1 (Fabric, NeoForge) and 1.20.1 (Fabric, Forge).

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
- English, Russian, Ukrainian, Belarusian, Polish, German, Dutch, Swedish, French, Spanish, Portuguese (Brazil), Japanese, Simplified Chinese, Traditional Chinese (Taiwan, Hong Kong).
