package dev.eliasnvx.tradery.api.event;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.api.vending.TradeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Events around a vending trade (a sale or a buyback). */
public final class VendingPurchaseEvent {
    private VendingPurchaseEvent() {
    }

    /**
     * Before the trade. Listeners may cancel it (with a message for the player), change the price per trade or
     * lower the number of trades.
     */
    public static final class Pre extends CancellableEvent {
        public static final Event<Pre> EVENT = Event.create("VendingPurchaseEvent.Pre");

        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos pos;
        private final AccountId owner;
        private final TradeDirection direction;
        private final ItemStack goods;
        private final PriceMode priceMode;
        private final ItemStack priceItem;
        private long price;
        private int trades;

        public Pre(ServerPlayer player, ServerLevel level, BlockPos pos, AccountId owner, TradeDirection direction,
                   ItemStack goods, PriceMode priceMode, ItemStack priceItem, long price, int trades) {
            this.player = player;
            this.level = level;
            this.pos = pos.immutable();
            this.owner = owner;
            this.direction = direction;
            this.goods = goods.copy();
            this.priceMode = priceMode;
            this.priceItem = priceItem.copy();
            this.price = price;
            this.trades = trades;
        }

        /** @return the buyer (sale) or the seller (buyback) */
        public ServerPlayer player() {
            return player;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        /** @return the vending block's owner account */
        public AccountId owner() {
            return owner;
        }

        public TradeDirection direction() {
            return direction;
        }

        /** @return goods of one trade (a copy; count = items per trade) */
        public ItemStack goods() {
            return goods.copy();
        }

        public PriceMode priceMode() {
            return priceMode;
        }

        /** @return the price item ({@link PriceMode#ITEM}), or empty */
        public ItemStack priceItem() {
            return priceItem.copy();
        }

        /** @return price of one trade: minor units ({@link PriceMode#CURRENCY}) or item count ({@link PriceMode#ITEM}) */
        public long price() {
            return price;
        }

        /** @param price new price of one trade, {@code >= 0} */
        public void setPrice(long price) {
            this.price = Math.max(0, price);
        }

        /** @return number of trades requested */
        public int trades() {
            return trades;
        }

        /** @param trades new number of trades; can only go down, {@code 0} cancels */
        public void setTrades(int trades) {
            this.trades = Math.max(0, Math.min(this.trades, trades));
        }
    }

    /**
     * After a successful trade. Read-only.
     *
     * @param player    buyer (sale) or seller (buyback)
     * @param level     the level
     * @param pos       the vending block
     * @param owner     its owner
     * @param direction sale or buyback
     * @param goods     goods of one trade
     * @param priceMode how it was paid
     * @param priceItem the price item, or empty
     * @param price     price of one trade
     * @param trades    number of trades done
     * @param fee       total fee destroyed (money only)
     */
    public record Post(ServerPlayer player, ServerLevel level, BlockPos pos, AccountId owner, TradeDirection direction,
                       ItemStack goods, PriceMode priceMode, ItemStack priceItem, long price, int trades, long fee) {
        public static final Event<Post> EVENT = Event.create("VendingPurchaseEvent.Post");
    }
}
