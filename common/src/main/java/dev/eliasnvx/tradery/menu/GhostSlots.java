package dev.eliasnvx.tradery.menu;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Click rules of {@link GhostSlot}s, shared by the menus. Runs on both sides with the same result. */
public final class GhostSlots {
    private GhostSlots() {
    }

    /**
     * Left click with an item: sample = a copy of the cursor stack. Right click with an item: a copy of one.
     * Click with an empty cursor: clear. Everything else (shift, number keys, drop, drag) does nothing.
     * The cursor stack is never touched.
     */
    static void click(AbstractContainerMenu menu, GhostSlot slot, int button, ClickType clickType) {
        if (clickType != ClickType.PICKUP) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else if (slot.accepts(carried)) {
            int count = button == 1 ? 1 : Math.min(carried.getCount(), carried.getMaxStackSize());
            slot.set(carried.copyWithCount(count));
        }
    }

    /**
     * A recipe viewer dropped {@code stack} on sample slot {@code index} of the player's open menu: accepted only for
     * an active sample slot of a Tradery menu that is still valid, and only items the slot accepts.
     */
    public static void setFromViewer(net.minecraft.server.level.ServerPlayer player, AbstractContainerMenu menu, int index, ItemStack stack) {
        if (!(menu instanceof VendingMenu) || index < 0 || index >= menu.slots.size() || !menu.stillValid(player)
            || !(menu.slots.get(index) instanceof GhostSlot slot) || !slot.isActive()) {
            return;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else if (slot.accepts(stack)) {
            slot.set(stack.copyWithCount(Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize()))));
        }
    }

    /** Adds {@code delta} to the sample count, keeping it within 1..max stack size. */
    static void adjust(GhostSlot slot, int delta) {
        ItemStack sample = slot.getItem();
        if (sample.isEmpty()) {
            return;
        }
        int count = Math.max(1, Math.min(sample.getMaxStackSize(), sample.getCount() + delta));
        slot.set(sample.copyWithCount(count));
    }
}
