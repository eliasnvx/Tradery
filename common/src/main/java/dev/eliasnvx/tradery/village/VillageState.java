package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One village's economy (SPEC "Деревня-экономика"): census, prosperity, treasury, projects, board orders, trading
 * licences and the dates of its events. Mutable, owned by {@link VillageEconomy}; change it only through
 * {@link Villages} and the features' services, which mark the saved data dirty. Days are in-game days
 * ({@link VillagerPurchases#today}), money is in minor units. Server thread only.
 */
public final class VillageState {
    /**
     * The latest count of the village's villagers.
     *
     * @param population  adults whose meeting point is this bell
     * @param employed    of them, with a profession (not nitwits)
     * @param professions profession id → adults with it
     * @param type        villager type of most adults ({@code minecraft:plains}, …), or empty before the first count
     * @param day         day of the count
     */
    public record Census(int population, int employed, Map<String, Integer> professions, String type, long day) {
        public static final Census EMPTY = new Census(0, 0, Map.of(), "", -1);
        static final Codec<Census> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("population", 0).forGetter(Census::population),
            Codec.INT.optionalFieldOf("employed", 0).forGetter(Census::employed),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("professions", Map.of()).forGetter(Census::professions),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Census::type),
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(Census::day)
        ).apply(i, Census::new));

        public boolean known() {
            return day >= 0;
        }
    }

    /**
     * An order on the village board.
     *
     * @param id            unique in this village
     * @param item          the item wanted (plain stacks only)
     * @param count         how many in total
     * @param delivered     how many so far
     * @param rewardPerItem money per item (minor units), reserved from the treasury when posted
     * @param expiresDay    first day it's gone
     * @param restoration   posted after a raid
     */
    public record Order(int id, ResourceLocation item, int count, int delivered, long rewardPerItem, long expiresDay, boolean restoration) {
        static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Order::id),
            ResourceLocation.CODEC.fieldOf("item").forGetter(Order::item),
            Codec.INT.fieldOf("count").forGetter(Order::count),
            Codec.INT.optionalFieldOf("delivered", 0).forGetter(Order::delivered),
            Codec.LONG.fieldOf("reward_per_item").forGetter(Order::rewardPerItem),
            Codec.LONG.fieldOf("expires_day").forGetter(Order::expiresDay),
            Codec.BOOL.optionalFieldOf("restoration", false).forGetter(Order::restoration)
        ).apply(i, Order::new));

        public int remaining() {
            return Math.max(0, count - delivered);
        }

        public Order withDelivered(int delivered) {
            return new Order(id, item, count, delivered, rewardPerItem, expiresDay, restoration);
        }
    }

    /**
     * Dates of the village's events.
     *
     * @param ordersDay        day the board was last filled
     * @param nextOrderId      id for the next order
     * @param nextMarketDay    day of the next market day (-1: not planned yet)
     * @param caravanDay       day the village last sent a caravan (-1: never)
     * @param restorationUntil restoration orders are posted until this day (exclusive)
     * @param raidActive       a raid was going on at the last check
     */
    public record Events(long ordersDay, int nextOrderId, long nextMarketDay, long caravanDay, long restorationUntil, boolean raidActive) {
        static final Events EMPTY = new Events(-1, 1, -1, -1, -1, false);
        static final Codec<Events> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("orders_day", -1L).forGetter(Events::ordersDay),
            Codec.INT.optionalFieldOf("next_order_id", 1).forGetter(Events::nextOrderId),
            Codec.LONG.optionalFieldOf("next_market_day", -1L).forGetter(Events::nextMarketDay),
            Codec.LONG.optionalFieldOf("caravan_day", -1L).forGetter(Events::caravanDay),
            Codec.LONG.optionalFieldOf("restoration_until", -1L).forGetter(Events::restorationUntil),
            Codec.BOOL.optionalFieldOf("raid_active", false).forGetter(Events::raidActive)
        ).apply(i, Events::new));
    }

    /**
     * What moved through the treasury on one day: {@code in} came in (daily income, trade tax, rent, donations,
     * money returned by expired orders and caravans), {@code out} left (orders posted, caravan purses, raid losses).
     * Minor units; both are never negative.
     */
    public record DayFlow(long day, long in, long out) {
        static final Codec<DayFlow> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("day").forGetter(DayFlow::day),
            Codec.LONG.optionalFieldOf("in", 0L).forGetter(DayFlow::in),
            Codec.LONG.optionalFieldOf("out", 0L).forGetter(DayFlow::out)
        ).apply(i, DayFlow::new));
        public static final StreamCodec<ByteBuf, DayFlow> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DayFlow::day,
            ByteBufCodecs.VAR_LONG, DayFlow::in,
            ByteBufCodecs.VAR_LONG, DayFlow::out,
            DayFlow::new);
    }

    /** The treasury's history keeps this many days. */
    public static final int HISTORY_DAYS = 7;

    /**
     * How the village grows on its own: the building fund, the level buildings built, the day of the last build
     * attempt and of the last festival ({@link VillageBuildings}, {@link VillageFestivals}).
     */
    record Growth(long savings, List<String> buildings, long buildDay, long festivalDay) {
        static final Growth NONE = new Growth(0, List.of(), -1, -1);
        static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("savings", 0L).forGetter(Growth::savings),
            Codec.STRING.listOf().optionalFieldOf("buildings", List.of()).forGetter(Growth::buildings),
            Codec.LONG.optionalFieldOf("build_day", -1L).forGetter(Growth::buildDay),
            Codec.LONG.optionalFieldOf("festival_day", -1L).forGetter(Growth::festivalDay)
        ).apply(i, Growth::new));
    }

    static final Codec<VillageState> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(v -> v.dimension),
        BlockPos.CODEC.fieldOf("bell").forGetter(v -> v.bell),
        Codec.STRING.optionalFieldOf("name", "").forGetter(v -> v.name),
        Census.CODEC.optionalFieldOf("census", Census.EMPTY).forGetter(v -> v.census),
        Codec.DOUBLE.optionalFieldOf("prosperity", 0.0).forGetter(v -> v.prosperity),
        Codec.LONG.optionalFieldOf("prosperity_day", -1L).forGetter(v -> v.prosperityDay),
        Codec.LONG.optionalFieldOf("treasury", 0L).forGetter(v -> v.treasury),
        Codec.LONG.optionalFieldOf("treasury_day", -1L).forGetter(v -> v.treasuryDay),
        Codec.STRING.listOf().optionalFieldOf("projects", List.of()).forGetter(v -> List.copyOf(v.projects)),
        Order.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(v -> v.orders),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("licences", Map.of()).forGetter(v -> v.licences),
        Events.CODEC.optionalFieldOf("events", Events.EMPTY).forGetter(v -> v.events),
        Codec.LONG.optionalFieldOf("seen_day", 0L).forGetter(v -> v.seenDay),
        DayFlow.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(v -> v.history),
        Growth.CODEC.optionalFieldOf("growth", Growth.NONE).forGetter(v -> new Growth(v.savings, List.copyOf(v.buildings), v.buildDay, v.festivalDay))
    ).apply(i, VillageState::new));

    final ResourceKey<Level> dimension;
    final BlockPos bell;
    String name;
    Census census;
    double prosperity;
    long prosperityDay;
    long treasury;
    long treasuryDay;
    final Set<String> projects;
    final List<Order> orders;
    final Map<UUID, Long> licences;
    Events events;
    long seenDay;
    /** Oldest first, at most {@link #HISTORY_DAYS} entries, one per day that saw money move. */
    final List<DayFlow> history;
    /** Money put aside for the next level building ({@link VillageBuildings}), minor units; raids don't touch it. */
    long savings;
    /** Level buildings the village has built, by id. */
    final Set<String> buildings;
    /** Day of the last attempt to build (one a day, built or not). */
    long buildDay;
    /** Day of the last festival. */
    long festivalDay;

    VillageState(ResourceKey<Level> dimension, BlockPos bell, String name, Census census, double prosperity, long prosperityDay, long treasury,
                 long treasuryDay, List<String> projects, List<Order> orders, Map<UUID, Long> licences, Events events, long seenDay,
                 List<DayFlow> history, Growth growth) {
        this.dimension = dimension;
        this.bell = bell.immutable();
        this.name = name;
        this.census = census;
        this.prosperity = prosperity;
        this.prosperityDay = prosperityDay;
        this.treasury = treasury;
        this.treasuryDay = treasuryDay;
        this.projects = new LinkedHashSet<>(projects);
        this.orders = new ArrayList<>(orders);
        this.licences = new HashMap<>(licences);
        this.events = events;
        this.seenDay = seenDay;
        this.history = new ArrayList<>(history.size() > HISTORY_DAYS ? history.subList(history.size() - HISTORY_DAYS, history.size()) : history);
        this.savings = Math.max(0, growth.savings());
        this.buildings = new LinkedHashSet<>(growth.buildings());
        this.buildDay = growth.buildDay();
        this.festivalDay = growth.festivalDay();
    }

    static VillageState create(ResourceKey<Level> dimension, BlockPos bell, long today) {
        return new VillageState(dimension, bell, "", Census.EMPTY, 0, today, 0, today, List.of(), List.of(), Map.of(), Events.EMPTY, today,
            List.of(), Growth.NONE);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos bell() {
        return bell;
    }

    /** The key the rest of the mod uses for this village ({@link VillagerPurchases#villageKey}). */
    public String key() {
        return Villages.key(dimension, bell);
    }

    /** A name given by players, or empty. */
    public String name() {
        return name;
    }

    public Census census() {
        return census;
    }

    /** Prosperity points (decay applied up to the last {@link Villages#touch}). */
    public double prosperity() {
        return prosperity;
    }

    /** Treasury, minor units (income added up to the last {@link Villages#touch}). */
    public long treasury() {
        return treasury;
    }

    public Set<String> projects() {
        return Set.copyOf(projects);
    }

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    /** Players with a trading place here → first day the licence no longer holds. */
    public Map<UUID, Long> licences() {
        return Map.copyOf(licences);
    }

    public Events events() {
        return events;
    }

    /** The treasury's last {@link #HISTORY_DAYS} days with money moving, oldest first. */
    public List<DayFlow> history() {
        return List.copyOf(history);
    }

    /** Money put aside for the next level building, minor units. */
    public long savings() {
        return savings;
    }

    /** Level buildings built here, by id ({@link VillageBuildings}). */
    public Set<String> buildings() {
        return Set.copyOf(buildings);
    }

    public long festivalDay() {
        return festivalDay;
    }
}
