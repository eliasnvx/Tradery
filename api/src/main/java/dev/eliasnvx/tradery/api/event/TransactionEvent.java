package dev.eliasnvx.tradery.api.event;

import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.Currency;
import dev.eliasnvx.tradery.api.Reason;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Events around every transaction. Posted on the server thread. */
public final class TransactionEvent {
    private TransactionEvent() {
    }

    /** What kind of transaction. */
    public enum Type {
        /** {@code from} pays {@code to}; both set. */
        TRANSFER,
        /** Money created on {@code to}; {@code from} is {@code null}. */
        DEPOSIT,
        /** Money destroyed from {@code from}; {@code to} is {@code null}. */
        WITHDRAW
    }

    /**
     * Before anything changes. Listeners may cancel it (with a message) or change the amount and fee: taxes,
     * bonuses, limits. Changes are validated again after all listeners ran.
     */
    public static final class Pre extends CancellableEvent {
        public static final Event<Pre> EVENT = Event.create("TransactionEvent.Pre");

        private final Type type;
        private final @Nullable Account from;
        private final @Nullable Account to;
        private final Currency currency;
        private final Reason reason;
        private long amount;
        private long fee;

        public Pre(Type type, @Nullable Account from, @Nullable Account to, Currency currency, long amount, long fee, Reason reason) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.currency = currency;
            this.amount = amount;
            this.fee = fee;
            this.reason = reason;
        }

        public Type type() {
            return type;
        }

        /** @return the payer, {@code null} for a deposit */
        public @Nullable Account from() {
            return from;
        }

        /** @return the payee, {@code null} for a withdrawal */
        public @Nullable Account to() {
            return to;
        }

        public Currency currency() {
            return currency;
        }

        public Reason reason() {
            return reason;
        }

        /** @return amount taken from the payer, minor units */
        public long amount() {
            return amount;
        }

        /** @param amount new amount, minor units, {@code > 0} */
        public void setAmount(long amount) {
            this.amount = amount;
        }

        /** @return part of the amount that is destroyed (transfers only) */
        public long fee() {
            return fee;
        }

        /** @param fee new fee, {@code 0 <= fee <= amount}; ignored for deposits and withdrawals */
        public void setFee(long fee) {
            this.fee = fee;
        }
    }

    /**
     * After a successful transaction. Read-only.
     *
     * @param type        kind of transaction
     * @param from        the payer, {@code null} for a deposit
     * @param to          the payee, {@code null} for a withdrawal
     * @param currency    the currency
     * @param amount      amount taken from the payer
     * @param fee         part of the amount that was destroyed
     * @param reason      why
     * @param txId        transaction id
     * @param balanceFrom payer's new balance or {@link dev.eliasnvx.tradery.api.TransactionResult#NO_BALANCE}
     * @param balanceTo   payee's new balance or {@link dev.eliasnvx.tradery.api.TransactionResult#NO_BALANCE}
     */
    public record Post(Type type, @Nullable Account from, @Nullable Account to, Currency currency, long amount, long fee,
                       Reason reason, UUID txId, long balanceFrom, long balanceTo) {
        public static final Event<Post> EVENT = Event.create("TransactionEvent.Post");
    }
}
