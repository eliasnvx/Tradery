package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The owner of a display block picks the shown item (a sample, not a real item) and the animation. */
public class DisplayMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int SAMPLE_SLOT = 0;
    public static final int CYCLE_ANIMATION = 0;
    public static final int SAMPLE_X = 80;
    public static final int SAMPLE_Y = 26;
    public static final int INVENTORY_TOP = 70;

    public record Data(BlockPos pos, ItemStack shown, DisplayAnimation animation) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeItem(shown);
            buf.writeVarInt(animation.ordinal());
        }

        public static Data read(FriendlyByteBuf buf) {
            return new Data(buf.readBlockPos(), buf.readItem(), VendingSettings.enumAt(DisplayAnimation.values(), buf.readVarInt()));
        }
    }

    private final @Nullable DisplayBlockEntity display;
    private final Data data;
    /** The shown item; edits go straight to the block entity (server side). */
    private final SimpleContainer sample;
    private final DataSlot animation = DataSlot.standalone();

    public DisplayMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null);
    }

    public DisplayMenu(int containerId, Inventory inventory, Data data, @Nullable DisplayBlockEntity display) {
        super(TraderyMenus.DISPLAY.get(), containerId);
        this.display = display;
        this.data = data;
        // Starts with the current item without writing it back to the block
        sample = new SimpleContainer(data.shown().copy()) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (DisplayMenu.this.display != null) {
                    DisplayMenu.this.display.setShown(getItem(0));
                }
            }
        };
        addSlot(new GhostSlot(sample, 0, SAMPLE_X, SAMPLE_Y, VendingConfigurator::isTradeable));
        PlayerInventorySlots.add(this::addSlot, inventory, 8, INVENTORY_TOP);
        animation.set(data.animation().ordinal());
        addDataSlot(animation);
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    public DisplayAnimation animation() {
        return VendingSettings.enumAt(DisplayAnimation.values(), Math.floorMod(animation.get(), DisplayAnimation.values().length));
    }

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
        if (display == null || buttonId != CYCLE_ANIMATION || !stillValid(player)) {
            return false;
        }
        animation.set((animation.get() + 1) % DisplayAnimation.values().length);
        display.setAnimation(animation());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // nothing moves: the only non-inventory slot is a sample
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        return display == null || (!display.isRemoved() && display.getLevel() == player.level()
            && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(display.getBlockPos())) <= 64);
    }
}
