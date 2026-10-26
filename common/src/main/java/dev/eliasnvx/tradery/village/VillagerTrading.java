package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The villager trade window in money (SPEC "Торговля с жителями за валюту"): the villager's own offers, every
 * emerald worth {@code villagerTrading.emeraldValue}. Emeralds the player would pay come off the balance into the
 * villager's wallet; emeralds the villager would pay come out of its wallet, so an empty wallet means "come back
 * tomorrow". A caravan's trader (SPEC "Торговля между деревнями", {@link Caravans}) trades in the same window: its
 * offers' single emerald is worth that offer's own price and the money goes into or out of the caravan's purse.
 * Server thread.
 */
public final class VillagerTrading {
    /** Most single trades one "trade all" runs (vanilla offers allow at most 16 uses; modded ones may allow more). */
    static final int MAX_BATCH = 64;

    private VillagerTrading() {
    }

    /** {@code villagerTrading.enabled}, and the economy is running. */
    public static boolean enabled() {
        return TraderyConfig.server().villagerTrading().enabled() && EconomyService.INSTANCE.isReady();
    }

    /** Money for one emerald (minor units). */
    public static long emeraldValue() {
        return VillagerPurchases.toMinor(TraderyConfig.server().villagerTrading().emeraldValue());
    }

    /**
     * Opens the money window instead of vanilla's. Called by {@code VillagerMixin} where vanilla would start
     * trading, after it set this player's special prices (reputation, Hero of the Village), and for a caravan's
     * trader by {@code WanderingTraderMixin}.
     */
    public static void open(ServerPlayer player, AbstractVillager merchant) {
        merchant.setTradingPlayer(player);
        VillagerTradeMenu.Data data = snapshot(merchant);
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new VillagerTradeMenu(id, inventory, data, merchant), merchant.getDisplayName()),
            VillagerTradeMenu.Data.STREAM_CODEC, data);
        if (!(player.containerMenu instanceof VillagerTradeMenu)) {
            merchant.setTradingPlayer(null);
        }
    }

    /**
     * What the window shows: a copy of the offers, the villager's level and experience, and its wallet; for a caravan,
     * its offers with what each is worth, and its purse (no offers once the caravan is over).
     */
    public static VillagerTradeMenu.Data snapshot(AbstractVillager merchant) {
        MinecraftServer server = ((ServerLevel) merchant.level()).getServer();
        if (merchant instanceof Villager villager) {
            long wallet = VillagerPurchases.wallet(villager, VillagerPurchases.today(server));
            return new VillagerTradeMenu.Data(villager.getOffers().copy(), villager.getVillagerData().getLevel(), villager.getVillagerXp(),
                villager.showProgressBar(), emeraldValue(), wallet, List.of(), false);
        }
        CaravanData.Caravan caravan = Caravans.of(merchant);
        MerchantOffers offers = caravan != null && caravan.values().size() == merchant.getOffers().size()
            ? merchant.getOffers().copy() : new MerchantOffers();
        return new VillagerTradeMenu.Data(offers, 0, 0, false, emeraldValue(), caravan == null ? 0 : caravan.purse(),
            offers.isEmpty() ? List.of() : caravan.values(), true);
    }

    /**
     * The other side of the window: a villager with its wallet, or a caravan with its purse. Says what an emerald in
     * an offer is worth, how much money it has, and books a trade whose money already moved.
     */
    private interface Partner {
        /** Money for one emerald in the offer at {@code index} (minor units). */
        long emeraldValue(int index);

        /** What it can pay today (minor units). */
        long money(long today);

        /** What stays with the partner out of {@code paid} (a villager's village takes its trade tax first). */
        default long keeps(long paid) {
            return paid;
        }

        /** The money of one trade moved: the player paid {@code paid} and got {@code payout}. Can't fail. */
        void settle(long paid, long payout, long today);

        Component outOfMoney();

        Component usedUp();

        ResourceLocation reason();
    }

    private record VillagerPartner(Villager villager, MinecraftServer server) implements Partner {
        @Override
        public long emeraldValue(int index) {
            return VillagerTrading.emeraldValue();
        }

        @Override
        public long money(long today) {
            return VillagerPurchases.wallet(villager, today);
        }

        @Override
        public long keeps(long paid) {
            return Villages.of(villager) == null ? paid : paid - Money.percentOf(paid, Villages.config().tradeTaxPercent());
        }

        @Override
        public void settle(long paid, long payout, long today) {
            VillagerLedger ledger = VillagerLedger.get(server);
            // The village takes its trade tax from what the player paid; the villager keeps the rest
            long tax = Villages.onWindowTrade(villager, paid, payout);
            if (paid - tax > 0) {
                ledger.receive(villager.getUUID(), paid - tax, today);
            }
            if (payout > 0) {
                ledger.pay(villager.getUUID(), payout);
            }
        }

        @Override
        public Component outOfMoney() {
            return Messages.tr("tradery.villager_trade.no_money", "%s is out of money for today. Come back tomorrow", villager.getDisplayName());
        }

        @Override
        public Component usedUp() {
            return Messages.tr("tradery.villager_trade.out_of_stock", "This trade is used up. The villager restocks at its workstation");
        }

        @Override
        public ResourceLocation reason() {
            return Reasons.VILLAGER_TRADE;
        }
    }

    private record CaravanPartner(CaravanData.Caravan caravan, MinecraftServer server) implements Partner {
        @Override
        public long emeraldValue(int index) {
            return caravan.values().get(index);
        }

        @Override
        public long money(long today) {
            return caravan.purse();
        }

        @Override
        public void settle(long paid, long payout, long today) {
            Caravans.settle(server, caravan, paid, payout);
        }

        @Override
        public Component outOfMoney() {
            return Messages.tr("tradery.caravan.no_money", "The caravan's purse is empty");
        }

        @Override
        public Component usedUp() {
            return Messages.tr("tradery.caravan.sold_out", "The caravan has no more of this");
        }

        @Override
        public ResourceLocation reason() {
            return Reasons.VILLAGE_CARAVAN;
        }
    }

    /**
     * @param trades  single trades done
     * @param message what the player reads in the window
     */
    public record Outcome(boolean success, int trades, Component message) {
        static Outcome fail(Component message) {
            return new Outcome(false, 0, message);
        }
    }

    /**
     * Runs the offer at {@code index} once, or with {@code all} as often as money, items, the wallet and the offer's
     * uses allow. Every single trade is whole or nothing: checked first, then the money moves (it may still be
     * refused), then the items.
     */
    public static Outcome trade(ServerPlayer player, AbstractVillager merchant, int index, boolean all) {
        MinecraftServer server = player.level().getServer();
        Partner partner;
        if (merchant instanceof Villager villager) {
            if (!enabled()) {
                return Outcome.fail(Messages.tr("tradery.villager_trade.off", "Trading for money is turned off"));
            }
            partner = new VillagerPartner(villager, server);
        } else {
            CaravanData.Caravan caravan = Caravans.of(merchant);
            if (caravan == null) {
                return Outcome.fail(Messages.tr("tradery.caravan.closed", "This caravan has packed up"));
            }
            if (!Caravans.enabled()) {
                return Outcome.fail(Messages.tr("tradery.caravan.off", "The caravan isn't trading right now"));
            }
            if (caravan.values().size() != merchant.getOffers().size()) {
                return Outcome.fail(Messages.failure(FailReason.INVALID_AMOUNT));
            }
            partner = new CaravanPartner(caravan, server);
        }
        MerchantOffers offers = merchant.getOffers();
        if (index < 0 || index >= offers.size()) {
            return Outcome.fail(Messages.failure(FailReason.INVALID_AMOUNT));
        }
        MerchantOffer offer = offers.get(index);
        long emeraldValue = partner.emeraldValue(index);
        int limit = all ? MAX_BATCH : 1;
        int done = 0;
        long paid = 0;
        long got = 0;
        Component problem = null;
        TradeTerms first = TradeTerms.of(offer, emeraldValue);
        while (done < limit) {
            TradeTerms terms = TradeTerms.of(offer, emeraldValue);
            problem = tradeOnce(player, merchant, partner, offer, terms);
            if (problem != null) {
                break;
            }
            done++;
            paid += terms.price();
            got += terms.payout();
        }
        ServerLevel level = (ServerLevel) merchant.level();
        if (done == 0) {
            level.playSound(null, merchant.getX(), merchant.getY(), merchant.getZ(),
                merchant instanceof Villager ? SoundEvents.VILLAGER_NO : SoundEvents.WANDERING_TRADER_NO, merchant.getSoundSource(), 1.0f, 1.0f);
            return Outcome.fail(problem);
        }
        level.playSound(null, merchant.getX(), merchant.getY(), merchant.getZ(), merchant.getNotifyTradeSound(), merchant.getSoundSource(), 1.0f, 1.0f);
        return new Outcome(true, done, describe(first, done, paid, got));
    }

    /** One trade: {@code null} when done, otherwise why not (and nothing changed). */
    private static @Nullable Component tradeOnce(ServerPlayer player, AbstractVillager merchant, Partner partner, MerchantOffer offer,
                                                 TradeTerms terms) {
        if (offer.isOutOfStock()) {
            return partner.usedUp();
        }
        if (!terms.valid()) {
            return Messages.failure(FailReason.LIMIT_EXCEEDED);
        }
        List<ItemStack> inventory = player.getInventory().items;
        Predicate<ItemStack> testA = terms.costA().isEmpty() ? null : offer.getItemCostA()::test;
        Predicate<ItemStack> testB = terms.costB().isEmpty() ? null : offer.getItemCostB().map(cost -> (Predicate<ItemStack>) cost::test).orElse(null);
        int needA = testA == null ? 0 : terms.costA().getCount();
        int needB = testB == null ? 0 : terms.costB().getCount();
        if (testA != null && StackMath.countMatching(inventory, testA) < needA) {
            return notEnough(terms.costA());
        }
        if (testB != null && StackMath.countMatching(inventory, testB) < needB) {
            return notEnough(terms.costB());
        }
        if (testA != null && testB != null && sameItem(offer.getItemCostA(), offer.getItemCostB().orElseThrow())
            && StackMath.countMatching(inventory, testA.or(testB)) < (long) needA + needB) {
            // Both costs are the same item: one pile pays for both
            return notEnough(terms.costA());
        }

        MinecraftServer server = player.level().getServer();
        long today = VillagerPurchases.today(server);
        long wallet = partner.money(today);
        // What the player gets minus what the partner keeps of the payment (after the village's tax); the villager's
        // wallet (the caravan's purse) covers a positive one, so it never ends below zero
        long net = terms.payout() - partner.keeps(terms.price());
        if (net > wallet) {
            return partner.outOfMoney();
        }

        EconomyService economy = EconomyService.INSTANCE;
        LedgerAccount account = economy.account(player.getUUID());
        Reason reason = Reason.of(partner.reason(), note(offer));
        if (net < 0 && account.balance(economy.defaultCurrency()) < -net) {
            return Messages.failure(FailReason.INSUFFICIENT_FUNDS);
        }
        if (net != 0) {
            TransactionResult result = net < 0
                ? economy.transfer(account, economy.serverAccount(), -net, 0, reason)
                : economy.transfer(economy.serverAccount(), account, net, 0, reason);
            if (result instanceof TransactionResult.Failure failure) {
                return Messages.failure(failure);
            }
        }

        // The money moved; the items were counted above, on this thread, with only the transaction in between
        if (testA != null) {
            warnIfShort("cost", StackMath.takeMatching(inventory, testA, needA), needA);
        }
        if (testB != null) {
            warnIfShort("cost", StackMath.takeMatching(inventory, testB, needB), needB);
        }
        partner.settle(terms.price(), terms.payout(), today);
        if (!terms.result().isEmpty()) {
            ItemStack goods = offer.assemble();
            goods.onCraftedBy(player.level(), player, goods.getCount());
            player.getInventory().placeItemBackInInventory(goods);
        }
        player.getInventory().setChanged();

        // Vanilla's bookkeeping: uses, the villager's experience and level, gossip, the trade advancement trigger
        merchant.notifyTrade(offer);
        player.awardStat(Stats.TRADED_WITH_VILLAGER);
        TradePersistence.tradedWithVillager(player);
        return null;
    }

    private static boolean sameItem(ItemCost a, ItemCost b) {
        return a.item().equals(b.item());
    }

    private static Component notEnough(ItemStack cost) {
        return Messages.tr("tradery.vending.not_enough_items", "You don't have enough %s", cost.getHoverName());
    }

    /**
     * "Bought 1 × Enchanted Book for (coin)15.00 + 1 × Book" / "Sold 24 × Paper for (coin)3.00" / "Got 1 × Bell for
     * 2 × Iron Ingot": the trades added up.
     */
    static Component describe(TradeTerms terms, int trades, long paid, long got) {
        if (terms.payout() > 0) {
            ItemStack sold = !terms.costA().isEmpty() ? terms.costA() : terms.costB();
            if (sold.isEmpty()) {
                return Messages.tr("tradery.villager_trade.received", "Received %s", Messages.coins(got));
            }
            return Messages.tr("tradery.vending.sold", "Sold %s × %s for %s", sold.getCount() * trades, sold.getHoverName(), Messages.coins(got));
        }
        MutableComponent price = Component.empty();
        if (paid > 0) {
            price.append(Messages.coins(paid));
        }
        for (ItemStack cost : new ItemStack[] {terms.costA(), terms.costB()}) {
            if (!cost.isEmpty()) {
                if (!price.getSiblings().isEmpty()) {
                    price.append(" + ");
                }
                price.append(Component.literal(cost.getCount() * trades + " × ").append(cost.getHoverName()));
            }
        }
        if (price.getSiblings().isEmpty()) {
            price.append(Messages.tr("tradery.vending.free", "free"));
        }
        ItemStack result = terms.result();
        return Messages.tr("tradery.vending.bought", "Bought %s × %s for %s", result.getCount() * trades, result.getHoverName(), price);
    }

    private static String note(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        ItemStack cost = offer.getCostA();
        return BuiltInRegistries.ITEM.getKey(cost.getItem()) + "x" + cost.getCount() + " -> "
            + BuiltInRegistries.ITEM.getKey(result.getItem()) + "x" + result.getCount();
    }

    /** Pre-checks make these impossible; if one ever happens, it's a bug worth a loud log line. */
    private static void warnIfShort(String what, int done, int expected) {
        if (done != expected) {
            Tradery.LOGGER.error("Villager trade {} moved {} of {} items; please report this", what, done, expected);
        }
    }

    /** Rate limit shared with vending trades. */
    public static boolean allow(ServerPlayer player) {
        return VendingTrades.allow(player);
    }
}
