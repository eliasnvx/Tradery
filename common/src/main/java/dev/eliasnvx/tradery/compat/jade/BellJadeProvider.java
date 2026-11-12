package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipLines;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip lines of a bell ({@link TooltipLines#bell}): the village's name, level, specialization, patron, treasury, savings. */
enum BellJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("bell_village");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BellJadeData.read(accessor).ifPresent(info -> TooltipLines.bell(info, tooltip::add));
    }
}
