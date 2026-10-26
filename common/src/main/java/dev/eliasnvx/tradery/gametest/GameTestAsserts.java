package dev.eliasnvx.tradery.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

/** What 1.20.1's {@code GameTestHelper} lacks: {@code assertValueEqual} and the test's bounds. */
public final class GameTestAsserts {
    /** Half the size of the area a Tradery test uses around its origin. */
    private static final int TEST_RADIUS = 16;

    private GameTestAsserts() {
    }

    /**
     * {@code GameTestHelper#assertValueEqual} of later versions, with the same message. Numbers compare by value whatever
     * their boxed type ({@code 5L} and {@code 5} are equal).
     */
    public static void assertValueEqual(GameTestHelper helper, Object actual, Object expected, String name) {
        boolean equal;
        if (actual instanceof Number a && expected instanceof Number b) {
            equal = isIntegral(a) && isIntegral(b) ? a.longValue() == b.longValue() : Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        } else {
            equal = Objects.equals(actual, expected);
        }
        helper.assertTrue(equal, "Expected " + name + " to be " + expected + ", but was " + actual);
    }

    /** The space around the test (later versions' {@code GameTestHelper#getBounds}, private here). */
    public static AABB bounds(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(TEST_RADIUS);
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte;
    }
}
