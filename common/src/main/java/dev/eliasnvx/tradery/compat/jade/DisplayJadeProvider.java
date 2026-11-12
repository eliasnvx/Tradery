package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipLines;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip line of a display block: what it shows. */
enum DisplayJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("display_block");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof DisplayBlockEntity display) {
            TooltipLines.display(display, tooltip::add);
        }
    }
}
