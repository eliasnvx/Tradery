package dev.eliasnvx.tradery.village;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A villager's offer in money (SPEC "Торговля с жителями за валюту"): emeralds on either side become amounts of
 * {@code emeraldValue} each, everything else stays an item. Pure, so the window on the client shows exactly what
 * the server charges.
 *
 * @param price     money the player pays (minor units); {@link Long#MAX_VALUE} if it overflows
 * @param basePrice the price before demand and discounts (struck out in the window when it differs)
 * @param costA     first item the player pays, or empty when it was emeralds
 * @param costB     second item the player pays, or empty
 * @param payout    money the villager pays (minor units); {@link Long#MAX_VALUE} if it overflows
 * @param result    item the player gets, or empty when it was emeralds
 */
public record TradeTerms(long price, long basePrice, ItemStack costA, ItemStack costB, long payout, ItemStack result) {
    /** The offer with its current price (demand and the trading player's special price included). */
    public static TradeTerms of(MerchantOffer offer, long emeraldValue) {
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();
        long price = 0;
        long basePrice = 0;
        if (isEmerald(costA)) {
            price = money(costA.getCount(), emeraldValue);
            basePrice = money(offer.getBaseCostA().getCount(), emeraldValue);
            costA = ItemStack.EMPTY;
        }
        if (isEmerald(costB)) {
            long more = money(costB.getCount(), emeraldValue);
            price = saturatedAdd(price, more);
            basePrice = saturatedAdd(basePrice, more);
            costB = ItemStack.EMPTY;
        }
        long payout = 0;
        if (isEmerald(result)) {
            payout = money(result.getCount(), emeraldValue);
            result = ItemStack.EMPTY;
        }
        return new TradeTerms(price, basePrice, costA, costB, payout, result);
    }

    /** A plain emerald (one with a name or other data stays an item). */
    public static boolean isEmerald(ItemStack stack) {
        return stack.is(Items.EMERALD) && stack.getComponentsPatch().isEmpty();
    }

    /** Neither side overflowed. */
    public boolean valid() {
        return price != Long.MAX_VALUE && payout != Long.MAX_VALUE;
    }

    private static long money(int emeralds, long emeraldValue) {
        try {
            return Math.multiplyExact(emeralds, emeraldValue);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
