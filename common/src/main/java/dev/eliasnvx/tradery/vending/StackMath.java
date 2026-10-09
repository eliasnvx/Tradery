package dev.eliasnvx.tradery.vending;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Counting, fitting, taking and inserting items of one kind in a list of slots. Items match only when item and
 * NBT are the same ({@link ItemStack#isSameItemSameTags}): a damaged sword never pays for a new one.
 * Every method works on the live stacks of the list, so menus watching the slots see the changes.
 */
public final class StackMath {
    private StackMath() {
    }

    public static boolean matches(ItemStack stack, ItemStack sample) {
        return !stack.isEmpty() && !sample.isEmpty() && ItemStack.isSameItemSameTags(stack, sample);
    }

    /** Items matching {@code sample} in the slots. */
    public static long count(List<ItemStack> slots, ItemStack sample) {
        long total = 0;
        for (ItemStack stack : slots) {
            if (matches(stack, sample)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** How many items like {@code sample} fit into the slots. */
    public static long space(List<ItemStack> slots, ItemStack sample) {
        if (sample.isEmpty()) {
            return 0;
        }
        int max = sample.getMaxStackSize();
        long total = 0;
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                total += max;
            } else if (matches(stack, sample)) {
                total += Math.max(0, max - stack.getCount());
            }
        }
        return total;
    }

    /**
     * Takes up to {@code amount} matching items, from the last slot backwards (keeps the first slots full).
     *
     * @return items taken
     */
    public static int take(List<ItemStack> slots, ItemStack sample, int amount) {
        int left = amount;
        for (int i = slots.size() - 1; i >= 0 && left > 0; i--) {
            ItemStack stack = slots.get(i);
            if (matches(stack, sample)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                if (stack.isEmpty()) {
                    slots.set(i, ItemStack.EMPTY);
                }
                left -= taken;
            }
        }
        return amount - left;
    }

    /**
     * Inserts up to {@code amount} copies of {@code sample}: tops up matching stacks first, then empty slots.
     *
     * @return items inserted
     */
    public static int insert(List<ItemStack> slots, ItemStack sample, int amount) {
        if (sample.isEmpty()) {
            return 0;
        }
        int max = sample.getMaxStackSize();
        int left = amount;
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (matches(stack, sample) && stack.getCount() < max) {
                int added = Math.min(left, max - stack.getCount());
                stack.grow(added);
                left -= added;
            }
        }
        for (int i = 0; i < slots.size() && left > 0; i++) {
            if (slots.get(i).isEmpty()) {
                int added = Math.min(left, max);
                slots.set(i, sample.copyWithCount(added));
                left -= added;
            }
        }
        return amount - left;
    }
}
