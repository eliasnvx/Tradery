package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.village.VillageBuildings;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/** Server side of the Jade tooltip of a bell: the village it belongs to, if Tradery knows it. */
enum BellJadeData implements StreamServerDataProvider<BlockAccessor, BellJadeData.Info> {
    INSTANCE;

    private static final Identifier UID = Tradery.id("bell_village");

    /** {@code known} false: not a village Tradery knows (yet). */
    record Info(boolean known, Component name, int level, int prosperity, long treasury, Component saving, long savings, long savingsNeed) {
        static final Info NONE = new Info(false, Component.empty(), 0, 0, 0, Component.empty(), 0, 0);
        static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Info::known,
            ComponentSerialization.TRUSTED_STREAM_CODEC, Info::name,
            ByteBufCodecs.VAR_INT, Info::level,
            ByteBufCodecs.VAR_INT, Info::prosperity,
            ByteBufCodecs.VAR_LONG, Info::treasury,
            ComponentSerialization.TRUSTED_STREAM_CODEC, Info::saving,
            ByteBufCodecs.VAR_LONG, Info::savings,
            ByteBufCodecs.VAR_LONG, Info::savingsNeed,
            Info::new);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public Info streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return Info.NONE;
        }
        VillageState village = Villages.byKey(level.getServer(), Villages.key(level.dimension(), accessor.getPosition()));
        if (village == null) {
            return Info.NONE;
        }
        VillageBuildings.Building next = VillageBuildings.next(village);
        return new Info(true, Villages.displayName(village), Villages.level(village),
            (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.floor(village.prosperity()))), village.treasury(),
            next == null ? Component.empty() : VillageBuildings.name(next.id()), village.savings(),
            next == null ? 0 : VillageBuildings.costMinor(next));
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Info> streamCodec() {
        return Info.STREAM_CODEC;
    }
}
