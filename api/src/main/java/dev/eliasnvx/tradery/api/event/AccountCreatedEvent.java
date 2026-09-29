package dev.eliasnvx.tradery.api.event;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Currency;

/**
 * An account is being created (first access). Listeners may change the starting balance; a positive one is
 * paid as a deposit with reason {@link dev.eliasnvx.tradery.api.Reasons#STARTING_BALANCE}.
 */
public final class AccountCreatedEvent {
    public static final Event<AccountCreatedEvent> EVENT = Event.create("AccountCreatedEvent");

    private final AccountId id;
    private final Currency currency;
    private long startingBalance;

    public AccountCreatedEvent(AccountId id, Currency currency, long startingBalance) {
        this.id = id;
        this.currency = currency;
        this.startingBalance = startingBalance;
    }

    public AccountId id() {
        return id;
    }

    public Currency currency() {
        return currency;
    }

    /** @return starting balance in minor units (config value for players, 0 for system accounts) */
    public long startingBalance() {
        return startingBalance;
    }

    /** @param startingBalance new starting balance, minor units, {@code >= 0} */
    public void setStartingBalance(long startingBalance) {
        this.startingBalance = Math.max(0, startingBalance);
    }
}
