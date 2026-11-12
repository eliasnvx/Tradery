package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.compat.TooltipData;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/**
 * Server side of the Jade tooltip: the stock isn't synced to clients, so the server sends one number (trades in
 * stock, or room for buyback). Jade wants data and tooltip providers to be separate classes.
 */
enum VendingStockData implements StreamServerDataProvider<BlockAccessor, Integer> {
    INSTANCE;

    private static final Identifier UID = Tradery.id("vending_stock");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public Integer streamData(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof VendingBlockEntity vendor ? TooltipData.vendingStock(vendor) : 0;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Integer> streamCodec() {
        return ByteBufCodecs.VAR_INT.cast();
    }
}
