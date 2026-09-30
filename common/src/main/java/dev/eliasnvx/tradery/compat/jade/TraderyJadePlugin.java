package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.vending.DisplayBlock;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade: what a vending block sells, for how much, how many are left, and who owns it. */
@WailaPlugin("tradery")
public final class TraderyJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(VendingStockData.INSTANCE, VendingBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(VendingJadeProvider.INSTANCE, VendingBlock.class);
        registration.registerBlockComponent(DisplayJadeProvider.INSTANCE, DisplayBlock.class);
    }
}
