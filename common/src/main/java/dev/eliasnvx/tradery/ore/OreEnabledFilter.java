package dev.eliasnvx.tradery.ore;

import com.mojang.serialization.MapCodec;
import dev.eliasnvx.tradery.config.TraderyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * Placement filter {@code tradery:enabled_in_config}: coin ore generates only while {@code ore.enabled} is true.
 * Data packs keep full control of count, height and vein size in the placed features.
 */
public final class OreEnabledFilter implements PlacementFilter {
    public static final OreEnabledFilter INSTANCE = new OreEnabledFilter();
    public static final MapCodec<OreEnabledFilter> CODEC = MapCodec.unit(() -> INSTANCE);

    private OreEnabledFilter() {
    }

    @Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return TraderyConfig.server().ore().enabled();
    }

    @Override
    public MapCodec<OreEnabledFilter> codec() {
        return CODEC;
    }
}
