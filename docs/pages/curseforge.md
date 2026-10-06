<div align="center">

![Tradery — Vending Blocks & Economy](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/banner.png)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3-E68A00?style=for-the-badge)](https://neoforged.net/)
[![Economy API](https://img.shields.io/badge/Economy%20API-1.0.0-C9962C?style=for-the-badge)](https://github.com/eliasnvx/Tradery/blob/dev/docs/api/README.md)
[![License](https://img.shields.io/badge/License-MIT-A3302B?style=for-the-badge)](https://github.com/eliasnvx/Tradery/blob/dev/LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-F0C95A?style=for-the-badge)](https://github.com/eliasnvx/Tradery/blob/dev/CHANGELOG.md)

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/tradery)
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/tradery)

**Player shops in a block, a currency you can see on screen, and coin ore as the source of money.<br>The spiritual successor to Vending Block — for Fabric and NeoForge, with a public Economy API.**

[Vending Blocks](#vending-blocks) • [Quick Trade](#quick-trade--look-at-hint) • [Balance HUD](#balance-hud) • [Money](#where-money-comes-from) • [Commands](#commands) • [Servers](#for-servers) • [Developers](#for-developers) • [Compatibility](#compatibility) • [Installation](#installation)

</div>

---

## About

Remember **Vending Block**? Put a block down, stock it, set a price — and people buy from you while you're off mining. Tradery brings that back for modern Minecraft and builds a whole small economy around it:

- **Vending blocks** that sell for **items** (the classic way) or for **money** — or *buy* goods from players.
- A **virtual currency** with your balance always in the corner of the screen.
- **Coin ore** in the world, so money comes from playing, not from an admin's command.
- Trade **without opening a window**: sneak + right-click to buy, hold to keep buying.
- Built for servers: permissions, configs, logs, crash-safe saves — and an **Economy API** for other mods.

**Languages:** English, Русский, Українська, Беларуская, Polski, Deutsch, Nederlands, Svenska, Français, Español, Português (Brasil), 日本語, 简体中文, 繁體中文 (台灣), 繁體中文 (香港)

![A small market street with vending blocks and display cases](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/market.jpg)

---

![Vending Blocks](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_vending.png)

## Vending Blocks

A red-and-brass machine with a glass case on top — the goods float inside so everyone can see what's for sale.

- **One block, one offer:** "4 bread for 2.50" or "1 diamond sword for 2 diamonds". Up to 64 items per trade, 27 slots of stock.
- **Money or items:** money goes straight to the owner's balance (minus a configurable fee); items land in 9 revenue slots.
- **Buyback:** flip one switch and the block *buys* the goods from players with the owner's money — a market for your miners.
- **Sells while you're offline.** Online, you get "Sold 16 × Bread for 40.00 ₮ (Steve)"; when you join, a summary of what sold while you were away.
- **Facades:** any full block as the base — oak, spruce, stone bricks, whatever fits your shop.
- **Showcase animations:** static, spin, bob, spin & bob, or hidden.
- **Display block:** a glass case that shows an item and sells nothing — for trophies and shop windows.
- **Admin vendors** with the vendor key: infinite stock, payment burned as a money sink, no fee, owned by the server.
- **Safe by design:** only the owner can break it (explosions, pistons and hoppers can't touch it), the buyer screen has no item slots to exploit, and every sale checks stock, money and room *before* anything moves. Every case is covered by automated tests.

| Buyer | Owner |
|---|---|
| ![Buyer screen](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/buyer_screen.jpg) | ![Owner screen](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/owner_screen.jpg) |

---

![Quick Trade & Hint](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_quick_trade.png)

## Quick Trade & Look-at Hint

**Look at a vending block** and a small card shows who sells what and for how much — no window, no guessing.

![The look-at hint: owner, goods, price and the keys](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/hint.jpg)

| | |
|---|---|
| **Shift + Right-click** | buy one lot from a selling block — **hold** to keep buying, about 5 lots a second |
| **Shift + Left-click** | sell one lot to a buying block — hold to keep selling |
| **Right-click** | the window: ×1, ×8, max, exact stock |
| **Sneak while looking** | the items' full tooltips: enchantments, potion effects, durability |

- The block in your hand is never placed by accident, and the machine is never mined.
- The action bar adds the whole streak up: *"Bought 20 × Bread for 12.50"*.
- Sold out or not set up? The card says so in red before you click.

![A held Shift + Right-click: 20 bread in one go](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/quick_buy.jpg)

---

![Balance HUD](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_hud.png)

## Balance HUD

![Balance HUD with a +1 234.50 popup](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/hud.png)

Your balance in any corner, with a coin icon, a rolling counter and `+120` / `-40` popups.

- Any corner and offset, scale 0.5–2, full (`1 250.00`) or short (`1.2K`) format.
- Hidden with F1, a key (unbound by default) or `/tradery hud`.
- Moves above the hotbar on small screens; tested at GUI scale 1–4.

---

![Where Money Comes From](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_money.png)

## Where Money Comes From

![Copper, silver and gold coin ore in stone and deepslate](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/page/ore_wall.jpg)

- **Coin ore** in three tiers — copper, silver, gold — in stone and deepslate. Mining it puts the coins' value **straight on your balance**; machines and explosions drop coin items instead.
- **No doubling:** Fortune doesn't multiply money, coin ore isn't a `c:ores` ore and has no furnace recipe, so crushers and ore processors can't double it.
- **Coins are cash:** right-click to deposit, `/tradery withdraw` to pay out, trade them in item-priced vending blocks.
- **Rewards** for killing mobs (on by default), and optionally for mining, crafting, fishing and advancements. Spawner mobs pay nothing; diminishing returns and a daily cap keep farms honest.
- **Money sinks:** the vending fee, the `/pay` tax, burning admin vendors. `/eco stats` shows money created and destroyed per day.
- Vein count, size and height are data-pack JSON — tune them like vanilla ores.

---

![Commands](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_commands.png)

## Commands

| Command | What it does | Permission | Default |
|---|---|---|---|
| `/bal [player]` (`/balance`) | Your or someone's balance | `tradery.balance`, `tradery.balance.others` | everyone / op 2 |
| `/pay <player> <amount>` | Pay a player (online or offline) | `tradery.pay` | everyone |
| `/baltop [page]` | Richest players | `tradery.baltop` | everyone |
| `/tradery history [page]` | Your last transactions | `tradery.history` | everyone |
| `/tradery withdraw <amount>` | Balance → coins | `tradery.withdraw` | everyone |
| `/tradery hud` | Show/hide the balance HUD | — | everyone |
| `/eco give\|take\|set <player> <amount>` | Change a balance | `tradery.admin.eco` | op 2 |
| `/eco lock\|unlock <player>` | Freeze an account | `tradery.admin.eco` | op 2 |
| `/eco stats` | Money supply, created/destroyed today and this week | `tradery.admin.stats` | op 2 |
| `/tradery vendors [player]` | Vending blocks with clickable coordinates | `tradery.admin.vendors` | op 2 |
| `/tradery reload` | Reload the configs | `tradery.admin.reload` | op 3 |

Amounts are typed in normal units (`/pay Steve 12.50`). Permissions work with LuckPerms or any permission mod.

---

![For Servers](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_servers.png)

## For Servers

- **`config/tradery/server.json5`** — currency (name, symbol, decimals, separator), starting balance, balance ceiling, `/pay` tax, vending fee and per-player limit, item and facade blacklists, coin ore on/off, direct-to-balance, coin values, daily cap, inflation damping, log retention.
- **`rewards.json5`** — rewards per mob / block / item / advancement (ids, `#tags` or `*`), spawner and fake-player rules, daily cap, diminishing returns.
- **`client.json5`** — each player's HUD, hint and notification settings.
- Broken values fall back to defaults and are reported in the log; `/tradery reload` applies changes.
- **Every transaction is logged** to `<world>/tradery/logs/YYYY-MM-DD.log` (JSON lines).
- **Crash-safe:** when Minecraft writes a vending block's chunk, the money and the traders' inventories are written in the same tick. After a crash the stock, the money and the items always match — checked with a real `kill -9`.
- **Hoppers and pipes can't touch vending blocks** — nothing to dupe through automation.

---

![For Developers](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_developers.png)

## For Developers

`dev.eliasnvx:tradery-api:1.0.0+26.3` — accounts, atomic transactions and events, one API for Fabric and NeoForge.

```java
TraderyEconomy eco = TraderyEconomy.get();               // server thread only
Account account = eco.account(player.getUUID());
TransactionResult result = eco.withdraw(account, 500,    // 5.00 with 2 decimals
    Reason.of(Identifier.fromNamespaceAndPath("mymod", "teleport_fee")));

// 5% tax on every vending sale inside a region
VendingPurchaseEvent.Pre.EVENT.register(e -> {
    if (Regions.at(e.pos()).is("market")) e.setPrice(e.price() * 105 / 100);
});
```

Guide: [`docs/api/README.md`](https://github.com/eliasnvx/Tradery/blob/dev/docs/api/README.md). On Fabric, Tradery is also a **Common Economy API** provider, so mods that use it work with Tradery balances out of the box.

---

![Compatibility](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_compat.png)

## Compatibility

| Mod | What you get |
|---|---|
| **Jade** | Owner, offer, stock and the trade keys in Jade's tooltip (the built-in hint steps aside) |
| **JEI / REI** | Drag items into the goods, price and facade slots; info pages for the blocks, coin ore and coins |
| **Common Economy API** (Fabric, included) | Other economy mods read and change Tradery balances |
| **Text Placeholder API** (Fabric) | `%tradery:balance%`, `%tradery:balance_short%`, `%tradery:top_name 1%`, `%tradery:top_balance 1%`, `%tradery:currency%` |
| **LuckPerms** | All `tradery.*` permission nodes |

All optional. EMI and WTHIT support will follow when they update to 26.3.

---

![Installation](https://raw.githubusercontent.com/eliasnvx/Tradery/dev/docs/images/header_install.png)

## Installation

| | Fabric | NeoForge |
|---|---|---|
| Minecraft | 26.3 | 26.3 |
| Loader | [Fabric Loader](https://fabricmc.net/) 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api) | [NeoForge](https://neoforged.net/) 26.3+ |
| Java | 25 | 25 |
| Jade, JEI, REI, Text Placeholder API | optional | optional (Jade, JEI, REI) |

1. Install the loader for Minecraft 26.3 (and Fabric API on Fabric).
2. Download Tradery for your loader.
3. Drop the `.jar` into `mods` — on **both** the client and the server.

---

## FAQ

**Does it work on a server without the mod on clients?** No — the blocks, the HUD and the screens need Tradery on both sides.

**Can hoppers fill or empty a vending block?** No, on purpose: automation is the classic way to dupe shops.

**Can I use another currency name or symbol?** Yes — name, symbol, decimals and the thousands separator are in `server.json5`. The HUD and screens show a coin icon instead of the symbol.

**Will it come to 1.21.1 / 1.20.1?** A 1.21.1 backport is planned for 1.1.

---

## Credits & Notes

- **Author:** eliasnvx
- Screenshots are real in-game renders. The banner background and the section icons were generated with an AI image model and composed by hand; the mod's own textures are hand-made pixel art.
- Inspired by the classic Vending Block mod. Tradery is written from scratch; no code from Vending Block or its ports is used.

## License

[MIT](https://github.com/eliasnvx/Tradery/blob/dev/LICENSE) © eliasnvx

<div align="center">

**Built for every server that wants a market square**

</div>
