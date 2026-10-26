package dev.eliasnvx.tradery.village;

import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;

/**
 * What a villager offer asks for, as 1.20.5+'s {@code ItemCost}: 1.20.1 offers keep plain stacks. {@link #test} is
 * 1.20.1's {@code MerchantOffer#isRequiredItem}: the item, and the cost's NBT (if any) contained in the stack's.
 */
public record ItemCost(ItemStack itemStack) {
    public ItemCost(ItemLike item, int count) {
        this(new ItemStack(item, count));
    }

    public ItemCost(ItemLike item) {
        this(item, 1);
    }

    /** The first cost of {@code offer} before discounts (as {@code MerchantOffer#getItemCostA} later). */
    public static ItemCost a(MerchantOffer offer) {
        return new ItemCost(offer.getBaseCostA());
    }

    /** The second cost of {@code offer}, if it has one. */
    public static Optional<ItemCost> b(MerchantOffer offer) {
        return offer.getCostB().isEmpty() ? Optional.empty() : Optional.of(new ItemCost(offer.getCostB()));
    }

    public Item item() {
        return itemStack.getItem();
    }

    public int count() {
        return itemStack.getCount();
    }

    public boolean test(ItemStack offered) {
        if (!offered.is(itemStack.getItem())) {
            return false;
        }
        return !itemStack.hasTag() || offered.hasTag() && NbtUtils.compareNbt(itemStack.getTag(), offered.getTag(), false);
    }
}
