package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.TraderyApi;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side state of the Living Market ({@code tradery_villagers}): villagers' wallets, visit cooldowns and produce
 * stock (by villager UUID), and per village the demand for each item and the money spent today. Kept here and not on
 * the entities, so it works the same on every loader. Days are in-game days ({@link VillagerPurchases#today}), ticks
 * are the server's game time. Server thread only.
 */
public final class VillagerLedger extends SavedData {
    /** Entries untouched for this many in-game days are dropped (villagers that died unseen or left for good). */
    static final long FORGET_AFTER_DAYS = 30;
    /** {@link Wallet#stockDay} of a villager whose produce was never counted. */
    static final long NEVER = -1;

    /** A villager's money, cooldown and the produce it has to sell ({@link VillagerSupplies}). */
    static final class Wallet {
        static final Codec<Wallet> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(w -> w.balance),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(w -> w.day),
            Codec.LONG.optionalFieldOf("seen_day", 0L).forGetter(w -> w.seenDay),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(w -> w.cooldownUntil),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stock", Map.of()).forGetter(w -> w.stock),
            Codec.LONG.optionalFieldOf("stock_day", NEVER).forGetter(w -> w.stockDay)
        ).apply(i, Wallet::new));

        /** Minor units. */
        long balance;
        /** Day the wages were last paid. */
        long day;
        long seenDay;
        /** Game time until which the villager doesn't shop again. */
        long cooldownUntil;
        /** Produce by {@link VillagerSupplies.Product#key()}: items the villager can sell. */
        final Map<String, Integer> stock;
        /** Day the produce was last counted, {@link #NEVER} before the first count. */
        long stockDay;

        Wallet(long balance, long day, long seenDay, long cooldownUntil) {
            this(balance, day, seenDay, cooldownUntil, Map.of(), NEVER);
        }

        Wallet(long balance, long day, long seenDay, long cooldownUntil, Map<String, Integer> stock, long stockDay) {
            this.balance = balance;
            this.day = day;
            this.seenDay = seenDay;
            this.cooldownUntil = cooldownUntil;
            this.stock = new HashMap<>(stock);
            this.stockDay = stockDay;
        }
    }

    /** One village: demand per item (missing = 1.0) and today's spending. */
    static final class Village {
        static final Codec<Village> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("demand", Map.of()).forGetter(v -> v.demand),
            Codec.LONG.optionalFieldOf("demand_day", 0L).forGetter(v -> v.demandDay),
            Codec.LONG.optionalFieldOf("spent", 0L).forGetter(v -> v.spent),
            Codec.LONG.optionalFieldOf("spent_day", 0L).forGetter(v -> v.spentDay),
            Codec.LONG.optionalFieldOf("seen_day", 0L).forGetter(v -> v.seenDay)
        ).apply(i, Village::new));

        final Map<String, Double> demand;
        long demandDay;
        long spent;
        long spentDay;
        long seenDay;

        Village(Map<String, Double> demand, long demandDay, long spent, long spentDay, long seenDay) {
            this.demand = new HashMap<>(demand);
            this.demandDay = demandDay;
            this.spent = spent;
            this.spentDay = spentDay;
            this.seenDay = seenDay;
        }
    }

    static final Codec<VillagerLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Wallet.CODEC).optionalFieldOf("wallets", Map.of()).forGetter(d -> d.wallets),
        Codec.unboundedMap(Codec.STRING, Village.CODEC).optionalFieldOf("villages", Map.of()).forGetter(d -> d.villages)
    ).apply(i, VillagerLedger::new));

    public static final SavedDataType<VillagerLedger> TYPE = new SavedDataType<>(
        TraderyApi.id("villagers"), VillagerLedger::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Wallet> wallets = new HashMap<>();
    private final Map<String, Village> villages = new HashMap<>();

    public VillagerLedger() {
    }

    private VillagerLedger(Map<UUID, Wallet> wallets, Map<String, Village> villages) {
        this.wallets.putAll(wallets);
        this.villages.putAll(villages);
    }

    public static VillagerLedger get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // ------------------------------------------------------------------ wallets

    /**
     * The villager's money after paying the wages due: {@code wage} for each whole day since the last payment, up
     * to {@code cap}. A villager seen for the first time starts with one day's wage.
     */
    public long wallet(UUID villager, long today, long wage, long cap) {
        Wallet wallet = wallets.get(villager);
        if (wallet == null) {
            wallet = new Wallet(Math.min(wage, cap), today, today, 0);
            wallets.put(villager, wallet);
            setDirty();
            return wallet.balance;
        }
        if (today > wallet.day) {
            long due = saturatedMultiply(wage, today - wallet.day);
            if (wallet.balance < cap) {
                wallet.balance = Math.min(cap, saturatedAdd(wallet.balance, due));
            }
            wallet.day = today;
            setDirty();
        } else if (today < wallet.day) {
            // The clock was set back: wages restart from today, nothing is paid twice
            wallet.day = today;
            setDirty();
        }
        touch(wallet, today);
        return wallet.balance;
    }

    /** Sets a wallet (tests and admin tools); wages are next due tomorrow. */
    public void setWallet(UUID villager, long balance, long today) {
        Wallet wallet = wallets.computeIfAbsent(villager, k -> new Wallet(0, today, today, 0));
        wallet.balance = Math.max(0, balance);
        wallet.day = today;
        wallet.seenDay = today;
        setDirty();
    }

    /** Takes a purchase from the wallet (never below zero). */
    void pay(UUID villager, long amount) {
        Wallet wallet = wallets.get(villager);
        if (wallet != null) {
            wallet.balance = Math.max(0, wallet.balance - amount);
            setDirty();
        }
    }

    /** Money the villager earned: paid by a player in its trade window, or for its produce. No wallet cap. */
    void receive(UUID villager, long amount, long today) {
        Wallet wallet = wallets.computeIfAbsent(villager, k -> new Wallet(0, today, today, 0));
        wallet.balance = saturatedAdd(wallet.balance, amount);
        touch(wallet, today);
        setDirty();
    }

    /** The villager may be invited again (game time). */
    public boolean onCooldown(UUID villager, long gameTime) {
        Wallet wallet = wallets.get(villager);
        return wallet != null && gameTime < wallet.cooldownUntil;
    }

    /** After a visit (bought or not): no new visit until {@code until} (game time). */
    public void setCooldown(UUID villager, long until, long today) {
        Wallet wallet = wallets.computeIfAbsent(villager, k -> new Wallet(0, today, today, 0));
        wallet.cooldownUntil = until;
        touch(wallet, today);
        setDirty();
    }

    /** The villager died: its wallet goes with it. */
    public void forget(UUID villager) {
        if (wallets.remove(villager) != null) {
            setDirty();
        }
    }

    public boolean knows(UUID villager) {
        return wallets.containsKey(villager);
    }

    private static void touch(Wallet wallet, long today) {
        wallet.seenDay = today;
    }

    // ------------------------------------------------------------------ produce (suppliers)

    /**
     * Counts the villager's produce up to {@code today}: each product gets its daily amount for every whole day since
     * the last count, piling up to {@code stockDays} days of it. The first count gives one day's produce (like the
     * first wallet gets one day's wage); products the villager no longer makes are dropped. The clock set back:
     * production restarts from today, nothing is produced twice.
     *
     * @param daily product key → items per day (already scaled by the profession level)
     */
    public void produce(UUID villager, long today, Map<String, Integer> daily, int stockDays) {
        Wallet wallet = wallets.computeIfAbsent(villager, k -> new Wallet(0, today, today, 0));
        boolean changed = wallet.stock.keySet().removeIf(key -> !daily.containsKey(key));
        if (wallet.stockDay == NEVER) {
            for (Map.Entry<String, Integer> entry : daily.entrySet()) {
                wallet.stock.put(entry.getKey(), Math.max(0, entry.getValue()));
            }
            wallet.stockDay = today;
            changed = true;
        } else if (today > wallet.stockDay) {
            long days = Math.min(today - wallet.stockDay, stockDays);
            for (Map.Entry<String, Integer> entry : daily.entrySet()) {
                long perDay = Math.max(0, entry.getValue());
                long cap = perDay * stockDays;
                long current = wallet.stock.getOrDefault(entry.getKey(), 0);
                if (current < cap) {
                    wallet.stock.put(entry.getKey(), (int) Math.min(cap, current + perDay * days));
                }
            }
            wallet.stockDay = today;
            changed = true;
        } else if (today < wallet.stockDay) {
            wallet.stockDay = today;
            changed = true;
        }
        touch(wallet, today);
        if (changed) {
            setDirty();
        }
    }

    /** Items of this product the villager has to sell (as of its last {@link #produce count}). */
    public int stock(UUID villager, String product) {
        Wallet wallet = wallets.get(villager);
        return wallet == null ? 0 : wallet.stock.getOrDefault(product, 0);
    }

    /** The villager sold {@code count} items of this product (never below zero). */
    void takeStock(UUID villager, String product, int count) {
        Wallet wallet = wallets.get(villager);
        if (wallet != null && count > 0) {
            wallet.stock.computeIfPresent(product, (key, current) -> Math.max(0, current - count));
            setDirty();
        }
    }

    /** Sets a villager's produce of one product, counted today (tests and admin tools). */
    public void setStock(UUID villager, String product, int count, long today) {
        Wallet wallet = wallets.computeIfAbsent(villager, k -> new Wallet(0, today, today, 0));
        wallet.stock.put(product, Math.max(0, count));
        wallet.stockDay = today;
        touch(wallet, today);
        setDirty();
    }

    // ------------------------------------------------------------------ villages

    private Village village(String key, long today) {
        Village village = villages.computeIfAbsent(key, k -> new Village(Map.of(), today, 0, today, today));
        village.seenDay = today;
        return village;
    }

    /** Demand of the village for {@code item} today, 0..1, after the recovery since it was last updated. */
    public double demand(String villageKey, String item, long today, double recoveryPerDay) {
        Village village = villages.get(villageKey);
        if (village == null) {
            return 1.0;
        }
        recover(village, today, recoveryPerDay);
        return village.demand.getOrDefault(item, 1.0);
    }

    /** {@code lots} were sold to the village: its demand for {@code item} drops by {@code dropPerLot} each. */
    void lowerDemand(String villageKey, String item, int lots, long today, double dropPerLot, double recoveryPerDay) {
        Village village = village(villageKey, today);
        recover(village, today, recoveryPerDay);
        double next = Math.max(0.0, village.demand.getOrDefault(item, 1.0) - lots * dropPerLot);
        village.demand.put(item, next);
        setDirty();
    }

    /** Sets a demand (tests). */
    public void setDemand(String villageKey, String item, double demand, long today) {
        Village village = village(villageKey, today);
        village.demandDay = today;
        village.demand.put(item, Math.max(0.0, Math.min(1.0, demand)));
        setDirty();
    }

    private void recover(Village village, long today, double recoveryPerDay) {
        if (today == village.demandDay) {
            return;
        }
        if (today > village.demandDay) {
            double recovered = (today - village.demandDay) * recoveryPerDay;
            Iterator<Map.Entry<String, Double>> it = village.demand.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Double> entry = it.next();
                double next = entry.getValue() + recovered;
                if (next >= 1.0) {
                    it.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }
        village.demandDay = today;
        setDirty();
    }

    /** Money the village's villagers spent today. */
    public long spentToday(String villageKey, long today) {
        Village village = villages.get(villageKey);
        return village == null || village.spentDay != today ? 0 : village.spent;
    }

    void addSpent(String villageKey, long amount, long today) {
        Village village = village(villageKey, today);
        if (village.spentDay != today) {
            village.spentDay = today;
            village.spent = 0;
        }
        village.spent = saturatedAdd(village.spent, amount);
        setDirty();
    }

    // ------------------------------------------------------------------ housekeeping

    /** Drops wallets and villages not seen for {@link #FORGET_AFTER_DAYS} days. */
    void prune(long today) {
        long before = today - FORGET_AFTER_DAYS;
        boolean changed = wallets.values().removeIf(w -> w.seenDay < before);
        changed |= villages.values().removeIf(v -> v.seenDay < before);
        if (changed) {
            setDirty();
        }
    }

    int walletCount() {
        return wallets.size();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    private static long saturatedMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
