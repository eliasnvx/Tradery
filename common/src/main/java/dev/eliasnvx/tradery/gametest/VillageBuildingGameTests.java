package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.village.VillageBuildings;
import dev.eliasnvx.tradery.village.VillageFestivals;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Villages growing on their own (SPEC "Деревня строит сама", "Праздник"): the building fund fed from the daily
 * income while a level building is pending, the building placed from the fund once a day with the village's name
 * on its sign, and festivals on market days of towns. Every test has its own bell inside its own space.
 */
public final class VillageBuildingGameTests {
    private static final BlockPos BELL = new BlockPos(2, 1, 4);
    private static final ResourceLocation WELL = new ResourceLocation("tradery", "well");

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("buildings_savings_from_income", VillageBuildingGameTests::savingsFromIncome),
        new TraderyGameTests.Entry("buildings_built_from_savings", VillageBuildingGameTests::builtFromSavings),
        new TraderyGameTests.Entry("festival_on_market_day", VillageBuildingGameTests::festivalOnMarketDay));

    private VillageBuildingGameTests() {
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static VillageState village(GameTestHelper helper, BlockPos bell) {
        return Villages.at(server(helper), helper.getLevel().dimension(), helper.absolutePos(bell));
    }

    private static void floor(GameTestHelper helper) {
        // Three solid blocks deep: buildings and lamp posts want real ground, not a floor over the void
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = -2; y <= 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static void openRoof(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 2; y < 32; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.BARRIER)) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }
    }

    private static Villager farmer(GameTestHelper helper, BlockPos at, BlockPos bell) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER)
            .setLevel(2));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bell)));
        return villager;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = MockPlayers.create(helper);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static VillagesConfig config() {
        return TraderyConfig.server().villages();
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(server(helper));
    }

    private static double level(int level) {
        return config().levels().thresholds().get(level - 1);
    }

    /** A Village puts a share of its daily income aside for the well; a Hamlet, with nothing to build, keeps it all. */
    public static void savingsFromIncome(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        floor(helper);
        Villager villager = farmer(helper, new BlockPos(3, 1, 3), BELL);
        Villages.census(helper.getLevel());
        VillageState village = village(helper, BELL);
        helper.assertTrue(village.census().population() == 1, "one villager counted");
        helper.assertTrue(VillageBuildings.next(village) == null, "a Hamlet has nothing to build");
        Villages.set(server, village, 0, 0);
        long income = Villages.dailyIncome(village);
        helper.assertTrue(income > 0, "the villager earns");
        Villages.backdate(server, village, 2);
        Villages.touch(server, village);
        GameTestAsserts.assertValueEqual(helper, village.savings(), 0L, "nothing saved by a Hamlet");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), Math.min(Villages.treasuryCap(village), 2 * income), "all of it in the treasury");

        // Twice the threshold: the backdated days' prosperity decay must leave it a Village
        Villages.set(server, village, level(2) * 2, 0);
        VillageBuildings.Building next = VillageBuildings.next(village);
        helper.assertTrue(next != null && next.id().equals(WELL), "a Village saves for the well");
        income = Villages.dailyIncome(village);
        Villages.backdate(server, village, 4);
        Villages.touch(server, village);
        long saved = (long) (4 * income * config().buildings().savingsShare());
        GameTestAsserts.assertValueEqual(helper, village.savings(), saved, "the share put aside");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), Math.min(Villages.treasuryCap(village), 4 * income - saved), "the rest in the treasury");
        villager.discard();
        helper.succeed();
    }

    /** With the fund full, the well goes up by the bell once, from the fund, with the village's name on its sign. */
    public static void builtFromSavings(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper);
        helper.setBlock(BELL, Blocks.BELL);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, level(2), 0);
        ServerPlayer player = player(helper, new BlockPos(4, 1, 4));
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 8, 4)));
        VillageBuildings.Building well = VillageBuildings.get(WELL).orElseThrow();
        long cost = VillageBuildings.costMinor(well);

        Villages.setSavings(server, village, cost - 1);
        helper.assertTrue(VillageBuildings.tryBuild(level, village, 4, area) == null, "not enough in the fund");
        GameTestAsserts.assertValueEqual(helper, village.savings(), cost - 1, "the fund is untouched");

        Villages.setSavings(server, village, cost + 1);
        VillageBuildings.Building built = VillageBuildings.tryBuild(level, village, 4, area);
        helper.assertTrue(built != null && built.id().equals(WELL), "the well is built");
        GameTestAsserts.assertValueEqual(helper, village.savings(), 1L, "paid from the fund");
        helper.assertTrue(village.buildings().contains(WELL.toString()), "the village has it");
        int water = 0;
        int banners = 0;
        String signLine = null;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.WATER)) {
                water++;
            }
            if (level.getBlockState(pos).is(Blocks.RED_BANNER)) {
                banners++;
            }
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                signLine = sign.getFrontText().getMessages(false)[0].getString();
                helper.assertTrue(sign.isWaxed(), "the sign can't be edited");
            }
        }
        GameTestAsserts.assertValueEqual(helper, water, 1, "the well's water");
        GameTestAsserts.assertValueEqual(helper, banners, 1, "the banner on top");
        String name = Villages.displayName(village).getString();
        helper.assertTrue(signLine != null && !signLine.isEmpty() && name.startsWith(signLine), "the sign says the village's name: " + signLine);
        helper.assertTrue(level.getBlockState(helper.absolutePos(BELL)).is(Blocks.BELL), "the bell stays");

        Villages.setSavings(server, village, cost * 10);
        helper.assertTrue(VillageBuildings.tryBuild(level, village, 4, area) == null, "one building a day, and the next needs a Town");
        helper.assertTrue(VillageBuildings.next(village) == null, "nothing more to build at this level");
        Villages.set(server, village, level(3), 0);
        helper.assertTrue(VillageBuildings.next(village) != null && VillageBuildings.next(village).level() == 3, "a Town saves for the next one");
        player.discard();
        helper.succeed();
    }

    /** A Town's market day at dusk: the festival starts once, villagers get a day of wages, the village gains prosperity. */
    public static void festivalOnMarketDay(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        helper.setBlock(BELL, Blocks.BELL);
        Villager villager = farmer(helper, new BlockPos(2, 1, 2), BELL);
        Villages.census(level);
        VillageState village = village(helper, BELL);
        long today = today(helper);
        VillageState.Events e = village.events();
        Villages.setEvents(server, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), today, e.caravanDay(), e.restorationUntil(),
            e.raidActive()));
        int minLevel = config().festival().minLevel();
        helper.assertTrue(minLevel <= config().levels().thresholds().size(), "festivals are possible with this config");

        Villages.set(server, village, 0, 0);
        helper.assertFalse(VillageFestivals.due(server, village, 9500, today), "no festival in a Hamlet");
        Villages.set(server, village, level(minLevel), 0);
        helper.assertTrue(VillageFestivals.due(server, village, 9500, today), "due at dusk on a market day");
        helper.assertFalse(VillageFestivals.due(server, village, 6000, today), "not at noon");
        Villages.setEvents(server, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), today + 1, e.caravanDay(), e.restorationUntil(),
            e.raidActive()));
        helper.assertFalse(VillageFestivals.due(server, village, 9500, today), "no market today: no festival");
        Villages.setEvents(server, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), today, e.caravanDay(), e.restorationUntil(),
            e.raidActive()));

        long wallet = VillagerPurchases.wallet(villager, today);
        long wage = VillagerPurchases.dailyWage(villager);
        double prosperity = village.prosperity();
        VillageFestivals.start(level, village, today);
        GameTestAsserts.assertValueEqual(helper, village.festivalDay(), today, "held today");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.wallet(villager, today), wallet + wage * config().festival().walletBonusDays(),
            "a day of wages on top");
        helper.assertTrue(village.prosperity() > prosperity, "prosperity gained");
        helper.assertFalse(VillageFestivals.due(server, village, 9500, today), "once a day");
        villager.discard();
        helper.succeed();
    }
}
