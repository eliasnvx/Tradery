<div align="center">

![Vending blocks along a village street](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/banner.png)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3_|_1.21.1_|_1.20.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric_Loader-0.15.11+-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3_|_21.1-E68A00?style=for-the-badge)](https://neoforged.net/)
[![Forge](https://img.shields.io/badge/Forge-47_(1.20.1)-D9534F?style=for-the-badge)](https://files.minecraftforge.net/)
[![Version](https://img.shields.io/badge/Version-1.1.0-F0C95A?style=for-the-badge)](https://github.com/eliasnvx/Tradery/blob/26.3-dev/CHANGELOG.md)
[![License](https://img.shields.io/badge/License-MIT-A3302B?style=for-the-badge)](https://github.com/eliasnvx/Tradery/blob/26.3-dev/LICENSE)

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/tradery)
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/tradery)

**Vending blocks, a currency with your balance on screen, and villages that buy, sell and grow with it.**

[Vending blocks](#vending-blocks) · [Quick trade](#quick-trade-and-the-hint) · [Money](#where-money-comes-from) · [Villagers](#villagers) · [Villages](#villages) · [Commands](#commands) · [Servers](#for-servers) · [Developers](#for-developers) · [Compatibility](#compatibility) · [Installation](#installation) · [FAQ](#faq)

</div>

---

## About

Tradery adds shops that keep selling while you're away. You place a vending block, fill it with goods and set a price, and anyone who passes can buy. The price is either items, the way the old Vending Block mod did it, or money in the server's currency. Your balance is always in a corner of the screen.

Money enters the world through coin ore, mob rewards and villagers, and leaves it through fees and village projects. Since 1.1 villagers take part in that: they carry wallets, walk to players' stalls to buy what they need, sell what they produce to players who run buyback blocks, and every village keeps a treasury that pays for orders and buildings. A village grows because players trade with it. All of this works in single-player too.

**Languages:** English, Русский, Українська, Беларуская, Polski, Deutsch, Nederlands, Svenska, Français, Español, Português (Brasil), 日本語, 简体中文, 繁體中文 (台灣), 繁體中文 (香港)

![A market street with vending blocks and display cases](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/market.jpg)

---

![Vending Blocks](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_vending.png)

## Vending blocks

A vending block holds one offer: up to 64 of one item for a price, from 27 slots of stock. Craft it from a chest with glass on top and on both sides and three gold ingots underneath, place it, and right-click it to set it up: a sample of the goods, how many per sale, and a price in money or in items.

- Money from a sale goes to your balance at once, minus a 2% fee that is destroyed. Item payments wait in 9 revenue slots; when they're full the block stops and tells you.
- Switch a money-priced block to buyback and it buys the goods from other players with your money, as long as you can pay and there's room.
- It sells while you're offline. You get a chat line for every sale while you're online and a summary of what sold while you were away when you join.
- Items are compared exactly, including name, enchantments and damage, for the goods and for an item price.
- Any full block can be the base (a facade). The goods on display spin, bob, both, stand still or are hidden.
- The display block is a glass case that shows an item and sells nothing.
- Only the owner and operators can break a vending block. Explosions don't destroy it, pistons don't move it, and it has no inventory that hoppers or pipes can reach. Every sale checks stock, payment and room on both sides before anything moves.
- The market counter holds up to six offers sharing one stock. The shop ledger (a book and a gold ingot, or `/tradery shops`) lists all your shops wherever they are, with their stock, waiting revenue and problems, and can put a beam of light over one.

| Buyer | Owner |
|---|---|
| ![Buyer screen](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/buyer_screen.jpg) | ![Owner screen](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/owner_screen.jpg) |

![A market counter with six offers](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/counter.jpg)

---

![Quick Trade & Hint](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_quick_trade.png)

## Quick trade and the hint

Looking at a vending block shows its owner, goods and price at the top of the screen, the keys to trade, or why it can't trade right now (sold out, no room, not set up).

![The hint: owner, goods, price and the keys](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/hint.jpg)

| Keys | What happens |
|---|---|
| Sneak + right-click | Buy one sale from a selling block. Hold to keep buying, about five sales a second |
| Sneak + left-click | Sell one sale to a buying block. Hold to keep selling |
| Right-click | The window: ×1, ×8, as many as you can, the exact stock |
| Sneak while looking | The items' full tooltips: enchantments, potion effects, durability |

The block in your hand isn't placed and the vending block isn't mined while you do this. The action bar adds up the whole streak, for example "Bought 20 × Bread for 12.50". When Jade is installed it shows the same lines and the built-in hint steps aside.

![Holding sneak + right-click: 20 bread in one go](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/quick_buy.jpg)

---

![Balance HUD](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_hud.png)

## Balance HUD

![Balance in the corner with a +1 234.50 popup](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/hud.png)

Your balance sits in a corner of the screen with a coin icon. It rolls to new values and shows each change as a short green or red line. You can move it to any corner, scale it from 0.5 to 2, switch between `1 250.00` and `1.2K`, or hide it with F1, `/tradery hud` or a key you bind yourself.

---

![Where Money Comes From](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_money.png)

## Where money comes from

New accounts start with 100.00 ₮. The currency's name, symbol and decimals are set by the server.

![Copper, silver and gold coin ore in stone and deepslate](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/page/ore_wall.jpg)

**Coin ore.** Mining it with a fitting tool in survival puts the coins' value straight on your balance. Machines and explosions drop coin items instead, and picking one up deposits it.

| Ore | Height | Veins per chunk | Coins per block | Value of a coin |
|---|---|---|---|---|
| Copper coin ore | Y 0 to 96 | 6 | 1 to 3 | 1 ₮ |
| Silver coin ore | Y −32 to 32 | 3 | 1 to 2 | 10 ₮ |
| Gold coin ore | Y −64 to −16 | 1 | 1 | 100 ₮ |

Fortune doesn't multiply coin ore, and it isn't tagged as an ore for other mods, so crushers and ore processors can't double it. Coins are items too: right-click to deposit them, `/tradery withdraw` turns balance into coins.

**Mob rewards.** Hostile mobs pay when a player kills them: 1 to 3 ₮ for a zombie or a skeleton, 2 to 4 for a creeper, 100 to 150 for the warden. Spawner mobs and kills by fake players pay nothing, rewards halve after 20 kills in a minute, and one player earns at most 1 000 ₮ a day this way. Rewards for mining, crafting, fishing and advancements are available and off by default.

**Villagers.** They pay for what they buy at players' stalls and in the trade window, and villages pay for orders, all limited by wallets and daily caps.

**Where it goes:** 2% of every money sale, village projects and donations, rent for trading places, server vendors that destroy what they take, and an optional `/pay` tax. `/eco stats` shows the money supply and how much was created and destroyed each day.

---

![Villagers](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_villagers.png)

## Villagers

![A farmer at a player's bread stall, the village market behind it](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/villager_customer.jpg)

**They buy from your stalls.** Every five seconds each loaded money vending block that belongs to a player, has stock and has "Villagers can buy" on (the default) has a 25% chance to call a villager from within 32 blocks. Only adults with a bed and a workstation come. The villager walks up, looks at the goods and decides:

- at or below the item's fair price, it buys;
- between the fair price and 1.5 times it, the chance falls evenly to zero;
- above that, or for an item without a fair price, it doesn't buy.

What a villager wants depends on its profession: food for everyone, bone meal and hoes for farmers, fishing rods and string for fishermen, paper and books for librarians, and so on. Each sale lowers the village's demand for that item by 5% and demand recovers by 25% a day. Fair prices make villagers like you, which brings more visits and the usual vanilla discounts; asking 1.25 times the fair price or more does the opposite.

**They have wallets.** A wallet refills daily by profession level, from 1 ₮ for an unemployed villager to 3 for a novice and 18 for a master, holds three days of pay and is multiplied by the village's level. One village spends at most 200 ₮ a day at stalls, more at higher levels. A villager you level up by trading becomes a richer customer.

![A villager's trade window priced in coins](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/villager_trade.jpg)

**Their trade window takes money.** Right-clicking a villager opens the same offers, levels, use limits and discounts as vanilla, but each emerald is paid as 3.00 ₮. What you pay goes into the villager's wallet, what it pays you comes out of it, and an empty wallet means "come back tomorrow". Emeralds stay an item: the market changer buys and sells them, and wandering traders still take them. The vanilla window is one setting away.

**They supply you.** Switch "Villagers can sell" on a buyback block and villagers bring what they produce: crops, fish, wool, stone and more, up to two days of output, if you pay at least 80% of the fair price.

---

![Villages](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_villages.png)

## Villages

A village is the villagers who meet at one bell. Looking at the bell shows its level and next market day; walking into the village shows its name and level at the top of the screen. Nothing is simulated in unloaded chunks: daily income is worked out when the village is next loaded.

**Prosperity and levels.** A village earns prosperity from the money moving through it (villagers' purchases at stalls, finished orders, the trade window, supplies, projects) and loses 5% of it a day.

| Level | Prosperity | Villager pay | Orders a day | Market day every | Unlocks |
|---|---|---|---|---|---|
| Hamlet | 0 | ×1 | 3 | 7 days | |
| Village | 300 | ×1.25 | 4 | 5 days | the well, level 2 projects |
| Town | 1 500 | ×1.5 | 5 | 4 days | caravans, festivals, the watchtower |
| Trade city | 5 000 | ×2 | 6 | 3 days | the trade hall |

**Treasury.** 1.00 ₮ per villager a day times the pay multiplier, up to seven days of it, plus 10% of what players pay villagers, rent and donations. It pays order rewards and caravan purses.

![The Village tab of the village board](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/board_village.jpg)

**The village board.** Craft one (three planks over paper, a sign and paper) and place it within 48 blocks of a bell, or find one on a village market. Its tabs show the orders, the village (level, people, the treasury and its last week, what it's saving for, your reputation, projects) and the market (cheap and dear goods, market days, trading places, caravans).

**Orders.** Each morning the village posts three to six orders for what its professions need, 8 to 64 items each, paying the fair price times 1.3 from its treasury. Deliver plain items all at once or in parts; orders are shared by everyone and last two days.

![The Orders tab](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/board_orders.jpg)

**Projects.** Paid from your balance, once per village. The money is destroyed, the village gains prosperity, and most projects put up a building in the village's style:

| Project | Cost | Level | What appears | What it does |
|---|---|---|---|---|
| Street Lamps | 150 | Hamlet | Street lights along every dark street | Customers come 10% more often |
| Granary | 300 | Hamlet | A granary | The treasury holds 50% more, wallets one more day |
| Paved Streets | 250 | Village | Stone streets through the whole village | Customers come 5% more often |
| Workshops | 500 | Village | A workshop with job site blocks | Pay and treasury income ×1.2 |
| Watch Post | 600 | Village | A wooden lookout and an iron golem | Raids cost half as much |
| New Market Stall | 400 | Village | A stall with three server vendors | |
| Trading Yard | 1 000 | Town | A fenced yard under an awning | Caravans and market days a day sooner |
| Donation | any | Hamlet | | Goes into the treasury |

Buildings go where a village house could stand: on real ground near the bell, never on a roof or inside a house, next to a street, facing the bell, with a path to the street. With no room, nothing is charged. Street lights hang a lantern over the road from an arm, ten blocks apart, wherever the street is still dark; paving turns every dirt path into stone and fills the gaps. Both keep up once a day as more of the village loads.

![Street lights at night](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/street_lights.jpg)

![A paved village from above](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/village_above.jpg)

**The village builds for itself.** Once a level offers a building the village doesn't have, half of its daily income goes into a building fund, and when the fund is full the village builds it: a well with its name on a sign at Village (200), a watchtower at Town (600), a trade hall with server vendors at Trade city (1 500).

![A well the village built for itself](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/village_well.jpg)

**Market days and festivals.** On a market day villagers visit stalls three times as often and the village may spend twice as much. In a Town or bigger it ends with a festival at dusk: the bell rings, every villager gets a day's pay on top, and fireworks go up until night.

![A festival by the well](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/festival.jpg)

**Trading places.** Rent one on the Market tab for seven days (5 to 40 ₮ a day by level, paid into the treasury) and that village's villagers come to your blocks from further away and more often.

**Regional prices and caravans.** Each village type has cheap and dear goods: desert villages sell sand and cactus cheap and pay more for logs, wool and fish. A Town sends a caravan every three days to a village of another type up to 1 500 blocks away: a trader with llamas that sells its home's cheap goods and buys what's dear there, for money from its home treasury.

![A caravan from a desert village](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/village/caravan.jpg)

**Raids.** A lost raid costs 30% of prosperity, half the treasury and half of the villagers' money (halved again with the Watch Post). A won raid brings prosperity and restoration orders that pay one and a half times as much.

**Village markets.** Newly generated plains, desert, savanna, taiga and snowy villages can have market stalls with server vendors selling enchanted books, name tags, saddles, maps, rare seeds and more for money, and a changer that sells emeralds for 3.00 ₮ and buys them for 2.00 ₮. Existing chunks don't change.

---

![Commands](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_commands.png)

## Commands

| Command | What it does | Permission | Default |
|---|---|---|---|
| `/bal [player]`, `/balance` | Your balance, or someone else's | `tradery.balance`, `tradery.balance.others` | everyone, op 2 |
| `/pay <player> <amount>` | Pay a player, online or offline | `tradery.pay` | everyone |
| `/baltop [page]` | Richest players | `tradery.baltop` | everyone |
| `/tradery history [page]` | Your last transactions | `tradery.history` | everyone |
| `/tradery withdraw <amount>` | Turn balance into coins | `tradery.withdraw` | everyone |
| `/tradery shops` | Your shops in the shop ledger window | `tradery.shops` | everyone |
| `/tradery hud` | Show or hide the balance | | everyone |
| `/eco give\|take\|set <player> <amount>` | Change a balance | `tradery.admin.eco` | op 2 |
| `/eco lock\|unlock <player>` | Freeze an account | `tradery.admin.eco` | op 2 |
| `/eco stats` | Money supply, created and destroyed today and this week | `tradery.admin.stats` | op 2 |
| `/tradery vendors [player]` | Vending blocks with clickable coordinates | `tradery.admin.vendors` | op 2 |
| `/tradery reload` | Reload the configs | `tradery.admin.reload` | op 3 |

Amounts are typed in normal units: `/pay Steve 12.50`. Permissions work with LuckPerms or any permission mod.

---

![For Servers](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_servers.png)

## For servers

- `config/tradery/server.json5`: the currency, starting and maximum balance, `/pay`, vending fees and limits, coin ore, villagers, markets and villages. `rewards.json5`: rewards and the rules against farms. `client.json5`: each player's balance display, hints, village title and notifications. Broken values fall back to defaults with a line in the log; `/tradery reload` applies changes.
- Data packs control fair prices, what each profession buys and produces, regional goods, projects, the buildings villages put up, market offers and coin ore placement.
- Each part of the villager economy can be turned off on its own: customers and suppliers, the trade window, market stalls, village buildings, festivals.
- Every transaction is logged to `<world>/tradery/logs/YYYY-MM-DD.log`, one JSON line each.
- When Minecraft writes a vending block's chunk, the money and the traders' inventories are written in the same tick, so after a crash the stock, the money and the items always match. Tested by killing the server mid-trade.

---

![For Developers](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_developers.png)

## For developers

`dev.eliasnvx:tradery-api:1.1.0+26.3` gives you accounts, atomic transactions and events, the same on every loader. On 1.21.1 and 1.20.1 ids are `ResourceLocation`s.

```java
TraderyEconomy eco = TraderyEconomy.get();               // server thread only
Account account = eco.account(player.getUUID());
TransactionResult result = eco.withdraw(account, 500,    // 5.00 with 2 decimals
    Reason.of(Identifier.fromNamespaceAndPath("mymod", "teleport_fee")));
```

Guide: [`docs/api/README.md`](https://github.com/eliasnvx/Tradery/blob/26.3-dev/docs/api/README.md). On Fabric, Tradery is also a Common Economy API provider, so mods built on that API use Tradery balances.

---

![Compatibility](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_compat.png)

## Compatibility

| Mod | What you get |
|---|---|
| Jade | Owner, offer, stock and keys of vending blocks; a bell's village, level, treasury and savings |
| JEI, REI | Drag items into the goods, price and facade slots; info pages for the blocks, coin ore and coins |
| Common Economy API (Fabric, built in) | Other economy mods read and change Tradery balances |
| Text Placeholder API (Fabric) | `%tradery:balance%`, `%tradery:balance_short%`, `%tradery:top_name 1%`, `%tradery:top_balance 1%`, `%tradery:currency%` |
| LuckPerms | All `tradery.*` permission nodes |
| Minimaps | The village title stays in the middle of the screen, clear of the corners |

All optional. EMI and WTHIT will follow when they're available for 26.3.

---

![Installation](https://raw.githubusercontent.com/eliasnvx/Tradery/26.3-dev/docs/images/header_install.png)

## Installation

| Minecraft | Fabric | NeoForge / Forge | Java |
|---|---|---|---|
| 26.3 | [Fabric Loader](https://fabricmc.net/) 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api) | [NeoForge](https://neoforged.net/) 26.3 | 25 |
| 1.21.1 | Fabric Loader 0.15.11+ and Fabric API | NeoForge 21.1 | 21 |
| 1.20.1 | Fabric Loader 0.16.10+ and Fabric API | [Forge](https://files.minecraftforge.net/) 47 | 17 |

Put the jar in `mods` on the client and on the server. Jade, JEI and REI are optional everywhere, Text Placeholder API on Fabric.

All versions have the same features. The one difference: on 1.21.1 and 1.20.1 the vending block's lamp glows on NeoForge and Forge only.

---

## FAQ

**Does it work without the mod on clients?** No. The blocks, the balance and the screens need Tradery on both sides.

**Can hoppers fill or empty a vending block?** No. Vending blocks have no inventory that automation can reach.

**Villagers don't come to my stall.** The block must sell for money, have stock and have "Villagers can buy" on, and the price must be at most 1.5 times the fair price. Villagers need a bed and a workstation, come during the day, and don't walk in unloaded chunks.

**What are emeralds for now?** They're an item like any other: the market changer trades them, item-priced blocks can ask for them, wandering traders use them.

**Does it change villages I already have?** Market stalls generate only in new chunks. Orders, levels and the board work in any village with a bell, and buildings appear only when players fund them or the village saves up.

**Can I rename the currency?** Yes: name, symbol, decimals and the thousands separator are in `server.json5`. Screens show a coin icon in place of the symbol.

---

## Credits

- Author: eliasnvx
- Every screenshot here is a real in-game picture, the banner too (with Complementary Reimagined shaders). The mod icon, the section icons and the textures are pixel art drawn pixel by pixel. No AI-generated images.
- Inspired by the Vending Block mod and written from scratch; no code from Vending Block or its ports is used.

## License

[MIT](https://github.com/eliasnvx/Tradery/blob/26.3-dev/LICENSE) © eliasnvx
