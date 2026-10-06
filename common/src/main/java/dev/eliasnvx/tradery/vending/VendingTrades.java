package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.api.event.VendingPurchaseEvent;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.api.vending.TradeDirection;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trades with a vending block. Every limit is checked first (stock, the buyer's room, money or price items, the
 * owner's room), then the one fallible step (the money transaction) runs, then the item moves that were already
 * proven to fit. All on the server thread, so two buyers can never take the same last item.
 */
public final class VendingTrades {
    /** Cap for "unlimited" counts (infinite stock, free price); also fits in a menu data slot. */
    public static final int UNLIMITED = 9999;
    /** Trade requests per player per second (spec: 10). */
    static final int RATE_LIMIT = 10;
    private static final Map<UUID, Deque<Long>> RECENT = new ConcurrentHashMap<>();

    private VendingTrades() {
    }

    /**
     * Result of a trade request.
     *
     * @param trades  trades done; 0 = nothing changed
     * @param goods   goods moved (items)
     * @param price   what changed hands for them: money the buyer paid or the seller got (minor units), or price items
     * @param message what to show the player
     */
    public record Outcome(int trades, int goods, long price, Component message) {
        public boolean success() {
            return trades > 0;
        }

        static Outcome fail(Component message) {
            return new Outcome(0, 0, 0, message);
        }
    }

    /** Spec: at most 10 trade requests per second per player; extra requests are dropped. */
    public static boolean allow(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Deque<Long> times = RECENT.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= 1000) {
                times.pollFirst();
            }
            if (times.size() >= RATE_LIMIT) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    public static void forget(ServerPlayer player) {
        RECENT.remove(player.getUUID());
    }

    /** One request from the buyer screen: {@code requested} trades (sale or buyback, as the block is set up). */
    public static Outcome execute(ServerPlayer player, VendingBlockEntity vendor, int requested) {
        VendingSettings settings = vendor.settings();
        if (!settings.isConfigured() || vendor.owner() == null || !(vendor.getLevel() instanceof ServerLevel)) {
            return Outcome.fail(Messages.tr("tradery.vending.not_configured", "This vending block isn't set up yet"));
        }
        if (requested <= 0) {
            return Outcome.fail(Messages.failure(dev.eliasnvx.tradery.api.FailReason.INVALID_AMOUNT));
        }
        if (vendor.isOwner(player) && !vendor.admin().infiniteStock()) {
            return Outcome.fail(Messages.tr("tradery.vending.own_block", "You can't trade with your own vending block"));
        }
        return settings.isBuyback() ? buyback(player, vendor, settings, requested) : sale(player, vendor, settings, requested);
    }

    // ------------------------------------------------------------------ sale: goods to the player

    private static Outcome sale(ServerPlayer player, VendingBlockEntity vendor, VendingSettings settings, int requested) {
        EconomyService economy = EconomyService.INSTANCE;
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        ItemStack goods = settings.goods();
        int perTrade = settings.perTrade();
        AdminFlags admin = vendor.admin();

        int trades = Math.min(requested, vendor.tradesInStock());
        if (trades <= 0) {
            return Outcome.fail(Messages.tr("tradery.vending.empty", "Out of stock"));
        }
        trades = (int) Math.min(trades, StackMath.space(inventory, goods) / perTrade);
        if (trades <= 0) {
            return Outcome.fail(Messages.tr("tradery.vending.no_space", "Not enough room in your inventory"));
        }

        VendingPurchaseEvent.Pre pre = VendingPurchaseEvent.Pre.EVENT.post(new VendingPurchaseEvent.Pre(player, (ServerLevel) vendor.getLevel(),
            vendor.getBlockPos(), vendor.owner(), TradeDirection.SALE, goods, settings.priceMode(), settings.priceItem(), priceOf(settings), trades));
        if (pre.isCancelled() || pre.trades() <= 0) {
            return Outcome.fail(pre.cancelMessage() != null ? pre.cancelMessage() : Messages.tr("tradery.vending.cancelled", "The trade was blocked"));
        }
        trades = Math.min(trades, pre.trades());
        long price = pre.price();
        long fee = 0;
        long moneyPaid = 0;

        if (settings.priceMode() == PriceMode.CURRENCY) {
            Account buyer = economy.account(player.getUUID());
            if (price > 0) {
                trades = (int) Math.min(trades, buyer.balance(economy.defaultCurrency()) / price);
                if (trades <= 0) {
                    return Outcome.fail(Messages.failure(dev.eliasnvx.tradery.api.FailReason.INSUFFICIENT_FUNDS));
                }
                long total;
                try {
                    total = Math.multiplyExact(price, trades);
                } catch (ArithmeticException e) {
                    return Outcome.fail(Messages.failure(dev.eliasnvx.tradery.api.FailReason.LIMIT_EXCEEDED));
                }
                fee = admin.noFee() || admin.burnPayment() ? 0 : Money.percentOf(total, TraderyConfig.server().vending().feePercent());
                Reason reason = Reason.of(Reasons.VENDING_SALE, note(goods, perTrade * trades));
                TransactionResult result = admin.burnPayment()
                    ? economy.withdraw(buyer, total, reason)
                    : economy.transfer(buyer, ownerAccount(vendor), total, fee, reason);
                if (result instanceof TransactionResult.Failure failure) {
                    return Outcome.fail(Messages.failure(failure));
                }
                moneyPaid = total;
            }
        } else {
            ItemStack priceItem = settings.priceItem();
            int pricePerTrade = (int) Math.max(1, Math.min(price, 64L * 36));
            trades = (int) Math.min(trades, StackMath.count(inventory, priceItem) / pricePerTrade);
            if (trades <= 0) {
                return Outcome.fail(Messages.tr("tradery.vending.not_enough_items", "You don't have enough %s", priceItem.getHoverName()));
            }
            if (!admin.burnPayment()) {
                trades = (int) Math.min(trades, StackMath.space(vendor.revenue().getItems(), priceItem) / pricePerTrade);
                if (trades <= 0) {
                    VendingNotifier.ownerFull(vendor);
                    return Outcome.fail(Messages.tr("tradery.vending.owner_full", "The vending block has no room for payment"));
                }
            }
            int priceItems = pricePerTrade * trades;
            int taken = StackMath.take(inventory, priceItem, priceItems);
            if (!admin.burnPayment()) {
                int stored = StackMath.insert(vendor.revenue().getItems(), priceItem, taken);
                warnIfShort("revenue", stored, taken);
                vendor.revenue().setChanged();
            }
            warnIfShort("payment", taken, priceItems);
        }

        int goodsCount = perTrade * trades;
        if (!admin.infiniteStock()) {
            int taken = StackMath.take(vendor.stock().getItems(), goods, goodsCount);
            warnIfShort("stock", taken, goodsCount);
            vendor.stock().setChanged();
        }
        int given = StackMath.insert(inventory, goods, goodsCount);
        warnIfShort("delivery", given, goodsCount);
        player.getInventory().setChanged();

        TradePersistence.traded((ServerLevel) vendor.getLevel(), vendor.getBlockPos(), player);
        VendingPurchaseEvent.Post.EVENT.post(new VendingPurchaseEvent.Post(player, (ServerLevel) vendor.getLevel(), vendor.getBlockPos(),
            vendor.owner(), TradeDirection.SALE, goods, settings.priceMode(), settings.priceItem(), price, trades, fee));
        VendingNotifier.sold(vendor, player, goods, goodsCount, settings.priceMode() == PriceMode.CURRENCY ? moneyPaid - fee : -1,
            settings.priceItem(), (int) price * trades);
        if (!admin.infiniteStock() && vendor.tradesInStock() == 0) {
            VendingNotifier.empty(vendor);
        }
        return new Outcome(trades, goodsCount, price * trades, describe(settings, goodsCount, price * trades));
    }

    // ------------------------------------------------------------------ buyback: goods from the player

    private static Outcome buyback(ServerPlayer player, VendingBlockEntity vendor, VendingSettings settings, int requested) {
        EconomyService economy = EconomyService.INSTANCE;
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        ItemStack goods = settings.goods();
        int perTrade = settings.perTrade();
        AdminFlags admin = vendor.admin();

        int trades = (int) Math.min(requested, StackMath.count(inventory, goods) / perTrade);
        if (trades <= 0) {
            return Outcome.fail(Messages.tr("tradery.vending.not_enough_items", "You don't have enough %s", goods.getHoverName()));
        }
        if (!admin.infiniteStock()) {
            trades = (int) Math.min(trades, StackMath.space(vendor.stock().getItems(), goods) / perTrade);
            if (trades <= 0) {
                return Outcome.fail(Messages.tr("tradery.vending.vendor_full", "The vending block is full"));
            }
        }
        Account owner = ownerAccount(vendor);
        long price = settings.price();
        if (price > 0 && !owner.isInfinite()) {
            trades = (int) Math.min(trades, owner.balance(economy.defaultCurrency()) / price);
            if (trades <= 0) {
                return Outcome.fail(Messages.tr("tradery.vending.owner_broke", "The owner can't afford to buy more"));
            }
        }

        VendingPurchaseEvent.Pre pre = VendingPurchaseEvent.Pre.EVENT.post(new VendingPurchaseEvent.Pre(player, (ServerLevel) vendor.getLevel(),
            vendor.getBlockPos(), vendor.owner(), TradeDirection.BUYBACK, goods, PriceMode.CURRENCY, ItemStack.EMPTY, price, trades));
        if (pre.isCancelled() || pre.trades() <= 0) {
            return Outcome.fail(pre.cancelMessage() != null ? pre.cancelMessage() : Messages.tr("tradery.vending.cancelled", "The trade was blocked"));
        }
        trades = Math.min(trades, pre.trades());
        price = pre.price();
        if (price > 0 && !owner.isInfinite()) {
            trades = (int) Math.min(trades, owner.balance(economy.defaultCurrency()) / price);
            if (trades <= 0) {
                return Outcome.fail(Messages.tr("tradery.vending.owner_broke", "The owner can't afford to buy more"));
            }
        }

        long fee = 0;
        long total = 0;
        if (price > 0) {
            try {
                total = Math.multiplyExact(price, trades);
            } catch (ArithmeticException e) {
                return Outcome.fail(Messages.failure(dev.eliasnvx.tradery.api.FailReason.LIMIT_EXCEEDED));
            }
            fee = admin.noFee() ? 0 : Money.percentOf(total, TraderyConfig.server().vending().feePercent());
            TransactionResult result = economy.transfer(owner, economy.account(player.getUUID()), total, fee,
                Reason.of(Reasons.VENDING_BUYBACK, note(goods, perTrade * trades)));
            if (result instanceof TransactionResult.Failure failure) {
                return Outcome.fail(Messages.failure(failure));
            }
        }
        int goodsCount = perTrade * trades;
        int taken = StackMath.take(inventory, goods, goodsCount);
        warnIfShort("buyback goods", taken, goodsCount);
        player.getInventory().setChanged();
        if (!admin.infiniteStock()) {
            int stored = StackMath.insert(vendor.stock().getItems(), goods, taken);
            warnIfShort("buyback stock", stored, taken);
            vendor.stock().setChanged();
        }

        TradePersistence.traded((ServerLevel) vendor.getLevel(), vendor.getBlockPos(), player);
        VendingPurchaseEvent.Post.EVENT.post(new VendingPurchaseEvent.Post(player, (ServerLevel) vendor.getLevel(), vendor.getBlockPos(),
            vendor.owner(), TradeDirection.BUYBACK, goods, PriceMode.CURRENCY, ItemStack.EMPTY, price, trades, fee));
        VendingNotifier.boughtBack(vendor, player, goods, goodsCount, total);
        return new Outcome(trades, goodsCount, total - fee, describe(settings, goodsCount, total - fee));
    }

    // ------------------------------------------------------------------ helpers

    /** Price of one trade: minor units, or the price item count. */
    static long priceOf(VendingSettings settings) {
        return settings.priceMode() == PriceMode.CURRENCY ? settings.price() : settings.pricePerTrade();
    }

    public static Account ownerAccount(VendingBlockEntity vendor) {
        EconomyService economy = EconomyService.INSTANCE;
        return switch (vendor.owner()) {
            case AccountId.Player player -> economy.account(player.uuid());
            case AccountId.System system -> system.id().equals(EconomyService.SERVER_ACCOUNT)
                ? economy.serverAccount() : economy.systemAccount(system.id());
            case null -> throw new IllegalStateException("Vending block without owner at " + vendor.getBlockPos());
        };
    }

    /** How many trades the player can pay for (or, for buyback, supply), capped for the menu. */
    public static int affordable(ServerPlayer player, VendingBlockEntity vendor) {
        VendingSettings settings = vendor.settings();
        if (!settings.isConfigured()) {
            return 0;
        }
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        if (settings.isBuyback()) {
            return (int) Math.min(UNLIMITED, StackMath.count(inventory, settings.goods()) / settings.perTrade());
        }
        if (settings.priceMode() == PriceMode.ITEM) {
            return (int) Math.min(UNLIMITED, StackMath.count(inventory, settings.priceItem()) / settings.pricePerTrade());
        }
        if (settings.price() <= 0) {
            return UNLIMITED;
        }
        EconomyService economy = EconomyService.INSTANCE;
        return (int) Math.min(UNLIMITED, economy.account(player.getUUID()).balance(economy.defaultCurrency()) / settings.price());
    }

    /**
     * "Bought 4 × Bread for (coin)2.50" / "Sold 8 × Cobblestone for (coin)1.96": a trade (or several added up) as the
     * player sees it in the buyer screen and the action bar.
     *
     * @param price total money (minor units) or price items, as in {@link Outcome#price()}
     */
    public static Component describe(VendingSettings settings, int goods, long price) {
        String key = settings.isBuyback() ? "tradery.vending.sold" : "tradery.vending.bought";
        String fallback = settings.isBuyback() ? "Sold %s × %s for %s" : "Bought %s × %s for %s";
        return Messages.tr(key, fallback, goods, settings.goods().getHoverName(), priceText(settings, price));
    }

    private static Component priceText(VendingSettings settings, long price) {
        if (settings.isBuyback() || settings.priceMode() == PriceMode.CURRENCY) {
            // Shown next to prices drawn with the coin icon
            return price == 0 ? Messages.tr("tradery.vending.free", "free") : Messages.coins(price);
        }
        return Component.literal(price + " × ").append(settings.priceItem().getHoverName());
    }

    private static String note(ItemStack goods, int count) {
        return count + "x " + BuiltInRegistries.ITEM.getKey(goods.getItem());
    }

    /** Pre-checks make these impossible; if one ever happens, it's a bug worth a loud log line. */
    private static void warnIfShort(String what, int done, int expected) {
        if (done != expected) {
            Tradery.LOGGER.error("Vending {} moved {} of {} items; please report this", what, done, expected);
        }
    }
}
