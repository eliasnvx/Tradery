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
    /** Players who traded with a villager since their data was last saved with the economy data. */
    private static final Set<UUID> VILLAGER_TRADERS = new HashSet<>();
    /** Traders to save at the end of this tick, for the chunks written in it. */
    private static final Set<UUID> SAVE_PLAYERS = new HashSet<>();
    private static boolean saveDue;

    private TradePersistence() {
    }

    /** A trade at the vending block at {@code pos} changed its stock (or revenue) and this player's inventory. */
    public static void traded(ServerLevel level, BlockPos pos, ServerPlayer player) {
        PENDING.computeIfAbsent(level.dimension(), key -> new HashMap<>())
            .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>())
            .add(player.getUUID());
    }

    /** A trade without a player (a villager bought): the stock changed, and so did the money. */
    public static void traded(ServerLevel level, BlockPos pos) {
        PENDING.computeIfAbsent(level.dimension(), key -> new HashMap<>())
            .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashSet<>());
    }

    /**
     * A villager trade changed this player's inventory and the money (balance, the villager's wallet), with no chunk
     * of its own: the player is saved whenever a chunk save here saves the economy data, and the economy data is
     * saved when the player leaves (vanilla saves the player then). The autosave writes both anyway.
     */
    public static void tradedWithVillager(ServerPlayer player) {
        VILLAGER_TRADERS.add(player.getUUID());
    }

    /** The player is leaving; vanilla saves their data in this tick. */
    public static void onPlayerLeave(ServerPlayer player) {
        if (VILLAGER_TRADERS.remove(player.getUUID())) {
            player.server.overworld().getDataStorage().save();
        }
    }

    /** Minecraft wrote a chunk (eagerly, on unload or at the autosave); called by {@code ChunkMapMixin}. */
    public static void chunkSaved(ServerLevel level, ChunkPos pos) {
        Map<Long, Set<UUID>> chunks = PENDING.get(level.dimension());
        Set<UUID> traders = chunks == null ? null : chunks.remove(pos.toLong());
        if (traders == null) {
            return;
        }
        // Coalesced: several chunks written in one tick (eager saves do up to 20) mean one server data save and one
        // save per trader, at the end of the tick, still before the next trade can happen
        saveDue = true;
        SAVE_PLAYERS.addAll(traders);
        SAVE_PLAYERS.addAll(VILLAGER_TRADERS);
        VILLAGER_TRADERS.clear();
    }

    /** End of a level tick: the saves {@link #chunkSaved} asked for, once each. */
    public static void flush(MinecraftServer server) {
        if (!saveDue) {
            return;
        }
        saveDue = false;
        server.overworld().getDataStorage().save();
        for (UUID trader : SAVE_PLAYERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(trader);
            if (player != null) {
                ((PlayerListAccessor) server.getPlayerList()).tradery$save(player);
            }
        }
        SAVE_PLAYERS.clear();
    }

    /** The player traded with a villager since the economy data was last saved with them (for tests). */
    public static boolean villagerTradePending(ServerPlayer player) {
        return VILLAGER_TRADERS.contains(player.getUUID());
    }

    /** Traders whose save waits for the chunk at {@code pos} (for tests). */
    public static Set<UUID> pending(ServerLevel level, BlockPos pos) {
        Map<Long, Set<UUID>> chunks = PENDING.get(level.dimension());
        Set<UUID> traders = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        return traders == null ? Set.of() : Set.copyOf(traders);
    }

    /** The chunk at {@code pos} had a trade since its last save (for tests). */
    public static boolean waitsForSave(ServerLevel level, BlockPos pos) {
        Map<Long, Set<UUID>> chunks = PENDING.get(level.dimension());
        return chunks != null && chunks.containsKey(ChunkPos.asLong(pos));
    }

    public static void clear() {
        PENDING.clear();
        VILLAGER_TRADERS.clear();
        SAVE_PLAYERS.clear();
        saveDue = false;
    }
}
