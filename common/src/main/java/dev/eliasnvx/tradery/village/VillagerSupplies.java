package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.CodecJsonReloadListener;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * Villagers as suppliers (SPEC "Жители-поставщики"): what each profession produces, the produce a villager has to
 * sell, and what a supplier does at a player's buyback block.
 * <ul>
 *   <li>Data pack {@code data/<ns>/tradery/villager_supplies/*.json}: {@code {"profession": "minecraft:farmer",
 *       "supplies": [{"item": "minecraft:wheat", "per_day": 16}, {"tag": "minecraft:wool", "per_day": 8}]}}. A novice
 *       makes {@code per_day} items a day, every profession level above novice 25% more of it (a master: twice).
 *       Files for the same profession add up; the same item or tag in a later file (by id) replaces the amount,
 *       {@code per_day: 0} removes it. A tag lets the villager sell any item of it; an item entry beats a tag.</li>
 *   <li>Produce piles up in {@link VillagerLedger} for at most {@code villages.suppliers.stockDays} days.</li>
 *   <li>At a buyback block with "Villagers can sell" on, a supplier sells plain stacks (no name, no enchantments) only
 *       if the price is at least fair price × the village's regional factor × {@code suppliers.minPriceRatio}.</li>
 * </ul>
 * Server thread.
 */
public final class VillagerSupplies {
    static final ResourceLocation LISTENER = Tradery.id("villager_supplies");
    /** Production grows by this share of a novice's amount with each profession level above novice (SPEC). */
    static final double PER_LEVEL = 0.25;
    /** Prosperity points per major unit of money a supplier earned (SPEC: half of a purchase's). */
    static final double POINTS_SUPPLY = 0.5;
    /** Highest {@code per_day} in a file. */
    static final int MAX_PER_DAY = 1024;
    /** Stock keys of tag products start with this. */
    public static final String TAG_PREFIX = "#";

    private VillagerSupplies() {
    }

    // ------------------------------------------------------------------ file format

    /** One supplied item or tag and how many a novice makes a day. */
    public record Supply(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int perDay) {
        static final Codec<Supply> CODEC = RecordCodecBuilder.<Supply>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Supply::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Supply::tag),
            Codec.intRange(0, MAX_PER_DAY).fieldOf("per_day").forGetter(Supply::perDay)
        ).apply(i, Supply::new)).validate(supply -> supply.item.isPresent() != supply.tag.isPresent()
            ? DataResult.success(supply) : DataResult.error(() -> "A supply needs exactly one of \"item\" or \"tag\""));
    }

    /** A {@code villager_supplies} file. */
    public record SuppliesFile(String profession, List<Supply> supplies) {
        static final Codec<SuppliesFile> CODEC = RecordCodecBuilder.<SuppliesFile>create(i -> i.group(
            Codec.STRING.fieldOf("profession").forGetter(SuppliesFile::profession),
            Supply.CODEC.listOf().fieldOf("supplies").forGetter(SuppliesFile::supplies)
        ).apply(i, SuppliesFile::new)).validate(file -> ResourceLocation.tryParse(file.profession) != null
            ? DataResult.success(file) : DataResult.error(() -> "\"profession\" must be a profession id, not " + file.profession));
    }

    // ------------------------------------------------------------------ loaded state

    /**
     * One thing a profession makes.
     *
     * @param key    the villager's stock key: the item id, or {@link #TAG_PREFIX} and the tag id
     * @param item   the item, or null for a tag
     * @param tag    the tag, or null for an item
     * @param perDay items a novice makes a day
     */
    public record Product(String key, @Nullable Item item, @Nullable TagKey<Item> tag, int perDay) {
        public boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) : stack.is(tag);
        }
    }

    private static volatile Map<String, List<Product>> products = Map.of();

    /** Registers the data pack listener; called once by each loader at startup. */
    public static void registerReloadListeners() {
        Platform.get().registerServerReloadListener(LISTENER, new Loader());
    }

    // ------------------------------------------------------------------ lookups

    /** What villagers of this profession make (empty for none). */
    public static List<Product> products(Holder<VillagerProfession> profession) {
        String key = profession.unwrapKey().map(k -> k.location().toString()).orElse(null);
        List<Product> list = key == null ? null : products.get(key);
        return list == null ? List.of() : list;
    }

    /**
     * The product a villager of this profession can sell as {@code goods}: plain stacks only (villagers make no named
     * or enchanted items); an item entry beats a tag.
     */
    public static Optional<Product> product(Holder<VillagerProfession> profession, ItemStack goods) {
        if (goods.isEmpty() || !goods.getComponentsPatch().isEmpty()) {
            return Optional.empty();
        }
        return find(products(profession), goods);
    }

    /** Some profession makes {@code goods} (cheap pre-check before looking for villagers). */
    public static boolean anyoneProduces(ItemStack goods) {
        if (goods.isEmpty() || !goods.getComponentsPatch().isEmpty()) {
            return false;
        }
        for (List<Product> list : products.values()) {
            if (find(list, goods).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Product> find(List<Product> list, ItemStack goods) {
        for (Product product : list) {
            if (product.item() != null && goods.is(product.item())) {
                return Optional.of(product);
            }
        }
        for (Product product : list) {
            if (product.tag() != null && goods.is(product.tag())) {
                return Optional.of(product);
            }
        }
        return Optional.empty();
    }

    /** Items a villager of profession level {@code level} (1 = novice) makes a day: per day × (1 + 0.25 × (level − 1)), rounded. */
    public static int dailyAmount(Product product, int level) {
        return (int) Math.round(product.perDay() * (1 + PER_LEVEL * (Math.max(1, level) - 1)));
    }

    // ------------------------------------------------------------------ stock and price

    /** Counts the villager's produce up to {@code today} and returns how many items of {@code product} it has to sell. */
    public static int stock(Villager villager, Product product, long today) {
        MinecraftServer server = ((ServerLevel) villager.level()).getServer();
        produce(villager, today);
        return VillagerLedger.get(server).stock(villager.getUUID(), product.key());
    }

    private static void produce(Villager villager, long today) {
        MinecraftServer server = ((ServerLevel) villager.level()).getServer();
        // The villager's ledger entry exists first, with its first day's wage, as for a customer
        VillagerPurchases.wallet(villager, today);
        int level = villager.getVillagerData().getLevel();
        Map<String, Integer> daily = new HashMap<>();
        for (Product product : products(VillagerCompat.profession(villager))) {
            daily.put(product.key(), dailyAmount(product, level));
        }
        VillagerLedger.get(server).produce(villager.getUUID(), today, daily, TraderyConfig.server().villages().suppliers().stockDays());
    }

    /**
     * The lowest price of one trade ({@code perTrade} of {@code goods}, minor units) a supplier of {@code village}
     * accepts: fair price × the village's regional factor × {@code suppliers.minPriceRatio}, rounded up. Empty
     * without a fair price (suppliers don't sell such goods).
     */
    public static OptionalLong minLotPrice(ItemStack goods, int perTrade, @Nullable VillageState village) {
        return lotPrice(goods, perTrade, village, TraderyConfig.server().villages().suppliers().minPriceRatio());
    }

    /** Fair price of one trade in this village × {@code ratio}, rounded up (minor units). */
    static OptionalLong lotPrice(ItemStack goods, int perTrade, @Nullable VillageState village, double ratio) {
        Optional<BigDecimal> fair = VillagerMarket.fairPrice(goods);
        if (fair.isEmpty()) {
            return OptionalLong.empty();
        }
        double factor = village == null ? 1.0 : VillageCommerce.priceFactor(village, goods);
        BigDecimal price = fair.get().movePointRight(EconomyService.INSTANCE.defaultCurrency().decimals())
            .multiply(BigDecimal.valueOf(perTrade)).multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(ratio))
            .setScale(0, RoundingMode.CEILING);
        try {
            return OptionalLong.of(Math.max(0, price.longValueExact()));
        } catch (ArithmeticException e) {
            return OptionalLong.of(Long.MAX_VALUE);
        }
    }

    /** The villager makes the vendor's goods, the price is high enough in its village and it has a trade's worth to sell. */
    public static boolean canSupply(Villager villager, VendingBlockEntity vendor, long today) {
        VendingSettings settings = vendor.settings();
        Product product = product(VillagerCompat.profession(villager), settings.goods()).orElse(null);
        if (product == null) {
            return false;
        }
        OptionalLong min = minLotPrice(settings.goods(), settings.perTrade(), Villages.of(villager));
        return min.isPresent() && settings.price() >= min.getAsLong() && stock(villager, product, today) >= settings.perTrade();
    }

    // ------------------------------------------------------------------ the visit

    /** How a supplier's visit ended. */
    public enum Result {
        SOLD, NOT_SERVED, NOT_PRODUCED, NO_FAIR_PRICE, TOO_CHEAP, NO_STOCK, FAILED;
    }

    /**
     * @param lots   trades sold
     * @param earned money the villager got (minor units, after the vending fee)
     */
    public record Visit(Result result, int lots, long earned) {
        static Visit of(Result result) {
            return new Visit(result, 0, 0);
        }

        public boolean sold() {
            return result == Result.SOLD;
        }
    }

    /**
     * The supplier looked at the buyback offer and decides: sells up to {@code suppliers.maxLotsPerVisit} trades of
     * its produce through {@link VendingTrades#buyFromVillager}, as many as its produce, the block's room and the
     * owner's money allow. On a sale its produce shrinks, its wallet grows (no cap: it earned it), the village gains
     * prosperity, and a price at least fair earns the owner {@link GossipType#TRADING} gossip. Nothing changes otherwise.
     */
    public static Visit visit(Villager villager, VendingBlockEntity vendor) {
        if (!(villager.level() instanceof ServerLevel level) || !VendingTrades.buysFromVillagers(vendor)
            || !(vendor.owner() instanceof AccountId.Player owner)) {
            return Visit.of(Result.NOT_SERVED);
        }
        VillagesConfig.Suppliers config = TraderyConfig.server().villages().suppliers();
        VendingSettings settings = vendor.settings();
        ItemStack goods = settings.goods();
        Product product = product(VillagerCompat.profession(villager), goods).orElse(null);
        if (product == null) {
            return Visit.of(Result.NOT_PRODUCED);
        }
        VillageState village = Villages.of(villager);
        OptionalLong min = lotPrice(goods, settings.perTrade(), village, config.minPriceRatio());
        if (min.isEmpty()) {
            return Visit.of(Result.NO_FAIR_PRICE);
        }
        if (settings.price() < min.getAsLong()) {
            decline(level, villager);
            return Visit.of(Result.TOO_CHEAP);
        }
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        int lots = Math.min(config.maxLotsPerVisit(), stock(villager, product, today) / settings.perTrade());
        if (lots <= 0) {
            decline(level, villager);
            return Visit.of(Result.NO_STOCK);
        }

        VendingTrades.Outcome outcome = VendingTrades.buyFromVillager(vendor, VillagerPurchases.customerName(villager), lots);
        if (!outcome.success()) {
            decline(level, villager);
            return Visit.of(Result.FAILED);
        }
        // Nothing below can fail: the money and the goods already moved
        VillagerLedger ledger = VillagerLedger.get(server);
        ledger.takeStock(villager.getUUID(), product.key(), outcome.goods());
        ledger.receive(villager.getUUID(), outcome.price(), today);
        OptionalLong fair = lotPrice(goods, settings.perTrade(), village, 1.0);
        if (fair.isPresent() && settings.price() >= fair.getAsLong()) {
            villager.getGossips().add(owner.uuid(), GossipType.TRADING, GossipType.REPUTATION_CHANGE_PER_TRADE * outcome.trades());
        }
        if (village != null) {
            Villages.addProsperity(server, village, Villages.points(outcome.price(), POINTS_SUPPLY));
        }
        happy(level, villager);
        return new Visit(Result.SOLD, outcome.trades(), outcome.price());
    }

    private static void happy(ServerLevel level, Villager villager) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3, villager.getZ(), 6, 0.35, 0.3, 0.35, 0.0);
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, villager.getSoundSource(), 1.0f, 1.0f);
    }

    private static void decline(ServerLevel level, Villager villager) {
        villager.setUnhappyCounter(VillagerPurchases.DECLINE_SHAKE_TICKS);
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, villager.getSoundSource(), 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------ loading

    static void apply(Map<ResourceLocation, SuppliesFile> files) {
        Map<String, Map<String, Product>> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, SuppliesFile> entry : new TreeMap<>(files).entrySet()) {
            SuppliesFile file = entry.getValue();
            Map<String, Product> list = loaded.computeIfAbsent(ResourceLocation.parse(file.profession()).toString(), k -> new LinkedHashMap<>());
            for (Supply supply : file.supplies()) {
                Product product = product(supply, entry.getKey());
                if (product == null) {
                    continue;
                }
                if (product.perDay() <= 0) {
                    list.remove(product.key());
                } else {
                    list.put(product.key(), product);
                }
            }
        }
        Map<String, List<Product>> frozen = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, Map<String, Product>> entry : loaded.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                frozen.put(entry.getKey(), List.copyOf(new ArrayList<>(entry.getValue().values())));
                count += entry.getValue().size();
            }
        }
        products = Map.copyOf(frozen);
        Tradery.LOGGER.debug("Loaded {} villager supplies for {} professions", count, frozen.size());
    }

    private static @Nullable Product product(Supply supply, ResourceLocation file) {
        if (supply.tag().isPresent()) {
            ResourceLocation tag = supply.tag().get();
            return new Product(TAG_PREFIX + tag, null, TagKey.create(Registries.ITEM, tag), supply.perDay());
        }
        ResourceLocation id = supply.item().orElseThrow();
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) {
            Tradery.LOGGER.warn("Unknown item {} in {}; skipped", id, file);
            return null;
        }
        return new Product(id.toString(), item.get(), null, supply.perDay());
    }

    private static final class Loader extends CodecJsonReloadListener<SuppliesFile> {
        Loader() {
            super(SuppliesFile.CODEC, FileToIdConverter.json("tradery/villager_supplies"));
        }

        @Override
        protected void apply(Map<ResourceLocation, SuppliesFile> files, ResourceManager manager, ProfilerFiller profiler) {
            VillagerSupplies.apply(files);
        }
    }
}
