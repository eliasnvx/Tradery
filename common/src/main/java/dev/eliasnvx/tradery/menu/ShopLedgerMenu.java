package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.ShopLedger;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop ledger window (SPEC "Удобство для серверов"): the player's own vending blocks and market counters, alerts
 * first, then by distance. No item slots. The server keeps the whole list (at most {@link ShopLedger#MAX_ENTRIES})
 * and sends one page of at most {@link ShopLedger#PAGE_SIZE} rows at a time. Actions are menu button clicks
 * ({@code action * 1000 + index}), the index into the page the server sent last, so the client never names a shop
 * the server didn't list.
 */
public class ShopLedgerMenu extends AbstractContainerMenu {
    /** Go to page {@code index}. */
    public static final int PAGE = 0;
    /** Show row {@code index} with a particle beam. */
    public static final int SHOW = 1;
    /** Flip the villagers toggle of row {@code index} (its chunk must be loaded). */
    public static final int VILLAGERS = 2;
    private static final int STRIDE = 1000;

    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_VENDING = 1;
    public static final int KIND_COUNTER = 2;

    /**
     * One offer of a row. Icons are plain items (no components) with the amount per trade as their count.
     *
     * @param price     money per trade (minor units); unused with a price item
     * @param priceItem the price item with its count per trade, or empty for money
     * @param stock     trades in stock (a sale) or of room (a buyback), at most {@link VendingTrades#UNLIMITED}
     */
    public record OfferView(ItemStack goods, long price, ItemStack priceItem, int stock) {
        static final StreamCodec<RegistryFriendlyByteBuf, OfferView> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, OfferView::goods,
            ByteBufCodecs.VAR_LONG, OfferView::price,
            ItemStack.OPTIONAL_STREAM_CODEC, OfferView::priceItem,
            ByteBufCodecs.VAR_INT, OfferView::stock,
            OfferView::new);
    }

    /**
     * One shop.
     *
     * @param kind          {@link #KIND_VENDING}, {@link #KIND_COUNTER} or {@link #KIND_UNKNOWN} (no snapshot yet)
     * @param live          read from the loaded block just now; false = the last known snapshot
     * @param villagersFlag the owner's villagers toggle
     * @param villagersUsed the toggle means something here (money trades)
     * @param revenue       an item waiting in the revenue slots (count 1), or empty
     * @param revenueCount  items waiting in the revenue slots
     * @param alerts        {@link dev.eliasnvx.tradery.vending.ShopSnapshot} alert bits
     */
    public record Row(GlobalPos pos, int kind, boolean live, boolean buyback, boolean villagersFlag, boolean villagersUsed,
                      List<OfferView> offers, ItemStack revenue, int revenueCount, int alerts) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of(
            (buf, row) -> {
                GlobalPos.STREAM_CODEC.encode(buf, row.pos);
                buf.writeVarInt(row.kind);
                buf.writeBoolean(row.live);
                buf.writeBoolean(row.buyback);
                buf.writeBoolean(row.villagersFlag);
                buf.writeBoolean(row.villagersUsed);
                OfferView.STREAM_CODEC.apply(ByteBufCodecs.list(CounterSettings.MAX_OFFERS)).encode(buf, row.offers);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.revenue);
                buf.writeVarInt(row.revenueCount);
                buf.writeVarInt(row.alerts);
            },
            buf -> {
                GlobalPos pos = GlobalPos.STREAM_CODEC.decode(buf);
                int kind = buf.readVarInt();
                if (kind < KIND_UNKNOWN || kind > KIND_COUNTER) {
                    throw new IllegalArgumentException("Bad shop kind: " + kind);
                }
                return new Row(pos, kind, buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    OfferView.STREAM_CODEC.apply(ByteBufCodecs.list(CounterSettings.MAX_OFFERS)).decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt());
            });

        public boolean has(int alert) {
            return (alerts & alert) != 0;
        }
    }

    /**
     * What the window shows: one page of the list.
     *
     * @param page  this page, from 0
     * @param pages pages in all (at least 1)
     * @param total shops in all the pages
     */
    public record Data(int page, int pages, int total, List<Row> rows) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Data::page,
            ByteBufCodecs.VAR_INT, Data::pages,
            ByteBufCodecs.VAR_INT, Data::total,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list(ShopLedger.PAGE_SIZE)), Data::rows,
            Data::new);
    }

    /** Server: the whole sorted list; client: empty. */
    private final List<ShopLedger.Listing> all;
    private Data data;

    /** Client side, from the opening data. */
    public ShopLedgerMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, List.of());
    }

    /** Server side, with the whole list the first page came from. */
    public ShopLedgerMenu(int containerId, Inventory inventory, Data data, List<ShopLedger.Listing> all) {
        super(TraderyMenus.SHOP_LEDGER.get(), containerId);
        this.all = new ArrayList<>(all);
        this.data = data;
    }

    public static int buttonId(int action, int index) {
        return action * STRIDE + index;
    }

    public Data data() {
        return data;
    }

    /** Fresh data from the server (client side). */
    public void update(Data data) {
        this.data = data;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || buttonId < 0 || !stillValid(player)) {
            return false;
        }
        int action = buttonId / STRIDE;
        int index = buttonId % STRIDE;
        boolean known = switch (action) {
            case PAGE -> index < data.pages();
            case SHOW, VILLAGERS -> index < data.rows().size() && data.page() * ShopLedger.PAGE_SIZE + index < all.size();
            default -> false;
        };
        if (!known) {
            return false;
        }
        if (!VendingTrades.allow(serverPlayer)) {
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        switch (action) {
            case PAGE -> {
                data = ShopLedger.page(all, index);
                sync(serverPlayer);
            }
            case SHOW -> ShopLedger.show(serverPlayer, all.get(data.page() * ShopLedger.PAGE_SIZE + index).pos());
            default -> {
                ShopLedger.Outcome outcome = ShopLedger.toggleVillagers(serverPlayer, all.get(data.page() * ShopLedger.PAGE_SIZE + index).pos());
                if (outcome.success()) {
                    all.clear();
                    all.addAll(ShopLedger.list(serverPlayer));
                    data = ShopLedger.page(all, Math.min(data.page(), ShopLedger.pages(all.size()) - 1));
                    sync(serverPlayer);
                }
                reply(serverPlayer, outcome.success(), outcome.message());
            }
        }
        return true;
    }

    private void sync(ServerPlayer player) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.ShopLedgerSyncPayload(containerId, data));
    }

    private void reply(ServerPlayer player, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(containerId, success, message));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    /** A book in the player's hands or a command: open while the player is alive. */
    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
