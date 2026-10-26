package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.CodecJsonReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Regional prices (SPEC "Торговля между деревнями"): data pack {@code data/<ns>/tradery/village_types/<type>.json}.
 * <pre>{@code
 * {
 *   "type": "minecraft:desert",          // optional: villager type, default "minecraft:<file name>"
 *   "caravan_trades": 8,                 // optional: how often a caravan trades each of its offers
 *   "cheap": [{"item": "minecraft:sand", "factor": 0.6, "lot": 32}],
 *   "dear":  [{"tag": "minecraft:logs", "factor": 1.5, "lot": 16}]
 * }
 * }</pre>
 * A factor multiplies the fair price of the goods in villages of that type (cheap: below 1, dear: above 1); {@code lot}
 * is how many items a caravan trades at once. Files for the same type add up; an item entry beats a tag, and among
 * files the one later by id wins. Replaced as a whole on every data pack (re)load.
 */
public final class VillageTypes {
    static final ResourceLocation LISTENER = Tradery.id("village_types");
    /** Default caravan trades per offer, and lot size, when a file doesn't say. */
    static final int DEFAULT_CARAVAN_TRADES = 8;
    static final int DEFAULT_LOT = 16;

    private VillageTypes() {
    }

    // ------------------------------------------------------------------ file format

    /** One cheap or dear item or tag. */
    public record Shift(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, double factor, int lot) {
        static final Codec<Shift> CODEC = RecordCodecBuilder.<Shift>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Shift::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Shift::tag),
            Codec.doubleRange(0.01, 10).fieldOf("factor").forGetter(Shift::factor),
            Codec.intRange(1, 64).optionalFieldOf("lot", DEFAULT_LOT).forGetter(Shift::lot)
        ).apply(i, Shift::new)).validate(shift -> shift.item.isPresent() != shift.tag.isPresent()
            ? DataResult.success(shift) : DataResult.error(() -> "A regional price needs exactly one of \"item\" or \"tag\""));
    }

    /** A {@code village_types} file. */
    public record TypeFile(Optional<ResourceLocation> type, int caravanTrades, List<Shift> cheap, List<Shift> dear) {
        static final Codec<TypeFile> CODEC = RecordCodecBuilder.<TypeFile>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("type").forGetter(TypeFile::type),
            Codec.intRange(1, 64).optionalFieldOf("caravan_trades", DEFAULT_CARAVAN_TRADES).forGetter(TypeFile::caravanTrades),
            Shift.CODEC.listOf().optionalFieldOf("cheap", List.of()).forGetter(TypeFile::cheap),
            Shift.CODEC.listOf().optionalFieldOf("dear", List.of()).forGetter(TypeFile::dear)
        ).apply(i, TypeFile::new)).validate(TypeFile::check);

        private static DataResult<TypeFile> check(TypeFile file) {
            for (Shift shift : file.cheap) {
                if (shift.factor >= 1) {
                    return DataResult.error(() -> "A \"cheap\" factor must be below 1, not " + shift.factor);
                }
            }
            for (Shift shift : file.dear) {
                if (shift.factor <= 1) {
                    return DataResult.error(() -> "A \"dear\" factor must be above 1, not " + shift.factor);
                }
            }
            return DataResult.success(file);
        }
    }

    // ------------------------------------------------------------------ loaded state

    /**
     * A loaded regional price: one item, or every item of a tag.
     *
     * @param factor fair price multiplier here
     * @param lot    items per caravan trade (capped at the item's stack size where it's used)
     */
    public record Entry(@Nullable Item item, @Nullable TagKey<Item> tag, double factor, int lot) {
        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) : stack.is(tag);
        }

        public boolean cheap() {
            return factor < 1;
        }

        /** The item itself, or the tag's first item (for icons); empty for an empty tag. */
        public ItemStack icon() {
            if (item != null) {
                return new ItemStack(item);
            }
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return new ItemStack(holder);
            }
            return ItemStack.EMPTY;
        }

        /** The item, or one of the tag's items picked with {@code random}; null for an empty tag. */
        public @Nullable Item pick(RandomSource random) {
            if (item != null) {
                return item;
            }
            List<Item> items = new ArrayList<>();
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                items.add(holder.value());
            }
            return items.isEmpty() ? null : items.get(random.nextInt(items.size()));
        }
    }

    /** Everything known about one villager type. */
    public record Region(Map<Item, Entry> items, List<Entry> tags, List<Entry> all, int caravanTrades) {
        static final Region NONE = new Region(Map.of(), List.of(), List.of(), DEFAULT_CARAVAN_TRADES);

        double factor(ItemStack stack) {
            Entry exact = items.get(stack.getItem());
            if (exact != null) {
                return exact.factor();
            }
            for (Entry entry : tags) {
                if (entry.matches(stack)) {
                    return entry.factor();
                }
            }
            return 1.0;
        }
    }

    private static volatile Map<String, Region> regions = Map.of();

    /** Registers the data pack listener; called once by each loader at startup. */
    public static void registerReloadListeners() {
        Platform.get().registerServerReloadListener(LISTENER, new Loader());
    }

    // ------------------------------------------------------------------ lookups

    /** The regional prices of a villager type ({@code minecraft:desert}); nothing for an unknown type. */
    public static Region region(String type) {
        return regions.getOrDefault(type, Region.NONE);
    }

    /** Multiplier of the fair price of {@code goods} in villages of {@code type}: 1 when it isn't listed. */
    public static double factor(String type, ItemStack goods) {
        return type.isEmpty() || goods.isEmpty() ? 1.0 : region(type).factor(goods);
    }

    // ------------------------------------------------------------------ loading

    static void apply(Map<ResourceLocation, TypeFile> files) {
        Map<String, Map<Item, Entry>> items = new HashMap<>();
        Map<String, LinkedHashMap<TagKey<Item>, Entry>> tags = new HashMap<>();
        Map<String, Integer> trades = new HashMap<>();
        for (Map.Entry<ResourceLocation, TypeFile> entry : new TreeMap<>(files).entrySet()) {
            TypeFile file = entry.getValue();
            String type = file.type().orElse(ResourceLocation.withDefaultNamespace(entry.getKey().getPath())).toString();
            trades.put(type, file.caravanTrades());
            List<Shift> shifts = new ArrayList<>(file.cheap());
            shifts.addAll(file.dear());
            for (Shift shift : shifts) {
                if (shift.tag().isPresent()) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, shift.tag().get());
                    LinkedHashMap<TagKey<Item>, Entry> byTag = tags.computeIfAbsent(type, k -> new LinkedHashMap<>());
                    // Later files win: re-inserted at the end, checked first below
                    byTag.remove(tag);
                    byTag.put(tag, new Entry(null, tag, shift.factor(), shift.lot()));
                    continue;
                }
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(shift.item().orElseThrow());
                if (item.isEmpty() || item.get() == Items.AIR) {
                    Tradery.LOGGER.warn("Unknown item {} in {}; skipped", shift.item().get(), entry.getKey());
                    continue;
                }
                // No ItemStacks here: item components aren't bound yet while data packs load (the lot is capped at the
                // stack size where a caravan uses it)
                items.computeIfAbsent(type, k -> new HashMap<>()).put(item.get(), new Entry(item.get(), null, shift.factor(), shift.lot()));
            }
        }
        Map<String, Region> loaded = new HashMap<>();
        java.util.Set<String> types = new java.util.TreeSet<>(trades.keySet());
        for (String type : types) {
            Map<Item, Entry> itemEntries = Map.copyOf(items.getOrDefault(type, Map.of()));
            List<Entry> tagEntries = new ArrayList<>(tags.getOrDefault(type, new LinkedHashMap<>()).values());
            java.util.Collections.reverse(tagEntries);
            List<Entry> all = new ArrayList<>(itemEntries.values());
            all.addAll(tagEntries);
            // Cheapest first; same factor: by name, so the board's list doesn't jump around
            all.sort(java.util.Comparator.comparingDouble(Entry::factor).thenComparing(VillageTypes::sortKey));
            loaded.put(type, new Region(itemEntries, List.copyOf(tagEntries), List.copyOf(all), trades.get(type)));
        }
        regions = Map.copyOf(loaded);
        Tradery.LOGGER.debug("Loaded regional prices for {} village types", loaded.size());
    }

    private static String sortKey(Entry entry) {
        return entry.item() != null ? BuiltInRegistries.ITEM.getKey(entry.item()).toString() : "#" + entry.tag().location();
    }

    private static final class Loader extends CodecJsonReloadListener<TypeFile> {
        Loader() {
            super(TypeFile.CODEC, FileToIdConverter.json("tradery/village_types"));
        }

        @Override
        protected void apply(Map<ResourceLocation, TypeFile> files, ResourceManager manager, ProfilerFiller profiler) {
            VillageTypes.apply(files);
        }
    }
}
