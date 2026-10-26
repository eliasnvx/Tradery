package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which known village each player is in (within {@code villages.boardRadius} of its bell), checked every
 * {@link #CHECK_TICKS}; the client gets {@link TraderyPayloads.VillageTitlePayload} when it changes, or when the
 * village's level changes while the player is there, and shows the name and level for a few seconds. Server thread.
 */
public final class VillagePresence {
    static final int CHECK_TICKS = 40;

    private record Shown(String key, int level) {
        static final Shown NONE = new Shown("", 0);
    }

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private VillagePresence() {
    }

    /** End of a level tick: every two seconds, every player in it. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            check(player);
        }
    }

    static void check(ServerPlayer player) {
        // Known villages only (the census and the board create them): a lone bell by a player's house stays a bell
        ServerLevel level = player.serverLevel();
        VillageState village = Villages.nearestBell(level, player.blockPosition(), Villages.config().boardRadius())
            .map(bell -> Villages.byKey(level.getServer(), Villages.key(level.dimension(), bell))).orElse(null);
        Shown now = village == null ? Shown.NONE : new Shown(village.key(), Villages.level(village));
        Shown before = SHOWN.getOrDefault(player.getUUID(), Shown.NONE);
        if (now.equals(before)) {
            return;
        }
        SHOWN.put(player.getUUID(), now);
        Platform.get().sendToPlayer(player, village == null
            ? new TraderyPayloads.VillageTitlePayload(Component.empty(), 0, false, false)
            : new TraderyPayloads.VillageTitlePayload(Villages.displayName(village), now.level(), true, false));
    }

    /** A festival started in the village: every player there sees it. */
    public static void announceFestival(ServerLevel level, VillageState village, int radius) {
        double radiusSqr = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(village.bell().getX() + 0.5, village.bell().getY(), village.bell().getZ() + 0.5) <= radiusSqr) {
                Platform.get().sendToPlayer(player, new TraderyPayloads.VillageTitlePayload(Villages.displayName(village), Villages.level(village),
                    true, true));
            }
        }
    }

    public static void forget(ServerPlayer player) {
        SHOWN.remove(player.getUUID());
    }

    public static void clear() {
        SHOWN.clear();
    }
}
