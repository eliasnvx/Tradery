package dev.eliasnvx.tradery.compat.wthit;

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
            switch (target) {
                case VendingBlockEntity vendor -> {
                    WthitData.Stock stock = accessor.getData().get(WthitData.Stock.TYPE);
                    TooltipLines.vending(vendor, accessor.getPlayer().getUUID(), stock == null ? -1 : stock.stock(), tooltip::addLine);
                }
                case MarketCounterBlockEntity counter -> TooltipLines.counter(counter, accessor.getPlayer().getUUID(), tooltip::addLine);
                case TradeCrateBlockEntity crate -> TooltipLines.crate(crate, tooltip::addLine);
                case DisplayBlockEntity display -> TooltipLines.display(display, tooltip::addLine);
                case BellBlockEntity ignored -> {
                    WthitData.Bell bell = accessor.getData().get(WthitData.Bell.TYPE);
                    if (bell != null) {
                        TooltipLines.bell(bell.bell(), tooltip::addLine);
                    }
                }
                case null, default -> {
                }
            }
        }
    }
}
