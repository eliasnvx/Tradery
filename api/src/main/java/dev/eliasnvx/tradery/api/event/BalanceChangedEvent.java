package dev.eliasnvx.tradery.api.event;

import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.Currency;
import dev.eliasnvx.tradery.api.Reason;

import java.util.UUID;

/**
 * A finite account's balance changed. Posted once per changed account after {@link TransactionEvent.Post}.
 *
 * @param account    the account
 * @param currency   the currency
 * @param oldBalance balance before, minor units
 * @param newBalance balance after, minor units
 * @param reason     why
 * @param txId       transaction id
 */
public record BalanceChangedEvent(Account account, Currency currency, long oldBalance, long newBalance, Reason reason, UUID txId) {
    public static final Event<BalanceChangedEvent> EVENT = Event.create("BalanceChangedEvent");

    /** @return {@code newBalance - oldBalance} */
    public long delta() {
        return newBalance - oldBalance;
    }
}
