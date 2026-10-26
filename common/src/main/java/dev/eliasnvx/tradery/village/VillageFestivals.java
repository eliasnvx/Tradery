package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Festivals (SPEC "Праздник"): on a market day of a village at {@code villages.festival.minLevel} and up, at dusk
 * (the villagers' meeting hours) the bell rings, every villager of the village gets {@code walletBonusDays} of wages
 * on top (to spend at the stalls), the village gains prosperity, players there see "Festival!" and fireworks go up
 * by the bell until night. Once a day per village. Server thread.
 */
public final class VillageFestivals {
    static final int CHECK_TICKS = 100;
    /** Day time the festival runs in: the villagers' meeting hours. */
    static final int START = 9000;
    static final int END = 12000;
    static final int FIREWORK_RADIUS = 6;
    static final double PROSPERITY = 25;
    private static final int[] COLORS = {0xFFD75E, 0xFF5555, 0x55FF55, 0x5599FF, 0xFFFFFF, 0xFF7AE0};

    private VillageFestivals() {
    }

    /** Whether a festival is due in the village at this day time: market day, big enough, not held today. */
    public static boolean due(MinecraftServer server, VillageState village, long dayTime, long today) {
        VillagesConfig.Festival config = Villages.config().festival();
        return config.enabled() && dayTime >= START && dayTime < END && village.festivalDay != today
            && Villages.level(village) >= config.minLevel() && VillageCommerce.isMarketDay(server, village);
    }

    /** End of a level tick: starts due festivals, keeps the fireworks going. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0 || !Villages.config().festival().enabled()) {
            return;
        }
        long dayTime = level.getServer().overworld().getDayTime() % 24000L;
        if (dayTime < START || dayTime >= END) {
            return;
        }
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        for (VillageState village : VillageEconomy.get(server).all()) {
            if (village.dimension() != level.dimension() || !village.census().known() || !level.isLoaded(village.bell())) {
                continue;
            }
            if (due(server, village, dayTime, today)) {
                start(level, village, today);
            }
            if (village.festivalDay == today) {
                firework(level, village.bell());
            }
        }
    }

    /** The festival begins: the bell, the wallets, prosperity, the word. */
    public static void start(ServerLevel level, VillageState village, long today) {
        MinecraftServer server = level.getServer();
        village.festivalDay = today;
        VillageEconomy.get(server).setDirty();
        BlockPos bell = village.bell();
        BlockState state = level.getBlockState(bell);
        if (state.getBlock() instanceof BellBlock block) {
            block.attemptToRing(level, bell, state.getValue(BellBlock.FACING));
        }
        int bonusDays = Villages.config().festival().walletBonusDays();
        if (bonusDays > 0) {
            VillagerLedger ledger = VillagerLedger.get(server);
            for (Villager villager : VillageBoard.villagers(level, village)) {
                long bonus = Villages.saturatedMultiply(VillagerPurchases.dailyWage(villager), bonusDays);
                ledger.setWallet(villager.getUUID(), Villages.saturatedAdd(VillagerPurchases.wallet(villager, today), bonus), today);
            }
        }
        Villages.addProsperity(server, village, PROSPERITY);
        VillagePresence.announceFestival(level, village, VillageBuildings.ANNOUNCE_RADIUS);
        double radiusSqr = (double) VillageBuildings.ANNOUNCE_RADIUS * VillageBuildings.ANNOUNCE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5) <= radiusSqr) {
                player.sendSystemMessage(Messages.tr("tradery.festival.started",
                    "Festival in %s! The villagers celebrate the market day with full wallets", Villages.displayName(village)));
            }
        }
    }

    /** One rocket from the ground near the bell. */
    static void firework(ServerLevel level, BlockPos bell) {
        RandomSource random = level.getRandom();
        int x = bell.getX() + random.nextInt(FIREWORK_RADIUS * 2 + 1) - FIREWORK_RADIUS;
        int z = bell.getZ() + random.nextInt(FIREWORK_RADIUS * 2 + 1) - FIREWORK_RADIUS;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion.Shape shape = random.nextBoolean() ? FireworkExplosion.Shape.LARGE_BALL
            : random.nextBoolean() ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.BURST;
        int color = COLORS[random.nextInt(COLORS.length)];
        int fade = COLORS[random.nextInt(COLORS.length)];
        rocket.set(DataComponents.FIREWORKS, new Fireworks(random.nextInt(2) + 1,
            List.of(new FireworkExplosion(shape, IntList.of(color), IntList.of(fade), random.nextBoolean(), random.nextBoolean()))));
        level.addFreshEntity(new FireworkRocketEntity(level, x + 0.5, y + 0.5, z + 0.5, rocket));
    }
}
