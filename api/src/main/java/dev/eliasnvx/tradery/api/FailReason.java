package dev.eliasnvx.tradery.api;

/** Why a transaction failed. */
public enum FailReason {
    /** The payer doesn't have enough money. */
    INSUFFICIENT_FUNDS,
    /** A {@code TransactionEvent.Pre} listener cancelled it. */
    CANCELLED,
    /** Amount {@code <= 0}, fee outside {@code [0, amount]}, or payer and payee are the same account. */
    INVALID_AMOUNT,
    /** One of the accounts is locked. */
    ACCOUNT_LOCKED,
    /** The payee's balance would overflow or exceed the configured maximum. */
    LIMIT_EXCEEDED
}
