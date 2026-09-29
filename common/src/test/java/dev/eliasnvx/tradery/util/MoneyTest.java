package dev.eliasnvx.tradery.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {
    @Test
    void formatsWithGroupsAndDecimals() {
        assertEquals("1 250.00 ₮", Money.format(125_000, 2, " ", "₮"));
        assertEquals("0.05 ₮", Money.format(5, 2, " ", "₮"));
        assertEquals("-12.30", Money.format(-1_230, 2, " ", ""));
        assertEquals("1,000,000", Money.format(1_000_000, 0, ",", ""));
        assertEquals("999", Money.formatNumber(999, 0, " "));
        assertEquals("92 233 720 368 547 758.07", Money.formatNumber(Long.MAX_VALUE, 2, " "));
        assertEquals("-92 233 720 368 547 758.08", Money.formatNumber(Long.MIN_VALUE, 2, " "));
    }

    @Test
    void formatsShort() {
        assertEquals("12.5 ₮", Money.formatShort(1_250, 2, "₮"));
        assertEquals("999", Money.formatShort(99_900, 2, ""));
        assertEquals("1.2K", Money.formatShort(125_000, 2, ""));
        assertEquals("1K", Money.formatShort(100_000, 2, ""));
        assertEquals("999K", Money.formatShort(99_999_999, 2, ""));
        assertEquals("123M", Money.formatShort(123_456_789, 0, ""));
        assertEquals("1.9B", Money.formatShort(1_999_999_999, 0, ""));
        assertEquals("-3.4M", Money.formatShort(-3_400_000, 0, ""));
    }

    @Test
    void parsesHumanAmounts() {
        assertEquals(OptionalLong.of(1_250), Money.parse("12.5", 2));
        assertEquals(OptionalLong.of(1_250), Money.parse("12,50", 2));
        assertEquals(OptionalLong.of(125_000), Money.parse("1 250.00", 2));
        assertEquals(OptionalLong.of(1_000_000), Money.parse("1_000_000", 0));
        assertEquals(OptionalLong.of(0), Money.parse("0", 2));
    }

    @Test
    void rejectsBadAmounts() {
        for (String bad : new String[]{"", "-1", "+1", "1e3", "1.", ".5", "1.2.3", "abc", "1.001", " 1", "1 .5", "NaN"}) {
            assertFalse(Money.parse(bad, 2).isPresent(), bad);
        }
        assertFalse(Money.parse("92233720368547758.08", 2).isPresent(), "overflow");
        assertTrue(Money.parse("92233720368547758.07", 2).isPresent());
    }

    @Test
    void convertsBetweenUnits() {
        assertEquals(150, Money.toMinor(new BigDecimal("1.5"), 2));
        assertEquals(10, Money.toMinor(new BigDecimal("0.109"), 2));
        assertTrue(Money.losesPrecision(new BigDecimal("0.109"), 2));
        assertFalse(Money.losesPrecision(new BigDecimal("0.100"), 2));
        assertEquals(new BigDecimal("1.50"), Money.toMajor(150, 2));
        assertThrows(ArithmeticException.class, () -> Money.toMinor(new BigDecimal("1e30"), 2));
        assertThrows(IllegalArgumentException.class, () -> Money.unit(7));
    }

    @Test
    void percentRoundsDown() {
        assertEquals(2, Money.percentOf(100, BigDecimal.valueOf(2)));
        assertEquals(0, Money.percentOf(49, BigDecimal.valueOf(2)));
        assertEquals(2, Money.percentOf(100, new BigDecimal("2.5")));
        assertEquals(0, Money.percentOf(100, BigDecimal.ZERO));
    }
}
