package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.SimpleCurrency;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterTrades;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingNotifier;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * What a villager does at a stall (SPEC "Живой рынок"): compares the price with the fair price, the village's
 * demand, its wallet and the village's daily cap, then buys through {@link VendingTrades#sellToVillager} and
 * updates the wallet, the demand and the owner's reputation. Server thread.
 */
public final class VillagerPurchases {
    /**
     * Reputation (vanilla gossip about the owner) that doubles the visit chance; the same amount below zero halves
     * it. A fully earned fair-trade reputation ({@link GossipType#TRADING} at its cap) reaches it.
     */
    static final int REPUTATION_FOR_DOUBLE_CHANCE = GossipType.TRADING.max * GossipType.TRADING.weight;
    /** Head shake of a villager that doesn't like the offer, in ticks (vanilla's "no trades" shake). */
    static final int DECLINE_SHAKE_TICKS = 40;

    private VillagerPurchases() {
    }

    /** How a visit ended. */
    public enum Result {
        BOUGHT, NOT_SERVED, NOT_WANTED, NO_FAIR_PRICE, DECLINED, NO_MONEY, DAILY_CAP, FAILED;
    }

    /**
     * @param lots  trades bought
     * @param paid  money paid (minor units)
     * @param ratio price per item / fair price (0 when not compared)
     */
    public record Visit(Result result, int lots, long paid, double ratio) {
        static Visit of(Result result) {
            return new Visit(result, 0, 0, 0);
        }

        public boolean bought() {
            return result == Result.BOUGHT;
        }
    }

    // ------------------------------------------------------------------ time, places, money

    /** In-game day number (the overworld clock), for wages, demand and the daily cap. */
    public static long today(MinecraftServer server) {
        return DayCounter.get(server).today(server.overworld().getDayTime());
    }

    /** The villager's village: its bell ({@code MEETING_POINT}), or the 64×64 region it stands in. */
    public static String villageKey(Villager villager) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT)) {
            GlobalPos bell = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).orElseThrow();
            BlockPos pos = bell.pos();
            return "bell:" + bell.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
        BlockPos pos = villager.blockPosition();
        return "region:" + villager.level().dimension().location() + ":" + (pos.getX() >> 6) + "," + (pos.getZ() >> 6);
    }

    static boolean hasProfession(Villager villager) {
        Holder<VillagerProfession> profession = VillagerCompat.profession(villager);
        return !(profession.value() == VillagerProfession.NONE) && !(profession.value() == VillagerProfession.NITWIT);
    }

    /** Daily wage in major units by profession level, before inflation damping. */
    static BigDecimal wage(Villager villager) {
        ServerConfig.Wages wages = TraderyConfig.server().villagers().wages();
        VillagerData data = villager.getVillagerData();
        return hasProfession(villager) ? wages.forLevel(data.getLevel()) : wages.none();
    }

    /**
     * Inflation damping shared with coin ore ({@code ore.inflationDamping}):
     * {@code clamp(targetSupply / supply, minFactor, 1)} while the money supply is above the target.
     */
    static BigDecimal dampingFactor() {
        ServerConfig.InflationDamping damping = TraderyConfig.server().ore().inflationDamping();
        EconomyService economy = EconomyService.INSTANCE;
        if (!damping.enabled() || !economy.isReady()) {
            return BigDecimal.ONE;
        }
        long supply = economy.moneySupply();
        long target = toMinor(damping.targetSupply());
        if (supply <= target || supply <= 0) {
            return BigDecimal.ONE;
        }
        double factor = Math.max(damping.minFactor(), Math.min(1.0, target / (double) supply));
        return BigDecimal.valueOf(factor);
    }

    static long toMinor(BigDecimal major) {
        SimpleCurrency currency = EconomyService.INSTANCE.defaultCurrency();
        try {
            return currency.toMinor(major);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * The villager's money today, after the wages due since its last visit. Wages grow with the village's level and
     * projects ({@link Villages#wageFactor}); projects can let the wallet hold more days.
     */
    public static long wallet(Villager villager, long today) {
        ServerConfig.VillagersSection config = TraderyConfig.server().villagers();
        VillageState village = Villages.of(villager);
        int walletDays = config.walletDays() + (village == null ? 0 : VillageProjects.effects(village).walletDays());
        BigDecimal daily = dailyWageMajor(villager, village);
        long perDay = toMinor(daily);
        long cap = toMinor(daily.multiply(BigDecimal.valueOf(walletDays)));
        MinecraftServer server = ((ServerLevel) villager.level()).getServer();
        return VillagerLedger.get(server).wallet(villager.getUUID(), today, perDay, cap);
    }

    private static BigDecimal dailyWageMajor(Villager villager, @org.jetbrains.annotations.Nullable VillageState village) {
        double villageFactor = village == null ? 1.0 : Villages.wageFactor(village);
        return wage(villager).multiply(dampingFactor()).multiply(BigDecimal.valueOf(villageFactor));
    }

    /** One day of the villager's wages, minor units, as the wallet earns them. */
    public static long dailyWage(Villager villager) {
        return toMinor(dailyWageMajor(villager, Villages.of(villager)));
    }

    /** Chance from the price alone: 1 up to the fair price, falling linearly to 0 at {@code maxPriceRatio}. */
    public static double priceChance(double ratio, double maxPriceRatio) {
        if (ratio <= 1.0) {
            return 1.0;
        }
        if (ratio >= maxPriceRatio) {
            return 0.0;
        }
        return (maxPriceRatio - ratio) / (maxPriceRatio - 1.0);
    }

    /** Price of one item / its fair price; empty without a fair price. */
    public static Optional<Double> priceRatio(VendingSettings settings) {
        return priceRatio(settings, 1.0);
    }

    /**
     * Price of one item / its fair price in a village where that price is {@code regionalFactor} times the usual one
     * ({@link VillageCommerce#priceFactor}); empty without a fair price.
     */
    public static Optional<Double> priceRatio(VendingSettings settings, double regionalFactor) {
        return priceRatio(settings.goods(), settings.perTrade(), settings.price(), regionalFactor);
    }

    /** {@link #priceRatio(VendingSettings, double)} for any offer: {@code price} for {@code perTrade} × {@code goods}. */
    public static Optional<Double> priceRatio(ItemStack goods, int perTrade, long price, double regionalFactor) {
        Optional<BigDecimal> fair = VillagerMarket.fairPrice(goods);
        if (fair.isEmpty() || regionalFactor <= 0) {
            return Optional.empty();
        }
        double fairMinor = fair.get().movePointRight(EconomyService.INSTANCE.defaultCurrency().decimals()).doubleValue() * regionalFactor;
        if (fairMinor <= 0) {
            return Optional.empty();
        }
        return Optional.of(price / (double) Math.max(1, perTrade) / fairMinor);
    }

    /** Visit chance multiplier from the owner's reputation with this villager: ×0.5 … ×2. */
    public static double reputationFactor(Villager villager, UUID owner) {
        int reputation = villager.getGossips().getReputation(owner, type -> true);
        double scaled = Math.max(-1.0, Math.min(1.0, reputation / (double) REPUTATION_FOR_DOUBLE_CHANCE));
        return Math.pow(2.0, scaled);
    }

    /** Item key used for the village's demand. */
    static String itemKey(ItemStack goods) {
        return BuiltInRegistries.ITEM.getKey(goods.getItem()).toString();
    }

    /** The villager's profession for the owner's notification, translated on the owner's client. */
    static String customerName(Villager villager) {
        Component name = VillagerCompat.name(VillagerCompat.profession(villager));
        return name.getContents() instanceof TranslatableContents translatable
            ? VendingNotifier.translatable(translatable.getKey()) : name.getString();
    }

    // ------------------------------------------------------------------ the visit

    /**
     * The villager looked at the goods and decides. {@code roll} is a uniform random number in [0, 1): it buys if
     * {@code roll} is below the chance from the price and the village's demand.
     */
    public static Visit visit(Villager villager, VendingBlockEntity vendor, double roll) {
        if (!(villager.level() instanceof ServerLevel) || !VendingTrades.servesVillagers(vendor)
            || !(vendor.owner() instanceof AccountId.Player owner)) {
            return Visit.of(Result.NOT_SERVED);
        }
        VendingSettings settings = vendor.settings();
        return visit(villager, new StallOffer(owner.uuid(), settings.goods(), settings.perTrade(), settings.price(),
            lots -> VendingTrades.sellToVillager(vendor, customerName(villager), lots)), roll);
    }

    /** At a market counter: the villager decides on offer {@code index}, the one it came for. */
    public static Visit visit(Villager villager, MarketCounterBlockEntity counter, int index, double roll) {
        if (!(villager.level() instanceof ServerLevel) || !CounterTrades.servesVillagers(counter, index)
            || !(counter.owner() instanceof AccountId.Player owner)) {
            return Visit.of(Result.NOT_SERVED);
        }
        CounterOffer offer = counter.settings().offer(index);
        return visit(villager, new StallOffer(owner.uuid(), offer.goods(), offer.perTrade(), offer.price(),
            lots -> CounterTrades.sellToVillager(counter, index, customerName(villager), lots)), roll);
    }

    /**
     * One offer a villager decides on: whose it is, what it sells for how much, and how to buy {@code lots} of it
     * (atomic, from the stall's own trade class).
     */
    private record StallOffer(UUID owner, ItemStack goods, int perTrade, long price, IntFunction<VendingTrades.Outcome> sell) {
    }

    private static Visit visit(Villager villager, StallOffer stall, double roll) {
        ServerConfig.VillagersSection config = TraderyConfig.server().villagers();
        ServerLevel level = (ServerLevel) villager.level();
        ItemStack goods = stall.goods();
        if (VillagerMarket.weight(VillagerCompat.profession(villager), goods) <= 0) {
            return Visit.of(Result.NOT_WANTED);
        }
        // The fair price as this village sees it: dear goods are worth more where they're scarce (regional prices)
        VillageState villageState = Villages.of(villager);
        Optional<Double> priceRatio = priceRatio(goods, stall.perTrade(), stall.price(),
            villageState == null ? 1.0 : VillageCommerce.priceFactor(villageState, goods));
        if (priceRatio.isEmpty()) {
            return Visit.of(Result.NO_FAIR_PRICE);
        }
        double ratio = priceRatio.get();
        MinecraftServer server = level.getServer();
        VillagerLedger ledger = VillagerLedger.get(server);
        long today = today(server);
        String village = villageKey(villager);
        String item = itemKey(goods);

        if (ratio >= config.gougingRatio()) {
            // Word gets around: overpricing costs reputation in the village, bought or not
            villager.getGossips().add(stall.owner(), GossipType.MINOR_NEGATIVE, GossipType.REPUTATION_CHANGE_PER_TRADE);
        }
        double chance = priceChance(ratio, config.maxPriceRatio()) * ledger.demand(village, item, today, config.demandRecoveryPerDay());
        if (roll >= chance) {
            decline(level, villager);
            return new Visit(Result.DECLINED, 0, 0, ratio);
        }

        long price = stall.price();
        long money = wallet(villager, today);
        long lots = Math.min(config.maxLotsPerVisit(), money / price);
        if (lots <= 0) {
            decline(level, villager);
            return new Visit(Result.NO_MONEY, 0, 0, ratio);
        }
        // The village's daily cap grows with its level, and doubles on its market day
        double capFactor = villageState == null ? 1.0
            : Villages.spendCapFactor(villageState) * VillageCommerce.spendCapFactor(server, villageState);
        long cap = toMinor(config.dailyCapPerVillage().multiply(BigDecimal.valueOf(capFactor)));
        if (cap > 0) {
            lots = Math.min(lots, Math.max(0, cap - ledger.spentToday(village, today)) / price);
            if (lots <= 0) {
                decline(level, villager);
                return new Visit(Result.DAILY_CAP, 0, 0, ratio);
            }
        }

        VendingTrades.Outcome outcome = stall.sell().apply((int) lots);
        if (!outcome.success()) {
            return new Visit(Result.FAILED, 0, 0, ratio);
        }
        // Nothing below can fail: the money and the stock already moved
        ledger.pay(villager.getUUID(), outcome.price());
        ledger.addSpent(village, outcome.price(), today);
        ledger.lowerDemand(village, item, outcome.trades(), today, config.demandDropPerLot(), config.demandRecoveryPerDay());
        if (ratio <= 1.0) {
            villager.getGossips().add(stall.owner(), GossipType.TRADING, GossipType.REPUTATION_CHANGE_PER_TRADE * outcome.trades());
        }
        Villages.onVillagerBought(villager, outcome.price());
        happy(level, villager);
        return new Visit(Result.BOUGHT, outcome.trades(), outcome.price(), ratio);
    }

    private static void happy(ServerLevel level, Villager villager) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3, villager.getZ(), 6, 0.35, 0.3, 0.35, 0.0);
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, villager.getSoundSource(), 1.0f, 1.0f);
    }

    private static void decline(ServerLevel level, Villager villager) {
        villager.setUnhappyCounter(DECLINE_SHAKE_TICKS);
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, villager.getSoundSource(), 1.0f, 1.0f);
    }
}
