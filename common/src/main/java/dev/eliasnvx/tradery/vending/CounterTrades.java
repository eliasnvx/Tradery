package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.FailReason;
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

import java.util.List;
import java.util.function.Predicate;

/**
 * Trades at a market counter, the same way as {@link VendingTrades}: every limit is checked first (the offer, its
 * stock, the buyer's room and money), then the one fallible step (the money transaction) runs, then the item move
 * that was already proven to fit. All on the server thread, so two buyers never take the same last item. Money goes
 * to the owner minus {@code vending.feePercent} (burnt), like a vending sale.
 */
public final class CounterTrades {
    private CounterTrades() {
    }

    /** A player buys up to {@code requested} trades of offer {@code index}. Nothing changes unless it succeeds. */
    public static VendingTrades.Outcome buy(ServerPlayer player, MarketCounterBlockEntity counter, int index, int requested) {
        if (counter.owner() == null || !(counter.getLevel() instanceof ServerLevel level) || counter.isRemoved()) {
            return fail(Messages.tr("tradery.counter.not_configured", "This market counter isn't set up yet"));
        }
        if (index < 0 || index >= CounterSettings.MAX_OFFERS || !counter.settings().offer(index).isConfigured()) {
            return fail(Messages.tr("tradery.counter.no_offer", "There's no such offer here"));
        }
        if (requested <= 0) {
            return fail(Messages.failure(FailReason.INVALID_AMOUNT));
        }
        if (counter.isOwner(player)) {
            return fail(Messages.tr("tradery.counter.own", "You can't trade with your own market counter"));
        }
        CounterOffer offer = counter.settings().offer(index);
        EconomyService economy = EconomyService.INSTANCE;
        List<ItemStack> inventory = player.getInventory().items;
        ItemStack goods = offer.goods();
        int perTrade = offer.perTrade();

        int trades = Math.min(requested, counter.tradesInStock(index));
        if (trades <= 0) {
            return fail(Messages.tr("tradery.vending.empty", "Out of stock"));
        }
        trades = (int) Math.min(trades, StackMath.space(inventory, goods) / perTrade);
        if (trades <= 0) {
            return fail(Messages.tr("tradery.vending.no_space", "Not enough room in your inventory"));
        }

        VendingPurchaseEvent.Pre pre = VendingPurchaseEvent.Pre.EVENT.post(new VendingPurchaseEvent.Pre(player, level, counter.getBlockPos(),
            counter.owner(), TradeDirection.SALE, goods, PriceMode.CURRENCY, ItemStack.EMPTY, offer.price(), trades));
        if (pre.isCancelled() || pre.trades() <= 0) {
            return fail(pre.cancelMessage() != null ? pre.cancelMessage() : Messages.tr("tradery.vending.cancelled", "The trade was blocked"));
        }
        trades = Math.min(trades, pre.trades());
        long price = pre.price();
        long fee = 0;
        long total = 0;
        if (price > 0) {
            Account buyer = economy.account(player.getUUID());
            trades = (int) Math.min(trades, buyer.balance(economy.defaultCurrency()) / price);
            if (trades <= 0) {
                return fail(Messages.failure(FailReason.INSUFFICIENT_FUNDS));
            }
            try {
                total = Math.multiplyExact(price, trades);
            } catch (ArithmeticException e) {
                return fail(Messages.failure(FailReason.LIMIT_EXCEEDED));
            }
            fee = Money.percentOf(total, TraderyConfig.server().vending().feePercent());
            TransactionResult result = economy.transfer(buyer, ownerAccount(counter), total, fee,
                Reason.of(Reasons.VENDING_SALE, note(goods, perTrade * trades)));
            if (result instanceof TransactionResult.Failure failure) {
                return fail(Messages.failure(failure));
            }
        }

        // Nothing below can fail: the stock and the room were proven above
        int goodsCount = perTrade * trades;
        int taken = StackMath.take(counter.stock().getItems(), goods, goodsCount);
        warnIfShort("stock", taken, goodsCount);
        counter.stock().setChanged();
        int given = StackMath.insert(inventory, goods, goodsCount);
        warnIfShort("delivery", given, goodsCount);
        player.getInventory().setChanged();

        TradePersistence.traded(level, counter.getBlockPos(), player);
        VendingPurchaseEvent.Post.EVENT.post(new VendingPurchaseEvent.Post(player, level, counter.getBlockPos(), counter.owner(),
            TradeDirection.SALE, goods, PriceMode.CURRENCY, ItemStack.EMPTY, price, trades, fee));
        CounterNotifier.sold(counter, player, goods, goodsCount, total - fee);
        if (counter.tradesInStock(index) == 0) {
            CounterNotifier.offerEmpty(counter, goods);
        }
        return new VendingTrades.Outcome(trades, goodsCount, total, describe(goods, goodsCount, total));
    }

    /** How many trades of offer {@code index} the player can pay for, capped for the menu. */
    public static int affordable(ServerPlayer player, MarketCounterBlockEntity counter, int index) {
        CounterOffer offer = counter.settings().offer(index);
        if (!offer.isConfigured()) {
            return 0;
        }
        if (offer.price() <= 0) {
            return VendingTrades.UNLIMITED;
        }
        EconomyService economy = EconomyService.INSTANCE;
        return (int) Math.min(VendingTrades.UNLIMITED, economy.account(player.getUUID()).balance(economy.defaultCurrency()) / offer.price());
    }

    // ------------------------------------------------------------------ villagers (hooks for the customer manager)

    /**
     * Villagers may buy here: a player's counter with "Villagers can buy" on. Server-owned counters don't serve
     * villagers, like vending blocks.
     */
    public static boolean servesVillagers(MarketCounterBlockEntity counter) {
        return !counter.isRemoved() && counter.getLevel() instanceof ServerLevel && counter.owner() instanceof AccountId.Player
            && counter.settings().villagers() && counter.settings().isConfigured();
    }

    /** Offer {@code index} can serve a villager now: configured, priced above zero, in stock. */
    public static boolean servesVillagers(MarketCounterBlockEntity counter, int index) {
        CounterOffer offer = counter.settings().offer(index);
        return servesVillagers(counter) && offer.isConfigured() && offer.price() > 0 && counter.tradesInStock(index) > 0;
    }

    /**
     * The first offer a villager would buy: one that {@link #servesVillagers(MarketCounterBlockEntity, int) serves
     * villagers} and whose goods pass {@code wanted} (the villager's wants). -1 when none.
     */
    public static int pickOffer(MarketCounterBlockEntity counter, Predicate<ItemStack> wanted) {
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            if (servesVillagers(counter, i) && wanted.test(counter.settings().offer(i).goods())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A villager buys up to {@code lots} trades of offer {@code index}, as {@link VendingTrades#sellToVillager} does
     * for a vending block: the limits first, then the money (the server account pays the owner the price minus the
     * fee, since the villager's wallet lives outside the ledger), then the stock move that was proven to fit. The
     * goods vanish. The caller checked the wallet and the village's daily cap for {@code lots}; it debits them with
     * {@link VendingTrades.Outcome#price()} on success.
     *
     * @param customer the villager's profession for the owner's notification ({@link VendingNotifier#translatable} key or text)
     */
    public static VendingTrades.Outcome sellToVillager(MarketCounterBlockEntity counter, int index, String customer, int lots) {
        if (index < 0 || index >= CounterSettings.MAX_OFFERS || !servesVillagers(counter)) {
            return fail(Messages.tr("tradery.counter.not_for_villagers", "This market counter doesn't sell to villagers"));
        }
        CounterOffer offer = counter.settings().offer(index);
        if (!offer.isConfigured() || offer.price() <= 0) {
            return fail(Messages.tr("tradery.counter.not_for_villagers", "This market counter doesn't sell to villagers"));
        }
        if (lots <= 0) {
            return fail(Messages.failure(FailReason.INVALID_AMOUNT));
        }
        ItemStack goods = offer.goods();
        int perTrade = offer.perTrade();
        int trades = Math.min(lots, counter.tradesInStock(index));
        if (trades <= 0) {
            return fail(Messages.tr("tradery.vending.empty", "Out of stock"));
        }
        long total;
        try {
            total = Math.multiplyExact(offer.price(), trades);
        } catch (ArithmeticException e) {
            return fail(Messages.failure(FailReason.LIMIT_EXCEEDED));
        }
        EconomyService economy = EconomyService.INSTANCE;
        long fee = Money.percentOf(total, TraderyConfig.server().vending().feePercent());
        TransactionResult result = economy.transfer(economy.serverAccount(), ownerAccount(counter), total, fee,
            Reason.of(Reasons.VILLAGER_PURCHASE, note(goods, perTrade * trades)));
        if (result instanceof TransactionResult.Failure failure) {
            return fail(Messages.failure(failure));
        }
        TransactionResult.Success success = (TransactionResult.Success) result;

        int goodsCount = perTrade * trades;
        int taken = StackMath.take(counter.stock().getItems(), goods, goodsCount);
        warnIfShort("villager stock", taken, goodsCount);
        counter.stock().setChanged();

        TradePersistence.traded((ServerLevel) counter.getLevel(), counter.getBlockPos());
        CounterNotifier.villagerBought(counter, customer, goods, goodsCount, success.amount() - success.fee());
        if (counter.tradesInStock(index) == 0) {
            CounterNotifier.offerEmpty(counter, goods);
        }
        return new VendingTrades.Outcome(trades, goodsCount, success.amount(), describe(goods, goodsCount, success.amount()));
    }

    // ------------------------------------------------------------------ helpers

    public static Account ownerAccount(MarketCounterBlockEntity counter) {
        EconomyService economy = EconomyService.INSTANCE;
        AccountId owner = counter.owner();
        if (owner instanceof AccountId.Player player) {
            return economy.account(player.uuid());
        }
        if (owner instanceof AccountId.System system) {
            return system.id().equals(EconomyService.SERVER_ACCOUNT) ? economy.serverAccount() : economy.systemAccount(system.id());
        }
        throw new IllegalStateException("Market counter without owner at " + counter.getBlockPos());
    }

    /** "Bought 4 × Bread for (coin)2.50". */
    public static Component describe(ItemStack goods, int count, long price) {
        return Messages.tr("tradery.vending.bought", "Bought %s × %s for %s", count, goods.getHoverName(),
            price == 0 ? Messages.tr("tradery.vending.free", "free") : Messages.coins(price));
    }

    private static VendingTrades.Outcome fail(Component message) {
        return new VendingTrades.Outcome(0, 0, 0, message);
    }

    private static String note(ItemStack goods, int count) {
        return count + "x " + BuiltInRegistries.ITEM.getKey(goods.getItem());
    }

    /** Pre-checks make these impossible; if one ever happens, it's a bug worth a loud log line. */
    private static void warnIfShort(String what, int done, int expected) {
        if (done != expected) {
            Tradery.LOGGER.error("Market counter {} moved {} of {} items; please report this", what, done, expected);
        }
    }
}
