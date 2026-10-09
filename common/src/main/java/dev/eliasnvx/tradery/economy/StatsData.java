package dev.eliasnvx.tradery.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Money flow statistics for {@code /eco stats} and per-player daily income for the daily caps. Days are UTC dates.
 */
public final class StatsData extends SavedData {
    /** Days of history kept. */
    static final int KEEP_DAYS = 31;

    /** Totals of one day, minor units. */
    public static final class Day {
        static final Codec<Day> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("emitted", 0L).forGetter(d -> d.emitted),
            Codec.LONG.optionalFieldOf("burned", 0L).forGetter(d -> d.burned),
            Codec.LONG.optionalFieldOf("cashOut", 0L).forGetter(d -> d.cashOut),
            Codec.LONG.optionalFieldOf("cashIn", 0L).forGetter(d -> d.cashIn),
            Codec.LONG.optionalFieldOf("transactions", 0L).forGetter(d -> d.transactions)
        ).apply(i, Day::new));

        long emitted;
        long burned;
        long cashOut;
        long cashIn;
        long transactions;

        Day() {
        }

        private Day(long emitted, long burned, long cashOut, long cashIn, long transactions) {
            this.emitted = emitted;
            this.burned = burned;
            this.cashOut = cashOut;
            this.cashIn = cashIn;
            this.transactions = transactions;
        }

        /** Money created (ore, rewards, admin give, server account payments). */
        public long emitted() {
            return emitted;
        }

        /** Money destroyed (fees, taxes, admin take, payments to the server account). */
        public long burned() {
            return burned;
        }

        /** Balance turned into coin items. */
        public long cashOut() {
            return cashOut;
        }

        /** Coin items turned into balance. */
        public long cashIn() {
            return cashIn;
        }

        public long transactions() {
            return transactions;
        }
    }

    /** Income categories with a daily cap. */
    public enum Earning {
        ORE, REWARD
    }

    private record Earnings(String day, Map<UUID, Map<Earning, Long>> byPlayer) {
        static final Codec<Map<Earning, Long>> PER_PLAYER = Codec.unboundedMap(
            dev.eliasnvx.tradery.config.ConfigCodecs.enumCodec(Earning.class), Codec.LONG);
        static final Codec<Earnings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("day").forGetter(Earnings::day),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PER_PLAYER).fieldOf("players").forGetter(Earnings::byPlayer)
        ).apply(i, Earnings::new));
    }

    static final Codec<StatsData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Day.CODEC).optionalFieldOf("days", Map.of()).forGetter(d -> d.days),
        Codec.LONG.optionalFieldOf("cashOutstanding", 0L).forGetter(d -> d.cashOutstanding),
        Earnings.CODEC.optionalFieldOf("earnings").forGetter(d -> java.util.Optional.of(new Earnings(d.earningsDay, d.earnings)))
    ).apply(i, StatsData::new));

    public static final String NAME = "tradery_stats";
    public static final SavedData.Factory<StatsData> FACTORY = CodecSavedData.factory(NAME, CODEC, StatsData::new);

    private final TreeMap<String, Day> days = new TreeMap<>();
    private long cashOutstanding;
    private String earningsDay = "";
    private final Map<UUID, Map<Earning, Long>> earnings = new HashMap<>();

    public StatsData() {
    }

    private StatsData(Map<String, Day> days, long cashOutstanding, java.util.Optional<Earnings> earnings) {
        this.days.putAll(days);
        this.cashOutstanding = cashOutstanding;
        earnings.ifPresent(e -> {
            earningsDay = e.day();
            e.byPlayer().forEach((uuid, map) -> this.earnings.put(uuid, new HashMap<>(map)));
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return CodecSavedData.save(CODEC, this, tag, registries);
    }

    public static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    public Day day(String date) {
        return days.getOrDefault(date, new Day());
    }

    private Day todayForUpdate() {
        String today = today();
        Day day = days.computeIfAbsent(today, k -> new Day());
        while (days.size() > KEEP_DAYS) {
            days.pollFirstEntry();
        }
        setDirty();
        return day;
    }

    public void recordEmitted(long amount) {
        todayForUpdate().emitted += amount;
    }

    public void recordBurned(long amount) {
        todayForUpdate().burned += amount;
    }

    public void recordCashOut(long amount) {
        todayForUpdate().cashOut += amount;
        cashOutstanding += amount;
    }

    public void recordCashIn(long amount) {
        todayForUpdate().cashIn += amount;
        cashOutstanding = Math.max(0, cashOutstanding - amount);
    }

    public void recordTransaction() {
        todayForUpdate().transactions++;
    }

    /** Coins minted straight into the world as items (coin ore broken by a machine). */
    public void recordCoinsMinted(long amount) {
        todayForUpdate().emitted += amount;
        cashOutstanding += amount;
    }

    /** Estimated value of coin items in the world: withdrawn or minted, minus deposited. */
    public long cashOutstanding() {
        return cashOutstanding;
    }

    /** Income of a player today in a capped category. */
    public long earnedToday(UUID player, Earning category) {
        rollEarnings();
        Map<Earning, Long> map = earnings.get(player);
        return map == null ? 0 : map.getOrDefault(category, 0L);
    }

    public void addEarned(UUID player, Earning category, long amount) {
        rollEarnings();
        earnings.computeIfAbsent(player, k -> new HashMap<>()).merge(category, amount, Long::sum);
        setDirty();
    }

    private void rollEarnings() {
        String today = today();
        if (!today.equals(earningsDay)) {
            earningsDay = today;
            earnings.clear();
            setDirty();
        }
    }
}
