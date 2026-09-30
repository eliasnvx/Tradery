<div align="center">

<img src="common/src/main/resources/assets/tradery/icon.png" width="96" alt="Tradery">

# Tradery — Vending Blocks & Economy

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3-E68A00?style=for-the-badge)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-25-B07219?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-D6303C?style=for-the-badge)](LICENSE)
[![Economy API](https://img.shields.io/badge/Economy%20API-1.0.0-8A5CF6?style=for-the-badge)](docs/api/README.md)

**Player shops in a block, a currency you can see on screen, and coin ore as the source of money — for Fabric and NeoForge, with a public Economy API.**

[Vending](#vending-blocks) • [Balance HUD](#balance-hud) • [Money](#where-money-comes-from) • [Commands](#commands) • [Servers](#for-servers) • [Developers](#for-developers) • [Compatibility](#compatibility)

</div>

---

Tradery is a spiritual successor to the classic **Vending Block** mod: put a block down, stock it, set a price and let people buy while you're away. Prices are paid in **items** (the classic way) or in the server's **currency**, which every player sees in the corner of the screen.

![Vending blocks and a display case](docs/images/showcase.png)

## Vending blocks

![A vending block selling bread](docs/images/vending.png)

- **One block, one offer:** "4 bread for 2.50 ₮" or "1 golden apple for 2 diamonds". Up to 64 items per trade, 27 slots of stock.
- **Money or items:** money goes straight to the owner's balance (minus a configurable fee); items land in 9 revenue slots.
- **Buyback:** flip one switch and the block *buys* the goods from players with the owner's money — a market for your miners.
- **Sells while you're offline**; you get "Sold 16 × Bread for 40 ₮ (Steve)" when online and a summary when you join.
- **Facades:** use any full block as the base. **Showcase animations:** static, spin, bob, spin & bob, or hidden.
- **Display block:** a glass case that shows an item and sells nothing.
- **Admin vendors** (vendor key): infinite stock, payment burned as a money sink, no fee, owned by the server.
- **Safe by design:** only the owner can break it (explosions, pistons and hoppers can't touch it), the buyer screen has no item slots to exploit, and every sale checks stock, money and room *before* anything moves. Each case is covered by automated tests.

| Buyer | Owner |
|---|---|
| ![Buyer screen](docs/images/buyer.png) | ![Owner screen](docs/images/owner.png) |

## Balance HUD

![Balance HUD with a +1 234.50 popup](docs/images/hud.png)

Your balance in any corner, with a coin icon, a rolling counter and `+120` / `-40` popups. Scale 0.5–2, full (`1 250.00`) or short (`1.2K`) format, hidden with F1 or a key (unbound by default) or `/tradery hud`. It moves above the hotbar on small screens.

## Where money comes from

![Coin ores and coins](docs/images/ore.png)

- **Coin ore** in three tiers (copper, silver, gold), in stone and deepslate. Mining it puts the coins' value **straight on your balance**; machines and explosions drop coin items instead. Fortune doesn't multiply money, and coin ore is not an `c:ores` ore, so crushers can't double it. Vein count, size and height are a data pack away.
- **Coins** are cash: right-click to deposit, `/tradery withdraw` to pay out, trade them in item-priced vending blocks.
- **Rewards** for killing mobs (on by default), and optionally for mining, crafting, fishing and advancements. Spawner mobs pay nothing, there are diminishing returns and a daily cap.
- **Money sinks:** vending fee, `/pay` tax, burning admin vendors. `/eco stats` shows money created and destroyed per day, to tune it all.

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

Amounts are typed in normal units (`/pay Steve 12.50`). Permissions work with LuckPerms or any permission mod (Fabric permission API / NeoForge PermissionAPI).

## For servers

Configs are JSON5 files with comments in `config/tradery/`:

- **`server.json5`** — currency (name, symbol, decimals, separator), starting balance, balance ceiling, `/pay` tax, vending fee and per-player limit, item and facade blacklists, coin ore on/off, direct-to-balance, coin values, daily cap, inflation damping, log retention.
- **`rewards.json5`** — rewards per mob / block / item / advancement (ids, `#tags` or `*`), spawner and fake-player rules, daily cap, diminishing returns.
- **`client.json5`** — each player's HUD and notification settings.

Broken values fall back to their defaults and are reported in the log; unknown keys are reported too. Apply changes with `/tradery reload`.

Every transaction is written to `<world>/tradery/logs/YYYY-MM-DD.log` (JSON lines). Balances are saved with the world; a crash rolls everything back to the last save together, so nothing is duplicated.

## For developers

`dev.eliasnvx:tradery-api:1.0.0+26.3` — accounts, atomic transactions and events, one API for Fabric and NeoForge. See the **[API guide](docs/api/README.md)**.

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

On Fabric, Tradery is also a **Common Economy API** provider, so mods like Universal Shops work with Tradery balances out of the box.

## Compatibility

| Mod | What you get |
|---|---|
| Jade | Owner, offer and stock when you look at a vending block |
| JEI / REI | Drag items into the goods, price and facade slots; info pages for the blocks, coin ore and coins |
| Common Economy API (Fabric, included) | Other economy mods read and change Tradery balances |
| Text Placeholder API (Fabric) | `%tradery:balance%`, `%tradery:balance_short%`, `%tradery:top_name 1%`, `%tradery:top_balance 1%`, `%tradery:currency%` |
| LuckPerms | All `tradery.*` permission nodes |

All of them are optional. EMI and WTHIT support will follow when they update to 26.3.

## Building

```bash
./gradlew build                      # both loaders, JUnit and GameTests
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
./gradlew :fabric:runClient -Pcompat=true   # with JEI, REI, Jade, Placeholder API
./gradlew :fabric:runClientGameTest  # client checks with screenshots
```

Requires JDK 25 (Gradle picks it through `gradle/gradle-daemon-jvm.properties`). Textures and models are generated by `tools/textures.py` and `tools/assets.py`.

## License

MIT. Tradery is written from scratch; no code from other Vending Block ports is used.
