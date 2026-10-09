# Tradery Economy API

`dev.eliasnvx:tradery-api:<api>+<minecraft>` (`+26.3`, `+1.21.1`, `+1.20.1`) — the same API on Fabric and NeoForge/Forge. It depends on vanilla Minecraft only.

```groovy
repositories { maven { url "<Tradery maven>" } }
dependencies {
    compileOnly "dev.eliasnvx:tradery-api:1.0.0+1.20.1"   // the classes ship inside the Tradery mod jar
}
```

Declare Tradery as an optional dependency and check it's loaded before touching the API.

## Rules

- Call everything on the **server thread** while a server is running (`TraderyEconomy.isAvailable()`); other threads get `IllegalStateException`.
- Money is a `long` in **minor units**: with `currency.decimals() == 2`, `150` is `1.50`. Use `currency.parse("1.50")`, `currency.toMinor(BigDecimal)` and `currency.format(amount)` at the edges.
- A transaction is **atomic**: it either changes every side or nothing, and says why in `TransactionResult.Failure`.

## Accounts and transactions

```java
TraderyEconomy eco = TraderyEconomy.get();
Currency coins = eco.defaultCurrency();

Account player = eco.account(uuid);                                         // created on first use
Account bank = eco.systemAccount(new ResourceLocation("mymod", "bank")); // your own account
Account server = eco.serverAccount();                                       // infinite: creates/destroys money

TransactionResult r = eco.transfer(player, bank, coins.parse("10").orElseThrow(), Reason.of(MY_REASON));
eco.transfer(player, bank, 1_000, 20, Reason.of(MY_REASON));   // payer pays 10.00, bank gets 9.80, 0.20 destroyed
eco.deposit(player, 500, Reason.of(MY_REASON));                // creates money
eco.withdraw(player, 500, Reason.of(MY_REASON));               // destroys money

switch (r) {
    case TransactionResult.Success s -> log(s.txId(), s.balanceFrom(), s.balanceTo());
    case TransactionResult.Failure f -> player.sendSystemMessage(f.message() != null ? f.message() : Component.literal(f.reason().name()));
}
```

Failure reasons: `INSUFFICIENT_FUNDS`, `CANCELLED` (an event listener), `INVALID_AMOUNT` (≤ 0, bad fee, same account), `ACCOUNT_LOCKED` (`/eco lock`), `LIMIT_EXCEEDED` (overflow or the configured ceiling).

A `Reason` is an id in your namespace (`mymod:teleport_fee`) plus an optional note. It shows up in the transaction log and in `/tradery history` (add `tradery.reason.<namespace>.<path with dots>` to your lang file for a nice name).

## Events

Every event has a static `EVENT`; listeners take a priority (`HIGHEST` … `LOWEST`, default `NORMAL`). Cancelled events skip later listeners unless they registered with `receiveCancelled = true`. A listener that throws is logged and skipped.

| Event | When | Can change |
|---|---|---|
| `TransactionEvent.Pre` | Before any transaction | cancel (with a message), amount, fee |
| `TransactionEvent.Post` | After a successful one | — |
| `BalanceChangedEvent` | A finite account's balance changed | — |
| `AccountCreatedEvent` | First access to an account | starting balance |
| `VendingPurchaseEvent.Pre` / `.Post` | A sale or buyback in a vending block | cancel, price per trade, number of trades |
| `VendingPlacedEvent` | A player places a vending block | cancel |
| `VendingConfiguredEvent` | The owner saves settings | cancel, price |
| `CoinOreMinedEvent` | Coin ore mined, before payout | cancel, amount, payout (balance / items) |
| `CoinPickedUpEvent` | Coins picked up straight to the balance | cancel (normal pickup), amount |
| `RewardGrantedEvent` | A mob/action reward | cancel, amount |

```java
// Double coin ore on weekends
CoinOreMinedEvent.EVENT.register(e -> {
    if (isWeekend()) e.setAmount(e.amount() * 2);
});

// No trading in spawn
VendingPurchaseEvent.Pre.EVENT.register(EventPriority.HIGH, e -> {
    if (isSpawn(e.pos())) e.cancel(Component.literal("No trading in spawn"));
});

// 10% tax on every /pay, burned
TransactionEvent.Pre.EVENT.register(e -> {
    if (e.reason().type().equals(Reasons.PAY)) e.setFee(e.amount() / 10);
});
```

Tradery's own reason ids are in `Reasons` (`tradery:pay`, `tradery:vending/sale`, `tradery:ore/mined`, `tradery:reward/kill`, …).

## Versioning

The API has its own SemVer (`TraderyApi.API_VERSION`), independent of the mod version. Breaking changes only in a major version.
