package dev.eliasnvx.tradery.fabric.compat.placeholders;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;

import java.util.List;

/**
 * Text Placeholder API: {@code %tradery:balance%}, {@code %tradery:balance_short%}, {@code %tradery:balance_raw%},
 * {@code %tradery:top_name N%}, {@code %tradery:top_balance N%}, {@code %tradery:currency%}.
 */
public final class TraderyPlaceholders {
    private TraderyPlaceholders() {
    }

    public static void register() {
        Placeholders.register(Tradery.id("balance"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !EconomyService.INSTANCE.isReady()) {
                return PlaceholderResult.invalid("No player");
            }
            EconomyService eco = EconomyService.INSTANCE;
            return PlaceholderResult.value(eco.defaultCurrency().formatPlain(eco.account(ctx.player().getUUID()).balance(eco.defaultCurrency())));
        });
        Placeholders.register(Tradery.id("balance_short"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !EconomyService.INSTANCE.isReady()) {
                return PlaceholderResult.invalid("No player");
            }
            EconomyService eco = EconomyService.INSTANCE;
            return PlaceholderResult.value(eco.defaultCurrency().formatShort(eco.account(ctx.player().getUUID()).balance(eco.defaultCurrency())));
        });
        Placeholders.register(Tradery.id("balance_raw"), (ctx, arg) -> {
            if (!ctx.hasPlayer() || !EconomyService.INSTANCE.isReady()) {
                return PlaceholderResult.invalid("No player");
            }
            EconomyService eco = EconomyService.INSTANCE;
            long balance = eco.account(ctx.player().getUUID()).balance(eco.defaultCurrency());
            return PlaceholderResult.value(eco.defaultCurrency().toMajor(balance).toPlainString());
        });
        Placeholders.register(Tradery.id("top_name"), (ctx, arg) -> {
            LedgerAccount entry = top(arg);
            return entry == null ? PlaceholderResult.value("") : PlaceholderResult.value(entry.displayName());
        });
        Placeholders.register(Tradery.id("top_balance"), (ctx, arg) -> {
            LedgerAccount entry = top(arg);
            EconomyService eco = EconomyService.INSTANCE;
            return entry == null ? PlaceholderResult.value("") : PlaceholderResult.value(eco.defaultCurrency().formatPlain(entry.balance(eco.defaultCurrency())));
        });
        Placeholders.register(Tradery.id("currency"), (ctx, arg) ->
            PlaceholderResult.value(EconomyService.INSTANCE.defaultCurrency().symbol()));
    }

    /** Rank N (1-based) of /baltop, or null. */
    private static LedgerAccount top(String arg) {
        if (!EconomyService.INSTANCE.isReady()) {
            return null;
        }
        int rank;
        try {
            rank = arg == null || arg.isBlank() ? 1 : Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank < 1 || rank > 1000) {
            return null;
        }
        List<LedgerAccount> top = EconomyService.INSTANCE.top(rank);
        return top.size() >= rank ? top.get(rank - 1) : null;
    }
}
