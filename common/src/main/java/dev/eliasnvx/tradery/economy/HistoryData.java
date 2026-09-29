package dev.eliasnvx.tradery.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.TraderyApi;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The last transactions of each player, newest first, for {@code /tradery history}. */
public final class HistoryData extends SavedData {
    /**
     * One line of a player's history.
     *
     * @param time         epoch millis
     * @param delta        change of the player's balance, minor units
     * @param balance      balance after it
     * @param reason       reason type
     * @param counterparty the other side's display name, or empty
     */
    public record Entry(long time, long delta, long balance, Identifier reason, String counterparty) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("t").forGetter(Entry::time),
            Codec.LONG.fieldOf("d").forGetter(Entry::delta),
            Codec.LONG.fieldOf("b").forGetter(Entry::balance),
            Identifier.CODEC.fieldOf("r").forGetter(Entry::reason),
            Codec.STRING.optionalFieldOf("c", "").forGetter(Entry::counterparty)
        ).apply(i, Entry::new));
    }

    static final Codec<HistoryData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC.listOf())
        .xmap(HistoryData::new, HistoryData::snapshot);

    public static final SavedDataType<HistoryData> TYPE = new SavedDataType<>(
        TraderyApi.id("history"), HistoryData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Deque<Entry>> entries = new HashMap<>();

    public HistoryData() {
    }

    private HistoryData(Map<UUID, List<Entry>> loaded) {
        loaded.forEach((uuid, list) -> entries.put(uuid, new ArrayDeque<>(list)));
    }

    private Map<UUID, List<Entry>> snapshot() {
        Map<UUID, List<Entry>> out = new HashMap<>();
        entries.forEach((uuid, deque) -> out.put(uuid, new ArrayList<>(deque)));
        return out;
    }

    /** Adds an entry, dropping the oldest beyond {@code limit} ({@code 0} keeps nothing). */
    public void add(UUID player, Entry entry, int limit) {
        if (limit <= 0) {
            if (entries.remove(player) != null) {
                setDirty();
            }
            return;
        }
        Deque<Entry> deque = entries.computeIfAbsent(player, k -> new ArrayDeque<>());
        deque.addFirst(entry);
        while (deque.size() > limit) {
            deque.removeLast();
        }
        setDirty();
    }

    /** Newest first. */
    public List<Entry> get(UUID player) {
        Deque<Entry> deque = entries.get(player);
        return deque == null ? List.of() : List.copyOf(deque);
    }
}
