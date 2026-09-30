package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip line of a display block: what it shows. */
enum DisplayJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Tradery.id("display_block");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof DisplayBlockEntity display && !display.shown().isEmpty()) {
            tooltip.add(Component.translatable("tradery.jade.shows", display.shown().getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
