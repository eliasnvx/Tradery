package dev.eliasnvx.tradery.api.event;

import dev.eliasnvx.tradery.api.vending.PriceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The owner saves a vending block's settings. Cancel to reject them, or change the price (a price ceiling). */
public final class VendingConfiguredEvent extends CancellableEvent {
    public static final Event<VendingConfiguredEvent> EVENT = Event.create("VendingConfiguredEvent");

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final ItemStack goods;
    private final PriceMode priceMode;
    private final ItemStack priceItem;
    private final boolean buyback;
    private long price;

    public VendingConfiguredEvent(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack goods, PriceMode priceMode,
                                  ItemStack priceItem, long price, boolean buyback) {
        this.player = player;
        this.level = level;
        this.pos = pos.immutable();
        this.goods = goods.copy();
        this.priceMode = priceMode;
        this.priceItem = priceItem.copy();
        this.price = price;
        this.buyback = buyback;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    /** @return goods of one trade (a copy) */
    public ItemStack goods() {
        return goods.copy();
    }

    public PriceMode priceMode() {
        return priceMode;
    }

    /** @return the price item, or empty */
    public ItemStack priceItem() {
        return priceItem.copy();
    }

    /** @return whether the block buys from players instead of selling */
    public boolean buyback() {
        return buyback;
    }

    /** @return price of one trade: minor units or item count */
    public long price() {
        return price;
    }

    /** @param price new price of one trade, {@code >= 0} */
    public void setPrice(long price) {
        this.price = Math.max(0, price);
    }
}
