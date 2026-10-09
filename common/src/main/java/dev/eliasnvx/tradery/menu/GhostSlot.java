package dev.eliasnvx.tradery.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A sample slot: shows a copy of an item and never holds a real one. Clicks are handled by the menu
 * ({@link GhostSlots#click}); vanilla can neither take from nor put into it.
 */
public class GhostSlot extends Slot {
    private final Predicate<ItemStack> accepts;
    private final BooleanSupplier active;

    public GhostSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts) {
        this(container, index, x, y, accepts, () -> true);
    }

    /** @param active whether the slot is shown and clickable right now (e.g. only in item-price mode) */
    public GhostSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts, BooleanSupplier active) {
        super(container, index, x, y);
        this.accepts = accepts;
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }

    /** Whether this item may become the sample. */
    public boolean accepts(ItemStack stack) {
        return accepts.test(stack);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
