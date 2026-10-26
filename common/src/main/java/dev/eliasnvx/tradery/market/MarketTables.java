package dev.eliasnvx.tradery.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Market tables from data packs: {@code data/<ns>/tradery/market/<village type>.json}, {@code {"offers": [...]}} (see
 * {@link MarketOffer}). Files with the same name in different namespaces add up; a data pack replaces one by
 * shipping the same path. Read lazily from the server's resources and read again after {@code /reload}; stall
 * generation runs on world generation threads, so access is synchronized.
 */
public final class MarketTables {
    private static final FileToIdConverter FILES = FileToIdConverter.json("tradery/market");
    private static final Object LOCK = new Object();
    private static WeakReference<ResourceManager> loadedFrom = new WeakReference<>(null);
    private static Map<String, Table> tables = Map.of();
    private static List<String> problems = List.of();

    private MarketTables() {
    }

    /** The offers of one village type. */
    public record Table(List<MarketOffer> offers) {
        public Table {
            offers = List.copyOf(offers);
        }

        /**
         * Picks an offer by weight and makes its goods and price; empty if nothing can be sold (no tradeable offers,
         * or the picked one failed).
         */
        public Optional<Rolled> roll(ServerLevel level, BlockPos pos, RandomSource random, int decimals) {
            List<MarketOffer> candidates = new ArrayList<>(offers.size());
            long total = 0;
            for (MarketOffer offer : offers) {
                if (VendingConfigurator.isTradeable(offer.item())) {
                    candidates.add(offer);
                    total += offer.weight();
                }
            }
            if (total <= 0) {
                return Optional.empty();
            }
            long pick = Math.floorMod(random.nextLong(), total);
            MarketOffer chosen = candidates.get(candidates.size() - 1);
            for (MarketOffer offer : candidates) {
                pick -= offer.weight();
                if (pick < 0) {
                    chosen = offer;
                    break;
                }
            }
            try {
                ItemStack goods = chosen.createGoods(level, pos, random);
                if (!VendingConfigurator.isTradeable(goods)) {
                    return Optional.empty();
                }
                return Optional.of(new Rolled(goods, chosen.price().roll(random, decimals)));
            } catch (RuntimeException e) {
                Tradery.LOGGER.warn("Market offer {} failed at {}: {}", chosen.item(), pos, e.toString());
                return Optional.empty();
            }
        }
    }

    /**
     * What a stall vendor sells.
     *
     * @param goods one trade's goods
     * @param price minor units per trade
     */
    public record Rolled(ItemStack goods, long price) {
    }

    /** The table of a village type ("plains"), loaded from the server's current data packs. */
    public static Optional<Table> get(MinecraftServer server, String villageType) {
        return Optional.ofNullable(all(server).get(villageType));
    }

    /** Every table by village type. */
    public static Map<String, Table> all(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        synchronized (LOCK) {
            if (loadedFrom.get() != resources) {
                load(server, resources);
                loadedFrom = new WeakReference<>(resources);
            }
            return tables;
        }
    }

    /** Problems found in the current tables (bad entries are skipped and logged); GameTests expect none. */
    public static List<String> problems(MinecraftServer server) {
        all(server);
        synchronized (LOCK) {
            return problems;
        }
    }

    private static void load(MinecraftServer server, ResourceManager resources) {
        // Registry access for item ids; loot functions are read with Gson (MarketOffer), item modifiers by reference
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Map<String, List<MarketOffer>> offers = new HashMap<>();
        List<String> found = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> file : FILES.listMatchingResources(resources).entrySet()) {
            ResourceLocation id = FILES.fileToId(file.getKey());
            List<MarketOffer> into = offers.computeIfAbsent(id.getPath(), k -> new ArrayList<>());
            try (Reader reader = file.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray entries = root.isJsonObject() && root.getAsJsonObject().get("offers") instanceof JsonArray array ? array : null;
                if (entries == null) {
                    found.add(id + ": no \"offers\" list");
                    continue;
                }
                for (int i = 0; i < entries.size(); i++) {
                    int index = i;
                    DataResult<MarketOffer> parsed = MarketOffer.CODEC.parse(ops, entries.get(i));
                    parsed.error().ifPresent(error -> found.add(id + " offer " + index + ": " + error.message()));
                    parsed.result().ifPresent(offer -> {
                        String problem = check(offer);
                        if (problem != null) {
                            found.add(id + " offer " + index + ": " + problem);
                        } else {
                            into.add(offer);
                        }
                    });
                }
            } catch (Exception e) {
                found.add(id + ": " + e.getMessage());
            }
        }
        Map<String, Table> loaded = new HashMap<>();
        offers.forEach((type, list) -> loaded.put(type, new Table(list)));
        tables = Map.copyOf(loaded);
        problems = List.copyOf(found);
        found.forEach(problem -> Tradery.LOGGER.warn("Market table {}", problem));
        Tradery.LOGGER.debug("Loaded market tables {}", tables.keySet());
    }

    /** Offers the stalls can't use, though their JSON is fine. */
    private static @Nullable String check(MarketOffer offer) {
        if (offer.item().isEmpty()) {
            return "empty item";
        }
        // Explorer maps search for structures: that would stall world generation
        if (offer.modifier().stream().anyMatch(function -> function instanceof ExplorationMapFunction)) {
            return "exploration_map can't run during world generation";
        }
        return null;
    }
}
