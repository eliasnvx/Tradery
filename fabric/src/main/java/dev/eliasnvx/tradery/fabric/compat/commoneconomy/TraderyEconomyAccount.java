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
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * A player's Tradery account seen through the Common Economy API. Every change is a normal Tradery transaction
 * (events, log, HUD) with reason {@code tradery:bridge/common_economy}. Amounts are minor units, as in Tradery
 * (Common Economy API 1.x uses {@code long}).
 */
final class TraderyEconomyAccount implements EconomyAccount {
    static final ResourceLocation REASON = new ResourceLocation("tradery", "bridge/common_economy");

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

    static long clamp(long value) {
        return Math.max(0, value);
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
    public ResourceLocation id() {
        return new ResourceLocation(TraderyEconomyProvider.ID, TraderyEconomyProvider.MAIN_ACCOUNT);
    }

    @Override
    public long balance() {
        if (!EconomyService.INSTANCE.isReady()) {
            return 0;
        }
        return account().balance(EconomyService.INSTANCE.defaultCurrency());
    }

    @Override
    public EconomyTransaction increaseBalance(long value) {
        return run(value, true, false);
    }

    @Override
    public EconomyTransaction canIncreaseBalance(long value) {
        return run(value, true, true);
    }

    @Override
    public EconomyTransaction decreaseBalance(long value) {
        return run(value, false, false);
    }

    @Override
    public EconomyTransaction canDecreaseBalance(long value) {
        return run(value, false, true);
    }

    @Override
    public void setBalance(long value) {
        if (EconomyService.INSTANCE.isReady()) {
            EconomyService.INSTANCE.setBalance(account(), clamp(value), Reason.of(REASON, "set"));
        }
    }

    private EconomyTransaction run(long value, boolean increase, boolean dryRun) {
        long before = balance();
        if (!EconomyService.INSTANCE.isReady()) {
            return new EconomyTransaction.Simple(false, Component.literal("Tradery economy is not available"), before, before, value, this);
        }
        if (value <= 0) {
            return new EconomyTransaction.Simple(false, Messages.failure(dev.eliasnvx.tradery.api.FailReason.INVALID_AMOUNT), before, before, value, this);
        }
        EconomyService economy = EconomyService.INSTANCE;
        Account account = account();
        if (dryRun) {
            boolean ok = !account.isLocked() && (increase
                ? value <= Math.max(0, (economy.maxBalance() > 0 ? economy.maxBalance() : Long.MAX_VALUE) - before)
                : account.canAfford(economy.defaultCurrency(), value));
            long after = ok ? (increase ? before + value : before - value) : before;
            return new EconomyTransaction.Simple(ok, ok ? Component.empty() : Messages.failure(increase
                ? dev.eliasnvx.tradery.api.FailReason.LIMIT_EXCEEDED : dev.eliasnvx.tradery.api.FailReason.INSUFFICIENT_FUNDS),
                after, before, value, this);
        }
        TransactionResult result = increase
            ? economy.deposit(account, value, Reason.of(REASON))
            : economy.withdraw(account, value, Reason.of(REASON));
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
