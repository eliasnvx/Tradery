package dev.eliasnvx.tradery.rewards;

import com.mojang.serialization.Codec;
import dev.eliasnvx.tradery.platform.Platform;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Arrays;

/**
 * Which blocks were placed by players, per chunk (a data attachment), so mining them never pays a {@code mine}
 * reward. Only blocks that would pay are marked, which keeps the sets tiny.
 */
public final class PlacedBlocks {
    public static final Codec<LongSet> CODEC = Codec.LONG_STREAM.xmap(
        stream -> (LongSet) new LongOpenHashSet(stream.toArray()),
        set -> Arrays.stream(set.toLongArray()));

    private PlacedBlocks() {
    }

    static boolean isPlaced(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return Platform.get().placedBlocks(chunk).contains(pos.asLong());
    }

    static void mark(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongSet current = Platform.get().placedBlocks(chunk);
        if (!current.contains(pos.asLong())) {
            LongSet next = new LongOpenHashSet(current);
            next.add(pos.asLong());
            Platform.get().setPlacedBlocks(chunk, next);
        }
    }

    static void unmark(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongSet current = Platform.get().placedBlocks(chunk);
        if (current.contains(pos.asLong())) {
            LongSet next = new LongOpenHashSet(current);
            next.remove(pos.asLong());
            Platform.get().setPlacedBlocks(chunk, next);
        }
    }
}
