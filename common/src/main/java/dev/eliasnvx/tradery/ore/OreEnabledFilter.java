package dev.eliasnvx.tradery.ore;

import com.mojang.serialization.MapCodec;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.registry.TraderyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code tradery:enabled_in_config}: coin ore generates only while {@code ore.enabled} is true.
 * Data packs keep full control of count, height and vein size in the placed features.
 */
public final class OreEnabledFilter extends PlacementFilter {
    public static final OreEnabledFilter INSTANCE = new OreEnabledFilter();
    public static final MapCodec<OreEnabledFilter> CODEC = MapCodec.unit(() -> INSTANCE);

    private OreEnabledFilter() {
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return TraderyConfig.server().ore().enabled();
    }

    @Override
    public PlacementModifierType<?> type() {
        return TraderyItems.ORE_ENABLED_FILTER.get();
    }
}
