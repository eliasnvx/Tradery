package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.registry.TraderyItems;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Coin values and splitting amounts into coins. */
public final class Coins {
    private Coins() {
    }

    public static boolean isCoin(ItemStack stack) {
        return stack.getItem() instanceof CoinItem;
    }

    /** Value of a stack of coins (0 for anything else), minor units. */
    public static long value(ItemStack stack) {
        if (!(stack.getItem() instanceof CoinItem coin)) {
            return 0;
        }
        long each = coin.tier().value();
        return each > Long.MAX_VALUE / Math.max(1, stack.getCount()) ? Long.MAX_VALUE : each * stack.getCount();
    }

    /**
     * Splits an amount into the fewest coins (largest first).
     *
     * @return coin stacks; {@link Split#remainder()} is what no coin covers
     */
    public static Split split(long amount) {
        List<ItemStack> stacks = new ArrayList<>();
        long left = amount;
        for (CoinTier tier : CoinTier.DESCENDING) {
            long value = tier.value();
            if (value <= 0 || left < value) {
                continue;
            }
            long count = left / value;
            left -= count * value;
            ItemStack sample = new ItemStack(TraderyItems.coin(tier));
            int max = sample.getMaxStackSize();
            while (count > 0) {
                int n = (int) Math.min(max, count);
                stacks.add(sample.copyWithCount(n));
                count -= n;
            }
        }
        return new Split(stacks, left);
    }

    public record Split(List<ItemStack> stacks, long remainder) {
    }
}
