package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Notifications for owners who were offline: a running summary (sales, money earned) that never overflows, and
 * the latest messages (at most {@link #MAX_LINES}).
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
            Codec.LONG.optionalFieldOf("earned", 0L).forGetter(p -> p.earned),
            Line.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(p -> new ArrayList<>(p.lines))
        ).apply(i, Pending::new));

        int sales;
        long earned;
        final Deque<Line> lines;

        Pending() {
            this(0, 0, List.of());
        }

        private Pending(int sales, long earned, List<Line> lines) {
            this.sales = sales;
            this.earned = earned;
            this.lines = new ArrayDeque<>(lines);
        }

        public int sales() {
            return sales;
        }

        public long earned() {
            return earned;
        }

        /** Newest last. */
        public List<Line> lines() {
            return List.copyOf(lines);
        }
    }

    static final Codec<NotificationsData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pending.CODEC)
        .optionalFieldOf("pending", Map.of())
        .xmap(NotificationsData::new, d -> d.pending).codec();

    /** File name in the overworld's data storage ({@code data/tradery_notifications.dat}). */
    public static final String NAME = "tradery_notifications";
    public static final SavedData.Factory<NotificationsData> FACTORY = CodecSavedData.factory(NAME, CODEC, NotificationsData::new);

    private final Map<UUID, Pending> pending = new HashMap<>();

    public NotificationsData() {
    }

    private NotificationsData(Map<UUID, Pending> loaded) {
        pending.putAll(loaded);
    }

    public static NotificationsData get(MinecraftServer server) {
        return CodecSavedData.get(server, FACTORY, NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return CodecSavedData.save(CODEC, this, tag, registries);
    }

    public void add(UUID owner, Line line, boolean sale, long earned) {
        Pending p = pending.computeIfAbsent(owner, k -> new Pending());
        if (sale) {
            p.sales++;
            p.earned += Math.max(0, earned);
        }
        p.lines.addLast(line);
        while (p.lines.size() > MAX_LINES) {
            p.lines.pollFirst();
        }
        setDirty();
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
