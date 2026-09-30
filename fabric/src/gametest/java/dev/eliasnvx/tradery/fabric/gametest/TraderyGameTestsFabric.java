package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.gametest.EconomyGameTests;
import dev.eliasnvx.tradery.gametest.SourcesGameTests;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import dev.eliasnvx.tradery.gametest.VendingGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric glue for the shared {@link TraderyGameTests}: one {@code @GameTest} method per entry.
 * {@link #allTestsRegistered} fails when a shared test is missing here.
 */
public final class TraderyGameTestsFabric {
    /** Methods below that forward to a shared test (everything except {@link #allTestsRegistered}). */
    private static final int WIRED = 24;

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
    public void vendingSaleMovesGoodsAndMoney(GameTestHelper helper) {
        VendingGameTests.saleMovesGoodsAndMoney(helper);
    }

    @GameTest
    public void vendingLastItemGoesOnce(GameTestHelper helper) {
        VendingGameTests.lastItemGoesOnce(helper);
    }

    @GameTest
    public void vendingNoRoomTakesNothing(GameTestHelper helper) {
        VendingGameTests.noRoomTakesNothing(helper);
    }

    @GameTest
    public void vendingItemPriceAndFullRevenue(GameTestHelper helper) {
        VendingGameTests.itemPriceAndFullRevenue(helper);
    }

    @GameTest
    public void vendingBuyback(GameTestHelper helper) {
        VendingGameTests.buyback(helper);
    }

    @GameTest
    public void vendingMenuRules(GameTestHelper helper) {
        VendingGameTests.menuRules(helper);
    }

    @GameTest
    public void vendingBreakClosesMenusAndDropsOnce(GameTestHelper helper) {
        VendingGameTests.breakClosesMenusAndDropsOnce(helper);
    }

    @GameTest
    public void vendingProtection(GameTestHelper helper) {
        VendingGameTests.protection(helper);
    }

    @GameTest
    public void vendingRateLimit(GameTestHelper helper) {
        VendingGameTests.rateLimit(helper);
    }

    @GameTest
    public void vendingOfflineOwnerGetsSummary(GameTestHelper helper) {
        VendingGameTests.offlineOwnerGetsSummary(helper);
    }

    @GameTest
    public void vendingNewPriceClosesBuyerScreens(GameTestHelper helper) {
        VendingGameTests.newPriceClosesBuyerScreens(helper);
    }

    @GameTest
    public void oreFeaturePlacesOre(GameTestHelper helper) {
        SourcesGameTests.oreFeaturePlacesOre(helper);
    }

    @GameTest
    public void orePaysTheMiner(GameTestHelper helper) {
        SourcesGameTests.orePaysTheMiner(helper);
    }

    @GameTest
    public void oreDropsCoinsWithoutAPlayer(GameTestHelper helper) {
        SourcesGameTests.oreDropsCoinsWithoutAPlayer(helper);
    }

    @GameTest
    public void oreDailyCap(GameTestHelper helper) {
        SourcesGameTests.oreDailyCap(helper);
    }

    @GameTest
    public void coinPickupWithFullInventory(GameTestHelper helper) {
        SourcesGameTests.coinPickupWithFullInventory(helper);
    }

    @GameTest
    public void coinsWithdrawAndUse(GameTestHelper helper) {
        SourcesGameTests.coinsWithdrawAndUse(helper);
    }

    @GameTest
    public void rewardKillSkipsSpawnerMobs(GameTestHelper helper) {
        SourcesGameTests.rewardKillSkipsSpawnerMobs(helper);
    }

    @GameTest
    public void rewardMineSkipsPlacedBlocks(GameTestHelper helper) {
        SourcesGameTests.rewardMineSkipsPlacedBlocks(helper);
    }

    @GameTest
    public void mixinsApply(GameTestHelper helper) {
        SourcesGameTests.mixinsApply(helper);
    }

    @GameTest
    public void allTestsRegistered(GameTestHelper helper) {
        helper.assertValueEqual(TraderyGameTests.all().size(), WIRED, "shared tests wired into the Fabric glue");
        helper.succeed();
    }
}
