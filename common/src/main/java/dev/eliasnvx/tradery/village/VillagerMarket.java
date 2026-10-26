package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.CodecJsonReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What villagers want and what they think it's worth, from data packs (SPEC "Живой рынок"):
 * <ul>
 *   <li>{@code data/<ns>/tradery/villager_wants/*.json}: {@code {"profession": "minecraft:farmer" | "any", "wants":
 *       [{"item": "minecraft:bone_meal", "weight": 3}, {"tag": "minecraft:hoes"}]}}. Files for the same profession add up;
 *       {@code any} applies to every villager. The weight (1..100, default 1) makes villagers who want an item more
 *       likely to be the one who comes to a stall selling it.</li>
 *   <li>{@code data/<ns>/tradery/prices/*.json}: {@code {"prices": [{"item": "minecraft:bread", "price": 0.6},
 *       {"tag": "minecraft:hoes", "price": 3}]}}: the fair price of one item in major units of the currency. An item
 *       entry beats a tag; among files, the one later by id wins. Without a fair price villagers don't buy an item.</li>
 * </ul>
 * Replaced as a whole on every data pack (re)load.
 */
public final class VillagerMarket {
    /** Profession key of wants that apply to every villager. */
    public static final String ANY = "any";
    static final ResourceLocation WANTS_LISTENER = Tradery.id("villager_wants");
    static final ResourceLocation PRICES_LISTENER = Tradery.id("prices");

    private VillagerMarket() {
    }

    // ------------------------------------------------------------------ file formats

    /** One wanted item or tag. */
    public record Want(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int weight) {
        static final Codec<Want> CODEC = RecordCodecBuilder.<Want>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Want::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Want::tag),
            Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(Want::weight)
        ).apply(i, Want::new)).validate(want -> want.item.isPresent() != want.tag.isPresent()
            ? DataResult.success(want) : DataResult.error(() -> "A want needs exactly one of \"item\" or \"tag\""));
    }

    /** A {@code villager_wants} file. */
    public record WantsFile(String profession, List<Want> wants) {
        static final Codec<WantsFile> CODEC = RecordCodecBuilder.<WantsFile>create(i -> i.group(
            Codec.STRING.fieldOf("profession").forGetter(WantsFile::profession),
            Want.CODEC.listOf().fieldOf("wants").forGetter(WantsFile::wants)
        ).apply(i, WantsFile::new)).validate(file -> file.profession.equals(ANY) || ResourceLocation.tryParse(file.profession) != null
            ? DataResult.success(file) : DataResult.error(() -> "\"profession\" must be \"any\" or a profession id, not " + file.profession));
    }

    /** A fair price of one item, or of every item in a tag. */
    public record Price(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, BigDecimal price) {
        static final Codec<Price> CODEC = RecordCodecBuilder.<Price>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Price::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Price::tag),
            ConfigCodecs.DECIMAL.fieldOf("price").forGetter(Price::price)
        ).apply(i, Price::new)).validate(price -> price.item.isPresent() == price.tag.isPresent()
            ? DataResult.error(() -> "A price needs exactly one of \"item\" or \"tag\"")
            : price.price.signum() > 0 ? DataResult.success(price) : DataResult.error(() -> "A fair price must be above zero"));
    }

    /** A {@code prices} file. */
    public record PricesFile(List<Price> prices) {
        static final Codec<PricesFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Price.CODEC.listOf().fieldOf("prices").forGetter(PricesFile::prices)
        ).apply(i, PricesFile::new));
    }

    // ------------------------------------------------------------------ loaded state

    private record Matcher(@Nullable Item item, @Nullable TagKey<Item> tag) {
        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) : stack.is(tag);
        }

        static @Nullable Matcher of(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, ResourceLocation file) {
            if (tag.isPresent()) {
                return new Matcher(null, TagKey.create(Registries.ITEM, tag.get()));
            }
            Optional<Item> found = BuiltInRegistries.ITEM.getOptional(item.orElseThrow());
            if (found.isEmpty()) {
                Tradery.LOGGER.warn("Unknown item {} in {}; skipped", item.get(), file);
                return null;
            }
            return new Matcher(found.get(), null);
        }
    }

    private record WeightedWant(Matcher matcher, int weight) {
    }

    private record TagPrice(TagKey<Item> tag, BigDecimal price) {
    }

    private record Prices(Map<Item, BigDecimal> items, List<TagPrice> tags) {
        static final Prices EMPTY = new Prices(Map.of(), List.of());
    }

    private static volatile Map<String, List<WeightedWant>> wants = Map.of();
    private static volatile Prices prices = Prices.EMPTY;
    /** Bumped on every data pack load, so caches built from the wants and prices know when to rebuild. */
    private static volatile int generation;

    /** Registers the two data pack listeners; called once by each loader at startup. */
    public static void registerReloadListeners() {
        Platform.get().registerServerReloadListener(WANTS_LISTENER, new WantsLoader());
        Platform.get().registerServerReloadListener(PRICES_LISTENER, new PricesLoader());
    }

    // ------------------------------------------------------------------ lookups

    /** Changes whenever the wants or the prices were (re)loaded. */
    public static int generation() {
        return generation;
    }

    /** Fair price of one {@code goods} item in major units; empty = villagers don't buy it. */
    public static Optional<BigDecimal> fairPrice(ItemStack goods) {
        Prices current = prices;
        BigDecimal exact = current.items().get(goods.getItem());
        if (exact != null) {
            return Optional.of(exact);
        }
        for (TagPrice tag : current.tags()) {
            if (goods.is(tag.tag())) {
                return Optional.of(tag.price());
            }
        }
        return Optional.empty();
    }

    /** How much a villager of this profession wants {@code goods}: the highest matching weight, 0 = not at all. */
    public static int weight(Holder<VillagerProfession> profession, ItemStack goods) {
        Map<String, List<WeightedWant>> current = wants;
        int best = weightIn(current.get(ANY), goods);
        String key = profession.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (key != null) {
            best = Math.max(best, weightIn(current.get(key), goods));
        }
        return best;
    }

    /** Somebody wants {@code goods} (cheap pre-check before looking for villagers). */
    public static boolean anyoneWants(ItemStack goods) {
        for (List<WeightedWant> list : wants.values()) {
            if (weightIn(list, goods) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int weightIn(@Nullable List<WeightedWant> list, ItemStack goods) {
        if (list == null) {
            return 0;
        }
        int best = 0;
        for (WeightedWant want : list) {
            if (want.weight() > best && want.matcher().matches(goods)) {
                best = want.weight();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ loading

    static void applyWants(Map<ResourceLocation, WantsFile> files) {
        Map<String, List<WeightedWant>> loaded = new HashMap<>();
        int count = 0;
        for (Map.Entry<ResourceLocation, WantsFile> entry : new TreeMap<>(files).entrySet()) {
            WantsFile file = entry.getValue();
            String profession = file.profession().equals(ANY) ? ANY : ResourceLocation.parse(file.profession()).toString();
            List<WeightedWant> list = loaded.computeIfAbsent(profession, k -> new ArrayList<>());
            for (Want want : file.wants()) {
                Matcher matcher = Matcher.of(want.item(), want.tag(), entry.getKey());
                if (matcher != null) {
                    list.add(new WeightedWant(matcher, want.weight()));
                    count++;
                }
            }
        }
        Map<String, List<WeightedWant>> frozen = new HashMap<>();
        loaded.forEach((profession, list) -> frozen.put(profession, List.copyOf(list)));
        wants = Map.copyOf(frozen);
        generation++;
        Tradery.LOGGER.debug("Loaded {} villager wants for {} professions", count, frozen.size());
    }

    static void applyPrices(Map<ResourceLocation, PricesFile> files) {
        Map<Item, BigDecimal> items = new HashMap<>();
        // Later files win: their tags are checked first
        Map<TagKey<Item>, BigDecimal> tags = new LinkedHashMap<>();
        List<Map.Entry<ResourceLocation, PricesFile>> ordered = new ArrayList<>(new TreeMap<>(files).entrySet());
        for (Map.Entry<ResourceLocation, PricesFile> entry : ordered) {
            for (Price price : entry.getValue().prices()) {
                Matcher matcher = Matcher.of(price.item(), price.tag(), entry.getKey());
                if (matcher == null) {
                    continue;
                }
                if (matcher.item() != null) {
                    items.put(matcher.item(), price.price());
                } else {
                    tags.remove(matcher.tag());
                    tags.put(matcher.tag(), price.price());
                }
            }
        }
        List<TagPrice> tagPrices = new ArrayList<>();
        tags.forEach((tag, price) -> tagPrices.add(0, new TagPrice(tag, price)));
        prices = new Prices(Map.copyOf(items), List.copyOf(tagPrices));
        generation++;
        Tradery.LOGGER.debug("Loaded fair prices for {} items and {} tags", items.size(), tagPrices.size());
    }

    private static final class WantsLoader extends CodecJsonReloadListener<WantsFile> {
        WantsLoader() {
            super(WantsFile.CODEC, FileToIdConverter.json("tradery/villager_wants"));
        }

        @Override
        protected void apply(Map<ResourceLocation, WantsFile> files, ResourceManager manager, ProfilerFiller profiler) {
            applyWants(files);
        }
    }

    private static final class PricesLoader extends CodecJsonReloadListener<PricesFile> {
        PricesLoader() {
            super(PricesFile.CODEC, FileToIdConverter.json("tradery/prices"));
        }

        @Override
        protected void apply(Map<ResourceLocation, PricesFile> files, ResourceManager manager, ProfilerFiller profiler) {
            applyPrices(files);
        }
    }
}
