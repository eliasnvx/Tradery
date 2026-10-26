package dev.eliasnvx.tradery.api;

import net.minecraft.resources.ResourceLocation;

/** Reason types Tradery itself uses. Other mods use their own namespace. */
public final class Reasons {
    /** {@code /pay}. */
    public static final ResourceLocation PAY = TraderyApi.id("pay");
    /** A purchase from a vending block: buyer to owner. */
    public static final ResourceLocation VENDING_SALE = TraderyApi.id("vending/sale");
    /** A vending block buying from a player: owner to seller. */
    public static final ResourceLocation VENDING_BUYBACK = TraderyApi.id("vending/buyback");
    /**
     * A villager bought from a player's vending block. The villager pays from its own wallet, which lives outside
     * the ledger, so the money is created: server account to the owner, minus the vending fee.
     */
    public static final ResourceLocation VILLAGER_PURCHASE = TraderyApi.id("villager/purchase");
    /**
     * A trade in a villager's trade window, where emeralds are replaced by money: the player pays the villager
     * (withdrawn to the server account) or the villager pays the player from its wallet (deposited from the server
     * account). The amount's direction tells which.
     */
    public static final ResourceLocation VILLAGER_TRADE = TraderyApi.id("villager/trade");
    /**
     * A villager sold its produce to a player's buyback vending block. The villager's wallet lives outside the
     * ledger, so the money leaves it: the owner pays the server account; the villager's wallet gets the price minus
     * the vending fee.
     */
    public static final ResourceLocation VILLAGER_SUPPLY = TraderyApi.id("villager/supply");
    /**
     * A player delivered items to an order on a village board. The reward was reserved from the village's treasury,
     * a counter outside the ledger, when the order was posted, so the money is created: server account to the player.
     */
    public static final ResourceLocation VILLAGE_ORDER = TraderyApi.id("village/order");
    /**
     * A player funded a project of a village from its board. The cost is burned: withdrawn to the server account,
     * nobody receives it.
     */
    public static final ResourceLocation VILLAGE_PROJECT = TraderyApi.id("village/project");
    /**
     * A player gave money to a village's treasury from its board. The treasury is a counter outside the ledger, so
     * the money leaves the ledger: player to the server account. The village posts orders paid from it.
     *
     * @since 1.1.0
     */
    public static final ResourceLocation VILLAGE_DONATION = TraderyApi.id("village/donation");
    /**
     * A player rented a trading place (market licence) in a village. The rent goes into the village's treasury, a
     * counter outside the ledger, so the money leaves the ledger: player to the server account.
     */
    public static final ResourceLocation VILLAGE_RENT = TraderyApi.id("village/rent");
    /**
     * A trade with a caravan from another village in its trade window, emeralds replaced by money: the player pays
     * the caravan (withdrawn to the server account) or the caravan pays the player from its purse (deposited from the
     * server account). The purse, taken from the origin village's treasury, is a counter outside the ledger. The
     * amount's direction tells which.
     */
    public static final ResourceLocation VILLAGE_CARAVAN = TraderyApi.id("village/caravan");
    /** Coin ore mined straight to the balance. */
    public static final ResourceLocation ORE_MINED = TraderyApi.id("ore/mined");
    /** Coin items turned into balance (pickup or use). */
    public static final ResourceLocation COIN_DEPOSIT = TraderyApi.id("coin/deposit");
    /** Balance turned into coin items ({@code /tradery withdraw}). */
    public static final ResourceLocation COIN_WITHDRAW = TraderyApi.id("coin/withdraw");
    /** Mob kill reward. */
    public static final ResourceLocation REWARD_KILL = TraderyApi.id("reward/kill");
    /** Block mining reward. */
    public static final ResourceLocation REWARD_MINE = TraderyApi.id("reward/mine");
    /** Crafting reward. */
    public static final ResourceLocation REWARD_CRAFT = TraderyApi.id("reward/craft");
    /** Fishing reward. */
    public static final ResourceLocation REWARD_FISH = TraderyApi.id("reward/fish");
    /** Advancement reward. */
    public static final ResourceLocation REWARD_ADVANCEMENT = TraderyApi.id("reward/advancement");
    /** {@code /eco give}. */
    public static final ResourceLocation ADMIN_GIVE = TraderyApi.id("admin/give");
    /** {@code /eco take}. */
    public static final ResourceLocation ADMIN_TAKE = TraderyApi.id("admin/take");
    /** {@code /eco set}. */
    public static final ResourceLocation ADMIN_SET = TraderyApi.id("admin/set");
    /** Starting balance of a new account. */
    public static final ResourceLocation STARTING_BALANCE = TraderyApi.id("account/starting_balance");

    private Reasons() {
    }
}
