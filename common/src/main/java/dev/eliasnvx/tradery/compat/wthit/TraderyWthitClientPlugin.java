package dev.eliasnvx.tradery.compat.wthit;

import dev.eliasnvx.tradery.compat.TooltipData;
import dev.eliasnvx.tradery.compat.TooltipLines;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** WTHIT, client side: the tooltip lines of Tradery's blocks and of a bell's village, the same as Jade's. */
public final class TraderyWthitClientPlugin implements IWailaClientPlugin {
    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(Lines.INSTANCE, VendingBlockEntity.class);
        registrar.body(Lines.INSTANCE, MarketCounterBlockEntity.class);
        registrar.body(Lines.INSTANCE, TradeCrateBlockEntity.class);
        registrar.body(Lines.INSTANCE, DisplayBlockEntity.class);
        registrar.body(Lines.INSTANCE, BellBlockEntity.class);
    }

    private enum Lines implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            BlockEntity target = accessor.getBlockEntity();
            CompoundTag data = accessor.getData().raw();
            if (target instanceof VendingBlockEntity vendor) {
                int stock = data.contains(TraderyWthitCommonPlugin.STOCK, Tag.TAG_INT) ? data.getInt(TraderyWthitCommonPlugin.STOCK) : -1;
                TooltipLines.vending(vendor, accessor.getPlayer().getUUID(), stock, tooltip::addLine);
            } else if (target instanceof MarketCounterBlockEntity counter) {
                TooltipLines.counter(counter, accessor.getPlayer().getUUID(), tooltip::addLine);
            } else if (target instanceof TradeCrateBlockEntity crate) {
                TooltipLines.crate(crate, tooltip::addLine);
            } else if (target instanceof DisplayBlockEntity display) {
                TooltipLines.display(display, tooltip::addLine);
            } else if (target instanceof BellBlockEntity && data.contains(TraderyWthitCommonPlugin.BELL, Tag.TAG_COMPOUND)) {
                TooltipLines.bell(TooltipData.Bell.read(data.getCompound(TraderyWthitCommonPlugin.BELL)), tooltip::addLine);
            }
        }
    }
}
