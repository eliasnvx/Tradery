package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.gametest.EconomyGameTests;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric glue for the shared {@link TraderyGameTests}: one {@code @GameTest} method per entry.
 * {@link #allTestsRegistered} fails when a shared test is missing here.
 */
public final class TraderyGameTestsFabric {
    /** Methods below that forward to a shared test (everything except {@link #allTestsRegistered}). */
    private static final int WIRED = 4;

    @GameTest
    public void economyTransferIsAtomic(GameTestHelper helper) {
        EconomyGameTests.transferIsAtomic(helper);
    }

    @GameTest
    public void economyPreEventTaxesAndCancels(GameTestHelper helper) {
        EconomyGameTests.preEventTaxesAndCancels(helper);
    }

    @GameTest
    public void economyRequiresServerThread(GameTestHelper helper) {
        EconomyGameTests.requiresServerThread(helper);
    }

    @GameTest
    public void economyPayCommand(GameTestHelper helper) {
        EconomyGameTests.payCommand(helper);
    }

    @GameTest
    public void allTestsRegistered(GameTestHelper helper) {
        helper.assertValueEqual(TraderyGameTests.all().size(), WIRED, "shared tests wired into the Fabric glue");
        helper.succeed();
    }
}
