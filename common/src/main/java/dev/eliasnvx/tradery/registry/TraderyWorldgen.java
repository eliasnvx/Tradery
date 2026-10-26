package dev.eliasnvx.tradery.registry;

import com.mojang.serialization.MapCodec;
import dev.eliasnvx.tradery.market.MarketStockProcessor;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

import java.util.function.Supplier;

/** World generation types: the market stall processor. {@link #init()} runs once from {@code Tradery.init}. */
public final class TraderyWorldgen {
    /** {@code tradery:market_stock}: puts the goods on the market stalls' vending blocks (see {@link MarketStockProcessor}). */
    public static final Supplier<MapCodec<? extends StructureProcessor>> MARKET_STOCK = Platform.get()
        .<MapCodec<? extends StructureProcessor>>register(Registries.STRUCTURE_PROCESSOR, "market_stock", () -> MarketStockProcessor.MAP_CODEC);

    private TraderyWorldgen() {
    }

    /** Loading the class registers everything above. */
    public static void init() {
    }
}
