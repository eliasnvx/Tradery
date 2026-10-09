package dev.eliasnvx.tradery.util;

import com.mojang.serialization.Codec;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Supplier;

/**
 * World-wide saved data serialized through a {@link Codec}. Stored in the overworld's data storage
 * ({@code <world>/data/<name>.dat}), which the server saves with the world.
 */
public final class CodecSavedData {
    private CodecSavedData() {
    }

    /**
     * A factory that decodes the data with {@code codec}. Data that can't be read is logged and the readable part
     * kept; nothing readable at all starts empty (as vanilla does with a broken file).
     */
    public static <T extends SavedData> SavedData.Factory<T> factory(String name, Codec<T> codec, Supplier<T> empty) {
        return new SavedData.Factory<>(empty, (tag, registries) -> codec.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag)
            .resultOrPartial(error -> Tradery.LOGGER.error("Failed to parse saved data '{}': {}", name, error))
            .orElseGet(empty),
            // Plain mod data: the command-storage fixer leaves it alone
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    /** Encodes {@code value} into {@code into} (the body of {@link SavedData#save(CompoundTag, HolderLookup.Provider)}). */
    public static <T> CompoundTag save(Codec<T> codec, T value, CompoundTag into, HolderLookup.Provider registries) {
        Tag encoded = codec.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), value).getOrThrow();
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalStateException("Saved data must encode to a compound tag, got " + encoded.getType().getName());
        }
        return into.merge(compound);
    }

    /** The server's instance, loaded or created on first use. */
    public static <T extends SavedData> T get(MinecraftServer server, SavedData.Factory<T> factory, String name) {
        return server.overworld().getDataStorage().computeIfAbsent(factory, name);
    }
}
