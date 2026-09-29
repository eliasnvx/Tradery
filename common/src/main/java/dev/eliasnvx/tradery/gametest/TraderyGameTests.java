package dev.eliasnvx.tradery.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loader-independent GameTest bodies. The Fabric glue ({@code fabric/src/gametest}) and
 * {@code TraderyGameTestsNeoForge} register {@link #all()}; a count check in the Fabric glue catches a test that
 * was added here but not wired there.
 */
public final class TraderyGameTests {
    /** Test name (registry path) and body. */
    public record Entry(String name, Consumer<GameTestHelper> body) {
    }

    private TraderyGameTests() {
    }

    public static List<Entry> all() {
        List<Entry> all = new ArrayList<>(EconomyGameTests.ALL);
        all.addAll(VendingGameTests.ALL);
        return List.copyOf(all);
    }
}
