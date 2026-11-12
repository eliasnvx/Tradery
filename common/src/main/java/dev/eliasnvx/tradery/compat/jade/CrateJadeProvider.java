package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipLines;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip lines of a trade crate ({@link TooltipLines#crate}), from the block's own client copy. */
enum CrateJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("trade_crate");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof TradeCrateBlockEntity crate) {
            TooltipLines.crate(crate, tooltip::add);
        }
    }
}
