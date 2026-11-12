package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Trades at trade crates (SPEC "Торговый ящик"): a villager does one of its own vanilla offers whose result the crate
 * wants, paid from the crate's payment store. Emeralds follow the trade window's rules: with
 * {@code villagerTrading.enabled} they are money (the owner's balance pays the villager's wallet, the wallet pays the
 * owner), otherwise they are items in the stores. The price is vanilla's (the villager's demand) with the owner's
 * reputation discount, worked out from the owner's UUID, so an offline owner gets the price they earned. Each trade
 * is checked whole first; then the money moves (it may still be refused), then the items. Server thread.
 */
public final class TradeCrates {
    private TradeCrates() {
    }

    /** {@code tradeCrates.enabled}, and the economy is running. */
    public static boolean enabled() {
        return TraderyConfig.server().tradeCrates().enabled() && EconomyService.INSTANCE.isReady();
    }

    /** Emeralds are money ({@code villagerTrading.enabled}) rather than items. */
    public static boolean emeraldsAsMoney() {
        return VillagerTrading.enabled();
    }

    /** {@code result} is what {@code want} asks for: the same item, and the same data when the sample has any. */
    public static boolean wants(ItemStack want, ItemStack result) {
        if (want.isEmpty() || result.isEmpty()) {
            return false;
        }
        return want.getComponentsPatch().isEmpty() ? result.is(want.getItem()) : ItemStack.isSameItemSameComponents(want, result);
    }

    /**
     * The first offer of {@code villager} this crate can do once right now (its result wanted, not used up, paid for,
     * room for the result), or -1.
     */
    public static int findOffer(Villager villager, TradeCrateBlockEntity crate) {
        UUID owner = owner(crate);
        // A player's trade window holds its own discount on these offers: never touch them then
        if (owner == null || crate.wanted().isEmpty() || villager.isTrading()) {
            return -1;
        }
        MerchantOffers offers = villager.getOffers();
        MinecraftServer server = ((ServerLevel) villager.level()).getServer();
        applyDiscount(villager, offers, owner);
        try {
            return firstOffer(crate, owner, offers, new VillagerTrading.VillagerPartner(villager, server), VillagerPurchases.today(server));
        } finally {
            resetDiscount(offers);
        }
    }

    /** {@link #findOffer} with the owner's discount already applied. */
    private static int firstOffer(TradeCrateBlockEntity crate, UUID owner, MerchantOffers offers, VillagerTrading.Partner partner, long today) {
        for (ItemStack want : crate.wanted()) {
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer offer = offers.get(i);
                if (wants(want, offer.getResult()) && canTrade(crate, owner, partner, offer, terms(offer), today)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The villager is at the crate: does its offer {@code index} (or, if that one no longer works, the first one that
     * does) as often as the offer's uses, the payment, the room, the money and {@code tradeCrates.maxTradesPerVisit}
     * allow.
     *
     * @return single trades done
     */
    public static int visit(Villager villager, TradeCrateBlockEntity crate, int index) {
        UUID owner = owner(crate);
        if (!enabled() || owner == null || villager.isTrading() || !(villager.level() instanceof ServerLevel level)) {
            return 0;
        }
        MerchantOffers offers = villager.getOffers();
        MinecraftServer server = level.getServer();
        VillagerTrading.VillagerPartner partner = new VillagerTrading.VillagerPartner(villager, server);
        long today = VillagerPurchases.today(server);
        int limit = TraderyConfig.server().tradeCrates().maxTradesPerVisit();
        int done = 0;
        applyDiscount(villager, offers, owner);
        try {
            int chosen = index;
            if (chosen < 0 || chosen >= offers.size() || crate.wanted().stream().noneMatch(want -> wants(want, offers.get(index).getResult()))
                || !canTrade(crate, owner, partner, offers.get(chosen), terms(offers.get(chosen)), today)) {
                chosen = firstOffer(crate, owner, offers, partner, today);
            }
            if (chosen < 0) {
                return 0;
            }
            MerchantOffer offer = offers.get(chosen);
            while (done < limit && tradeOnce(level, villager, crate, owner, partner, offer, today)) {
                done++;
            }
        } finally {
            resetDiscount(offers);
        }
        if (done > 0) {
            crate.setChanged();
            villager.getGossips().add(owner, GossipType.TRADING, GossipType.REPUTATION_CHANGE_PER_TRADE * done);
            level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), villager.getNotifyTradeSound(), villager.getSoundSource(), 1.0f, 1.0f);
            TradePersistence.traded(level, crate.getBlockPos());
        }
        return done;
    }

    private static @Nullable UUID owner(TradeCrateBlockEntity crate) {
        return crate.owner() instanceof AccountId.Player player ? player.uuid() : null;
    }

    /** The offer in money ({@code emeraldsAsMoney}) or all in items. */
    private static TradeTerms terms(MerchantOffer offer) {
        return emeraldsAsMoney() ? TradeTerms.of(offer, VillagerTrading.emeraldValue())
            : new TradeTerms(0, 0, offer.getCostA(), offer.getCostB(), 0, offer.getResult());
    }

    /** This one trade can happen now. Changes nothing. */
    private static boolean canTrade(TradeCrateBlockEntity crate, UUID owner, VillagerTrading.Partner partner, MerchantOffer offer,
                                          TradeTerms terms, long today) {
        if (offer.isOutOfStock()) {
            return false;
        }
        if (!terms.valid()) {
            return false;
        }
        List<ItemStack> payment = crate.payment();
        Predicate<ItemStack> testA = terms.costA().isEmpty() ? null : offer.getItemCostA()::test;
        Predicate<ItemStack> testB = terms.costB().isEmpty() ? null : offer.getItemCostB().map(cost -> (Predicate<ItemStack>) cost::test).orElse(null);
        int needA = testA == null ? 0 : terms.costA().getCount();
        int needB = testB == null ? 0 : terms.costB().getCount();
        if (testA != null && StackMath.countMatching(payment, testA) < needA) {
            return false;
        }
        if (testB != null && StackMath.countMatching(payment, testB) < needB) {
            return false;
        }
        if (testA != null && testB != null && VillagerTrading.sameItem(offer.getItemCostA(), offer.getItemCostB().orElseThrow())
            && StackMath.countMatching(payment, testA.or(testB)) < (long) needA + needB) {
            return false;
        }
        ItemStack result = terms.result();
        if (!result.isEmpty() && StackMath.space(crate.received(), result) < result.getCount()) {
            return false;
        }
        // The villager's wallet covers what it pays out beyond what it keeps of the payment (after the village's tax)
        if (terms.payout() - partner.keeps(terms.price()) > partner.money(today)) {
            return false;
        }
        // The owner pays the full price (the tax is part of it) and gets the full payout
        long net = terms.payout() - terms.price();
        EconomyService economy = EconomyService.INSTANCE;
        if (net < 0 && economy.account(owner).balance(economy.defaultCurrency()) < -net) {
            return false;
        }
        return true;
    }

    /** One trade, whole or nothing. */
    private static boolean tradeOnce(ServerLevel level, Villager villager, TradeCrateBlockEntity crate, UUID owner,
                                     VillagerTrading.VillagerPartner partner, MerchantOffer offer, long today) {
        TradeTerms terms = terms(offer);
        if (!canTrade(crate, owner, partner, offer, terms, today)) {
            return false;
        }
        long net = terms.payout() - terms.price();
        if (net != 0) {
            EconomyService economy = EconomyService.INSTANCE;
            LedgerAccount account = economy.account(owner);
            Reason reason = Reason.of(partner.reason(), "crate");
            TransactionResult result = net < 0
                ? economy.transfer(account, economy.serverAccount(), -net, 0, reason)
                : economy.transfer(economy.serverAccount(), account, net, 0, reason);
            if (result instanceof TransactionResult.Failure) {
                return false;
            }
        }
        // The money moved; the items were checked above, on this thread, with only the transaction in between
        List<ItemStack> payment = crate.payment();
        if (!terms.costA().isEmpty()) {
            VillagerTrading.warnIfShort("crate cost", StackMath.takeMatching(payment, offer.getItemCostA()::test, terms.costA().getCount()),
                terms.costA().getCount());
        }
        if (!terms.costB().isEmpty()) {
            Predicate<ItemStack> testB = offer.getItemCostB().map(cost -> (Predicate<ItemStack>) cost::test).orElse(stack -> false);
            VillagerTrading.warnIfShort("crate cost", StackMath.takeMatching(payment, testB, terms.costB().getCount()), terms.costB().getCount());
        }
        partner.settle(terms.price(), terms.payout(), today);
        if (!terms.result().isEmpty()) {
            ItemStack goods = offer.assemble();
            VillagerTrading.warnIfShort("crate result", StackMath.insert(crate.received(), goods, goods.getCount()), goods.getCount());
        }
        // Vanilla's bookkeeping without a player: uses, the villager's experience and level, its experience orbs
        villager.notifyTrade(offer);
        return true;
    }

    /** Vanilla's reputation discount for a trading player, for the owner by UUID (no Hero of the Village: no player). */
    private static void applyDiscount(Villager villager, MerchantOffers offers, UUID owner) {
        int reputation = villager.getGossips().getReputation(owner, type -> true);
        if (reputation == 0) {
            return;
        }
        for (MerchantOffer offer : offers) {
            offer.addToSpecialPriceDiff(-Mth.floor(reputation * offer.getPriceMultiplier()));
        }
    }

    private static void resetDiscount(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            offer.resetSpecialPriceDiff();
        }
    }
}
