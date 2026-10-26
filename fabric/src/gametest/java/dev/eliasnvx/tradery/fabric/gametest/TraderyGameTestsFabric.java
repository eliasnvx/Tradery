package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.gametest.EconomyGameTests;
import dev.eliasnvx.tradery.gametest.MarketGameTests;
import dev.eliasnvx.tradery.gametest.SourcesGameTests;
import dev.eliasnvx.tradery.gametest.RobustnessGameTests;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import dev.eliasnvx.tradery.gametest.ShopLedgerGameTests;
import dev.eliasnvx.tradery.gametest.MarketCounterGameTests;
import dev.eliasnvx.tradery.gametest.VillageCommerceGameTests;
import dev.eliasnvx.tradery.gametest.SupplierGameTests;
import dev.eliasnvx.tradery.gametest.VillageBoardGameTests;
import dev.eliasnvx.tradery.gametest.VillageBuildingGameTests;
import dev.eliasnvx.tradery.gametest.VendingGameTests;
import dev.eliasnvx.tradery.gametest.VillageGameTests;
import dev.eliasnvx.tradery.gametest.VillagerGameTests;
import dev.eliasnvx.tradery.gametest.VillagerTradeGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric glue for the shared {@link TraderyGameTests}: one {@code @GameTest} method per entry.
 * {@link #allTestsRegistered} fails when a shared test is missing here.
 */
public final class TraderyGameTestsFabric {
    /** Methods below that forward to a shared test (everything except {@link #allTestsRegistered}). */
    private static final int WIRED = 112;

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
    public void vendingQuickTradeBuysOneLot(GameTestHelper helper) {
        VendingGameTests.quickTradeBuysOneLot(helper);
    }

    @GameTest
    public void vendingQuickTradeWrongButton(GameTestHelper helper) {
        VendingGameTests.quickTradeWrongButton(helper);
    }

    @GameTest
    public void vendingQuickTradeSellsToBuyback(GameTestHelper helper) {
        VendingGameTests.quickTradeSellsToBuyback(helper);
    }

    @GameTest
    public void vendingQuickTradeNeedsReach(GameTestHelper helper) {
        VendingGameTests.quickTradeNeedsReach(helper);
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
    public void coinOreInOverworldBiomes(GameTestHelper helper) {
        SourcesGameTests.coinOreInOverworldBiomes(helper);
    }

    @GameTest
    public void coinOreNotProcessable(GameTestHelper helper) {
        SourcesGameTests.coinOreNotProcessable(helper);
    }

    @GameTest
    public void vendingTradesSavedWithChunk(GameTestHelper helper) {
        VendingGameTests.tradesSavedWithChunk(helper);
    }

    /** A villager walks a few blocks and browses ({@code villagers.browseTicks}) before it buys. */
    @GameTest(maxTicks = 200)
    public void villagerWalksUpAndBuys(GameTestHelper helper) {
        VillagerGameTests.walksUpAndBuys(helper);
    }

    @GameTest
    public void villagerWalletLimitsLots(GameTestHelper helper) {
        VillagerGameTests.walletLimitsLots(helper);
    }

    @GameTest
    public void villagerPriceAndReputation(GameTestHelper helper) {
        VillagerGameTests.priceAndReputation(helper);
    }

    @GameTest
    public void villagerIgnoresOtherVendors(GameTestHelper helper) {
        VillagerGameTests.ignoresOtherVendors(helper);
    }

    @GameTest
    public void villagerDemandDropsAndRecovers(GameTestHelper helper) {
        VillagerGameTests.demandDropsAndRecovers(helper);
    }

    @GameTest
    public void villagerDailyCapPerVillage(GameTestHelper helper) {
        VillagerGameTests.dailyCapPerVillage(helper);
    }

    @GameTest
    public void villagerCooldown(GameTestHelper helper) {
        VillagerGameTests.cooldown(helper);
    }

    @GameTest
    public void villagerToggleSavedAndSynced(GameTestHelper helper) {
        VillagerGameTests.toggleSavedAndSynced(helper);
    }

    @GameTest
    public void marketStallsStockOffers(GameTestHelper helper) {
        MarketGameTests.stallsStockOffers(helper);
    }

    @GameTest
    public void marketChangersUseConfigPrices(GameTestHelper helper) {
        MarketGameTests.changersUseConfigPrices(helper);
    }

    @GameTest
    public void marketPoolsGetStalls(GameTestHelper helper) {
        MarketGameTests.poolsGetStalls(helper);
    }

    @GameTest
    public void villagerTradeBuyWithMoney(GameTestHelper helper) {
        VillagerTradeGameTests.buyWithMoney(helper);
    }

    @GameTest
    public void villagerTradeSellPaidFromWallet(GameTestHelper helper) {
        VillagerTradeGameTests.sellPaidFromWallet(helper);
    }

    @GameTest
    public void villagerTradeFailuresChangeNothing(GameTestHelper helper) {
        VillagerTradeGameTests.failuresChangeNothing(helper);
    }

    @GameTest
    public void villagerTradeAllStopsAtLimits(GameTestHelper helper) {
        VillagerTradeGameTests.tradeAllStopsAtLimits(helper);
    }

    @GameTest
    public void villagerTradeWindowReplacesVanilla(GameTestHelper helper) {
        VillagerTradeGameTests.windowReplacesVanilla(helper);
    }

    @GameTest
    public void villageCensusCountsVillagers(GameTestHelper helper) {
        VillageGameTests.censusCountsVillagers(helper);
    }

    @GameTest
    public void villageLevelsAndDecay(GameTestHelper helper) {
        VillageGameTests.levelsAndDecay(helper);
    }

    @GameTest
    public void villageTreasuryIncomeAndCap(GameTestHelper helper) {
        VillageGameTests.treasuryIncomeAndCap(helper);
    }

    @GameTest
    public void villageTradeTaxAndProsperity(GameTestHelper helper) {
        VillageGameTests.tradeTaxAndProsperity(helper);
    }

    @GameTest
    public void villageLevelRaisesWages(GameTestHelper helper) {
        VillageGameTests.levelRaisesWages(helper);
    }

    @GameTest
    public void villageFoundByBell(GameTestHelper helper) {
        VillageGameTests.foundByBell(helper);
    }

    @GameTest
    public void boardOrdersPostedFromTreasury(GameTestHelper helper) {
        VillageBoardGameTests.ordersPostedFromTreasury(helper);
    }

    @GameTest
    public void boardDeliveryPartialThenFull(GameTestHelper helper) {
        VillageBoardGameTests.deliveryPartialThenFull(helper);
    }

    @GameTest
    public void boardDeliveryFailsCleanly(GameTestHelper helper) {
        VillageBoardGameTests.deliveryFailsCleanly(helper);
    }

    @GameTest
    public void boardOrdersExpireRefunded(GameTestHelper helper) {
        VillageBoardGameTests.ordersExpireRefunded(helper);
    }

    @GameTest
    public void boardRestorationOrders(GameTestHelper helper) {
        VillageBoardGameTests.restorationOrders(helper);
    }

    @GameTest
    public void boardProjectFunding(GameTestHelper helper) {
        VillageBoardGameTests.projectFunding(helper);
    }

    @GameTest
    public void boardProjectIronGolem(GameTestHelper helper) {
        VillageBoardGameTests.projectIronGolem(helper);
    }

    @GameTest
    public void boardProjectMarketStall(GameTestHelper helper) {
        VillageBoardGameTests.projectMarketStall(helper);
    }

    @GameTest
    public void boardMenuValidatesClicks(GameTestHelper helper) {
        VillageBoardGameTests.menuValidatesClicks(helper);
    }

    @GameTest
    public void boardDataBounded(GameTestHelper helper) {
        VillageBoardGameTests.dataBounded(helper);
    }

    @GameTest
    public void boardOnMarketStalls(GameTestHelper helper) {
        VillageBoardGameTests.boardOnMarketStalls(helper);
    }

    @GameTest
    public void boardBellInfo(GameTestHelper helper) {
        VillageBoardGameTests.bellInfo(helper);
    }

    @GameTest(maxTicks = 200)
    public void supplierWalksUpAndSells(GameTestHelper helper) {
        SupplierGameTests.walksUpAndSells(helper);
    }

    @GameTest
    public void supplierSaleAtomic(GameTestHelper helper) {
        SupplierGameTests.saleAtomic(helper);
    }

    @GameTest
    public void supplierPriceThreshold(GameTestHelper helper) {
        SupplierGameTests.priceThreshold(helper);
    }

    @GameTest
    public void supplierStockAndMoneyLimits(GameTestHelper helper) {
        SupplierGameTests.stockAndMoneyLimits(helper);
    }

    @GameTest
    public void supplierRoomLimit(GameTestHelper helper) {
        SupplierGameTests.roomLimit(helper);
    }

    @GameTest
    public void supplierNotServed(GameTestHelper helper) {
        SupplierGameTests.notServed(helper);
    }

    @GameTest
    public void supplierProductionPilesUp(GameTestHelper helper) {
        SupplierGameTests.productionPilesUp(helper);
    }

    @GameTest
    public void supplierDefaultSupplies(GameTestHelper helper) {
        SupplierGameTests.defaultSupplies(helper);
    }

    @GameTest
    public void commerceRegionalPrices(GameTestHelper helper) {
        VillageCommerceGameTests.regionalPrices(helper);
    }

    @GameTest
    public void commerceMarketDays(GameTestHelper helper) {
        VillageCommerceGameTests.marketDays(helper);
    }

    @GameTest
    public void commerceRentLicence(GameTestHelper helper) {
        VillageCommerceGameTests.rentLicence(helper);
    }

    @GameTest
    public void commerceCaravanSentAndArrives(GameTestHelper helper) {
        VillageCommerceGameTests.caravanSentAndArrives(helper);
    }

    @GameTest
    public void commerceCaravanTradesInMoney(GameTestHelper helper) {
        VillageCommerceGameTests.caravanTradesInMoney(helper);
    }

    @GameTest
    public void commerceCaravanFailuresChangeNothing(GameTestHelper helper) {
        VillageCommerceGameTests.caravanFailuresChangeNothing(helper);
    }

    @GameTest
    public void commerceCaravanLeavesPurseHome(GameTestHelper helper) {
        VillageCommerceGameTests.caravanLeavesPurseHome(helper);
    }

    @GameTest
    public void commerceRaidOutcomes(GameTestHelper helper) {
        VillageCommerceGameTests.raidOutcomes(helper);
    }

    @GameTest
    public void counterSaleMovesGoodsAndMoney(GameTestHelper helper) {
        MarketCounterGameTests.saleMovesGoodsAndMoney(helper);
    }

    @GameTest
    public void counterFailuresChangeNothing(GameTestHelper helper) {
        MarketCounterGameTests.failuresChangeNothing(helper);
    }

    @GameTest
    public void counterLastItemGoesOnce(GameTestHelper helper) {
        MarketCounterGameTests.lastItemGoesOnce(helper);
    }

    @GameTest
    public void counterOwnerOnlySetup(GameTestHelper helper) {
        MarketCounterGameTests.ownerOnlySetup(helper);
    }

    @GameTest
    public void counterSaveValidates(GameTestHelper helper) {
        MarketCounterGameTests.saveValidates(helper);
    }

    @GameTest
    public void counterMenuRules(GameTestHelper helper) {
        MarketCounterGameTests.menuRules(helper);
    }

    @GameTest
    public void counterBreakDropsOnce(GameTestHelper helper) {
        MarketCounterGameTests.breakDropsOnce(helper);
    }

    @GameTest
    public void counterProtection(GameTestHelper helper) {
        MarketCounterGameTests.protection(helper);
    }

    @GameTest
    public void counterVillagerHooks(GameTestHelper helper) {
        MarketCounterGameTests.villagerHooks(helper);
    }

    @GameTest
    public void counterTradesSavedWithChunk(GameTestHelper helper) {
        MarketCounterGameTests.tradesSavedWithChunk(helper);
    }

    @GameTest
    public void counterInVendingIndex(GameTestHelper helper) {
        MarketCounterGameTests.inVendingIndex(helper);
    }

    @GameTest
    public void ledgerListsOwnShopsOnly(GameTestHelper helper) {
        ShopLedgerGameTests.listsOwnShopsOnly(helper);
    }

    @GameTest
    public void ledgerAlertsAndOrder(GameTestHelper helper) {
        ShopLedgerGameTests.alertsAndOrder(helper);
    }

    @GameTest
    public void ledgerSnapshotForUnloaded(GameTestHelper helper) {
        ShopLedgerGameTests.snapshotForUnloaded(helper);
    }

    @GameTest
    public void ledgerSnapshotSavedWithIndex(GameTestHelper helper) {
        ShopLedgerGameTests.snapshotSavedWithIndex(helper);
    }

    @GameTest
    public void ledgerMenuActions(GameTestHelper helper) {
        ShopLedgerGameTests.menuActions(helper);
    }

    @GameTest
    public void ledgerPayloadBounds(GameTestHelper helper) {
        ShopLedgerGameTests.payloadBounds(helper);
    }

    @GameTest
    public void counterVillagerBuysWantedOffer(GameTestHelper helper) {
        MarketCounterGameTests.villagerBuysWantedOffer(helper);
    }

    @GameTest
    public void robustSampleSizeCapped(GameTestHelper helper) {
        RobustnessGameTests.sampleSizeCapped(helper);
    }

    @GameTest
    public void robustSaveRateLimited(GameTestHelper helper) {
        RobustnessGameTests.saveRateLimited(helper);
    }

    @GameTest
    public void robustVillagesWithMoneySurvivePruning(GameTestHelper helper) {
        RobustnessGameTests.villagesWithMoneySurvivePruning(helper);
    }

    @GameTest
    public void robustVillagerWalletCoversTax(GameTestHelper helper) {
        RobustnessGameTests.villagerWalletCoversTax(helper);
    }

    @GameTest
    public void boardDonation(GameTestHelper helper) {
        VillageBoardGameTests.donation(helper);
    }

    @GameTest
    public void boardTreasuryHistory(GameTestHelper helper) {
        VillageBoardGameTests.treasuryHistory(helper);
    }

    @GameTest
    public void boardProjectStreetLamps(GameTestHelper helper) {
        VillageBoardGameTests.projectStreetLamps(helper);
    }

    @GameTest
    public void boardProjectGranary(GameTestHelper helper) {
        VillageBoardGameTests.projectGranary(helper);
    }

    @GameTest
    public void boardProjectPavedStreets(GameTestHelper helper) {
        VillageBoardGameTests.projectPavedStreets(helper);
    }

    @GameTest
    public void streetsConnectBuilding(GameTestHelper helper) {
        VillageBoardGameTests.streetsConnectBuilding(helper);
    }

    @GameTest
    public void buildingsSavingsFromIncome(GameTestHelper helper) {
        VillageBuildingGameTests.savingsFromIncome(helper);
    }

    @GameTest
    public void buildingsBuiltFromSavings(GameTestHelper helper) {
        VillageBuildingGameTests.builtFromSavings(helper);
    }

    @GameTest
    public void festivalOnMarketDay(GameTestHelper helper) {
        VillageBuildingGameTests.festivalOnMarketDay(helper);
    }

    @GameTest
    public void allTestsRegistered(GameTestHelper helper) {
        helper.assertValueEqual(TraderyGameTests.all().size(), WIRED, "shared tests wired into the Fabric glue");
        helper.succeed();
    }
}
