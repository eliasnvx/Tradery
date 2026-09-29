package dev.eliasnvx.tradery.api;

/**
 * A live view of an account. Balances change only through {@link TraderyEconomy} transactions. Server thread only.
 */
public interface Account {
    /**
     * @return the account id
     */
    AccountId id();

    /**
     * @param currency the currency
     * @return the balance in minor units; {@link Long#MAX_VALUE} for an infinite account
     */
    long balance(Currency currency);

    /**
     * Balance in the default currency.
     *
     * @return the balance in minor units
     */
    default long balance() {
        return balance(TraderyEconomy.get().defaultCurrency());
    }

    /**
     * @param currency the currency
     * @param amount   amount in minor units
     * @return whether a transaction taking {@code amount} would not fail for lack of funds
     */
    default boolean canAfford(Currency currency, long amount) {
        return isInfinite() || balance(currency) >= amount;
    }

    /**
     * Infinite accounts (such as {@link TraderyEconomy#serverAccount()}) never run out: paying from one creates
     * money, paying into one destroys it.
     *
     * @return whether this account is infinite
     */
    boolean isInfinite();

    /**
     * Locked accounts can neither pay nor be paid ({@link FailReason#ACCOUNT_LOCKED}).
     *
     * @return whether this account is locked
     */
    boolean isLocked();

    /**
     * For player accounts, the last name the player was seen with; for system accounts, the id.
     *
     * @return a display name
     */
    String displayName();
}
