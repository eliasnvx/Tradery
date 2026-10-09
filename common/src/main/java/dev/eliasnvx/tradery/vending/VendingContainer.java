package dev.eliasnvx.tradery.vending;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Storage of a vending block. Deliberately not exposed as the block's own {@link net.minecraft.world.Container}:
 * hoppers and pipes can't reach it, only the owner's menu and the trade code do. Behaves like a
 * {@link net.minecraft.world.SimpleContainer} (which on 1.20.1 hides its list) and exposes the live slots through
 * {@link #getItems()}.
 */
public final class VendingContainer implements Container {
    private final NonNullList<ItemStack> items;
    private final Runnable onChanged;

    public VendingContainer(int size, Runnable onChanged) {
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.onChanged = onChanged;
    }

    /** The live slots: changes go straight into the storage (call {@link #setChanged()} after). */
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        onChanged.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu checks the block entity itself
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
