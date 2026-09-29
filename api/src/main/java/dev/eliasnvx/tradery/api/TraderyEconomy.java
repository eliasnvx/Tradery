package dev.eliasnvx.tradery.api;

import dev.eliasnvx.tradery.api.internal.TraderyEconomyHolder;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Entry point of the Tradery Economy API.
 *
 * <p>All account and transaction methods must be called on the logical server thread while a server is running;
 * anything else throws {@link IllegalStateException}. Currency accessors may be called from any thread.
 *
 * <p>Amounts are {@code long} values in the currency's minor units: with {@link Currency#decimals()} = 2,
 * {@code 150} means {@code 1.50}.
 *
 * <pre>{@code
 * TraderyEconomy eco = TraderyEconomy.get();
 * Account player = eco.account(serverPlayer.getUUID());
 * TransactionResult result = eco.withdraw(player, 500, Reason.of(Identifier.fromNamespaceAndPath("mymod", "teleport_fee")));
 * if (result instanceof TransactionResult.Failure failure) { ... }
 * }</pre>
 */
public interface TraderyEconomy {
    /**
     * Returns the economy.
     *
     * @return the economy implementation
     * @throws IllegalStateException if Tradery isn't loaded yet
     */
    static TraderyEconomy get() {
        return TraderyEconomyHolder.get();
    }

    /**
     * Whether the economy can be used right now: Tradery is loaded, a server is running and the caller is on its
     * thread.
     *
     * @return {@code true} if account and transaction methods may be called
     */
    static boolean isAvailable() {
        return TraderyEconomyHolder.isInstalled() && TraderyEconomyHolder.get().isReady();
    }

    /**
     * Whether a server is running and the current thread is its main thread.
     *
     * @return {@code true} if account and transaction methods may be called
     */
    boolean isReady();

    /**
     * The currency every balance is kept in (MVP has exactly one).
     *
     * @return the default currency
     */
    Currency defaultCurrency();

    /**
     * Looks up a currency by id.
     *
     * @param id the currency id, e.g. {@code tradery:coin}
     * @return the currency, or empty if unknown
     */
    Optional<Currency> currency(Identifier id);

    /**
     * The account of a player, created on first access (with the configured starting balance).
     *
     * @param player the player's UUID
     * @return the account, never {@code null}
     */
    Account account(UUID player);

    /**
     * Looks up an existing account of any kind. Never creates one.
     *
     * @param id the account id
     * @return the account, or empty if it doesn't exist
     */
    Optional<Account> account(AccountId id);

    /**
     * A system account owned by a mod (a treasury, a bank), created on first access with a zero balance.
     *
     * @param id the account id, in the owning mod's namespace
     * @return the account, never {@code null}
     */
    Account systemAccount(Identifier id);

    /**
     * The server account {@code tradery:server}. It is infinite: paying from it creates money, paying into it
     * destroys money. Admin vendors belong to it.
     *
     * @return the server account
     */
    Account serverAccount();

    /**
     * Moves money between two accounts atomically: either both balances change, or neither does.
     *
     * @param from   the payer
     * @param to     the payee
     * @param amount amount taken from the payer, in minor units, {@code > 0}
     * @param reason why (shown in logs and history)
     * @return the result
     */
    default TransactionResult transfer(Account from, Account to, long amount, Reason reason) {
        return transfer(from, to, amount, 0, reason);
    }

    /**
     * Moves money between two accounts, keeping a fee. The payer loses {@code amount}, the payee gets
     * {@code amount - fee}, and the fee is destroyed (a money sink).
     *
     * @param from   the payer
     * @param to     the payee
     * @param amount amount taken from the payer, in minor units, {@code > 0}
     * @param fee    part of {@code amount} that is destroyed, {@code 0 <= fee <= amount}
     * @param reason why
     * @return the result
     */
    TransactionResult transfer(Account from, Account to, long amount, long fee, Reason reason);

    /**
     * Creates money on an account (emission).
     *
     * @param to     the account
     * @param amount amount in minor units, {@code > 0}
     * @param reason why
     * @return the result
     */
    TransactionResult deposit(Account to, long amount, Reason reason);

    /**
     * Destroys money from an account (a sink).
     *
     * @param from   the account
     * @param amount amount in minor units, {@code > 0}
     * @param reason why
     * @return the result
     */
    TransactionResult withdraw(Account from, long amount, Reason reason);
}
