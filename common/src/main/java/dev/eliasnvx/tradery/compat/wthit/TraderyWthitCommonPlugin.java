package dev.eliasnvx.tradery.compat.wthit;

import dev.eliasnvx.tradery.compat.TooltipData;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.level.block.entity.BellBlockEntity;

/**
 * WTHIT, both sides (listed in {@code waila_plugins.json}): the data clients don't have, in WTHIT's raw data tag
 * (WTHIT 8 on 1.20.1). The lines are the same as Jade's ({@link TraderyWthitClientPlugin}).
 */
public final class TraderyWthitCommonPlugin implements IWailaCommonPlugin {
    /** Keys in WTHIT's data tag; namespaced so other plugins of the same block can't clash. */
    static final String STOCK = "tradery:vending_stock";
    static final String BELL = "tradery:bell_village";

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData((IDataProvider<VendingBlockEntity>) (data, accessor, config) ->
            data.raw().putInt(STOCK, TooltipData.vendingStock(accessor.getTarget())), VendingBlockEntity.class);
        registrar.blockData((IDataProvider<BellBlockEntity>) (data, accessor, config) ->
            data.raw().put(BELL, TooltipData.bell(accessor.getLevel(), accessor.getTarget().getBlockPos()).write()), BellBlockEntity.class);
    }
}
