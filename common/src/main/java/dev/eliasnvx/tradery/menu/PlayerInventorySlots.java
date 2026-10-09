package dev.eliasnvx.tradery.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.function.Consumer;

/** The player's 27 inventory slots and 9 hotbar slots, laid out as in every vanilla container screen. */
final class PlayerInventorySlots {
    static final int SLOT_SIZE = 18;
    /** Gap between the inventory grid and the hotbar, in pixels. */
    static final int HOTBAR_GAP = 4;

    private PlayerInventorySlots() {
    }

    /**
     * Adds the inventory grid (container slots 9..35) at {@code left, top}, then the hotbar (slots 0..8) under it.
     *
     * @param addSlot the menu's {@code addSlot}
     */
    static void add(Consumer<Slot> addSlot, Container inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(new Slot(inventory, col + (row + 1) * 9, left + col * SLOT_SIZE, top + row * SLOT_SIZE));
            }
        }
        int hotbarTop = top + 3 * SLOT_SIZE + HOTBAR_GAP;
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(inventory, col, left + col * SLOT_SIZE, hotbarTop));
        }
    }
}
