package dev.eliasnvx.tradery.market;

import com.mojang.datafixers.util.Pair;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.mixin.SinglePoolElementAccessor;
import dev.eliasnvx.tradery.mixin.StructureTemplatePoolAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adds the market stalls to the vanilla village house pools ({@code minecraft:village/<type>/houses}) when a server
 * starts, before its levels generate: each stall template with weight {@code market.weight}, as a rigid legacy
 * element like the vanilla houses, with the {@code tradery:market_stock} processor list. Zombie villages get none
 * (abandoned villages have no market). Template pools are rebuilt from data packs for every server start, so this
 * runs once per start; {@code market.*} changes apply after a restart.
 */
public final class MarketPools {
    /** Village types with a market, in vanilla's pool names. */
    public static final List<String> VILLAGE_TYPES = List.of("plains", "desert", "savanna", "taiga", "snowy");
    /** Three vendors with offers from the type's market table. */
    public static final String STALL = "market_stall";
    /** The emerald changer: sells and buys back emeralds. */
    public static final String CHANGER = "market_changer";
    public static final List<String> STALLS = List.of(STALL, CHANGER);
    public static final ResourceKey<StructureProcessorList> PROCESSORS = ResourceKey.create(Registries.PROCESSOR_LIST, Tradery.id("market_stock"));

    private MarketPools() {
    }

    /** {@code minecraft:village/<type>/houses}. */
    public static ResourceLocation housesPool(String villageType) {
        return ResourceLocation.withDefaultNamespace("village/" + villageType + "/houses");
    }

    /** {@code tradery:village/<type>/<stall>}. */
    public static ResourceLocation template(String villageType, String stall) {
        return Tradery.id("village/" + villageType + "/" + stall);
    }

    /** The pool element of a stall, as the injection adds it. */
    public static StructurePoolElement element(ResourceLocation template, Holder<StructureProcessorList> processors) {
        return StructurePoolElement.legacy(template.toString(), processors).apply(StructureTemplatePool.Projection.RIGID);
    }

    /** Loader hook: the server is starting (Fabric {@code SERVER_STARTING}, NeoForge {@code ServerAboutToStartEvent}). */
    public static void onServerStarting(MinecraftServer server) {
        inject(server, TraderyConfig.loadServer().market());
    }

    /**
     * Adds the stalls that aren't in the pools yet.
     *
     * @return elements added (0 when disabled or already there)
     */
    public static int inject(MinecraftServer server, ServerConfig.MarketSection market) {
        if (!market.enabled() || market.weight() <= 0) {
            Tradery.LOGGER.info("Village markets are off (market.enabled = {}, market.weight = {})", market.enabled(), market.weight());
            return 0;
        }
        Optional<Holder.Reference<StructureProcessorList>> processors = server.registryAccess()
            .lookupOrThrow(Registries.PROCESSOR_LIST).get(PROCESSORS);
        if (processors.isEmpty()) {
            Tradery.LOGGER.warn("Processor list {} is missing; no village markets", PROCESSORS.location());
            return 0;
        }
        Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        int added = 0;
        for (String type : VILLAGE_TYPES) {
            Optional<StructureTemplatePool> pool = pools.getOptional(housesPool(type));
            if (pool.isEmpty()) {
                Tradery.LOGGER.warn("Template pool {} is missing; no market there", housesPool(type));
                continue;
            }
            for (String stall : STALLS) {
                ResourceLocation template = template(type, stall);
                if (server.getStructureManager().get(template).isEmpty()) {
                    Tradery.LOGGER.warn("Structure {} is missing; not added to {}", template, housesPool(type));
                } else if (add(pool.get(), element(template, processors.get()), market.weight())) {
                    added++;
                }
            }
        }
        Tradery.LOGGER.info("Village markets: {} stall templates added to the village house pools with weight {}", added, market.weight());
        return added;
    }

    /** How often a stall template is in the pool's pick list (its weight once added, else 0). */
    public static int weightOf(StructureTemplatePool pool, ResourceLocation template) {
        int count = 0;
        for (StructurePoolElement element : ((StructureTemplatePoolAccessor) pool).tradery$templates()) {
            if (isTemplate(element, template)) {
                count++;
            }
        }
        return count;
    }

    private static boolean add(StructureTemplatePool pool, StructurePoolElement element, int weight) {
        ResourceLocation template = ((SinglePoolElementAccessor) element).tradery$template().left().orElseThrow();
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
        for (Pair<StructurePoolElement, Integer> entry : accessor.tradery$rawTemplates()) {
            if (isTemplate(entry.getFirst(), template)) {
                return false;
            }
        }
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(accessor.tradery$rawTemplates());
        raw.add(Pair.of(element, weight));
        accessor.tradery$setRawTemplates(raw);
        for (int i = 0; i < weight; i++) {
            accessor.tradery$templates().add(element);
        }
        return true;
    }

    private static boolean isTemplate(StructurePoolElement element, ResourceLocation template) {
        // An element built from an inline template (data generation) has no location
        return element instanceof SinglePoolElement single
            && ((SinglePoolElementAccessor) single).tradery$template().left().map(template::equals).orElse(false);
    }
}
