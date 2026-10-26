package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerTrading;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Village economy core (SPEC "Деревня-экономика"): census, levels and prosperity decay, treasury income and cap,
 * the trade tax, wages growing with the level, finding a village by its bell. Every test has its own bell at its own
 * origin, so villages never mix.
 */
public final class VillageGameTests {
    private static final BlockPos BELL = new BlockPos(0, 1, 0);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("village_census_counts_villagers", VillageGameTests::censusCountsVillagers),
        new TraderyGameTests.Entry("village_levels_and_decay", VillageGameTests::levelsAndDecay),
        new TraderyGameTests.Entry("village_treasury_income_and_cap", VillageGameTests::treasuryIncomeAndCap),
        new TraderyGameTests.Entry("village_trade_tax_and_prosperity", VillageGameTests::tradeTaxAndProsperity),
        new TraderyGameTests.Entry("village_level_raises_wages", VillageGameTests::levelRaisesWages),
        new TraderyGameTests.Entry("village_found_by_bell", VillageGameTests::foundByBell));

    private VillageGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static GlobalPos bell(GameTestHelper helper) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BELL));
    }

    /** An adult villager of this test's village, with a profession or without. */
    private static Villager villager(GameTestHelper helper, BlockPos at, boolean employed) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at);
        if (employed) {
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER)
                .setLevel(2));
        }
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell(helper));
        return villager;
    }

    private static VillageState village(GameTestHelper helper) {
        GlobalPos bell = bell(helper);
        return Villages.at(server(helper), bell.dimension(), bell.pos());
    }

    private static long minor(double major) {
        return Math.round(major * Math.pow(10, EconomyService.INSTANCE.defaultCurrency().decimals()));
    }

    private static VillagesConfig config() {
        return TraderyConfig.server().villages();
    }

    // ------------------------------------------------------------------ tests

    /** The census counts the adults of the bell, who works, which professions and the village type. */
    public static void censusCountsVillagers(GameTestHelper helper) {
        Villager a = villager(helper, new BlockPos(1, 1, 1), true);
        Villager b = villager(helper, new BlockPos(2, 1, 1), true);
        Villager c = villager(helper, new BlockPos(3, 1, 1), false);
        Villages.census(helper.getLevel());
        VillageState.Census census = village(helper).census();
        GameTestAsserts.assertValueEqual(helper, census.population(), 3, "population");
        GameTestAsserts.assertValueEqual(helper, census.employed(), 2, "employed");
        GameTestAsserts.assertValueEqual(helper, census.professions().get("minecraft:farmer"), 2, "farmers");
        GameTestAsserts.assertValueEqual(helper, census.type(), "minecraft:plains", "type of most villagers");
        a.discard();
        b.discard();
        c.discard();
        helper.succeed();
    }

    /** Prosperity sets the level; a day takes its share away. */
    public static void levelsAndDecay(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper);
        List<Double> thresholds = config().levels().thresholds();
        Villages.set(server, village, 0, 0);
        GameTestAsserts.assertValueEqual(helper, Villages.level(village), 1, "starts as a hamlet");
        Villages.set(server, village, thresholds.get(1), 0);
        GameTestAsserts.assertValueEqual(helper, Villages.level(village), 2, "second threshold: level 2");
        Villages.set(server, village, thresholds.get(thresholds.size() - 1) + 1, 0);
        GameTestAsserts.assertValueEqual(helper, Villages.level(village), thresholds.size(), "top level");
        GameTestAsserts.assertValueEqual(helper, Villages.nextThreshold(village), -1.0, "nothing above the top");

        Villages.set(server, village, 1000, 0);
        Villages.backdate(server, village, 2);
        Villages.touch(server, village);
        double expected = 1000 * Math.pow(1 - config().prosperityDecayPerDay(), 2);
        helper.assertTrue(Math.abs(village.prosperity() - expected) < 1e-6, "two days of decay: " + village.prosperity() + " vs " + expected);
        helper.succeed();
    }

    /** The daily income is per villager; it fills the treasury up to its cap, not beyond. */
    public static void treasuryIncomeAndCap(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        Villager a = villager(helper, new BlockPos(1, 1, 1), true);
        Villager b = villager(helper, new BlockPos(2, 1, 1), true);
        Villager c = villager(helper, new BlockPos(3, 1, 1), false);
        Villages.census(helper.getLevel());
        VillageState village = village(helper);
        Villages.set(server, village, 0, 0);
        long daily = Villages.dailyIncome(village);
        GameTestAsserts.assertValueEqual(helper, daily, minor(3 * config().treasuryPerVillager().doubleValue()), "income: three villagers at level 1");

        Villages.backdate(server, village, 2);
        Villages.touch(server, village);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), daily * 2, "two days of income");

        Villages.backdate(server, village, 1000);
        Villages.touch(server, village);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), Villages.treasuryCap(village), "capped");
        long cap = village.treasury();
        Villages.addToTreasury(server, village, 500);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), cap + 500, "rent and taxes go above the cap");
        helper.assertFalse(Villages.takeFromTreasury(server, village, cap + 501), "can't take more than there is");
        helper.assertTrue(Villages.takeFromTreasury(server, village, 500), "takes what's there");
        a.discard();
        b.discard();
        c.discard();
        helper.succeed();
    }

    /** Buying in a village villager's money window: the tax goes to the treasury, the rest to the wallet; prosperity grows. */
    public static void tradeTaxAndProsperity(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        Villager villager = villager(helper, new BlockPos(2, 1, 2), true);
        MerchantOffer offer = new MerchantOffer(new ItemStack(Items.EMERALD, 10), ItemStack.EMPTY, new ItemStack(Items.BOOKSHELF), 0, 12, 1, 0.05f);
        villager.getOffers().clear();
        villager.getOffers().add(offer);
        @SuppressWarnings("removal")
        ServerPlayer player = MockPlayers.create(helper);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 2)));
        player.setPos(at.x, at.y, at.z);
        villager.setTradingPlayer(player);
        long price = 10 * VillagerTrading.emeraldValue();
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), price, Reason.of(Reasons.ADMIN_SET, "test"));
        long today = VillagerPurchases.today(server);
        VillagerLedger.get(server).setWallet(villager.getUUID(), 0, today);
        VillageState village = village(helper);
        Villages.set(server, village, 0, 0);

        helper.assertTrue(VillagerTrading.trade(player, villager, 0, false).success(), "bought");
        long tax = Money.percentOf(price, config().tradeTaxPercent());
        helper.assertTrue(tax > 0, "there is a tax");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), tax, "tax in the treasury");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.wallet(villager, today), price - tax, "the rest in the wallet");
        helper.assertTrue(village.prosperity() > 0, "prosperity grew");
        villager.discard();
        helper.succeed();
    }

    /** A higher village level means higher wages, so a fresh wallet holds more. */
    public static void levelRaisesWages(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        Villager villager = villager(helper, new BlockPos(2, 1, 2), true);
        VillageState village = village(helper);
        long today = VillagerPurchases.today(server);
        Villages.set(server, village, 0, 0);
        VillagerLedger.get(server).forget(villager.getUUID());
        long hamlet = VillagerPurchases.wallet(villager, today);

        List<Double> thresholds = config().levels().thresholds();
        Villages.set(server, village, thresholds.get(thresholds.size() - 1), 0);
        VillagerLedger.get(server).forget(villager.getUUID());
        long city = VillagerPurchases.wallet(villager, today);
        double multiplier = config().levels().wageMultiplier().get(config().levels().wageMultiplier().size() - 1) / config().levels().wageMultiplier().get(0);
        helper.assertTrue(hamlet > 0, "a wage at all");
        helper.assertTrue(Math.abs(city - hamlet * multiplier) <= 1, "wage x" + multiplier + ": " + hamlet + " -> " + city);
        villager.discard();
        helper.succeed();
    }

    /** A block near a bell belongs to its village; far from any bell, to none. */
    public static void foundByBell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BELL, Blocks.BELL);
        VillageState near = Villages.near(level, helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertTrue(near != null, "a village next to the bell");
        GameTestAsserts.assertValueEqual(helper, near.bell(), helper.absolutePos(BELL), "that bell's");
        GameTestAsserts.assertValueEqual(helper, near.key(), Villages.key(level.dimension(), helper.absolutePos(BELL)), "keyed like villagers' villages");
        helper.setBlock(BELL, Blocks.AIR);
        helper.succeed();
    }
}
