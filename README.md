<div align="center">

# Tradery — Vending Blocks & Economy

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-DBD0B4?style=for-the-badge)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3-E68A00?style=for-the-badge)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-25-B07219?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-D6303C?style=for-the-badge)](LICENSE)
[![Economy API](https://img.shields.io/badge/Economy%20API-0.1.0-8A5CF6?style=for-the-badge)](#for-developers-economy-api)

**A virtual currency with your balance on screen, vending blocks for player shops, and coin ore as the source of money — for Fabric and NeoForge, with a public Economy API.**

</div>

> **Status: in development.** The economy core, commands and the HUD work; vending blocks, coin ore and integrations are next (see [`docs/PROGRESS.md`](docs/PROGRESS.md)).

## About

Tradery is a spiritual successor to the classic **Vending Block** mod: put a block down, stock it, set a price and let people buy while you're away. Prices can be paid in **items** (the classic way) or in the server's **currency**, which every player sees in the corner of the screen.

## Features

- **Virtual currency** with a configurable name, symbol and decimals; starting balance, balance ceiling, money sinks
- **Balance HUD**: any corner, scale 0.5–2, full (`1 250.00 ₮`) or short (`1.2K ₮`) format, count-up animation, `+120 ₮` / `-40 ₮` popups
- **Commands**: `/bal`, `/pay`, `/baltop`, `/tradery history`, admin `/eco give|take|set|lock|unlock|stats`
- **Permissions** through LuckPerms or any permission mod (Fabric permission API / NeoForge PermissionAPI), with op-level fallbacks
- **Transaction log** (JSON lines per day) and money-supply statistics for balancing
- Coming next: vending and display blocks, buyback mode, admin vendors, coin ore, rewards for mobs and actions, Jade / JEI / REI, Common Economy API and Text Placeholder API on Fabric

## For servers

Configs live in `config/tradery/`:

- `server.json5` — currency, starting balance, `/pay` tax, vending fee, coin ore, log retention. Reload with `/tradery reload`.
- `client.json5` — each player's HUD and notification preferences.

Money values in configs are in normal units (`100`, `0.5`). Every file is JSON5 with comments; broken values fall back to their defaults and are reported in the server log.

| Permission | Default |
|---|---|
| `tradery.balance`, `tradery.pay`, `tradery.baltop`, `tradery.history`, `tradery.withdraw` | everyone |
| `tradery.balance.others`, `tradery.admin.eco`, `tradery.admin.stats`, `tradery.admin.vendors` | op level 2 |
| `tradery.admin.reload` | op level 3 |

## For developers: Economy API

`dev.eliasnvx:tradery-api:<api>+26.3` has no dependency on the mod's internals and works the same on both loaders.

```java
TraderyEconomy eco = TraderyEconomy.get();              // server thread only
Account account = eco.account(player.getUUID());
TransactionResult result = eco.withdraw(account, 500,     // 5.00 with 2 decimals
    Reason.of(Identifier.fromNamespaceAndPath("mymod", "teleport_fee")));

// 5% tax on every vending sale inside a region
VendingPurchaseEvent.Pre.EVENT.register(e -> {
    if (Regions.at(e.pos()).is("market")) e.setPrice(e.price() * 105 / 100);
});
```

Amounts are `long` minor units. Transactions are atomic; events can cancel them or change amounts and fees.

## Building

```bash
./gradlew build            # both loaders, JUnit and GameTests
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

Requires JDK 25 (Gradle picks it through `gradle/gradle-daemon-jvm.properties`).

## License

MIT. Tradery is written from scratch; no code from other Vending Block ports is used.
