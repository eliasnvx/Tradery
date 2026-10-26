package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Saved data {@code tradery_caravans}: caravans on the road and at their destination's market (SPEC "Торговля между
 * деревнями"). The trader entity keeps its offers (and their uses) itself; this keeps the money: the purse and what
 * each offer is worth. Server thread only; {@link Caravans} changes it.
 */
public final class CaravanData extends SavedData {
    /** One caravan. */
    public static final class Caravan {
        static final Codec<Caravan> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(c -> c.id),
            Codec.STRING.fieldOf("origin").forGetter(c -> c.origin),
            Codec.STRING.fieldOf("destination").forGetter(c -> c.destination),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(c -> c.dimension),
            BlockPos.CODEC.fieldOf("bell").forGetter(c -> c.bell),
            Codec.LONG.fieldOf("sent_day").forGetter(c -> c.sentDay),
            Codec.LONG.fieldOf("arrive_day").forGetter(c -> c.arriveDay),
            Codec.LONG.optionalFieldOf("purse", 0L).forGetter(c -> c.purse),
            UUIDUtil.STRING_CODEC.optionalFieldOf("trader").forGetter(c -> Optional.ofNullable(c.trader)),
            BlockPos.CODEC.optionalFieldOf("last_pos").forGetter(c -> Optional.ofNullable(c.lastPos)),
            Codec.LONG.optionalFieldOf("last_seen_day", -1L).forGetter(c -> c.lastSeenDay),
            Codec.LONG.listOf().optionalFieldOf("values", List.of()).forGetter(c -> c.values)
        ).apply(i, Caravan::new));

        final UUID id;
        /** Village keys. */
        final String origin;
        final String destination;
        final ResourceKey<Level> dimension;
        /** The destination's bell, where the caravan shows up. */
        final BlockPos bell;
        final long sentDay;
        /** First day it may show up at the destination. */
        final long arriveDay;
        /** Money it can pay players, minor units; taken from the origin's treasury, goes back there when it leaves. */
        long purse;
        /** The trader entity once it arrived. */
        @Nullable UUID trader;
        @Nullable BlockPos lastPos;
        long lastSeenDay;
        /** Money per offer of the trader, by offer index (the emerald placeholder in each offer is worth this). */
        List<Long> values;

        Caravan(UUID id, String origin, String destination, ResourceKey<Level> dimension, BlockPos bell, long sentDay, long arriveDay,
                long purse, Optional<UUID> trader, Optional<BlockPos> lastPos, long lastSeenDay, List<Long> values) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
            this.dimension = dimension;
            this.bell = bell.immutable();
            this.sentDay = sentDay;
            this.arriveDay = arriveDay;
            this.purse = purse;
            this.trader = trader.orElse(null);
            this.lastPos = lastPos.orElse(null);
            this.lastSeenDay = lastSeenDay;
            this.values = List.copyOf(values);
        }

        public UUID id() {
            return id;
        }

        public String origin() {
            return origin;
        }

        public String destination() {
            return destination;
        }

        public long arriveDay() {
            return arriveDay;
        }

        /** Minor units. */
        public long purse() {
            return purse;
        }

        public boolean arrived() {
            return trader != null;
        }

        public @Nullable UUID trader() {
            return trader;
        }

        /** Money per offer, by offer index. */
        public List<Long> values() {
            return values;
        }
    }

    // 1.21.1 saved data must be a compound: the list goes under "caravans"
    static final Codec<CaravanData> CODEC = Caravan.CODEC.listOf().optionalFieldOf("caravans", List.of())
        .xmap(CaravanData::new, data -> List.copyOf(data.caravans.values())).codec();

    static final String NAME = "tradery_caravans";
    public static final CodecSavedData.Factory<CaravanData> FACTORY = CodecSavedData.factory(NAME, CODEC, CaravanData::new);

    private final Map<UUID, Caravan> caravans = new LinkedHashMap<>();

    public CaravanData() {
    }

    private CaravanData(List<Caravan> list) {
        for (Caravan caravan : list) {
            caravans.put(caravan.id, caravan);
        }
    }

    public static CaravanData get(MinecraftServer server) {
        return CodecSavedData.get(server, FACTORY, NAME);
    }

    /** Every caravan, a copy (callers may close caravans while going through it). */
    public List<Caravan> all() {
        return new ArrayList<>(caravans.values());
    }

    public @Nullable Caravan get(UUID id) {
        return caravans.get(id);
    }

    /** The caravan whose trader is this entity. */
    public @Nullable Caravan byTrader(UUID trader) {
        for (Caravan caravan : caravans.values()) {
            if (trader.equals(caravan.trader)) {
                return caravan;
            }
        }
        return null;
    }

    /** This village has a caravan out. */
    boolean sending(String village) {
        for (Caravan caravan : caravans.values()) {
            if (caravan.origin.equals(village)) {
                return true;
            }
        }
        return false;
    }

    /** A caravan is on its way to this village or at its market. */
    boolean receiving(String village) {
        for (Caravan caravan : caravans.values()) {
            if (caravan.destination.equals(village)) {
                return true;
            }
        }
        return false;
    }

    void add(Caravan caravan) {
        caravans.put(caravan.id, caravan);
        setDirty();
    }

    void remove(UUID id) {
        if (caravans.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CodecSavedData.save(CODEC, this, tag);
    }
}
