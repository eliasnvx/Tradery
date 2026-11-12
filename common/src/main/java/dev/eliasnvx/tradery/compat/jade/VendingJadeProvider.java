package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipLines;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Tooltip lines of a vending block ({@link TooltipLines#vending}). Owner and offer come from the block's own client
 * copy; the stock comes from {@link VendingStockData} on the server.
 */
enum VendingJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Tradery.id("vending_block");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof VendingBlockEntity vendor) {
            int stock = VendingStockData.INSTANCE.decodeFromData(accessor).orElse(-1);
            TooltipLines.vending(vendor, accessor.getPlayer().getUUID(), stock, tooltip::add);
        }
    }
}
