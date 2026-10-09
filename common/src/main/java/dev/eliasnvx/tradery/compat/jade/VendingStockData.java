package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.OptionalInt;

/**
 * Server side of the Jade tooltip: the stock isn't synced to clients, so the server sends one number (trades in
 * stock, or room for buyback) in Jade's server data tag. Jade wants data and tooltip providers to be separate classes.
 */
enum VendingStockData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tradery.id("vending_stock");
    /** Key in Jade's server data tag; namespaced so other providers of the same block can't clash. */
    private static final String KEY = UID.toString();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof VendingBlockEntity vendor)) {
            return;
        }
        data.putInt(KEY, count(vendor));
    }

    private static int count(VendingBlockEntity vendor) {
        VendingSettings settings = vendor.settings();
        if (settings.isBuyback() && !vendor.admin().infiniteStock()) {
            return (int) Math.min(VendingTrades.UNLIMITED, StackMath.space(vendor.stock().getItems(), settings.goods()) / settings.perTrade());
        }
        return vendor.tradesInStock();
    }

    /** Client side: the number the server sent, if it did (it has Tradery and Jade). */
    static OptionalInt read(BlockAccessor accessor) {
        CompoundTag data = accessor.getServerData();
        return data.contains(KEY, Tag.TAG_INT) ? OptionalInt.of(data.getInt(KEY)) : OptionalInt.empty();
    }
}
