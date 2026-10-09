package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Codec values in block entity tags, with registry access (item components, block states). A value that can't be
 * written or read is logged and skipped, so one bad field never loses the rest of the block.
 */
final class TagCodecs {
    private TagCodecs() {
    }

    static <T> void put(CompoundTag tag, String key, Codec<T> codec, T value, HolderLookup.Provider registries) {
        DataResult<Tag> result = codec.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), value);
        result.error().ifPresent(error -> Tradery.LOGGER.warn("Couldn't save '{}': {}", key, error.message()));
        result.result().ifPresent(encoded -> tag.put(key, encoded));
    }

    /** Writes nothing for {@code null}. */
    static <T> void putNullable(CompoundTag tag, String key, Codec<T> codec, @Nullable T value, HolderLookup.Provider registries) {
        if (value != null) {
            put(tag, key, codec, value, registries);
        }
    }

    /** Empty when the key is missing or its value can't be read. */
    static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec, HolderLookup.Provider registries) {
        Tag value = tag.get(key);
        if (value == null) {
            return Optional.empty();
        }
        DataResult<T> result = codec.parse(RegistryOps.create(NbtOps.INSTANCE, registries), value);
        result.error().ifPresent(error -> Tradery.LOGGER.warn("Couldn't load '{}': {}", key, error.message()));
        return result.result();
    }
}
