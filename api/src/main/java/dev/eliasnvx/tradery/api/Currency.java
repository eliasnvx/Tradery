package dev.eliasnvx.tradery.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.OptionalLong;

/**
 * A currency. Amounts are {@code long} minor units; {@link #decimals()} says where the decimal point goes.
 * All methods are safe to call from any thread.
 */
public interface Currency {
    /**
     * @return the currency id, e.g. {@code tradery:coin}
     */
    ResourceLocation id();

    /**
     * Number of digits after the decimal point. Fixed for a world once it has been created.
     *
     * @return 0..6
     */
    int decimals();

    /**
     * @return the display name, e.g. "Coins"
     */
    Component name();

    /**
     * @return the symbol, e.g. "₮"
     */
    String symbol();

    /**
     * Formats an amount with thousands separators and the symbol.
     *
     * @param amount amount in minor units
     * @return e.g. "1 250.00 ₮"
     */
    default Component format(long amount) {
        return Component.literal(formatPlain(amount));
    }

    /**
     * Same as {@link #format(long)} as a plain string.
     *
     * @param amount amount in minor units
     * @return e.g. "1 250.00 ₮"
     */
    String formatPlain(long amount);

    /**
     * Parses a human amount in major units ("12", "12.5", "1 250.00").
     *
     * @param text the text
     * @return the amount in minor units, or empty if it isn't a valid non-negative amount with at most
     * {@link #decimals()} decimals that fits in a {@code long}
     */
    OptionalLong parse(String text);

    /**
     * Converts an amount in major units to minor units, dropping digits beyond {@link #decimals()}.
     *
     * @param major amount in major units, e.g. {@code 1.5}
     * @return amount in minor units, e.g. {@code 150}
     * @throws ArithmeticException if it doesn't fit in a {@code long}
     */
    long toMinor(BigDecimal major);

    /**
     * Converts an amount in minor units to major units.
     *
     * @param minor amount in minor units
     * @return amount in major units, with exactly {@link #decimals()} decimals
     */
    BigDecimal toMajor(long minor);
}
