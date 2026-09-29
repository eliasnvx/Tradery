package dev.eliasnvx.tradery.api;

import net.minecraft.resources.Identifier;

/** Reason types Tradery itself uses. Other mods use their own namespace. */
public final class Reasons {
    /** {@code /pay}. */
    public static final Identifier PAY = TraderyApi.id("pay");
    /** A purchase from a vending block: buyer to owner. */
    public static final Identifier VENDING_SALE = TraderyApi.id("vending/sale");
    /** A vending block buying from a player: owner to seller. */
    public static final Identifier VENDING_BUYBACK = TraderyApi.id("vending/buyback");
    /** Coin ore mined straight to the balance. */
    public static final Identifier ORE_MINED = TraderyApi.id("ore/mined");
    /** Coin items turned into balance (pickup or use). */
    public static final Identifier COIN_DEPOSIT = TraderyApi.id("coin/deposit");
    /** Balance turned into coin items ({@code /tradery withdraw}). */
    public static final Identifier COIN_WITHDRAW = TraderyApi.id("coin/withdraw");
    /** Mob kill reward. */
    public static final Identifier REWARD_KILL = TraderyApi.id("reward/kill");
    /** Block mining reward. */
    public static final Identifier REWARD_MINE = TraderyApi.id("reward/mine");
    /** Crafting reward. */
    public static final Identifier REWARD_CRAFT = TraderyApi.id("reward/craft");
    /** Fishing reward. */
    public static final Identifier REWARD_FISH = TraderyApi.id("reward/fish");
    /** Advancement reward. */
    public static final Identifier REWARD_ADVANCEMENT = TraderyApi.id("reward/advancement");
    /** {@code /eco give}. */
    public static final Identifier ADMIN_GIVE = TraderyApi.id("admin/give");
    /** {@code /eco take}. */
    public static final Identifier ADMIN_TAKE = TraderyApi.id("admin/take");
    /** {@code /eco set}. */
    public static final Identifier ADMIN_SET = TraderyApi.id("admin/set");
    /** Starting balance of a new account. */
    public static final Identifier STARTING_BALANCE = TraderyApi.id("account/starting_balance");

    private Reasons() {
    }
}
