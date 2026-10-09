package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.mixin.PlayerListAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a vending block's stock, the money and the traders' inventories on disk together. Minecraft saves a changed
 * chunk on its own about 10 s after the change, but server saved data (balances) and player data (inventories) only
 * at the autosave or on logout. After a crash the stock would then be newer than the money and items it was traded
 * for: a buyback would leave the goods both in the block and in the seller's inventory. So when a chunk with trades
 * since its last save is saved, the economy data and the traders' player data are saved in the same tick.
 * Main thread only.
 */
public final class TradePersistence {
    private static final Map<ResourceKey<Level>, Map<Long, Set<UUID>>> PENDING = new HashMap<>();

    private TradePersistence() {
    }

    /** A trade at the vending block at {@code pos} changed its stock (or revenue) and this player's inventory. */
    public static void traded(ServerLevel level, BlockPos pos, ServerPlayer player) {
        PENDING.computeIfAbsent(level.dimension(), key -> new HashMap<>())
            .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>())
            .add(player.getUUID());
    }

    /** Minecraft wrote a chunk (eagerly, on unload or at the autosave); called by {@code ChunkMapMixin}. */
    public static void chunkSaved(ServerLevel level, ChunkPos pos) {
        Map<Long, Set<UUID>> chunks = PENDING.get(level.dimension());
        Set<UUID> traders = chunks == null ? null : chunks.remove(pos.toLong());
        if (traders == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        // Tradery's saved data (balances included) lives in the overworld's storage: write what changed now
        server.overworld().getDataStorage().save();
        for (UUID trader : traders) {
            ServerPlayer player = server.getPlayerList().getPlayer(trader);
            if (player != null) {
                ((PlayerListAccessor) server.getPlayerList()).tradery$save(player);
            }
        }
    }

    /** Traders whose save waits for the chunk at {@code pos} (for tests). */
    public static Set<UUID> pending(ServerLevel level, BlockPos pos) {
        Map<Long, Set<UUID>> chunks = PENDING.get(level.dimension());
        Set<UUID> traders = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        return traders == null ? Set.of() : Set.copyOf(traders);
    }

    public static void clear() {
        PENDING.clear();
    }
}
