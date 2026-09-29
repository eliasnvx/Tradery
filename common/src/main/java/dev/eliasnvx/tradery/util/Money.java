package dev.eliasnvx.tradery.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;

/**
 * Pure money arithmetic and text: amounts are {@code long} minor units, {@code decimals} digits after the point.
 * No Minecraft classes, so everything here is unit-tested directly.
 */
public final class Money {
    /** Largest supported number of decimals: 10^6 minor units per major unit still leaves ~9.2 * 10^12 major units. */
    public static final int MAX_DECIMALS = 6;
    private static final String[] SHORT_SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private Money() {
    }

    /** @return 10^decimals */
    public static long unit(int decimals) {
        checkDecimals(decimals);
        long unit = 1;
        for (int i = 0; i < decimals; i++) {
            unit *= 10;
        }
        return unit;
    }

    /**
     * "1 250.00" for (125000, 2, " "). Negative amounts get a leading minus.
     *
     * @param amount             minor units
     * @param decimals           digits after the point
     * @param thousandsSeparator separator between groups of three digits, may be empty
     * @return the number without a symbol
     */
    public static String formatNumber(long amount, int decimals, String thousandsSeparator) {
        BigDecimal major = BigDecimal.valueOf(amount).movePointLeft(checkDecimals(decimals));
        String plain = major.abs().setScale(decimals, RoundingMode.UNNECESSARY).toPlainString();
        int point = plain.indexOf('.');
        String integer = point < 0 ? plain : plain.substring(0, point);
        String fraction = point < 0 ? "" : plain.substring(point);
        StringBuilder out = new StringBuilder(plain.length() + 8);
        if (amount < 0) {
            out.append('-');
        }
        int firstGroup = integer.length() % 3 == 0 ? 3 : integer.length() % 3;
        out.append(integer, 0, firstGroup);
        for (int i = firstGroup; i < integer.length(); i += 3) {
            out.append(thousandsSeparator).append(integer, i, i + 3);
        }
        return out.append(fraction).toString();
    }

    /**
     * Full form with the symbol: "1 250.00 ₮".
     */
    public static String format(long amount, int decimals, String thousandsSeparator, String symbol) {
        String number = formatNumber(amount, decimals, thousandsSeparator);
        return symbol.isEmpty() ? number : number + " " + symbol;
    }

    /**
     * Short form for tight spaces: "950", "1.2K", "3.4M", "12B". Amounts under 1000 major units use the full
     * number (with decimals, but trailing zeros dropped: "12.5", "12").
     */
    public static String formatShort(long amount, int decimals, String symbol) {
        BigDecimal major = BigDecimal.valueOf(amount).movePointLeft(checkDecimals(decimals));
        BigDecimal abs = major.abs();
        String number;
        if (abs.compareTo(BigDecimal.valueOf(1000)) < 0) {
            BigDecimal trimmed = abs.stripTrailingZeros();
            number = (trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY) : trimmed).toPlainString();
        } else {
            int tier = 0;
            BigDecimal scaled = abs;
            while (scaled.compareTo(BigDecimal.valueOf(1000)) >= 0 && tier < SHORT_SUFFIXES.length - 1) {
                scaled = scaled.movePointLeft(3);
                tier++;
            }
            // One decimal below 100 ("1.2K", "12.3M"), none above ("123M"); always rounded down so it never overstates
            int scale = scaled.compareTo(BigDecimal.valueOf(100)) < 0 ? 1 : 0;
            BigDecimal rounded = scaled.setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
            if (rounded.scale() < 0) {
                rounded = rounded.setScale(0, RoundingMode.UNNECESSARY);
            }
            number = rounded.toPlainString() + SHORT_SUFFIXES[tier];
        }
        String signed = amount < 0 ? "-" + number : number;
        return symbol.isEmpty() ? signed : signed + " " + symbol;
    }

    /**
     * Parses a non-negative amount in major units: digits, an optional '.' or ',' decimal point, and optional
     * spaces / underscores / apostrophes / non-breaking spaces between digit groups. Rejects signs, exponents,
     * more decimals than allowed, and values that don't fit in a {@code long}.
     *
     * @return minor units, or empty
     */
    public static OptionalLong parse(String text, int decimals) {
        checkDecimals(decimals);
        if (text == null) {
            return OptionalLong.empty();
        }
        StringBuilder clean = new StringBuilder(text.length());
        boolean point = false;
        boolean digits = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                clean.append(c);
                digits = true;
            } else if ((c == '.' || c == ',') && !point) {
                clean.append('.');
                point = true;
            } else if (c == ' ' || c == '_' || c == '\'' || c == ' ' || c == ' ') {
                // separators only between two digits of the integer part
                boolean digitBefore = i > 0 && Character.isDigit(text.charAt(i - 1));
                boolean digitAfter = i + 1 < text.length() && Character.isDigit(text.charAt(i + 1));
                if (point || !digitBefore || !digitAfter) {
                    return OptionalLong.empty();
                }
            } else {
                return OptionalLong.empty();
            }
        }
        if (!digits || clean.charAt(clean.length() - 1) == '.' || clean.charAt(0) == '.') {
            return OptionalLong.empty();
        }
        BigDecimal value = new BigDecimal(clean.toString());
        if (value.stripTrailingZeros().scale() > decimals) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(value.movePointRight(decimals).setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Major to minor units, dropping digits beyond {@code decimals} (towards zero).
     *
     * @throws ArithmeticException if the result doesn't fit in a {@code long}
     */
    public static long toMinor(BigDecimal major, int decimals) {
        return major.movePointRight(checkDecimals(decimals)).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** Whether {@code major} has more decimals than the currency can hold (the extra digits would be dropped). */
    public static boolean losesPrecision(BigDecimal major, int decimals) {
        return major.stripTrailingZeros().scale() > decimals;
    }

    /** Minor to major units with exactly {@code decimals} decimals. */
    public static BigDecimal toMajor(long minor, int decimals) {
        return BigDecimal.valueOf(minor).movePointLeft(checkDecimals(decimals)).setScale(decimals, RoundingMode.UNNECESSARY);
    }

    /**
     * {@code amount * percent / 100}, rounded down, for fees and taxes. {@code percent} may have decimals (2.5).
     *
     * @throws ArithmeticException on overflow
     */
    public static long percentOf(long amount, BigDecimal percent) {
        if (percent.signum() <= 0 || amount <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).multiply(percent).movePointLeft(2).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    private static int checkDecimals(int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("decimals must be 0.." + MAX_DECIMALS + ": " + decimals);
        }
        return decimals;
    }
}
