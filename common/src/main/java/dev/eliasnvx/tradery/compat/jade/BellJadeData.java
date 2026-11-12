package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.Optional;

/**
 * Server side of the Jade tooltip of a bell: the village it belongs to ({@link TooltipData#bell}), in Jade's server
 * data tag (Jade 11 on 1.20.1 has no stream providers).
 */
enum BellJadeData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("bell_village");
    /** Key in Jade's server data tag; namespaced so other providers of the same block can't clash. */
    private static final String KEY = UID.toString();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            data.put(KEY, TooltipData.bell(level, accessor.getPosition()).write());
        }
    }

    /** Client side: what the server sent, if it did (it has Tradery and Jade). */
    static Optional<TooltipData.Bell> read(BlockAccessor accessor) {
        CompoundTag data = accessor.getServerData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? Optional.of(TooltipData.Bell.read(data.getCompound(KEY))) : Optional.empty();
    }
}
