package dev.eliasnvx.tradery.menu;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Click rules of {@link GhostSlot}s, shared by the menus. Runs on both sides with the same result. */
final class GhostSlots {
    private GhostSlots() {
    }

    /**
     * Left click with an item: sample = a copy of the cursor stack. Right click with an item: a copy of one.
     * Click with an empty cursor: clear. Everything else (shift, number keys, drop, drag) does nothing.
     * The cursor stack is never touched.
     */
    static void click(AbstractContainerMenu menu, GhostSlot slot, int button, ContainerInput input) {
        if (input != ContainerInput.PICKUP) {
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
