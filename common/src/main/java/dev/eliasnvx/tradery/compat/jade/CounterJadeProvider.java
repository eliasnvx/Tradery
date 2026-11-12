package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipLines;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip lines of a market counter ({@link TooltipLines#counter}), from the block's own client copy. */
enum CounterJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("market_counter");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof MarketCounterBlockEntity counter) {
            TooltipLines.counter(counter, accessor.getPlayer().getUUID(), tooltip::add);
        }
    }
}
