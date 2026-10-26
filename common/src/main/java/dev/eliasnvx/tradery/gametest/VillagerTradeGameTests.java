package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerTrading;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Trading with villagers for money (SPEC "Торговля с жителями за валюту"). The balance, the villager's wallet, the
 * player's items and the offer's uses are counted before and after. Offers are set on the villager directly, so the
 * tests don't depend on vanilla's trade tables or random prices.
 */
public final class VillagerTradeGameTests {
    private static final BlockPos AT = new BlockPos(1, 1, 1);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("villager_trade_buy_with_money", VillagerTradeGameTests::buyWithMoney),
        new TraderyGameTests.Entry("villager_trade_sell_paid_from_wallet", VillagerTradeGameTests::sellPaidFromWallet),
        new TraderyGameTests.Entry("villager_trade_failures_change_nothing", VillagerTradeGameTests::failuresChangeNothing),
        new TraderyGameTests.Entry("villager_trade_all_stops_at_limits", VillagerTradeGameTests::tradeAllStopsAtLimits),
        new TraderyGameTests.Entry("villager_trade_window_replaces_vanilla", VillagerTradeGameTests::windowReplacesVanilla));

    private VillagerTradeGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** A librarian with exactly these offers. */
    private static Villager villager(GameTestHelper helper, MerchantOffer... offers) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, AT);
        villager.setVillagerData(villager.getVillagerData()
            .withProfession(helper.getLevel().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));
        // overrideOffers is the client's; on the server the live list is the villager's own
        MerchantOffers list = villager.getOffers();
        list.clear();
        list.addAll(List.of(offers));
        return villager;
    }

    /** A player next to the villager, trading with it. */
    @SuppressWarnings("removal")
    private static ServerPlayer trader(GameTestHelper helper, Villager villager) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(AT.east()));
        player.setPos(at.x, at.y, at.z);
        villager.setTradingPlayer(player);
        return player;
    }

    /** {@code emeralds} plus a book → a bookshelf (librarian-like). */
    private static MerchantOffer buyOffer(int emeralds, int maxUses) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.BOOKSHELF),
            0, maxUses, 10, 0.05f);
    }

    /** {@code count} of {@code item} → one emerald. */
    private static MerchantOffer sellOffer(Item item, int count, int maxUses) {
        return new MerchantOffer(new ItemCost(item, count), Optional.empty(), new ItemStack(Items.EMERALD), 0, maxUses, 2, 0.05f);
    }

    private static long emerald() {
        return VillagerTrading.emeraldValue();
    }

    private static long balance(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(ServerPlayer player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(helper.getLevel().getServer());
    }

    private static void setWallet(GameTestHelper helper, Villager villager, long amount) {
        VillagerLedger.get(helper.getLevel().getServer()).setWallet(villager.getUUID(), amount, today(helper));
    }

    private static long wallet(GameTestHelper helper, Villager villager) {
        return VillagerPurchases.wallet(villager, today(helper));
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents contents ? contents.getKey() : message.getString();
    }

    private static ServerConfig withTrading(ServerConfig config, boolean enabled) {
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(), config.ore(), config.history(),
            config.log(), config.villagers(), config.market(),
            new ServerConfig.VillagerTradingSection(enabled, config.villagerTrading().emeraldValue()), config.villages());
    }

    // ------------------------------------------------------------------ tests

    /** Emeralds come off the balance into the villager's wallet; the book is taken, the shelf given; vanilla counts it. */
    public static void buyWithMoney(GameTestHelper helper) {
        MerchantOffer offer = buyOffer(5, 12);
        Villager villager = villager(helper, offer);
        ServerPlayer player = trader(helper, villager);
        player.getInventory().add(new ItemStack(Items.BOOK, 2));
        setBalance(player, 100 * emerald());
        setWallet(helper, villager, 0);

        VillagerTrading.Outcome outcome = VillagerTrading.trade(player, villager, 0, false);
        helper.assertTrue(outcome.success(), "bought: " + outcome.message().getString());
        helper.assertValueEqual(outcome.trades(), 1, "one trade");
        helper.assertValueEqual(balance(player), 95 * emerald(), "5 emeralds' worth paid from the balance");
        helper.assertValueEqual(wallet(helper, villager), 5 * emerald(), "the money is in the villager's wallet");
        helper.assertValueEqual(count(player, Items.BOOK), 1, "one book taken");
        helper.assertValueEqual(count(player, Items.BOOKSHELF), 1, "bookshelf given");
        helper.assertValueEqual(count(player, Items.EMERALD), 0, "no emeralds involved");
        helper.assertValueEqual(offer.getUses(), 1, "the offer counted a use");
        helper.assertValueEqual(villager.getVillagerXp(), 10, "the villager got the trade's experience");
        helper.assertTrue(TradePersistence.villagerTradePending(player), "player saved with the economy data");
        villager.discard();
        helper.succeed();
    }

    /** The villager pays emeralds' worth from its wallet; an empty wallet refuses and nothing moves. */
    public static void sellPaidFromWallet(GameTestHelper helper) {
        MerchantOffer offer = sellOffer(Items.PAPER, 24, 16);
        Villager villager = villager(helper, offer);
        ServerPlayer player = trader(helper, villager);
        player.getInventory().add(new ItemStack(Items.PAPER, 60));
        setBalance(player, 0);
        long start = emerald() + emerald() / 2;
        setWallet(helper, villager, start);

        VillagerTrading.Outcome first = VillagerTrading.trade(player, villager, 0, false);
        helper.assertTrue(first.success(), "sold: " + first.message().getString());
        helper.assertValueEqual(balance(player), emerald(), "paid one emerald's worth");
        helper.assertValueEqual(wallet(helper, villager), start - emerald(), "from the villager's wallet");
        helper.assertValueEqual(count(player, Items.PAPER), 36, "24 paper taken");

        VillagerTrading.Outcome second = VillagerTrading.trade(player, villager, 0, false);
        helper.assertFalse(second.success(), "the wallet can't pay a second time");
        helper.assertValueEqual(key(second.message()), "tradery.villager_trade.no_money", "come back tomorrow");
        helper.assertValueEqual(balance(player), emerald(), "balance unchanged");
        helper.assertValueEqual(wallet(helper, villager), start - emerald(), "wallet unchanged");
        helper.assertValueEqual(count(player, Items.PAPER), 36, "paper kept");
        helper.assertValueEqual(offer.getUses(), 1, "no use counted");
        helper.assertValueEqual(count(player, Items.EMERALD), 0, "no emerald items");
        villager.discard();
        helper.succeed();
    }

    /** Short of money, short of items, used up, a bad index: refused, and every count is as before. */
    public static void failuresChangeNothing(GameTestHelper helper) {
        MerchantOffer offer = buyOffer(5, 12);
        MerchantOffer usedUp = buyOffer(1, 1);
        usedUp.increaseUses();
        Villager villager = villager(helper, offer, usedUp);
        ServerPlayer player = trader(helper, villager);
        player.getInventory().add(new ItemStack(Items.BOOK, 1));
        setWallet(helper, villager, 0);

        setBalance(player, 4 * emerald());
        VillagerTrading.Outcome poor = VillagerTrading.trade(player, villager, 0, false);
        helper.assertFalse(poor.success(), "not enough money");
        helper.assertValueEqual(key(poor.message()), "tradery.error.insufficient_funds", "says why");
        helper.assertValueEqual(balance(player), 4 * emerald(), "balance unchanged");
        helper.assertValueEqual(count(player, Items.BOOK), 1, "book kept");

        setBalance(player, 100 * emerald());
        player.getInventory().clearContent();
        VillagerTrading.Outcome noBook = VillagerTrading.trade(player, villager, 0, false);
        helper.assertFalse(noBook.success(), "no book");
        helper.assertValueEqual(key(noBook.message()), "tradery.vending.not_enough_items", "says why");
        helper.assertValueEqual(balance(player), 100 * emerald(), "nothing paid without the book");

        player.getInventory().add(new ItemStack(Items.BOOK, 1));
        VillagerTrading.Outcome out = VillagerTrading.trade(player, villager, 1, false);
        helper.assertFalse(out.success(), "used up");
        helper.assertValueEqual(key(out.message()), "tradery.villager_trade.out_of_stock", "says why");
        helper.assertFalse(VillagerTrading.trade(player, villager, 7, false).success(), "no such offer");
        helper.assertFalse(VillagerTrading.trade(player, villager, -1, false).success(), "negative index");

        helper.assertValueEqual(balance(player), 100 * emerald(), "balance unchanged");
        helper.assertValueEqual(count(player, Items.BOOK), 1, "book kept");
        helper.assertValueEqual(count(player, Items.BOOKSHELF), 0, "nothing given");
        helper.assertValueEqual(wallet(helper, villager), 0L, "wallet unchanged");
        helper.assertValueEqual(offer.getUses(), 0, "no use counted");
        helper.assertValueEqual(villager.getVillagerXp(), 0, "no experience");
        villager.discard();
        helper.succeed();
    }

    /** "Trade all" stops at the villager's money, then at the offer's uses. */
    public static void tradeAllStopsAtLimits(GameTestHelper helper) {
        MerchantOffer sticks = sellOffer(Items.STICK, 1, 16);
        MerchantOffer paper = sellOffer(Items.PAPER, 1, 3);
        Villager villager = villager(helper, sticks, paper);
        ServerPlayer player = trader(helper, villager);
        player.getInventory().add(new ItemStack(Items.STICK, 10));
        player.getInventory().add(new ItemStack(Items.PAPER, 10));
        setBalance(player, 0);

        setWallet(helper, villager, 2 * emerald());
        VillagerTrading.Outcome byWallet = VillagerTrading.trade(player, villager, 0, true);
        helper.assertTrue(byWallet.success(), "sold sticks");
        helper.assertValueEqual(byWallet.trades(), 2, "as many as the wallet pays for");
        helper.assertValueEqual(count(player, Items.STICK), 8, "two sticks taken");
        helper.assertValueEqual(wallet(helper, villager), 0L, "wallet empty");

        setWallet(helper, villager, 100 * emerald());
        VillagerTrading.Outcome byUses = VillagerTrading.trade(player, villager, 1, true);
        helper.assertValueEqual(byUses.trades(), 3, "as many as the offer allows");
        helper.assertTrue(paper.isOutOfStock(), "used up");
        helper.assertValueEqual(count(player, Items.PAPER), 7, "three paper taken");
        helper.assertValueEqual(balance(player), 5 * emerald(), "paid for 2 + 3 trades");
        helper.assertValueEqual(wallet(helper, villager), 97 * emerald(), "paid from the wallet");
        villager.discard();
        helper.succeed();
    }

    /** Right-click opens the money window (vanilla's checks first); closing it ends the trade; off = vanilla's window. */
    public static void windowReplacesVanilla(GameTestHelper helper) {
        MerchantOffer offer = buyOffer(5, 12);
        Villager villager = villager(helper, offer);
        ServerPlayer player = trader(helper, villager);
        villager.setTradingPlayer(null);
        player.getInventory().add(new ItemStack(Items.BOOK, 1));
        setBalance(player, 100 * emerald());
        setWallet(helper, villager, 0);

        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof VillagerTradeMenu, "Tradery's window opened");
        helper.assertTrue(villager.getTradingPlayer() == player, "the villager trades with the player");
        VillagerTradeMenu menu = (VillagerTradeMenu) player.containerMenu;
        helper.assertValueEqual(menu.data().offers().size(), 1, "the window got the offers");
        helper.assertValueEqual(menu.data().emeraldValue(), emerald(), "and the emerald value");
        helper.assertTrue(menu.clickMenuButton(player, VillagerTradeMenu.buttonId(0, false)), "trade button accepted");
        helper.assertValueEqual(count(player, Items.BOOKSHELF), 1, "traded through the menu");
        helper.assertFalse(menu.clickMenuButton(player, -1), "bad button refused");
        player.closeContainer();
        helper.assertTrue(villager.getTradingPlayer() == null, "closing ends the trade");

        ServerConfig original = TraderyConfig.server();
        try {
            TraderyConfig.setServerForTests(withTrading(original, false));
            villager.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof MerchantMenu, "turned off: vanilla's window");
            player.closeContainer();
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        villager.discard();
        helper.succeed();
    }
}
