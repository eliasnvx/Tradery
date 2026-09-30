package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.Tradery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Coin ore worldgen: the placed features in {@code data/tradery/worldgen/placed_feature} go into every biome of
 * {@code #tradery:has_coin_ore} (NeoForge: a biome modifier file, Fabric: {@code BiomeModifications}).
 */
public final class CoinOreGeneration {
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, Tradery.id("has_coin_ore"));
    public static final ResourceKey<PlacedFeature> COPPER = placed("copper_coin_ore");
    public static final ResourceKey<PlacedFeature> SILVER = placed("silver_coin_ore");
    public static final ResourceKey<PlacedFeature> GOLD = placed("gold_coin_ore");
    public static final java.util.List<ResourceKey<PlacedFeature>> ALL = java.util.List.of(COPPER, SILVER, GOLD);

    private CoinOreGeneration() {
    }

    private static ResourceKey<PlacedFeature> placed(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Tradery.id(name));
    }
}
