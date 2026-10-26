package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.TraderyApi;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Notifications for owners who were offline: a running summary (sales, money earned; villagers' supplies, money
 * spent on them) that never overflows, and the latest messages (at most {@link #MAX_LINES}).
 */
public final class NotificationsData extends SavedData {
    public static final int MAX_LINES = 100;

    /** One stored message, sent as a {@link TraderyPayloads.NotificationPayload} later. */
    public record Line(TraderyPayloads.NotificationKind kind, String key, List<String> args) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
            dev.eliasnvx.tradery.config.ConfigCodecs.enumCodec(TraderyPayloads.NotificationKind.class).fieldOf("kind").forGetter(Line::kind),
            Codec.STRING.fieldOf("key").forGetter(Line::key),
            Codec.STRING.listOf().fieldOf("args").forGetter(Line::args)
        ).apply(i, Line::new));
    }

    /** Everything waiting for one owner. */
    public static final class Pending {
        static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("sales", 0).forGetter(p -> p.sales),
            Codec.INT.optionalFieldOf("villager_sales", 0).forGetter(p -> p.villagerSales),
            Codec.LONG.optionalFieldOf("earned", 0L).forGetter(p -> p.earned),
            Line.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(p -> new ArrayList<>(p.lines)),
            Codec.INT.optionalFieldOf("supplies", 0).forGetter(p -> p.supplies),
            Codec.LONG.optionalFieldOf("supply_spent", 0L).forGetter(p -> p.supplySpent)
        ).apply(i, Pending::new));

        int sales;
        int villagerSales;
        long earned;
        final Deque<Line> lines;
        int supplies;
        long supplySpent;

        Pending() {
            this(0, 0, 0, List.of(), 0, 0);
        }

        private Pending(int sales, int villagerSales, long earned, List<Line> lines, int supplies, long supplySpent) {
            this.sales = sales;
            this.villagerSales = villagerSales;
            this.earned = earned;
            this.lines = new ArrayDeque<>(lines);
            this.supplies = supplies;
            this.supplySpent = supplySpent;
        }

        /** All sales, villagers' included. */
        public int sales() {
            return sales;
        }

        /** Of {@link #sales()}, those to villagers. */
        public int villagerSales() {
            return villagerSales;
        }

        public long earned() {
            return earned;
        }

        /** Villagers' sales to the owner's buyback blocks. */
        public int supplies() {
            return supplies;
        }

        /** Money the owner paid for {@link #supplies()}. */
        public long supplySpent() {
            return supplySpent;
        }

        /** Newest last. */
        public List<Line> lines() {
            return List.copyOf(lines);
        }
    }

    static final Codec<NotificationsData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pending.CODEC)
        .xmap(NotificationsData::new, d -> d.pending);

    public static final SavedDataType<NotificationsData> TYPE = new SavedDataType<>(
        TraderyApi.id("notifications"), NotificationsData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Pending> pending = new HashMap<>();

    public NotificationsData() {
    }

    private NotificationsData(Map<UUID, Pending> loaded) {
        pending.putAll(loaded);
    }

    public static NotificationsData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(UUID owner, Line line, boolean sale, long earned) {
        add(owner, line, sale, earned, false);
    }

    /** @param villager the sale was to a villager (counted in the summary separately too) */
    public void add(UUID owner, Line line, boolean sale, long earned, boolean villager) {
        Pending p = pending.computeIfAbsent(owner, k -> new Pending());
        if (sale) {
            p.sales = saturatedIncrement(p.sales);
            if (villager) {
                p.villagerSales = saturatedIncrement(p.villagerSales);
            }
            p.earned = saturatedAdd(p.earned, Math.max(0, earned));
        }
        p.lines.addLast(line);
        while (p.lines.size() > MAX_LINES) {
            p.lines.pollFirst();
        }
        setDirty();
    }

    /** A villager sold to the owner's buyback block for {@code spent}; counted in the summary's supplies. */
    public void addSupply(UUID owner, Line line, long spent) {
        Pending p = pending.computeIfAbsent(owner, k -> new Pending());
        p.supplies = saturatedIncrement(p.supplies);
        p.supplySpent = saturatedAdd(p.supplySpent, Math.max(0, spent));
        p.lines.addLast(line);
        while (p.lines.size() > MAX_LINES) {
            p.lines.pollFirst();
        }
        setDirty();
    }

    private static int saturatedIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    /** Removes and returns what waited for this owner. */
    public Optional<Pending> take(UUID owner) {
        Pending p = pending.remove(owner);
        if (p != null) {
            setDirty();
        }
        return Optional.ofNullable(p);
    }
}
