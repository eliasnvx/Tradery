package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.village.VillageBuildings;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.Optional;

/**
 * Server side of the Jade tooltip of a bell: the village it belongs to, if Tradery knows it, in Jade's server data
 * tag (Jade 11 on 1.20.1 has no stream providers).
 */
enum BellJadeData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("bell_village");
    /** Key in Jade's server data tag; namespaced so other providers of the same block can't clash. */
    private static final String KEY = UID.toString();

    /** {@code known} false: not a village Tradery knows (yet). */
    record Info(boolean known, Component name, int level, int prosperity, long treasury, Component saving, long savings, long savingsNeed) {
        static final Info NONE = new Info(false, Component.empty(), 0, 0, 0, Component.empty(), 0, 0);

        CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("known", known);
            tag.putString("name", Component.Serializer.toJson(name));
            tag.putInt("level", level);
            tag.putInt("prosperity", prosperity);
            tag.putLong("treasury", treasury);
            tag.putString("saving", Component.Serializer.toJson(saving));
            tag.putLong("savings", savings);
            tag.putLong("savings_need", savingsNeed);
            return tag;
        }

        static Info read(CompoundTag tag) {
            return new Info(tag.getBoolean("known"), text(tag.getString("name")), tag.getInt("level"), tag.getInt("prosperity"),
                tag.getLong("treasury"), text(tag.getString("saving")), tag.getLong("savings"), tag.getLong("savings_need"));
        }

        private static Component text(String json) {
            try {
                Component parsed = Component.Serializer.fromJson(json);
                return parsed == null ? Component.empty() : parsed;
            } catch (RuntimeException e) {
                return Component.empty();
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            data.put(KEY, info(level, accessor).write());
        }
    }

    private static Info info(ServerLevel level, BlockAccessor accessor) {
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

    /** Client side: what the server sent, if it did (it has Tradery and Jade). */
    static Optional<Info> read(BlockAccessor accessor) {
        CompoundTag data = accessor.getServerData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? Optional.of(Info.read(data.getCompound(KEY))) : Optional.empty();
    }
}
