package dev.eliasnvx.tradery.api.vending;

/** Which way goods go in a vending trade. */
public enum TradeDirection {
    /** The player buys goods from the vending block. */
    SALE,
    /** The vending block buys goods from the player (buyback mode, money only). */
    BUYBACK
}
