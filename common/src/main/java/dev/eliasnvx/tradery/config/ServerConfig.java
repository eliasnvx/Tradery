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
                           OreSection ore, HistorySection history, LogSection log) {

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

    public static final ServerConfig DEFAULT = new ServerConfig(CurrencySection.DEFAULT, EconomySection.DEFAULT,
        PaySection.DEFAULT, VendingSection.DEFAULT, OreSection.DEFAULT, HistorySection.DEFAULT, LogSection.DEFAULT);

    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        field(CurrencySection.CODEC, "currency", DEFAULT.currency).forGetter(ServerConfig::currency),
        field(EconomySection.CODEC, "economy", DEFAULT.economy).forGetter(ServerConfig::economy),
        field(PaySection.CODEC, "pay", DEFAULT.pay).forGetter(ServerConfig::pay),
        field(VendingSection.CODEC, "vending", DEFAULT.vending).forGetter(ServerConfig::vending),
        field(OreSection.CODEC, "ore", DEFAULT.ore).forGetter(ServerConfig::ore),
        field(HistorySection.CODEC, "history", DEFAULT.history).forGetter(ServerConfig::history),
        field(LogSection.CODEC, "log", DEFAULT.log).forGetter(ServerConfig::log)
    ).apply(i, ServerConfig::new));

    public static final String HEADER = """
        Tradery server config. JSON5: comments, unquoted keys and trailing commas are fine.
        Money values are in major units of the currency (100 = one hundred coins, 0.5 = half a coin).
        Apply changes with /tradery reload (currency.decimals is fixed per world once it exists).""";

    public static final Map<String, String> COMMENTS = Map.ofEntries(
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
        Map.entry("log.retentionDays", "Older log files are deleted; 0 = keep forever")
    );
}
