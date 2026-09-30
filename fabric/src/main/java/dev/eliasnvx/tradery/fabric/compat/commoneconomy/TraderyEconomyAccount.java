package dev.eliasnvx.tradery.fabric.compat.commoneconomy;

import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import eu.pb4.common.economy.api.EconomyTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.math.BigInteger;
import java.util.UUID;

/**
 * A player's Tradery account seen through the Common Economy API. Every change is a normal Tradery transaction
 * (events, log, HUD) with reason {@code tradery:bridge/common_economy}.
 */
final class TraderyEconomyAccount implements EconomyAccount {
    static final Identifier REASON = Identifier.fromNamespaceAndPath("tradery", "bridge/common_economy");
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final EconomyProvider provider;
    private final EconomyCurrency currency;
    private final UUID owner;
    private final String ownerName;

    TraderyEconomyAccount(EconomyProvider provider, EconomyCurrency currency, UUID owner, String ownerName) {
        this.provider = provider;
        this.currency = currency;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    static long clamp(BigInteger value) {
        return value.signum() < 0 ? 0 : value.min(LONG_MAX).longValue();
    }

    private LedgerAccount account() {
        return EconomyService.INSTANCE.account(owner);
    }

    @Override
    public Component name() {
        return Component.literal(ownerName);
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(TraderyEconomyProvider.ID, TraderyEconomyProvider.MAIN_ACCOUNT);
    }

    @Override
    public BigInteger balance() {
        if (!EconomyService.INSTANCE.isReady()) {
            return BigInteger.ZERO;
        }
        return BigInteger.valueOf(account().balance(EconomyService.INSTANCE.defaultCurrency()));
    }

    @Override
    public EconomyTransaction increaseBalance(BigInteger value) {
        return run(value, true, false);
    }

    @Override
    public EconomyTransaction canIncreaseBalance(BigInteger value) {
        return run(value, true, true);
    }

    @Override
    public EconomyTransaction decreaseBalance(BigInteger value) {
        return run(value, false, false);
    }

    @Override
    public EconomyTransaction canDecreaseBalance(BigInteger value) {
        return run(value, false, true);
    }

    @Override
    public void setBalance(BigInteger value) {
        if (EconomyService.INSTANCE.isReady()) {
            EconomyService.INSTANCE.setBalance(account(), clamp(value), Reason.of(REASON, "set"));
        }
    }

    private EconomyTransaction run(BigInteger value, boolean increase, boolean dryRun) {
        BigInteger before = balance();
        if (!EconomyService.INSTANCE.isReady()) {
            return new EconomyTransaction.Simple(false, Component.literal("Tradery economy is not available"), before, before, value, this);
        }
        if (value.signum() <= 0 || value.compareTo(LONG_MAX) > 0) {
            return new EconomyTransaction.Simple(false, Messages.failure(dev.eliasnvx.tradery.api.FailReason.INVALID_AMOUNT), before, before, value, this);
        }
        long amount = value.longValue();
        EconomyService economy = EconomyService.INSTANCE;
        Account account = account();
        if (dryRun) {
            boolean ok = !account.isLocked() && (increase
                ? amount <= Math.max(0, (economy.maxBalance() > 0 ? economy.maxBalance() : Long.MAX_VALUE) - before.longValue())
                : account.canAfford(economy.defaultCurrency(), amount));
            BigInteger after = increase ? before.add(value) : before.subtract(value);
            return new EconomyTransaction.Simple(ok, ok ? Component.empty() : Messages.failure(increase
                ? dev.eliasnvx.tradery.api.FailReason.LIMIT_EXCEEDED : dev.eliasnvx.tradery.api.FailReason.INSUFFICIENT_FUNDS),
                ok ? after : before, before, value, this);
        }
        TransactionResult result = increase
            ? economy.deposit(account, amount, Reason.of(REASON))
            : economy.withdraw(account, amount, Reason.of(REASON));
        if (result instanceof TransactionResult.Failure failure) {
            return new EconomyTransaction.Simple(false, Messages.failure(failure), before, before, value, this);
        }
        return new EconomyTransaction.Simple(true, Component.empty(), balance(), before, value, this);
    }

    @Override
    public EconomyProvider provider() {
        return provider;
    }

    @Override
    public EconomyCurrency currency() {
        return currency;
    }
}
