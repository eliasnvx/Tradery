package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.command.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a buyer sees: the offer and three buttons. There are no item slots at all, so shift-clicks, number keys
 * or drags have nothing to take. Trades go through {@link #clickMenuButton} (vanilla checks it's this open menu).
 */
public class VendingBuyerMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int BUY_ONE = 0;
    public static final int BUY_EIGHT = 1;
    public static final int BUY_MAX = 2;

    /**
     * Opening data.
     *
     * @param pos       the vending block
     * @param settings  what it trades
     * @param ownerName shown as the shop name
     */
    public record Data(BlockPos pos, VendingSettings settings, String ownerName) {
        /** Longest shop name sent (player names are 16 characters). */
        public static final int MAX_OWNER_NAME = 64;

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            settings.write(buf);
            buf.writeUtf(ownerName, MAX_OWNER_NAME);
        }

        public static Data read(FriendlyByteBuf buf) {
            return new Data(buf.readBlockPos(), VendingSettings.read(buf), buf.readUtf(MAX_OWNER_NAME));
        }
    }

    private final @Nullable VendingBlockEntity vendor;
    private final Player viewer;
    private final Data data;
    /** Trades the stock covers (sale) or the stock has room for (buyback). */
    private final DataSlot available = DataSlot.standalone();
    /** Trades this player can pay for (sale) or supply (buyback). */
    private final DataSlot affordable = DataSlot.standalone();

    /** Client side, from the opening data. */
    public VendingBuyerMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null);
    }

    /** Server side, on the real block entity. */
    public VendingBuyerMenu(int containerId, Inventory inventory, Data data, @Nullable VendingBlockEntity vendor) {
        super(TraderyMenus.VENDING_BUYER.get(), containerId);
        this.vendor = vendor;
        this.viewer = inventory.player;
        this.data = data;
        addDataSlot(available);
        addDataSlot(affordable);
    }

    public Data data() {
        return data;
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    public int available() {
        return available.get();
    }

    public int affordable() {
        return affordable.get();
    }

    /** Keeps the two numbers fresh while the screen is open (stock and money change under it). */
    @Override
    public void broadcastChanges() {
        if (vendor != null && !vendor.isRemoved()) {
            VendingSettings settings = vendor.settings();
            int space = settings.isBuyback() && !vendor.admin().infiniteStock()
                ? (int) Math.min(VendingTrades.UNLIMITED, dev.eliasnvx.tradery.vending.StackMath.space(vendor.stock().getItems(), settings.goods())
                    / settings.perTrade())
                : vendor.tradesInStock();
            available.set(settings.isBuyback() && vendor.admin().infiniteStock() ? VendingTrades.UNLIMITED : space);
            if (viewer instanceof ServerPlayer serverPlayer) {
                affordable.set(VendingTrades.affordable(serverPlayer, vendor));
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || vendor == null || !stillValid(player)) {
            return false;
        }
        if (!VendingTrades.allow(serverPlayer)) {
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        int requested = switch (buttonId) {
            case BUY_ONE -> 1;
            case BUY_EIGHT -> 8;
            case BUY_MAX -> VendingTrades.UNLIMITED;
            default -> 0;
        };
        if (requested == 0) {
            return false;
        }
        VendingTrades.Outcome outcome = VendingTrades.execute(serverPlayer, vendor, requested);
        reply(serverPlayer, outcome.success(), outcome.message());
        broadcastChanges();
        return true;
    }

    private void reply(ServerPlayer player, boolean success, net.minecraft.network.chat.Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(containerId, success, message));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return vendor == null || vendor.isWithinReach(player);
    }
}
