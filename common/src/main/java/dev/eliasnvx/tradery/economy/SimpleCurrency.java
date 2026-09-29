package dev.eliasnvx.tradery.economy;

import dev.eliasnvx.tradery.api.Currency;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.util.OptionalLong;

/** The configured currency. Immutable; replaced as a whole on reload. */
public record SimpleCurrency(Identifier id, String displayName, String symbol, int decimals, String thousandsSeparator)
    implements Currency {

    public SimpleCurrency {
        Money.unit(decimals); // validates the range
    }

    @Override
    public Component name() {
        return Component.literal(displayName);
    }

    @Override
    public String formatPlain(long amount) {
        return Money.format(amount, decimals, thousandsSeparator, symbol);
    }

    public String formatShort(long amount) {
        return Money.formatShort(amount, decimals, symbol);
    }

    @Override
    public OptionalLong parse(String text) {
        return Money.parse(text, decimals);
    }

    @Override
    public long toMinor(BigDecimal major) {
        return Money.toMinor(major, decimals);
    }

    @Override
    public BigDecimal toMajor(long minor) {
        return Money.toMajor(minor, decimals);
    }
}
