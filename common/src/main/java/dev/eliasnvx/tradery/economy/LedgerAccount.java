package dev.eliasnvx.tradery.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Currency;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * One account's stored state. Mutated only by {@link Ledger}; everyone else reads it through {@link Account}.
 */
public final class LedgerAccount implements Account {
    static final Codec<LedgerAccount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        AccountId.CODEC.fieldOf("id").forGetter(a -> a.id),
        Codec.STRING.optionalFieldOf("name", "").forGetter(a -> a.name),
        Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("balances", Map.of()).forGetter(a -> a.balances),
        Codec.BOOL.optionalFieldOf("infinite", false).forGetter(a -> a.infinite),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(a -> a.locked)
    ).apply(instance, LedgerAccount::new));

    private final AccountId id;
    private final Map<Identifier, Long> balances;
    private final boolean infinite;
    private String name;
    private boolean locked;

    LedgerAccount(AccountId id, String name, Map<Identifier, Long> balances, boolean infinite, boolean locked) {
        this.id = id;
        this.name = name;
        this.balances = new HashMap<>(balances);
        this.balances.values().removeIf(v -> v == 0);
        this.infinite = infinite;
        this.locked = locked;
    }

    @Override
    public AccountId id() {
        return id;
    }

    @Override
    public long balance(Currency currency) {
        return balance(currency.id());
    }

    public long balance(Identifier currency) {
        return infinite ? Long.MAX_VALUE : balances.getOrDefault(currency, 0L);
    }

    void setBalance(Identifier currency, long balance) {
        if (balance == 0) {
            balances.remove(currency);
        } else {
            balances.put(currency, balance);
        }
    }

    Map<Identifier, Long> balances() {
        return balances;
    }

    @Override
    public boolean isInfinite() {
        return infinite;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        return switch (id) {
            case AccountId.Player player -> player.uuid().toString();
            case AccountId.System system -> system.id().toString();
        };
    }

    /** Last known player name, or empty. */
    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Account[" + id.serialize() + (name.isEmpty() ? "" : " " + name) + "]";
    }
}
