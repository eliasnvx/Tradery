package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Raids hurting the village economy (SPEC "Угрозы"): the level tick watches vanilla raids at known bells. A lost raid
 * takes prosperity, half the treasury and half the money of the village's loaded villagers (less with projects such as
 * the watch post); a won raid brings prosperity and restoration orders on the board. Server thread.
 */
public final class VillageRaids {
    private static final EntityTypeTest<Entity, Villager> VILLAGERS = EntityTypeTest.forClass(Villager.class);
    /** How often the level tick looks at the raids near known bells. */
    static final int CHECK_TICKS = 20;
    /** Share of the treasury and of the villagers' money a lost raid takes (SPEC: half), before projects. */
    static final double LOSS_SHARE = 0.5;
    /** Days the board posts restoration orders after a won raid (SPEC: 3). */
    static final int RESTORATION_DAYS = 3;
    /** Players this close to the bell hear how the raid went. */
    static final int NEWS_RADIUS = 64;

    private VillageRaids() {
    }

    /** What a check saw at a village's bell. */
    public enum Seen {
        NONE, ONGOING, VICTORY, LOSS;

        static Seen of(@Nullable Raid raid) {
            if (raid == null || raid.isStopped()) {
                return NONE;
            }
            return raid.isVictory() ? VICTORY : raid.isLoss() ? LOSS : ONGOING;
        }
    }

    /** Level tick: every {@link #CHECK_TICKS}, the raids at the loaded bells of this level. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (VillageState village : VillageEconomy.get(server).all()) {
            if (village.dimension() == level.dimension() && level.isLoaded(village.bell())) {
                observe(level, village, Seen.of(level.getRaidAt(village.bell())));
            }
        }
    }

    /**
     * One look at the village's bell. A raid seen going on is remembered ({@code Events.raidActive}, saved); when that
     * raid is then seen won or lost, the outcome is applied once. A raid that vanished unseen (stopped, timed out)
     * changes nothing.
     */
    public static void observe(ServerLevel level, VillageState village, Seen seen) {
        MinecraftServer server = level.getServer();
        boolean active = village.events.raidActive();
        switch (seen) {
            case ONGOING -> {
                if (!active) {
                    setActive(server, village, true);
                }
            }
            case VICTORY, LOSS -> {
                if (active) {
                    setActive(server, village, false);
                    if (seen == Seen.VICTORY) {
                        won(level, village);
                    } else {
                        lost(level, village);
                    }
                }
            }
            case NONE -> {
                if (active) {
                    setActive(server, village, false);
                }
            }
        }
    }

    private static void setActive(MinecraftServer server, VillageState village, boolean active) {
        VillageState.Events e = village.events;
        Villages.setEvents(server, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), e.nextMarketDay(), e.caravanDay(),
            e.restorationUntil(), active));
    }

    /**
     * A lost raid: prosperity times {@code 1 - raidLossProsperity × raidLoss}, the treasury and the loaded villagers'
     * wallets lose {@code half × raidLoss} ({@code raidLoss}: the projects' factor, 1 without them).
     *
     * @return what the treasury lost (minor units)
     */
    public static long lost(ServerLevel level, VillageState village) {
        MinecraftServer server = level.getServer();
        VillagesConfig.Threats threats = Villages.config().threats();
        double raidLoss = VillageProjects.effects(village).raidLoss();
        Villages.scaleProsperity(server, village, 1 - threats.raidLossProsperity() * raidLoss);
        double share = LOSS_SHARE * raidLoss;
        long treasuryLost = Villages.loseTreasury(server, village, share);
        long today = VillagerPurchases.today(server);
        VillagerLedger ledger = VillagerLedger.get(server);
        GlobalPos bell = GlobalPos.of(village.dimension(), village.bell());
        for (Villager villager : level.getEntities(VILLAGERS, v -> v.isAlive() && bell.equals(Villages.bellOf(v).orElse(null)))) {
            long wallet = VillagerPurchases.wallet(villager, today);
            long taken = (long) (wallet * Math.max(0, Math.min(1, share)));
            if (taken > 0) {
                ledger.pay(villager.getUUID(), taken);
            }
        }
        tell(level, village, Messages.tr("tradery.raid.lost", "%s lost the raid: its treasury lost %s and its villagers lost money",
            Villages.displayName(village), Messages.coins(treasuryLost)));
        return treasuryLost;
    }

    /** A won raid: {@code raidWinProsperity} points and restoration orders for {@link #RESTORATION_DAYS} days. */
    public static void won(ServerLevel level, VillageState village) {
        MinecraftServer server = level.getServer();
        Villages.addProsperity(server, village, Villages.config().threats().raidWinProsperity());
        Villages.startRestoration(server, village, RESTORATION_DAYS);
        tell(level, village, Messages.tr("tradery.raid.won", "%s won the raid! The board asks for building materials to restore it",
            Villages.displayName(village)));
    }

    private static void tell(ServerLevel level, VillageState village, Component message) {
        Vec3 bell = Vec3.atCenterOf(village.bell());
        double radiusSqr = (double) NEWS_RADIUS * NEWS_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(bell) <= radiusSqr) {
                player.sendSystemMessage(message);
            }
        }
    }
}
