package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.StackMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The owner's view of a vending block: 27 stock slots, 9 revenue slots (take only), three sample slots (goods,
 * price item, facade) and a draft of the settings that only applies on "Save". Admins opening it with the vendor
 * key also get the admin toggles, which apply at once.
 */
public class VendingOwnerMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int STOCK_START = 0;
    public static final int REVENUE_START = VendingBlockEntity.STOCK_SIZE;
    public static final int GOODS_SLOT = REVENUE_START + VendingBlockEntity.REVENUE_SIZE;
    public static final int PRICE_SLOT = GOODS_SLOT + 1;
    public static final int FACADE_SLOT = GOODS_SLOT + 2;
    public static final int INVENTORY_START = FACADE_SLOT + 1;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    // Buttons (clickMenuButton ids)
    public static final int GOODS_MINUS = 0;
    public static final int GOODS_PLUS = 1;
    public static final int GOODS_MINUS_8 = 2;
    public static final int GOODS_PLUS_8 = 3;
    public static final int PRICE_MINUS = 4;
    public static final int PRICE_PLUS = 5;
    public static final int PRICE_MINUS_8 = 6;
    public static final int PRICE_PLUS_8 = 7;
    public static final int TOGGLE_MODE = 8;
    public static final int TOGGLE_BUYBACK = 9;
    public static final int CYCLE_ANIMATION = 10;
    public static final int ADMIN_INFINITE = 20;
    public static final int ADMIN_BURN = 21;
    public static final int ADMIN_NO_FEE = 22;
    public static final int ADMIN_SERVER_OWNER = 23;

    /** Chest-grid layout shared with the screen. */
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 18;
    public static final int INVENTORY_TOP = GRID_TOP + 4 * 18 + 13;
    public static final int PANEL_LEFT = 180;
    public static final int GOODS_X = PANEL_LEFT + 8;
    public static final int GOODS_Y = 24;
    public static final int PRICE_X = PANEL_LEFT + 8;
    public static final int PRICE_Y = 66;
    public static final int FACADE_X = PANEL_LEFT + 8;
    public static final int FACADE_Y = 126;

    /**
     * Opening data.
     *
     * @param pos       the vending block
     * @param settings  its saved settings (the draft starts from them)
     * @param admin     admin flags
     * @param facade    facade block state, if any
     * @param adminMode opened with the vendor key by an admin
     * @param serverOwned owned by the server account
     * @param feePercent vending fee for the info line
     */
    public record Data(BlockPos pos, VendingSettings settings, AdminFlags admin, Optional<BlockState> facade, boolean adminMode,
                       boolean serverOwned, String feePercent) {
        /** Longest fee text sent ("2.5%"). */
        public static final int MAX_FEE_TEXT = 16;

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            settings.write(buf);
            admin.write(buf);
            buf.writeOptional(facade, (b, state) -> b.writeVarInt(Block.getId(state)));
            buf.writeBoolean(adminMode);
            buf.writeBoolean(serverOwned);
            buf.writeUtf(feePercent, MAX_FEE_TEXT);
        }

        public static Data read(FriendlyByteBuf buf) {
            return new Data(buf.readBlockPos(), VendingSettings.read(buf), AdminFlags.read(buf),
                buf.readOptional(b -> Block.stateById(b.readVarInt())), buf.readBoolean(), buf.readBoolean(), buf.readUtf(MAX_FEE_TEXT));
        }
    }

    private final @Nullable VendingBlockEntity vendor;
    private final Data data;
    private final SimpleContainer samples = new SimpleContainer(3);
    /** Draft state shown by the screen: price mode, buyback, animation, admin flags, server owner. */
    private final DataSlot priceMode = DataSlot.standalone();
    private final DataSlot buyback = DataSlot.standalone();
    private final DataSlot animation = DataSlot.standalone();
    private final DataSlot adminFlags = DataSlot.standalone();

    /** Client side, from the opening data. */
    public VendingOwnerMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null, new SimpleContainer(VendingBlockEntity.STOCK_SIZE),
            new SimpleContainer(VendingBlockEntity.REVENUE_SIZE));
    }

    /** Server side, on the real block entity. */
    public VendingOwnerMenu(int containerId, Inventory inventory, Data data, VendingBlockEntity vendor) {
        this(containerId, inventory, data, vendor, vendor.stock(), vendor.revenue());
    }

    private VendingOwnerMenu(int containerId, Inventory inventory, Data data, @Nullable VendingBlockEntity vendor,
                             Container stock, Container revenue) {
        super(TraderyMenus.VENDING_OWNER.get(), containerId);
        this.vendor = vendor;
        this.data = data;
        VendingSettings settings = data.settings();
        samples.setItem(0, settings.goods());
        samples.setItem(1, settings.priceItem());
        samples.setItem(2, data.facade().map(state -> new ItemStack(state.getBlock())).orElse(ItemStack.EMPTY));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new StockSlot(stock, col + row * 9, GRID_LEFT + col * 18, GRID_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new RevenueSlot(revenue, col, GRID_LEFT + col * 18, GRID_TOP + 3 * 18));
        }
        addSlot(new GhostSlot(samples, 0, GOODS_X, GOODS_Y, VendingConfigurator::isTradeable));
        addSlot(new GhostSlot(samples, 1, PRICE_X, PRICE_Y, VendingConfigurator::isTradeable, () -> priceMode() == PriceMode.ITEM));
        addSlot(new GhostSlot(samples, 2, FACADE_X, FACADE_Y, VendingConfigurator::isFacadeCandidate));
        PlayerInventorySlots.add(this::addSlot, inventory, GRID_LEFT, INVENTORY_TOP);

        priceMode.set(settings.priceMode().ordinal());
        buyback.set(settings.buyback() ? 1 : 0);
        animation.set(settings.animation().ordinal());
        adminFlags.set(encodeAdmin(data.admin(), data.serverOwned()));
        addDataSlot(priceMode);
        addDataSlot(buyback);
        addDataSlot(animation);
        addDataSlot(adminFlags);
    }

    static int encodeAdmin(AdminFlags flags, boolean serverOwned) {
        return (flags.infiniteStock() ? 1 : 0) | (flags.burnPayment() ? 2 : 0) | (flags.noFee() ? 4 : 0) | (serverOwned ? 8 : 0);
    }

    // ------------------------------------------------------------------ draft (both sides)

    public Data data() {
        return data;
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    public ItemStack goodsSample() {
        return samples.getItem(0);
    }

    public ItemStack priceSample() {
        return samples.getItem(1);
    }

    public ItemStack facadeSample() {
        return samples.getItem(2);
    }

    public PriceMode priceMode() {
        return VendingSettings.enumAt(PriceMode.values(), Math.floorMod(priceMode.get(), PriceMode.values().length));
    }

    public boolean buyback() {
        return buyback.get() != 0;
    }

    public DisplayAnimation animation() {
        return VendingSettings.enumAt(DisplayAnimation.values(), Math.floorMod(animation.get(), DisplayAnimation.values().length));
    }

    public AdminFlags adminFlags() {
        int bits = adminFlags.get();
        return new AdminFlags((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
    }

    public boolean serverOwned() {
        return (adminFlags.get() & 8) != 0;
    }

    public boolean adminMode() {
        return data.adminMode();
    }

    /** The draft as settings, with the given money price. */
    public VendingSettings draft(long price) {
        return new VendingSettings(goodsSample(), priceMode(), price, priceSample(), buyback(), animation());
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void clicked(int slotIndex, int buttonNum, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex) instanceof GhostSlot ghost) {
            GhostSlots.click(this, ghost, buttonNum, clickType);
            return;
        }
        super.clicked(slotIndex, buttonNum, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || vendor == null || !stillValid(player)) {
            return false;
        }
        switch (buttonId) {
            case GOODS_MINUS -> GhostSlots.adjust((GhostSlot) slots.get(GOODS_SLOT), -1);
            case GOODS_PLUS -> GhostSlots.adjust((GhostSlot) slots.get(GOODS_SLOT), 1);
            case GOODS_MINUS_8 -> GhostSlots.adjust((GhostSlot) slots.get(GOODS_SLOT), -8);
            case GOODS_PLUS_8 -> GhostSlots.adjust((GhostSlot) slots.get(GOODS_SLOT), 8);
            case PRICE_MINUS -> GhostSlots.adjust((GhostSlot) slots.get(PRICE_SLOT), -1);
            case PRICE_PLUS -> GhostSlots.adjust((GhostSlot) slots.get(PRICE_SLOT), 1);
            case PRICE_MINUS_8 -> GhostSlots.adjust((GhostSlot) slots.get(PRICE_SLOT), -8);
            case PRICE_PLUS_8 -> GhostSlots.adjust((GhostSlot) slots.get(PRICE_SLOT), 8);
            case TOGGLE_MODE -> priceMode.set((priceMode.get() + 1) % PriceMode.values().length);
            case TOGGLE_BUYBACK -> buyback.set(buyback.get() == 0 ? 1 : 0);
            case CYCLE_ANIMATION -> animation.set((animation.get() + 1) % DisplayAnimation.values().length);
            case ADMIN_INFINITE, ADMIN_BURN, ADMIN_NO_FEE, ADMIN_SERVER_OWNER -> {
                if (!data.adminMode() || !VendingConfigurator.applyAdminToggle(serverPlayer, vendor, buttonId)) {
                    return false;
                }
                adminFlags.set(encodeAdmin(vendor.admin(), vendor.owner() instanceof AccountId.System));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ vanilla rules

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot instanceof GhostSlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < GOODS_SLOT) {
            // Stock or revenue → player inventory
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, STOCK_START, REVENUE_START, false)) {
            // Player inventory → stock (StockSlot.mayPlace only takes the goods)
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && !(slot instanceof RevenueSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (vendor == null) {
            return true;
        }
        return vendor.isWithinReach(player) && player instanceof ServerPlayer serverPlayer
            && (vendor.isOwner(serverPlayer) || (data.adminMode() && dev.eliasnvx.tradery.vending.VendingProtection.isAdmin(serverPlayer)));
    }

    public @Nullable VendingBlockEntity vendor() {
        return vendor;
    }

    /** Stock slots take only the goods of the draft. */
    private final class StockSlot extends Slot {
        StockSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return StackMath.matches(stack, goodsSample());
        }
    }

    /** Item revenue: the owner takes it out; nothing goes in by hand. */
    private static final class RevenueSlot extends Slot {
        RevenueSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
