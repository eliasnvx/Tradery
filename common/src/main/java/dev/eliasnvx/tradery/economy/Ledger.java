package dev.eliasnvx.tradery.economy;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.FailReason;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All accounts and the rules of moving money between them. Pure state with no events or I/O, so every rule is
 * unit-tested: a move either passes every check and changes both sides, or changes nothing.
 */
public final class Ledger {
    private final Map<AccountId, LedgerAccount> accounts = new LinkedHashMap<>();
    /** Lower-cased last known name → player UUID, for commands that take offline players. */
    private final Map<String, UUID> playersByName = new HashMap<>();
    /** Sum of every finite balance, per currency: the money supply held on accounts. */
    private final Map<Identifier, Long> totals = new HashMap<>();

    /** Result of {@link #move}. */
    public sealed interface Outcome permits Moved, Refused {
    }

    /**
     * Money moved. Balances are {@code -1} for a missing or infinite side.
     */
    public record Moved(long fromBefore, long fromAfter, long toBefore, long toAfter) implements Outcome {
    }

    /** Nothing changed. */
    public record Refused(FailReason reason) implements Outcome {
    }

    public Optional<LedgerAccount> get(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Collection<LedgerAccount> all() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public int size() {
        return accounts.size();
    }

    /** Creates an empty account; the caller pays any starting balance through {@link #move}. */
    public LedgerAccount create(AccountId id, String name, boolean infinite) {
        if (accounts.containsKey(id)) {
            throw new IllegalStateException("Account already exists: " + id.serialize());
        }
        LedgerAccount account = new LedgerAccount(id, name, Map.of(), infinite, false);
        accounts.put(id, account);
        indexName(account);
        return account;
    }

    /** Adds a loaded account (from saved data). */
    void load(LedgerAccount account) {
        accounts.put(account.id(), account);
        indexName(account);
        if (!account.isInfinite()) {
            account.balances().forEach((currency, balance) -> totals.merge(currency, balance, Ledger::saturatedAdd));
        }
    }

    public void rename(UUID player, String name) {
        LedgerAccount account = accounts.get(AccountId.player(player));
        if (account == null || account.name().equals(name)) {
            return;
        }
        if (!account.name().isEmpty()) {
            playersByName.remove(account.name().toLowerCase(Locale.ROOT), player);
        }
        account.setName(name);
        indexName(account);
    }

    private void indexName(LedgerAccount account) {
        if (account.id() instanceof AccountId.Player player && !account.name().isEmpty()) {
            playersByName.put(account.name().toLowerCase(Locale.ROOT), player.uuid());
        }
    }

    public Optional<UUID> playerByName(String name) {
        return Optional.ofNullable(playersByName.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<String> knownPlayerNames() {
        return accounts.values().stream()
            .filter(a -> a.id() instanceof AccountId.Player && !a.name().isEmpty())
            .map(LedgerAccount::name)
            .toList();
    }

    public void setLocked(LedgerAccount account, boolean locked) {
        account.setLocked(locked);
    }

    /** Money held on finite accounts in this currency. */
    public long total(Identifier currency) {
        return totals.getOrDefault(currency, 0L);
    }

    /**
     * Checks and applies one move. {@code from == null} creates money (deposit), {@code to == null} destroys it
     * (withdraw). The payer loses {@code amount}; the payee gains {@code amount - fee}.
     *
     * @param maxBalance ceiling for the payee's balance, {@code <= 0} for none
     */
    public Outcome move(@Nullable LedgerAccount from, @Nullable LedgerAccount to, Identifier currency, long amount, long fee, long maxBalance) {
        if (amount <= 0 || fee < 0 || fee > amount || (from == null && to == null) || (from != null && from == to)) {
            return new Refused(FailReason.INVALID_AMOUNT);
        }
        if ((from != null && from.isLocked()) || (to != null && to.isLocked())) {
            return new Refused(FailReason.ACCOUNT_LOCKED);
        }
        boolean debit = from != null && !from.isInfinite();
        boolean credit = to != null && !to.isInfinite();
        long fromBefore = debit ? from.balance(currency) : -1;
        long toBefore = credit ? to.balance(currency) : -1;
        long fromAfter = -1;
        long toAfter = -1;
        if (debit) {
            if (fromBefore < amount) {
                return new Refused(FailReason.INSUFFICIENT_FUNDS);
            }
            fromAfter = fromBefore - amount;
        }
        if (credit) {
            long gain = amount - fee;
            try {
                toAfter = Math.addExact(toBefore, gain);
            } catch (ArithmeticException e) {
                return new Refused(FailReason.LIMIT_EXCEEDED);
            }
            if (maxBalance > 0 && gain > 0 && toAfter > maxBalance) {
                return new Refused(FailReason.LIMIT_EXCEEDED);
            }
        }
        if (debit) {
            from.setBalance(currency, fromAfter);
            totals.merge(currency, -amount, Ledger::saturatedAdd);
        }
        if (credit) {
            to.setBalance(currency, toAfter);
            totals.merge(currency, amount - fee, Ledger::saturatedAdd);
        }
        return new Moved(fromBefore, fromAfter, toBefore, toAfter);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }
}
