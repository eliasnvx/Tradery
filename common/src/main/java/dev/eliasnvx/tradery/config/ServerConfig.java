package dev.eliasnvx.tradery.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.TraderyApi;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static dev.eliasnvx.tradery.config.ConfigCodecs.field;

/**
 * {@code config/tradery/server.json5}: economy rules. Money values are in major units of the currency.
 */
public record ServerConfig(CurrencySection currency, EconomySection economy, PaySection pay, VendingSection vending,
                           OreSection ore, HistorySection history, LogSection log, VillagersSection villagers,
                           MarketSection market, VillagerTradingSection villagerTrading, VillagesConfig villages) {

    public record CurrencySection(ResourceLocation id, String name, String symbol, int decimals, String thousandsSeparator) {
        static final CurrencySection DEFAULT = new CurrencySection(TraderyApi.id("coin"), "Coins", "₮", 2, " ");
        static final Codec<CurrencySection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ResourceLocation.CODEC, "id", DEFAULT.id).forGetter(CurrencySection::id),
            field(ConfigCodecs.string(1, 32), "name", DEFAULT.name).forGetter(CurrencySection::name),
            field(ConfigCodecs.string(0, 8), "symbol", DEFAULT.symbol).forGetter(CurrencySection::symbol),
            field(Codec.intRange(0, 6), "decimals", DEFAULT.decimals).forGetter(CurrencySection::decimals),
            field(ConfigCodecs.string(0, 3), "thousandsSeparator", DEFAULT.thousandsSeparator).forGetter(CurrencySection::thousandsSeparator)
        ).apply(i, CurrencySection::new));
    }

    public record EconomySection(BigDecimal startingBalance, BigDecimal maxBalance) {
        static final EconomySection DEFAULT = new EconomySection(BigDecimal.valueOf(100), BigDecimal.valueOf(1_000_000_000));
        static final Codec<EconomySection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ConfigCodecs.NON_NEGATIVE, "startingBalance", DEFAULT.startingBalance).forGetter(EconomySection::startingBalance),
            field(ConfigCodecs.NON_NEGATIVE, "maxBalance", DEFAULT.maxBalance).forGetter(EconomySection::maxBalance)
        ).apply(i, EconomySection::new));
    }

    public record PaySection(boolean enabled, BigDecimal taxPercent, BigDecimal minAmount) {
        static final PaySection DEFAULT = new PaySection(true, BigDecimal.ZERO, BigDecimal.ONE);
        static final Codec<PaySection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(PaySection::enabled),
            field(ConfigCodecs.PERCENT, "taxPercent", DEFAULT.taxPercent).forGetter(PaySection::taxPercent),
            field(ConfigCodecs.NON_NEGATIVE, "minAmount", DEFAULT.minAmount).forGetter(PaySection::minAmount)
        ).apply(i, PaySection::new));
    }

    public record VendingSection(BigDecimal feePercent, int maxPerPlayer, List<ResourceLocation> itemBlacklist,
                                 List<ResourceLocation> facadeBlacklist, DisplayAnimation defaultAnimation) {
        static final VendingSection DEFAULT = new VendingSection(BigDecimal.valueOf(2), 0,
            List.of(new ResourceLocation("bedrock")), List.of(), DisplayAnimation.SPIN_BOB);
        static final Codec<VendingSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ConfigCodecs.PERCENT, "feePercent", DEFAULT.feePercent).forGetter(VendingSection::feePercent),
            field(Codec.intRange(0, 100_000), "maxPerPlayer", DEFAULT.maxPerPlayer).forGetter(VendingSection::maxPerPlayer),
            field(ResourceLocation.CODEC.listOf(), "itemBlacklist", DEFAULT.itemBlacklist).forGetter(VendingSection::itemBlacklist),
            field(ResourceLocation.CODEC.listOf(), "facadeBlacklist", DEFAULT.facadeBlacklist).forGetter(VendingSection::facadeBlacklist),
            field(ConfigCodecs.enumCodec(DisplayAnimation.class), "defaultAnimation", DEFAULT.defaultAnimation).forGetter(VendingSection::defaultAnimation)
        ).apply(i, VendingSection::new));
    }

    public record CoinValues(BigDecimal copper, BigDecimal silver, BigDecimal gold) {
        static final CoinValues DEFAULT = new CoinValues(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(100));
        static final Codec<CoinValues> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ConfigCodecs.NON_NEGATIVE, "copper", DEFAULT.copper).forGetter(CoinValues::copper),
            field(ConfigCodecs.NON_NEGATIVE, "silver", DEFAULT.silver).forGetter(CoinValues::silver),
            field(ConfigCodecs.NON_NEGATIVE, "gold", DEFAULT.gold).forGetter(CoinValues::gold)
        ).apply(i, CoinValues::new));
    }

    public record InflationDamping(boolean enabled, BigDecimal targetSupply, double minFactor) {
        static final InflationDamping DEFAULT = new InflationDamping(false, BigDecimal.valueOf(1_000_000), 0.25);
        static final Codec<InflationDamping> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(InflationDamping::enabled),
            field(ConfigCodecs.NON_NEGATIVE, "targetSupply", DEFAULT.targetSupply).forGetter(InflationDamping::targetSupply),
            field(Codec.doubleRange(0, 1), "minFactor", DEFAULT.minFactor).forGetter(InflationDamping::minFactor)
        ).apply(i, InflationDamping::new));
    }

    public record OreSection(boolean enabled, boolean directToBalance, CoinValues coinValues, BigDecimal dailyCap,
                             InflationDamping inflationDamping) {
        static final OreSection DEFAULT = new OreSection(true, true, CoinValues.DEFAULT, BigDecimal.ZERO, InflationDamping.DEFAULT);
        static final Codec<OreSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(OreSection::enabled),
            field(Codec.BOOL, "directToBalance", DEFAULT.directToBalance).forGetter(OreSection::directToBalance),
            field(CoinValues.CODEC, "coinValues", DEFAULT.coinValues).forGetter(OreSection::coinValues),
            field(ConfigCodecs.NON_NEGATIVE, "dailyCap", DEFAULT.dailyCap).forGetter(OreSection::dailyCap),
            field(InflationDamping.CODEC, "inflationDamping", DEFAULT.inflationDamping).forGetter(OreSection::inflationDamping)
        ).apply(i, OreSection::new));
    }

    public record HistorySection(int perPlayer) {
        static final HistorySection DEFAULT = new HistorySection(20);
        static final Codec<HistorySection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.intRange(0, 500), "perPlayer", DEFAULT.perPlayer).forGetter(HistorySection::perPlayer)
        ).apply(i, HistorySection::new));
    }

    public record LogSection(boolean enabled, int retentionDays) {
        static final LogSection DEFAULT = new LogSection(true, 30);
        static final Codec<LogSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(LogSection::enabled),
            field(Codec.intRange(0, 3650), "retentionDays", DEFAULT.retentionDays).forGetter(LogSection::retentionDays)
        ).apply(i, LogSection::new));
    }

    /** Daily wage of a villager by profession level, in major units. */
    public record Wages(BigDecimal none, BigDecimal novice, BigDecimal apprentice, BigDecimal journeyman, BigDecimal expert,
                        BigDecimal master) {
        static final Wages DEFAULT = new Wages(BigDecimal.ONE, BigDecimal.valueOf(3), BigDecimal.valueOf(5), BigDecimal.valueOf(8),
            BigDecimal.valueOf(12), BigDecimal.valueOf(18));
        static final Codec<Wages> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ConfigCodecs.NON_NEGATIVE, "none", DEFAULT.none).forGetter(Wages::none),
            field(ConfigCodecs.NON_NEGATIVE, "novice", DEFAULT.novice).forGetter(Wages::novice),
            field(ConfigCodecs.NON_NEGATIVE, "apprentice", DEFAULT.apprentice).forGetter(Wages::apprentice),
            field(ConfigCodecs.NON_NEGATIVE, "journeyman", DEFAULT.journeyman).forGetter(Wages::journeyman),
            field(ConfigCodecs.NON_NEGATIVE, "expert", DEFAULT.expert).forGetter(Wages::expert),
            field(ConfigCodecs.NON_NEGATIVE, "master", DEFAULT.master).forGetter(Wages::master)
        ).apply(i, Wages::new));

        /** The wage for a villager level (1 = novice .. 5 = master); anything else counts as no profession. */
        public BigDecimal forLevel(int level) {
            return switch (level) {
                case 1 -> novice;
                case 2 -> apprentice;
                case 3 -> journeyman;
                case 4 -> expert;
                case 5 -> master;
                default -> none;
            };
        }
    }

    /** Living Market: villagers buying from players' vending blocks (SPEC "Живой рынок"). */
    public record VillagersSection(boolean enabled, boolean requireHomeAndJob, int visitRadius, int checkIntervalTicks,
                                   double visitChance, int browseTicks, int walkTimeoutTicks, int customerCooldownTicks,
                                   int maxLotsPerVisit, Wages wages, int walletDays, BigDecimal dailyCapPerVillage,
                                   double maxPriceRatio, double gougingRatio, double demandDropPerLot,
                                   double demandRecoveryPerDay) {
        static final VillagersSection DEFAULT = new VillagersSection(true, true, 32, 100, 0.25, 60, 600, 6000, 3, Wages.DEFAULT, 3,
            BigDecimal.valueOf(200), 1.5, 1.25, 0.05, 0.25);
        static final Codec<VillagersSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(VillagersSection::enabled),
            field(Codec.BOOL, "requireHomeAndJob", DEFAULT.requireHomeAndJob).forGetter(VillagersSection::requireHomeAndJob),
            field(Codec.intRange(4, 64), "visitRadius", DEFAULT.visitRadius).forGetter(VillagersSection::visitRadius),
            field(Codec.intRange(20, 12_000), "checkIntervalTicks", DEFAULT.checkIntervalTicks).forGetter(VillagersSection::checkIntervalTicks),
            field(Codec.doubleRange(0, 1), "visitChance", DEFAULT.visitChance).forGetter(VillagersSection::visitChance),
            field(Codec.intRange(0, 1200), "browseTicks", DEFAULT.browseTicks).forGetter(VillagersSection::browseTicks),
            field(Codec.intRange(100, 6000), "walkTimeoutTicks", DEFAULT.walkTimeoutTicks).forGetter(VillagersSection::walkTimeoutTicks),
            field(Codec.intRange(0, 168_000), "customerCooldownTicks", DEFAULT.customerCooldownTicks).forGetter(VillagersSection::customerCooldownTicks),
            field(Codec.intRange(1, 64), "maxLotsPerVisit", DEFAULT.maxLotsPerVisit).forGetter(VillagersSection::maxLotsPerVisit),
            field(Wages.CODEC, "wages", DEFAULT.wages).forGetter(VillagersSection::wages),
            field(Codec.intRange(1, 30), "walletDays", DEFAULT.walletDays).forGetter(VillagersSection::walletDays),
            field(ConfigCodecs.NON_NEGATIVE, "dailyCapPerVillage", DEFAULT.dailyCapPerVillage).forGetter(VillagersSection::dailyCapPerVillage),
            field(Codec.doubleRange(1, 10), "maxPriceRatio", DEFAULT.maxPriceRatio).forGetter(VillagersSection::maxPriceRatio),
            field(Codec.doubleRange(1, 10), "gougingRatio", DEFAULT.gougingRatio).forGetter(VillagersSection::gougingRatio),
            field(Codec.doubleRange(0, 1), "demandDropPerLot", DEFAULT.demandDropPerLot).forGetter(VillagersSection::demandDropPerLot),
            field(Codec.doubleRange(0, 1), "demandRecoveryPerDay", DEFAULT.demandRecoveryPerDay).forGetter(VillagersSection::demandRecoveryPerDay)
        ).apply(i, VillagersSection::new));
    }

    /** Village market stalls (SPEC "Рынок деревни"). */
    public record MarketSection(boolean enabled, int weight, BigDecimal emeraldSellPrice, BigDecimal emeraldBuyPrice) {
        static final MarketSection DEFAULT = new MarketSection(true, 2, BigDecimal.valueOf(3), BigDecimal.valueOf(2));
        static final Codec<MarketSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(MarketSection::enabled),
            field(Codec.intRange(0, 50), "weight", DEFAULT.weight).forGetter(MarketSection::weight),
            field(ConfigCodecs.NON_NEGATIVE, "emeraldSellPrice", DEFAULT.emeraldSellPrice).forGetter(MarketSection::emeraldSellPrice),
            field(ConfigCodecs.NON_NEGATIVE, "emeraldBuyPrice", DEFAULT.emeraldBuyPrice).forGetter(MarketSection::emeraldBuyPrice)
        ).apply(i, MarketSection::new));
    }

    /** The villager trade window in money instead of emeralds (SPEC "Торговля с жителями за валюту"). */
    public record VillagerTradingSection(boolean enabled, BigDecimal emeraldValue) {
        static final VillagerTradingSection DEFAULT = new VillagerTradingSection(true, BigDecimal.valueOf(3));
        static final Codec<VillagerTradingSection> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(VillagerTradingSection::enabled),
            field(ConfigCodecs.NON_NEGATIVE, "emeraldValue", DEFAULT.emeraldValue).forGetter(VillagerTradingSection::emeraldValue)
        ).apply(i, VillagerTradingSection::new));
    }

    public static final ServerConfig DEFAULT = new ServerConfig(CurrencySection.DEFAULT, EconomySection.DEFAULT,
        PaySection.DEFAULT, VendingSection.DEFAULT, OreSection.DEFAULT, HistorySection.DEFAULT, LogSection.DEFAULT,
        VillagersSection.DEFAULT, MarketSection.DEFAULT, VillagerTradingSection.DEFAULT, VillagesConfig.DEFAULT);

    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        field(CurrencySection.CODEC, "currency", DEFAULT.currency).forGetter(ServerConfig::currency),
        field(EconomySection.CODEC, "economy", DEFAULT.economy).forGetter(ServerConfig::economy),
        field(PaySection.CODEC, "pay", DEFAULT.pay).forGetter(ServerConfig::pay),
        field(VendingSection.CODEC, "vending", DEFAULT.vending).forGetter(ServerConfig::vending),
        field(OreSection.CODEC, "ore", DEFAULT.ore).forGetter(ServerConfig::ore),
        field(HistorySection.CODEC, "history", DEFAULT.history).forGetter(ServerConfig::history),
        field(LogSection.CODEC, "log", DEFAULT.log).forGetter(ServerConfig::log),
        field(VillagersSection.CODEC, "villagers", DEFAULT.villagers).forGetter(ServerConfig::villagers),
        field(MarketSection.CODEC, "market", DEFAULT.market).forGetter(ServerConfig::market),
        field(VillagerTradingSection.CODEC, "villagerTrading", DEFAULT.villagerTrading).forGetter(ServerConfig::villagerTrading),
        field(VillagesConfig.CODEC, "villages", DEFAULT.villages).forGetter(ServerConfig::villages)
    ).apply(i, ServerConfig::new));

    public static final String HEADER = """
        Tradery server config. JSON5: comments, unquoted keys and trailing commas are fine.
        Money values are in major units of the currency (100 = one hundred coins, 0.5 = half a coin).
        Apply changes with /tradery reload (currency.decimals is fixed per world once it exists).""";

    public static final Map<String, String> COMMENTS = withVillages(Map.ofEntries(
        Map.entry("currency", "The one currency of this server"),
        Map.entry("currency.id", "Currency id seen by other mods through the API"),
        Map.entry("currency.decimals", "Digits after the decimal point, 0..6. Fixed for a world after its first start"),
        Map.entry("currency.thousandsSeparator", "Between groups of three digits: \" \", \",\", \"'\" or \"\""),
        Map.entry("economy.startingBalance", "Given once to every new player"),
        Map.entry("economy.maxBalance", "No account can be paid above this; 0 = no ceiling"),
        Map.entry("pay", "/pay between players"),
        Map.entry("pay.taxPercent", "Destroyed from every /pay (a money sink), 0..100"),
        Map.entry("pay.minAmount", "Smallest amount /pay accepts"),
        Map.entry("vending.feePercent", "Destroyed from every money sale in a vending block (a money sink), 0..100"),
        Map.entry("vending.maxPerPlayer", "Vending blocks one player may own; 0 = no limit"),
        Map.entry("vending.itemBlacklist", "Items that can't be sold or used as a price"),
        Map.entry("vending.facadeBlacklist", "Blocks that can't be used as a vending block facade"),
        Map.entry("vending.defaultAnimation", "Goods in the window: STATIC, SPIN, BOB, SPIN_BOB or NONE (players may override)"),
        Map.entry("ore", "Coin ore. Vein size, count and height are in the data pack (worldgen/placed_feature)"),
        Map.entry("ore.enabled", "false = coin ore no longer generates in new chunks"),
        Map.entry("ore.directToBalance", "true = mined coins go straight to the miner's balance; false = they drop as items"),
        Map.entry("ore.coinValues", "Value of one coin item"),
        Map.entry("ore.dailyCap", "Most money one player can get from coin ore per day (UTC); 0 = no limit"),
        Map.entry("ore.inflationDamping", "Fewer coins while the money supply is above targetSupply: factor = clamp(target / supply, minFactor, 1)"),
        Map.entry("history.perPlayer", "Transactions kept per player for /tradery history"),
        Map.entry("log", "Transaction log: <world>/tradery/logs/YYYY-MM-DD.log (JSON lines)"),
        Map.entry("log.retentionDays", "Older log files are deleted; 0 = keep forever"),
        Map.entry("villagers", "Living Market: villagers walk to players' vending blocks and buy with their own money"),
        Map.entry("villagers.requireHomeAndJob", "Only villagers with a bed and a job site shop (keeps caged trading-hall villagers out)"),
        Map.entry("villagers.visitRadius", "How far (blocks) a villager walks to a vending block"),
        Map.entry("villagers.checkIntervalTicks", "How often each loaded vending block may call a customer"),
        Map.entry("villagers.visitChance", "Chance per check that a customer comes (reputation scales it x0.5..x2)"),
        Map.entry("villagers.browseTicks", "How long a villager looks at the goods before buying"),
        Map.entry("villagers.walkTimeoutTicks", "A villager that hasn't arrived by then gives up"),
        Map.entry("villagers.customerCooldownTicks", "Pause between two visits of the same villager"),
        Map.entry("villagers.maxLotsPerVisit", "Most lots a villager buys in one visit"),
        Map.entry("villagers.wages", "Money a villager gets per in-game day by profession level; none = no profession or nitwit"),
        Map.entry("villagers.walletDays", "A wallet holds at most this many days of wages"),
        Map.entry("villagers.dailyCapPerVillage", "Most money the villagers of one village spend per in-game day; 0 = no limit"),
        Map.entry("villagers.maxPriceRatio", "Villagers never pay more than this times the fair price (data pack tradery/prices)"),
        Map.entry("villagers.gougingRatio", "From this price ratio on, a sale lowers the owner's reputation in the village"),
        Map.entry("villagers.demandDropPerLot", "Each lot sold lowers the village's demand for that item (0..1)"),
        Map.entry("villagers.demandRecoveryPerDay", "Demand recovered per in-game day"),
        Map.entry("market", "Village market stalls (server-owned vending blocks) in newly generated villages"),
        Map.entry("market.weight", "Weight of the stall among village houses; 0 = no stalls"),
        Map.entry("market.emeraldSellPrice", "The market changer sells one emerald for this much"),
        Map.entry("market.emeraldBuyPrice", "The market changer buys one emerald for this much (keep it below the sell price)"),
        Map.entry("villagerTrading", "The villager trade window takes and pays money instead of emeralds; false = vanilla window"),
        Map.entry("villagerTrading.emeraldValue", "Money for each emerald in a villager's trades (paid from the balance, or from the villager's wallet)")
    ));

    private static Map<String, String> withVillages(Map<String, String> comments) {
        Map<String, String> all = new java.util.HashMap<>(comments);
        all.putAll(VillagesConfig.COMMENTS);
        return Map.copyOf(all);
    }
}
