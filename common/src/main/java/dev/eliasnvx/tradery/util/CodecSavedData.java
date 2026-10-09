package dev.eliasnvx.tradery.util;

import com.mojang.serialization.Codec;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * World-wide saved data serialized through a {@link Codec}. Stored in the overworld's data storage
 * ({@code <world>/data/<name>.dat}), which the server saves with the world.
 */
public final class CodecSavedData {
    private CodecSavedData() {
    }

    /**
     * How to create and load one kind of saved data: what {@code DimensionDataStorage#computeIfAbsent} takes
     * (1.20.1 has no {@code SavedData.Factory}).
     *
     * @param constructor  a fresh, empty instance
     * @param deserializer reads the {@code data} compound of the file
     */
    public record Factory<T extends SavedData>(Supplier<T> constructor, Function<CompoundTag, T> deserializer) {
    }

    /**
     * A factory that decodes the data with {@code codec}. Data that can't be read is logged and the readable part
     * kept; nothing readable at all starts empty (as vanilla does with a broken file).
     */
    public static <T extends SavedData> Factory<T> factory(String name, Codec<T> codec, Supplier<T> empty) {
        return new Factory<>(empty, tag -> codec.parse(NbtOps.INSTANCE, tag)
            .resultOrPartial(error -> Tradery.LOGGER.error("Failed to parse saved data '{}': {}", name, error))
            .orElseGet(empty));
    }

    /** Encodes {@code value} into {@code into} (the body of {@link SavedData#save(CompoundTag)}). */
    public static <T> CompoundTag save(Codec<T> codec, T value, CompoundTag into) {
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false, error -> { });
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalStateException("Saved data must encode to a compound tag, got " + encoded.getType().getName());
        }
        return into.merge(compound);
    }

    /** The server's instance, loaded or created on first use. */
    public static <T extends SavedData> T get(MinecraftServer server, Factory<T> factory, String name) {
        return server.overworld().getDataStorage().computeIfAbsent(factory.deserializer(), factory.constructor(), name);
    }
}
