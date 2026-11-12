package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/** Server side of the Jade tooltip of a bell: the village it belongs to ({@link TooltipData#bell}). */
enum BellJadeData implements StreamServerDataProvider<BlockAccessor, TooltipData.Bell> {
    INSTANCE;

    private static final Identifier UID = Tradery.id("bell_village");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public TooltipData.Bell streamData(BlockAccessor accessor) {
        return accessor.getLevel() instanceof ServerLevel level ? TooltipData.bell(level, accessor.getPosition()) : TooltipData.Bell.NONE;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TooltipData.Bell> streamCodec() {
        return TooltipData.Bell.STREAM_CODEC;
    }
}
