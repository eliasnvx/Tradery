package dev.eliasnvx.tradery.compat.wthit;

import dev.eliasnvx.tradery.compat.TooltipData;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.entity.BellBlockEntity;

/**
 * WTHIT, both sides (listed in {@code waila_plugins.json}): the data clients don't have, sent with the tooltip. The
 * lines are the same as Jade's ({@link TraderyWthitClientPlugin}).
 */
public final class TraderyWthitCommonPlugin implements IWailaCommonPlugin {
    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.dataType(WthitData.Stock.TYPE, ByteBufCodecs.VAR_INT.map(WthitData.Stock::new, WthitData.Stock::stock));
        registrar.dataType(WthitData.Bell.TYPE, TooltipData.Bell.STREAM_CODEC.map(WthitData.Bell::new, WthitData.Bell::bell));
        registrar.blockData((IDataProvider<VendingBlockEntity>) (data, accessor, config) ->
            data.add(WthitData.Stock.TYPE, result -> result.add(new WthitData.Stock(TooltipData.vendingStock(accessor.getTarget())))),
            VendingBlockEntity.class);
        registrar.blockData((IDataProvider<BellBlockEntity>) (data, accessor, config) ->
            data.add(WthitData.Bell.TYPE, result -> result.add(new WthitData.Bell(TooltipData.bell(accessor.getLevel(),
                accessor.getTarget().getBlockPos())))),
            BellBlockEntity.class);
    }
}
