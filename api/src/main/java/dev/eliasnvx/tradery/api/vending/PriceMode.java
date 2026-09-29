package dev.eliasnvx.tradery.api.vending;

/** How a vending block is paid. */
public enum PriceMode {
    /** Paid with items (classic Vending Block): price = item count of the price item per trade. */
    ITEM,
    /** Paid with money: price = minor units per trade. */
    CURRENCY
}
