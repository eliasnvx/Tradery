package dev.eliasnvx.tradery.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Outcome of a transaction. */
public sealed interface TransactionResult permits TransactionResult.Success, TransactionResult.Failure {
    /** Balance value for a side that doesn't exist (deposit / withdraw) or is infinite. */
    long NO_BALANCE = -1L;

    /**
     * @return whether money moved
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * The transaction happened.
     *
     * @param txId        unique id, also written to the transaction log
     * @param amount      amount taken from the payer (after event changes)
     * @param fee         part of {@code amount} that was destroyed
     * @param balanceFrom the payer's new balance, or {@link #NO_BALANCE}
     * @param balanceTo   the payee's new balance, or {@link #NO_BALANCE}
     */
    record Success(UUID txId, long amount, long fee, long balanceFrom, long balanceTo) implements TransactionResult {
    }

    /**
     * Nothing changed.
     *
     * @param reason  why
     * @param message a text for the player (e.g. from a cancelling listener), may be {@code null}
     */
    record Failure(FailReason reason, @Nullable Component message) implements TransactionResult {
        /**
         * @param reason why
         * @return a failure without a message
         */
        public static Failure of(FailReason reason) {
            return new Failure(reason, null);
        }
    }
}
