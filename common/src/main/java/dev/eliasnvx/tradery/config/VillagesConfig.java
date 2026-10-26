package dev.eliasnvx.tradery.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static dev.eliasnvx.tradery.config.ConfigCodecs.field;

/**
 * {@code villages} in server.json5: the village economy (SPEC "Деревня-экономика"). Grouped in subsections so each
 * codec stays within Mojang's field limit. Money values are in major units.
 */
public record VillagesConfig(int censusIntervalTicks, double prosperityDecayPerDay, BigDecimal treasuryPerVillager, int treasuryDays,
                             BigDecimal tradeTaxPercent, int boardRadius, Levels levels, Orders orders, Suppliers suppliers,
                             Caravans caravans, MarketDay marketDay, Rent rent, Threats threats, Buildings buildings, Festival festival) {

    /**
     * Village levels: prosperity thresholds and what each level gives. Every list has one entry per level, the
     * first level's threshold is 0.
     */
    public record Levels(List<Double> thresholds, List<Double> wageMultiplier, List<Integer> ordersPerDay, List<Integer> marketDayEvery,
                         List<Double> spendCapMultiplier, List<BigDecimal> rentPerDay) {
        static final Levels DEFAULT = new Levels(List.of(0.0, 300.0, 1500.0, 5000.0), List.of(1.0, 1.25, 1.5, 2.0), List.of(3, 4, 5, 6),
            List.of(7, 5, 4, 3), List.of(1.0, 1.5, 2.0, 3.0),
            List.of(BigDecimal.valueOf(5), BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(40)));
        static final Codec<Levels> CODEC = RecordCodecBuilder.<Levels>create(i -> i.group(
            field(Codec.doubleRange(0, 1.0e9).listOf(1, 16), "thresholds", DEFAULT.thresholds).forGetter(Levels::thresholds),
            field(Codec.doubleRange(0, 100).listOf(1, 16), "wageMultiplier", DEFAULT.wageMultiplier).forGetter(Levels::wageMultiplier),
            field(Codec.intRange(0, 20).listOf(1, 16), "ordersPerDay", DEFAULT.ordersPerDay).forGetter(Levels::ordersPerDay),
            field(Codec.intRange(1, 365).listOf(1, 16), "marketDayEvery", DEFAULT.marketDayEvery).forGetter(Levels::marketDayEvery),
            field(Codec.doubleRange(0, 100).listOf(1, 16), "spendCapMultiplier", DEFAULT.spendCapMultiplier).forGetter(Levels::spendCapMultiplier),
            field(ConfigCodecs.NON_NEGATIVE.listOf(1, 16), "rentPerDay", DEFAULT.rentPerDay).forGetter(Levels::rentPerDay)
        ).apply(i, Levels::new)).validate(Levels::check);

        private static DataResult<Levels> check(Levels levels) {
            int count = levels.thresholds.size();
            if (levels.thresholds.getFirst() != 0) {
                return DataResult.error(() -> "villages.levels.thresholds must start at 0");
            }
            for (int i = 1; i < count; i++) {
                if (levels.thresholds.get(i) <= levels.thresholds.get(i - 1)) {
                    return DataResult.error(() -> "villages.levels.thresholds must grow");
                }
            }
            if (levels.wageMultiplier.size() != count || levels.ordersPerDay.size() != count || levels.marketDayEvery.size() != count
                || levels.spendCapMultiplier.size() != count || levels.rentPerDay.size() != count) {
                return DataResult.error(() -> "every villages.levels list needs one entry per level (" + count + ")");
            }
            return DataResult.success(levels);
        }

        /** Number of levels. */
        public int count() {
            return thresholds.size();
        }
    }

    /** Village board orders. */
    public record Orders(double rewardMarkup, int lifetimeDays) {
        static final Orders DEFAULT = new Orders(1.3, 2);
        static final Codec<Orders> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.doubleRange(0, 10), "rewardMarkup", DEFAULT.rewardMarkup).forGetter(Orders::rewardMarkup),
            field(Codec.intRange(1, 30), "lifetimeDays", DEFAULT.lifetimeDays).forGetter(Orders::lifetimeDays)
        ).apply(i, Orders::new));
    }

    /** Villagers selling their produce to players' buyback blocks. */
    public record Suppliers(boolean enabled, double minPriceRatio, int maxLotsPerVisit, int stockDays) {
        static final Suppliers DEFAULT = new Suppliers(true, 0.8, 2, 2);
        static final Codec<Suppliers> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Suppliers::enabled),
            field(Codec.doubleRange(0, 10), "minPriceRatio", DEFAULT.minPriceRatio).forGetter(Suppliers::minPriceRatio),
            field(Codec.intRange(1, 64), "maxLotsPerVisit", DEFAULT.maxLotsPerVisit).forGetter(Suppliers::maxLotsPerVisit),
            field(Codec.intRange(1, 30), "stockDays", DEFAULT.stockDays).forGetter(Suppliers::stockDays)
        ).apply(i, Suppliers::new));
    }

    /** Caravans between villages. */
    public record Caravans(boolean enabled, int minLevel, int intervalDays, int maxDistance, int stayTicks) {
        static final Caravans DEFAULT = new Caravans(true, 3, 3, 1500, 12_000);
        static final Codec<Caravans> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Caravans::enabled),
            field(Codec.intRange(1, 16), "minLevel", DEFAULT.minLevel).forGetter(Caravans::minLevel),
            field(Codec.intRange(1, 365), "intervalDays", DEFAULT.intervalDays).forGetter(Caravans::intervalDays),
            field(Codec.intRange(64, 30_000), "maxDistance", DEFAULT.maxDistance).forGetter(Caravans::maxDistance),
            field(Codec.intRange(1200, 168_000), "stayTicks", DEFAULT.stayTicks).forGetter(Caravans::stayTicks)
        ).apply(i, Caravans::new));
    }

    /** Market days. */
    public record MarketDay(boolean enabled, double visitMultiplier) {
        static final MarketDay DEFAULT = new MarketDay(true, 3.0);
        static final Codec<MarketDay> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(MarketDay::enabled),
            field(Codec.doubleRange(1, 20), "visitMultiplier", DEFAULT.visitMultiplier).forGetter(MarketDay::visitMultiplier)
        ).apply(i, MarketDay::new));
    }

    /** Market licences (a trading place in a village). The price per day is in {@link Levels#rentPerDay}. */
    public record Rent(int days, double visitMultiplier) {
        static final Rent DEFAULT = new Rent(7, 1.5);
        static final Codec<Rent> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.intRange(1, 365), "days", DEFAULT.days).forGetter(Rent::days),
            field(Codec.doubleRange(1, 10), "visitMultiplier", DEFAULT.visitMultiplier).forGetter(Rent::visitMultiplier)
        ).apply(i, Rent::new));
    }

    /** Raids and deaths hurting the village economy. */
    /** Level buildings the village saves up for and builds itself. */
    public record Buildings(boolean enabled, double savingsShare) {
        static final Buildings DEFAULT = new Buildings(true, 0.5);
        static final Codec<Buildings> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Buildings::enabled),
            field(Codec.doubleRange(0, 1), "savingsShare", DEFAULT.savingsShare).forGetter(Buildings::savingsShare)
        ).apply(i, Buildings::new));
    }

    /** Festivals on market days of bigger villages. */
    public record Festival(boolean enabled, int minLevel, int walletBonusDays) {
        static final Festival DEFAULT = new Festival(true, 3, 1);
        static final Codec<Festival> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Festival::enabled),
            field(Codec.intRange(1, 16), "minLevel", DEFAULT.minLevel).forGetter(Festival::minLevel),
            field(Codec.intRange(0, 30), "walletBonusDays", DEFAULT.walletBonusDays).forGetter(Festival::walletBonusDays)
        ).apply(i, Festival::new));
    }

    public record Threats(double raidLossProsperity, double raidWinProsperity, double restorationMarkup) {
        static final Threats DEFAULT = new Threats(0.3, 100, 1.5);
        static final Codec<Threats> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.doubleRange(0, 1), "raidLossProsperity", DEFAULT.raidLossProsperity).forGetter(Threats::raidLossProsperity),
            field(Codec.doubleRange(0, 1.0e6), "raidWinProsperity", DEFAULT.raidWinProsperity).forGetter(Threats::raidWinProsperity),
            field(Codec.doubleRange(0, 10), "restorationMarkup", DEFAULT.restorationMarkup).forGetter(Threats::restorationMarkup)
        ).apply(i, Threats::new));
    }

    static final VillagesConfig DEFAULT = new VillagesConfig(600, 0.05, BigDecimal.ONE, 7, BigDecimal.TEN, 48, Levels.DEFAULT,
        Orders.DEFAULT, Suppliers.DEFAULT, Caravans.DEFAULT, MarketDay.DEFAULT, Rent.DEFAULT, Threats.DEFAULT, Buildings.DEFAULT, Festival.DEFAULT);

    static final Codec<VillagesConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        field(Codec.intRange(20, 24_000), "censusIntervalTicks", DEFAULT.censusIntervalTicks).forGetter(VillagesConfig::censusIntervalTicks),
        field(Codec.doubleRange(0, 1), "prosperityDecayPerDay", DEFAULT.prosperityDecayPerDay).forGetter(VillagesConfig::prosperityDecayPerDay),
        field(ConfigCodecs.NON_NEGATIVE, "treasuryPerVillager", DEFAULT.treasuryPerVillager).forGetter(VillagesConfig::treasuryPerVillager),
        field(Codec.intRange(1, 365), "treasuryDays", DEFAULT.treasuryDays).forGetter(VillagesConfig::treasuryDays),
        field(ConfigCodecs.PERCENT, "tradeTaxPercent", DEFAULT.tradeTaxPercent).forGetter(VillagesConfig::tradeTaxPercent),
        field(Codec.intRange(8, 128), "boardRadius", DEFAULT.boardRadius).forGetter(VillagesConfig::boardRadius),
        field(Levels.CODEC, "levels", DEFAULT.levels).forGetter(VillagesConfig::levels),
        field(Orders.CODEC, "orders", DEFAULT.orders).forGetter(VillagesConfig::orders),
        field(Suppliers.CODEC, "suppliers", DEFAULT.suppliers).forGetter(VillagesConfig::suppliers),
        field(Caravans.CODEC, "caravans", DEFAULT.caravans).forGetter(VillagesConfig::caravans),
        field(MarketDay.CODEC, "marketDay", DEFAULT.marketDay).forGetter(VillagesConfig::marketDay),
        field(Rent.CODEC, "rent", DEFAULT.rent).forGetter(VillagesConfig::rent),
        field(Threats.CODEC, "threats", DEFAULT.threats).forGetter(VillagesConfig::threats),
        field(Buildings.CODEC, "buildings", DEFAULT.buildings).forGetter(VillagesConfig::buildings),
        field(Festival.CODEC, "festival", DEFAULT.festival).forGetter(VillagesConfig::festival)
    ).apply(i, VillagesConfig::new));

    /** Comments for server.json5, keyed like {@link ServerConfig#COMMENTS}. */
    static final Map<String, String> COMMENTS = Map.ofEntries(
        Map.entry("villages", "Village economy: treasury, prosperity, board orders, projects, suppliers, caravans, market days"),
        Map.entry("villages.censusIntervalTicks", "How often loaded villages count their villagers"),
        Map.entry("villages.prosperityDecayPerDay", "Share of a village's prosperity lost every in-game day (0..1)"),
        Map.entry("villages.treasuryPerVillager", "Daily treasury income per villager (times the level's wage multiplier)"),
        Map.entry("villages.treasuryDays", "The daily income fills the treasury up to this many days of it"),
        Map.entry("villages.tradeTaxPercent", "Share of what players pay villagers in the trade window that goes to the treasury"),
        Map.entry("villages.boardRadius", "A village board belongs to the nearest bell within this many blocks"),
        Map.entry("villages.levels", "One entry per village level: Hamlet, Village, Town, Trade city"),
        Map.entry("villages.levels.thresholds", "Prosperity needed for each level (the first is 0)"),
        Map.entry("villages.levels.wageMultiplier", "Villagers' daily wages and the treasury income, times this"),
        Map.entry("villages.levels.ordersPerDay", "Orders the village posts on its board every day"),
        Map.entry("villages.levels.marketDayEvery", "A market day every this many days"),
        Map.entry("villages.levels.spendCapMultiplier", "villagers.dailyCapPerVillage times this"),
        Map.entry("villages.levels.rentPerDay", "Price of a trading place per day"),
        Map.entry("villages.orders.rewardMarkup", "Order reward = fair price x amount x this"),
        Map.entry("villages.orders.lifetimeDays", "Days an order stays on the board"),
        Map.entry("villages.suppliers", "Villagers sell what they produce to players' buyback blocks"),
        Map.entry("villages.suppliers.minPriceRatio", "They sell only at this share of the fair price or more"),
        Map.entry("villages.suppliers.stockDays", "A villager's produce piles up for at most this many days"),
        Map.entry("villages.caravans", "Prosperous villages send caravans to villages of another type"),
        Map.entry("villages.caravans.stayTicks", "How long a caravan stays at the market"),
        Map.entry("villages.marketDay.visitMultiplier", "Customers and suppliers come this many times more often on a market day"),
        Map.entry("villages.rent.days", "A trading place is rented for this many days"),
        Map.entry("villages.rent.visitMultiplier", "Villagers come to a licensed owner's blocks this many times more often"),
        Map.entry("villages.threats.raidLossProsperity", "Share of prosperity (and half the treasury and wallets) lost to a lost raid"),
        Map.entry("villages.threats.raidWinProsperity", "Prosperity gained for a won raid"),
        Map.entry("villages.threats.restorationMarkup", "Rewards of the restoration orders after a raid, times this"),
        Map.entry("villages.buildings", "Villages save up for and build a well, a watchtower and a trade hall as they level up (data pack tradery/village_buildings)"),
        Map.entry("villages.buildings.savingsShare", "Share of the daily treasury income put aside while a building is pending (0..1)"),
        Map.entry("villages.festival", "On market days of villages at minLevel and up, a festival at dusk: fireworks and a wallet bonus"),
        Map.entry("villages.festival.walletBonusDays", "Days of wages every villager gets on top at the festival")
    );
}
