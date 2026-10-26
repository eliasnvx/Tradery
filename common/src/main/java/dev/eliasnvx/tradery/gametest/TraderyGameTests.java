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
        all.addAll(SourcesGameTests.ALL);
        all.addAll(VillagerGameTests.ALL);
        all.addAll(MarketGameTests.ALL);
        all.addAll(VillagerTradeGameTests.ALL);
        all.addAll(VillageGameTests.ALL);
        all.addAll(RobustnessGameTests.ALL);
        all.addAll(SupplierGameTests.ALL);
        all.addAll(VillageCommerceGameTests.ALL);
        all.addAll(VillageBoardGameTests.ALL);
        all.addAll(MarketCounterGameTests.ALL);
        all.addAll(ShopLedgerGameTests.ALL);
        all.addAll(VillageBuildingGameTests.ALL);
        return List.copyOf(all);
    }
}
