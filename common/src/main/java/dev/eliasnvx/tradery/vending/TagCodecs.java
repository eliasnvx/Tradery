package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Codec values in block entity tags (item stacks, block states). A value that can't be written or read is logged and
 * skipped, so one bad field never loses the rest of the block.
 */
final class TagCodecs {
    /**
     * An item stack as vanilla saves it ({@link ItemStack#save} / {@link ItemStack#of}). Unlike {@link ItemStack#CODEC} it
     * keeps what a loader adds (Forge capabilities), so a saved sample still matches stock saved the same way
     * ({@link StackMath}). Empty or unreadable reads as empty.
     */
    static final Codec<ItemStack> ITEM = CompoundTag.CODEC.xmap(ItemStack::of, stack -> stack.save(new CompoundTag()));

    private TagCodecs() {
    }

    static <T> void put(CompoundTag tag, String key, Codec<T> codec, T value) {
        DataResult<Tag> result = codec.encodeStart(NbtOps.INSTANCE, value);
        result.error().ifPresent(error -> Tradery.LOGGER.warn("Couldn't save '{}': {}", key, error.message()));
        result.result().ifPresent(encoded -> tag.put(key, encoded));
    }

    /** Writes nothing for {@code null}. */
    static <T> void putNullable(CompoundTag tag, String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            put(tag, key, codec, value);
        }
    }

    /** Empty when the key is missing or its value can't be read. */
    static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec) {
        Tag value = tag.get(key);
        if (value == null) {
            return Optional.empty();
        }
        DataResult<T> result = codec.parse(NbtOps.INSTANCE, value);
        result.error().ifPresent(error -> Tradery.LOGGER.warn("Couldn't load '{}': {}", key, error.message()));
        return result.result();
    }
}
