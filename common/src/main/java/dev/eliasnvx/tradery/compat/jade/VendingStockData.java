package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/**
 * Server side of the Jade tooltip: the stock isn't synced to clients, so the server sends one number (trades in
 * stock, or room for buyback). Jade wants data and tooltip providers to be separate classes.
 */
enum VendingStockData implements StreamServerDataProvider<BlockAccessor, Integer> {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("vending_stock");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public Integer streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof VendingBlockEntity vendor)) {
            return 0;
        }
        VendingSettings settings = vendor.settings();
        if (settings.isBuyback() && !vendor.admin().infiniteStock()) {
            return (int) Math.min(VendingTrades.UNLIMITED, StackMath.space(vendor.stock().getItems(), settings.goods()) / settings.perTrade());
        }
        return vendor.tradesInStock();
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Integer> streamCodec() {
        return ByteBufCodecs.VAR_INT.cast();
    }
}
