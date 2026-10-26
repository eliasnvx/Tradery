package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.network.codec.ByteBufCodecs;
import dev.eliasnvx.tradery.network.codec.StreamCodec;
import dev.eliasnvx.tradery.network.codec.TraderyStreamCodecs;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.CounterTrades;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a buyer sees at a market counter: the offers as rows, each with ×1 / ×8 / ×max. No item slots, so nothing can
 * be shift-clicked, dragged or swapped out. Trades go through {@link #clickMenuButton} as {@code offer * 3 + amount};
 * vanilla checks it's this open menu, the server checks the rest.
 */
public class CounterBuyerMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int BUY_ONE = 0;
    public static final int BUY_EIGHT = 1;
    public static final int BUY_MAX = 2;
    public static final int AMOUNTS = 3;

    /**
     * Opening data.
     *
     * @param pos       the counter
     * @param settings  its offers as the buyer sees them
     * @param ownerName shown as the shop name
     */
    public record Data(BlockPos pos, CounterSettings settings, String ownerName) {
        public static final StreamCodec<FriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            TraderyStreamCodecs.BLOCK_POS, Data::pos,
            CounterSettings.STREAM_CODEC, Data::settings,
            ByteBufCodecs.stringUtf8(64), Data::ownerName,
            Data::new);
    }

    private final @Nullable MarketCounterBlockEntity counter;
    private final Player viewer;
    private final Data data;
    /** Per offer: trades the stock covers. */
    private final DataSlot[] available = new DataSlot[CounterSettings.MAX_OFFERS];
    /** Per offer: trades this player can pay for. */
    private final DataSlot[] affordable = new DataSlot[CounterSettings.MAX_OFFERS];

    /** Client side, from the opening data. */
    public CounterBuyerMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null);
    }

    /** Server side, on the real block entity. */
    public CounterBuyerMenu(int containerId, Inventory inventory, Data data, @Nullable MarketCounterBlockEntity counter) {
        super(TraderyMenus.COUNTER_BUYER.get(), containerId);
        this.counter = counter;
        this.viewer = inventory.player;
        this.data = data;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            available[i] = addDataSlot(DataSlot.standalone());
            affordable[i] = addDataSlot(DataSlot.standalone());
        }
    }

    public static int buttonId(int offer, int amount) {
        return offer * AMOUNTS + amount;
    }

    public Data data() {
        return data;
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    public int available(int offer) {
        return available[offer].get();
    }

    public int affordable(int offer) {
        return affordable[offer].get();
    }

    /**
     * Keeps the numbers fresh while the screen is open (stock and money change under it). No slots here: purchases
     * change the inventory, which vanilla syncs only through the inventory menu.
     */
    @Override
    public void broadcastChanges() {
        if (counter != null && !counter.isRemoved()) {
            for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
                available[i].set(counter.tradesInStock(i));
                if (viewer instanceof ServerPlayer serverPlayer) {
                    affordable[i].set(CounterTrades.affordable(serverPlayer, counter, i));
                }
            }
        }
        super.broadcastChanges();
        if (counter != null) {
            viewer.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || counter == null || !stillValid(player)
            || buttonId < 0 || buttonId >= CounterSettings.MAX_OFFERS * AMOUNTS) {
            return false;
        }
        int offer = buttonId / AMOUNTS;
        if (!data.settings().offer(offer).isConfigured()) {
            return false;
        }
        if (!VendingTrades.allow(serverPlayer)) {
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        // Never at a price the buyer didn't see (saving new offers closes this screen; this covers the rest)
        if (!data.settings().offer(offer).sameAs(counter.settings().offer(offer))) {
            reply(serverPlayer, false, Messages.tr("tradery.counter.offer_changed", "This offer just changed: open the counter again"));
            return true;
        }
        int requested = switch (buttonId % AMOUNTS) {
            case BUY_ONE -> 1;
            case BUY_EIGHT -> 8;
            default -> VendingTrades.UNLIMITED;
        };
        VendingTrades.Outcome outcome = CounterTrades.buy(serverPlayer, counter, offer, requested);
        reply(serverPlayer, outcome.success(), outcome.message());
        broadcastChanges();
        return true;
    }

    private void reply(ServerPlayer player, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(containerId, success, message));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return counter == null || counter.isWithinReach(player);
    }
}
