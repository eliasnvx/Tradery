package dev.eliasnvx.tradery.command;

import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.economy.EconomyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Chat texts. Every text is a translation key with an English fallback, so the server console and clients with an
 * outdated language file still read something sensible.
 */
public final class Messages {
    private Messages() {
    }

    public static MutableComponent tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    /** An amount in the server's currency, highlighted. */
    public static MutableComponent money(long amount) {
        return Component.literal(EconomyService.INSTANCE.defaultCurrency().formatPlain(amount)).withStyle(ChatFormatting.GOLD);
    }

    /** A signed amount: green "+12.00 ₮" or red "-12.00 ₮". */
    public static MutableComponent delta(long delta) {
        String text = EconomyService.INSTANCE.defaultCurrency().formatPlain(Math.abs(delta));
        return delta >= 0
            ? Component.literal("+" + text).withStyle(ChatFormatting.GREEN)
            : Component.literal("-" + text).withStyle(ChatFormatting.RED);
    }

    public static MutableComponent name(String name) {
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    /** Human text for a failed transaction; a cancelling listener's own message wins. */
    public static Component failure(TransactionResult.Failure failure) {
        if (failure.message() != null) {
            return failure.message();
        }
        return failure(failure.reason());
    }

    public static Component failure(FailReason reason) {
        return switch (reason) {
            case INSUFFICIENT_FUNDS -> tr("tradery.error.insufficient_funds", "Not enough money");
            case CANCELLED -> tr("tradery.error.cancelled", "The transaction was blocked");
            case INVALID_AMOUNT -> tr("tradery.error.invalid_amount", "Invalid amount");
            case ACCOUNT_LOCKED -> tr("tradery.error.account_locked", "The account is locked");
            case LIMIT_EXCEEDED -> tr("tradery.error.limit_exceeded", "The balance would exceed the limit");
        };
    }

    /**
     * Name of a reason type: {@code tradery:vending/sale} → key {@code tradery.reason.tradery.vending.sale}, falling
     * back to the raw id for other mods' reasons without a translation.
     */
    public static MutableComponent reason(Identifier type) {
        String key = "tradery.reason." + type.getNamespace() + "." + type.getPath().replace('/', '.');
        return tr(key, type.toString().toLowerCase(Locale.ROOT));
    }
}
