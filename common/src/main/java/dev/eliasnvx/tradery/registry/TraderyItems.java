package dev.eliasnvx.tradery.registry;

import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/** Items of later phases (coins, ores) register here. */
public final class TraderyItems {
    private TraderyItems() {
    }

    static void init() {
    }

    /** Shown in the creative tab after the blocks. */
    static List<Supplier<? extends ItemLike>> tabItems() {
        return List.of();
    }
}
