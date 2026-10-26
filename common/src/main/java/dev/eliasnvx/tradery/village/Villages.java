package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The village economy's core (SPEC "Деревня-экономика"): which village a villager or a block belongs to, the census,
 * prosperity and levels, the treasury. Features (board, orders, projects, suppliers, caravans, market days, rent,
 * raids) build on these calls. Daily changes are applied lazily by {@link #touch}, for the days passed since the
 * village was last touched. Server thread only.
 */
public final class Villages {
    private static final EntityTypeTest<Entity, Villager> VILLAGERS = EntityTypeTest.forClass(Villager.class);
    /** Share of prosperity lost when a villager of the village dies, and when a player killed it. */
    static final double DEATH_LOSS = 0.01;
    static final double MURDER_LOSS = 0.03;
    /** Prosperity points per major unit of money, by source. */
    static final double POINTS_PURCHASE = 1.0;
    static final double POINTS_WINDOW_TRADE = 0.5;

    private Villages() {
    }

    static VillagesConfig config() {
        return TraderyConfig.server().villages();
    }

    // ------------------------------------------------------------------ which village

    /** Village key of a bell: the same string {@link VillagerPurchases#villageKey} gives its villagers. */
    public static String key(ResourceKey<Level> dimension, BlockPos bell) {
        return "bell:" + dimension.identifier() + ":" + bell.getX() + "," + bell.getY() + "," + bell.getZ();
    }

    /** The bell of the villager's village, if it has one. */
    public static Optional<GlobalPos> bellOf(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
    }

    /** The villager's village (created when first met), or null for a villager without a bell. */
    public static @Nullable VillageState of(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        GlobalPos bell = bellOf(villager).orElse(null);
        return bell == null ? null : at(level.getServer(), bell.dimension(), bell.pos());
    }

    /** The village of this bell (created when first met). */
    public static VillageState at(MinecraftServer server, ResourceKey<Level> dimension, BlockPos bell) {
        long today = VillagerPurchases.today(server);
        VillageEconomy economy = VillageEconomy.get(server);
        VillageState state = economy.getOrCreate(key(dimension, bell), () -> VillageState.create(dimension, bell, today));
        touch(server, state);
        return state;
    }

    /** The nearest bell within {@code radius} blocks (a village board, a stall), if there is one. */
    public static Optional<BlockPos> nearestBell(ServerLevel level, BlockPos pos, int radius) {
        return level.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING), pos, radius, PoiManager.Occupancy.ANY);
    }

    /** The village a block at {@code pos} belongs to: nearest bell within {@code villages.boardRadius}. */
    public static @Nullable VillageState near(ServerLevel level, BlockPos pos) {
        return nearestBell(level, pos, config().boardRadius()).map(bell -> at(level.getServer(), level.dimension(), bell)).orElse(null);
    }

    public static @Nullable VillageState byKey(MinecraftServer server, String key) {
        VillageState state = VillageEconomy.get(server).get(key);
        if (state != null) {
            touch(server, state);
        }
        return state;
    }

    /** "Plains village (120, -45)", or the name players gave it. */
    public static Component displayName(VillageState village) {
        if (!village.name.isEmpty()) {
            return Component.literal(village.name);
        }
        String type = village.census.type().isEmpty() ? "minecraft:plains" : village.census.type();
        String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        return Component.translatableWithFallback("tradery.village.name", "%s village (%s, %s)",
            Component.translatableWithFallback("tradery.village.type." + path, path), village.bell.getX(), village.bell.getZ());
    }

    // ------------------------------------------------------------------ days

    /**
     * Applies what the days since the last touch brought: prosperity decay and treasury income. Cheap when nothing
     * is due; called by every lookup.
     */
    public static void touch(MinecraftServer server, VillageState village) {
        long today = VillagerPurchases.today(server);
        VillagesConfig config = config();
        boolean changed = false;
        if (today != village.seenDay) {
            village.seenDay = today;
            changed = true;
        }
        if (today > village.prosperityDay) {
            long days = Math.min(365, today - village.prosperityDay);
            village.prosperity *= Math.pow(1 - config.prosperityDecayPerDay(), days);
            village.prosperityDay = today;
            changed = true;
        } else if (today < village.prosperityDay) {
            village.prosperityDay = today;
            changed = true;
        }
        if (today > village.treasuryDay) {
            long days = today - village.treasuryDay;
            long income = dailyIncome(village);
            long cap = treasuryCap(village);
            long due = income > 0 ? saturatedMultiply(income, days) : 0;
            // While a level building is pending, a share of the income goes to the building fund
            if (due > 0 && VillageBuildings.savingUp(village)) {
                long saved = (long) (due * config.buildings().savingsShare());
                village.savings = saturatedAdd(village.savings, saved);
                due -= saved;
            }
            if (village.treasury < cap && due > 0) {
                long before = village.treasury;
                village.treasury = Math.min(cap, saturatedAdd(village.treasury, due));
                recordFlow(village, today, village.treasury - before, 0);
            }
            village.treasuryDay = today;
            changed = true;
        } else if (today < village.treasuryDay) {
            village.treasuryDay = today;
            changed = true;
        }
        if (changed) {
            VillageEconomy.get(server).setDirty();
        }
    }

    // ------------------------------------------------------------------ levels

    /** Village level, 1 (Hamlet) .. number of thresholds. */
    public static int level(VillageState village) {
        List<Double> thresholds = config().levels().thresholds();
        int level = 1;
        for (int i = 1; i < thresholds.size(); i++) {
            if (village.prosperity >= thresholds.get(i)) {
                level = i + 1;
            }
        }
        return level;
    }

    /** Prosperity needed for the next level, or -1 at the top. */
    public static double nextThreshold(VillageState village) {
        List<Double> thresholds = config().levels().thresholds();
        int level = level(village);
        return level < thresholds.size() ? thresholds.get(level) : -1;
    }

    /** {@code list[level - 1]}, clamped to the list. */
    static <T> T byLevel(List<T> list, int level) {
        return list.get(Math.max(0, Math.min(list.size() - 1, level - 1)));
    }

    /** "Hamlet", "Village", "Town", "Trade city" (lang {@code tradery.village.level.N}). */
    public static Component levelName(int level) {
        String[] fallback = {"Hamlet", "Village", "Town", "Trade city"};
        return Component.translatableWithFallback("tradery.village.level." + level, fallback[Math.max(0, Math.min(fallback.length - 1, level - 1))]);
    }

    /** Villagers' wages and the treasury income are multiplied by this: the level's multiplier and the projects'. */
    public static double wageFactor(VillageState village) {
        return byLevel(config().levels().wageMultiplier(), level(village)) * VillageProjects.effects(village).wageMultiplier();
    }

    /** {@code villagers.dailyCapPerVillage} is multiplied by this. */
    public static double spendCapFactor(VillageState village) {
        return byLevel(config().levels().spendCapMultiplier(), level(village));
    }

    // ------------------------------------------------------------------ prosperity

    /** Adds prosperity points (negative ones take away, never below zero). */
    public static void addProsperity(MinecraftServer server, VillageState village, double points) {
        touch(server, village);
        village.prosperity = Math.max(0, village.prosperity + points);
        VillageEconomy.get(server).setDirty();
    }

    /** Multiplies prosperity (a lost raid: 0.7). */
    public static void scaleProsperity(MinecraftServer server, VillageState village, double factor) {
        touch(server, village);
        village.prosperity = Math.max(0, village.prosperity * factor);
        VillageEconomy.get(server).setDirty();
    }

    /** Prosperity points for an amount of money (minor units) at {@code pointsPerUnit} per major unit. */
    static double points(long amount, double pointsPerUnit) {
        int decimals = EconomyService.INSTANCE.isReady() ? EconomyService.INSTANCE.defaultCurrency().decimals() : 2;
        return amount / Math.pow(10, decimals) * pointsPerUnit;
    }

    // ------------------------------------------------------------------ treasury

    /** Treasury income per day: per villager, times the level and projects. */
    public static long dailyIncome(VillageState village) {
        BigDecimal perVillager = config().treasuryPerVillager();
        BigDecimal income = perVillager.multiply(BigDecimal.valueOf(village.census.population())).multiply(BigDecimal.valueOf(wageFactor(village)));
        return VillagerPurchases.toMinor(income);
    }

    /** The daily income fills the treasury up to this ({@code villages.treasuryDays} days, more with projects). */
    public static long treasuryCap(VillageState village) {
        double factor = config().treasuryDays() * (1 + VillageProjects.effects(village).treasuryCap());
        return (long) Math.min(Long.MAX_VALUE / 2.0, dailyIncome(village) * factor);
    }

    /** Puts money in the treasury (rent, trade tax, donations, money coming back), above the income cap. */
    public static void addToTreasury(MinecraftServer server, VillageState village, long amount) {
        if (amount <= 0) {
            return;
        }
        touch(server, village);
        long before = village.treasury;
        village.treasury = saturatedAdd(village.treasury, amount);
        recordFlow(village, VillagerPurchases.today(server), village.treasury - before, 0);
        VillageEconomy.get(server).setDirty();
    }

    /** Takes {@code amount} from the treasury if it's all there. */
    public static boolean takeFromTreasury(MinecraftServer server, VillageState village, long amount) {
        touch(server, village);
        if (amount < 0 || village.treasury < amount) {
            return false;
        }
        village.treasury -= amount;
        recordFlow(village, VillagerPurchases.today(server), 0, amount);
        VillageEconomy.get(server).setDirty();
        return true;
    }

    /** Takes a share of the treasury (a lost raid), returns what was taken. */
    public static long loseTreasury(MinecraftServer server, VillageState village, double share) {
        touch(server, village);
        long lost = (long) (village.treasury * Math.max(0, Math.min(1, share)));
        village.treasury -= lost;
        recordFlow(village, VillagerPurchases.today(server), 0, lost);
        VillageEconomy.get(server).setDirty();
        return lost;
    }

    /**
     * Writes a movement of money into the treasury's history ({@link VillageState#history}): folded into the day's
     * entry, a new entry for a new day, the oldest dropped beyond {@link VillageState#HISTORY_DAYS}. A day before the
     * latest entry (the clock set back) is folded into the latest. Nothing for zero amounts. The caller marks the
     * saved data dirty.
     */
    static void recordFlow(VillageState village, long day, long in, long out) {
        if (in <= 0 && out <= 0) {
            return;
        }
        List<VillageState.DayFlow> history = village.history;
        VillageState.DayFlow last = history.isEmpty() ? null : history.getLast();
        if (last != null && day <= last.day()) {
            history.set(history.size() - 1, new VillageState.DayFlow(last.day(), saturatedAdd(last.in(), Math.max(0, in)),
                saturatedAdd(last.out(), Math.max(0, out))));
            return;
        }
        history.add(new VillageState.DayFlow(day, Math.max(0, in), Math.max(0, out)));
        while (history.size() > VillageState.HISTORY_DAYS) {
            history.removeFirst();
        }
    }

    /** Writes a day's movement into the treasury's history directly (tests and admin tools). */
    public static void recordFlow(MinecraftServer server, VillageState village, long day, long in, long out) {
        recordFlow(village, day, in, out);
        VillageEconomy.get(server).setDirty();
    }

    // ------------------------------------------------------------------ events

    /** Restoration orders are posted for the next {@code days} days (after a raid); the board reads it. */
    public static void startRestoration(MinecraftServer server, VillageState village, int days) {
        long today = VillagerPurchases.today(server);
        VillageState.Events e = village.events;
        village.events = new VillageState.Events(e.ordersDay(), e.nextOrderId(), e.nextMarketDay(), e.caravanDay(),
            Math.max(e.restorationUntil(), today + days), e.raidActive());
        VillageEconomy.get(server).setDirty();
    }

    /** Restoration orders are due today. */
    public static boolean restorationDue(MinecraftServer server, VillageState village) {
        return VillagerPurchases.today(server) < village.events.restorationUntil();
    }

    /** Replaces the village's event dates (features that own a date: orders, market days, caravans, raids). */
    public static void setEvents(MinecraftServer server, VillageState village, VillageState.Events events) {
        village.events = events;
        VillageEconomy.get(server).setDirty();
    }

    // ------------------------------------------------------------------ census

    /** Level tick: the census of loaded villages every {@code villages.censusIntervalTicks}. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % config().censusIntervalTicks() != 0) {
            return;
        }
        census(level);
    }

    /** Counts the loaded villagers of every bell in this level and updates the villages. */
    public static void census(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        Map<BlockPos, Tally> tallies = new HashMap<>();
        for (Villager villager : level.getEntities(VILLAGERS, v -> v.isAlive() && !v.isBaby())) {
            GlobalPos bell = bellOf(villager).orElse(null);
            if (bell == null || bell.dimension() != level.dimension()) {
                continue;
            }
            tallies.computeIfAbsent(bell.pos(), k -> new Tally()).add(villager);
        }
        VillageEconomy economy = VillageEconomy.get(server);
        for (Map.Entry<BlockPos, Tally> entry : tallies.entrySet()) {
            VillageState village = at(server, level.dimension(), entry.getKey());
            VillageState.Census census = entry.getValue().census(today);
            if (!census.equals(village.census)) {
                village.census = census;
                economy.setDirty();
            }
        }
        if (level.dimension() == Level.OVERWORLD && level.getGameTime() % (config().censusIntervalTicks() * 20L) == 0) {
            economy.prune(today);
        }
    }

    /** Villagers of one bell, counted. */
    private static final class Tally {
        int population;
        int employed;
        final Map<String, Integer> professions = new HashMap<>();
        final Map<String, Integer> types = new HashMap<>();

        void add(Villager villager) {
            population++;
            if (VillagerPurchases.hasProfession(villager)) {
                employed++;
                villager.getVillagerData().profession().unwrapKey()
                    .ifPresent(key -> professions.merge(key.identifier().toString(), 1, Integer::sum));
            }
            villager.getVillagerData().type().unwrapKey().ifPresent(key -> types.merge(key.identifier().toString(), 1, Integer::sum));
        }

        VillageState.Census census(long day) {
            String type = types.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse("");
            return new VillageState.Census(population, employed, Map.copyOf(professions), type, day);
        }
    }

    // ------------------------------------------------------------------ hooks from other features

    /** A villager bought at a player's stall for {@code amount} (minor units). */
    public static void onVillagerBought(Villager villager, long amount) {
        VillageState village = of(villager);
        if (village != null && villager.level() instanceof ServerLevel level) {
            addProsperity(level.getServer(), village, points(amount, POINTS_PURCHASE));
        }
    }

    /**
     * A trade in a villager's money window: the player paid {@code paid} and got {@code received} (minor units).
     * Returns the trade tax taken from {@code paid} for the treasury (the villager's wallet gets the rest).
     */
    public static long onWindowTrade(Villager villager, long paid, long received) {
        VillageState village = of(villager);
        if (village == null || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        MinecraftServer server = level.getServer();
        addProsperity(server, village, points(paid + received, POINTS_WINDOW_TRADE));
        long tax = paid > 0 ? Money.percentOf(paid, config().tradeTaxPercent()) : 0;
        addToTreasury(server, village, tax);
        return tax;
    }

    /** A villager of the village died; worse if a player killed it. */
    public static void onVillagerDeath(Villager villager, @Nullable Entity killer) {
        VillageState village = of(villager);
        if (village != null && villager.level() instanceof ServerLevel level) {
            scaleProsperity(level.getServer(), village, 1 - (killer instanceof Player ? MURDER_LOSS : DEATH_LOSS));
        }
    }

    // ------------------------------------------------------------------ tests

    /** Moves the village's last daily update {@code days} back, as if it hadn't been touched since (tests). */
    public static void backdate(MinecraftServer server, VillageState village, int days) {
        village.prosperityDay -= days;
        village.treasuryDay -= days;
        VillageEconomy.get(server).setDirty();
    }

    /** Sets the building fund directly (tests and admin tools). */
    public static void setSavings(MinecraftServer server, VillageState village, long savings) {
        village.savings = Math.max(0, savings);
        VillageEconomy.get(server).setDirty();
    }

    /** Sets prosperity and treasury directly (tests and admin tools). */
    public static void set(MinecraftServer server, VillageState village, double prosperity, long treasury) {
        touch(server, village);
        village.prosperity = Math.max(0, prosperity);
        village.treasury = Math.max(0, treasury);
        VillageEconomy.get(server).setDirty();
    }

    // ------------------------------------------------------------------ helpers

    static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    static long saturatedMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
