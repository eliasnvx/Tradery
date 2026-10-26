package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.CounterBuyerMenu;
import dev.eliasnvx.tradery.menu.CounterOwnerMenu;
import dev.eliasnvx.tradery.menu.GhostSlot;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.CounterConfigurator;
import dev.eliasnvx.tradery.vending.CounterMenus;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.CounterTrades;
import dev.eliasnvx.tradery.vending.MarketCounterBlock;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.NotificationsData;
import dev.eliasnvx.tradery.vending.ShopSnapshot;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.VillagerCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The market counter (SPEC "Удобство для серверов"): trades atomic and dupe-free like vending sales, nothing changes on
 * any failure, owner-only setup validated on the server, protection like the vending block, the villager hooks, the
 * vending index. Items and money are counted before and after.
 */
public final class MarketCounterGameTests {
    private static final BlockPos COUNTER = new BlockPos(2, 1, 2);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("counter_sale_moves_goods_and_money", MarketCounterGameTests::saleMovesGoodsAndMoney),
        new TraderyGameTests.Entry("counter_failures_change_nothing", MarketCounterGameTests::failuresChangeNothing),
        new TraderyGameTests.Entry("counter_last_item_goes_once", MarketCounterGameTests::lastItemGoesOnce),
        new TraderyGameTests.Entry("counter_owner_only_setup", MarketCounterGameTests::ownerOnlySetup),
        new TraderyGameTests.Entry("counter_save_validates", MarketCounterGameTests::saveValidates),
        new TraderyGameTests.Entry("counter_menu_rules", MarketCounterGameTests::menuRules),
        new TraderyGameTests.Entry("counter_break_drops_once", MarketCounterGameTests::breakDropsOnce),
        new TraderyGameTests.Entry("counter_protection", MarketCounterGameTests::protection),
        new TraderyGameTests.Entry("counter_villager_hooks", MarketCounterGameTests::villagerHooks),
        new TraderyGameTests.Entry("counter_trades_saved_with_chunk", MarketCounterGameTests::tradesSavedWithChunk),
        new TraderyGameTests.Entry("counter_in_vending_index", MarketCounterGameTests::inVendingIndex),
        new TraderyGameTests.Entry("counter_villager_buys_wanted_offer", MarketCounterGameTests::villagerBuysWantedOffer));

    private MarketCounterGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** A counter placed as a player would (owner, index entry, snapshot). */
    static MarketCounterBlockEntity counter(GameTestHelper helper, BlockPos at, AccountId owner, CounterSettings settings) {
        helper.setBlock(at, TraderyBlocks.MARKET_COUNTER.get().defaultBlockState().setValue(MarketCounterBlock.FACING, Direction.NORTH));
        MarketCounterBlockEntity counter = (MarketCounterBlockEntity) helper.getBlockEntity(at);
        counter.setOwner(owner, "owner");
        VendorsData.get(helper.getLevel().getServer()).add(helper.getLevel(), helper.absolutePos(at), owner, true);
        counter.setSettings(settings);
        return counter;
    }

    static CounterSettings offers(CounterOffer... offers) {
        return new CounterSettings(List.of(offers), true);
    }

    static CounterOffer offer(Item goods, int perTrade, long price) {
        return new CounterOffer(new ItemStack(goods, perTrade), price);
    }

    /** A mock player standing in front of the counter, with an empty inventory. */
    @SuppressWarnings("removal")
    static ServerPlayer playerAt(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = MockPlayers.create(helper);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(at.north()));
        player.setPos(pos.x, pos.y, pos.z);
        player.getInventory().clearContent();
        return player;
    }

    static long balance(UUID player) {
        return EconomyService.INSTANCE.account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    static void setBalance(UUID player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    static long supply() {
        return EconomyService.INSTANCE.ledger().total(EconomyService.INSTANCE.defaultCurrency().id());
    }

    static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    /** Money and items that a failed trade must leave alone. */
    private record State(long buyer, long owner, long supply, int buyerBread, int buyerApples, int stockBread, int stockApples) {
        static State of(ServerPlayer buyer, UUID owner, MarketCounterBlockEntity counter) {
            return new State(balance(buyer.getUUID()), balance(owner), MarketCounterGameTests.supply(), count(buyer, Items.BREAD), count(buyer, Items.APPLE),
                count(counter.stock(), Items.BREAD), count(counter.stock(), Items.APPLE));
        }
    }

    private static void assertUnchanged(GameTestHelper helper, State before, ServerPlayer buyer, UUID owner, MarketCounterBlockEntity counter,
                                        String what) {
        GameTestAsserts.assertValueEqual(helper, State.of(buyer, owner, counter), before, what + ": nothing changed");
    }

    // ------------------------------------------------------------------ tests

    public static void saleMovesGoodsAndMoney(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.BREAD, 4, 250), offer(Items.APPLE, 2, 100)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        counter.stock().setItem(1, new ItemStack(Items.APPLE, 10));
        counter.stock().setChanged();
        ServerPlayer buyer = playerAt(helper, COUNTER);
        setBalance(buyer.getUUID(), 1_000);
        setBalance(ownerId, 0);
        long supplyBefore = supply();

        VendingTrades.Outcome outcome = CounterTrades.buy(buyer, counter, 1, 3);
        GameTestAsserts.assertValueEqual(helper, outcome.trades(), 3, "three trades of apples: " + outcome.message().getString());
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.APPLE), 6, "buyer got 6 apples");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.APPLE), 4, "stock lost 6 apples");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 64, "the other offer's goods untouched");
        GameTestAsserts.assertValueEqual(helper, balance(buyer.getUUID()), 700L, "buyer paid 3.00");
        long fee = 300 * 2 / 100;
        GameTestAsserts.assertValueEqual(helper, balance(ownerId), 300 - fee, "owner got the price minus the 2% fee");
        GameTestAsserts.assertValueEqual(helper, supply(), supplyBefore - fee, "the fee left the economy");

        VendingTrades.Outcome bread = CounterTrades.buy(buyer, counter, 0, VendingTrades.UNLIMITED);
        GameTestAsserts.assertValueEqual(helper, bread.trades(), 2, "money for two lots of bread: " + bread.message().getString());
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.BREAD), 8, "buyer got 8 bread");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 56, "stock lost 8 bread");
        GameTestAsserts.assertValueEqual(helper, balance(buyer.getUUID()), 200L, "buyer paid 5.00 more");
        GameTestAsserts.assertValueEqual(helper, counter.tradesInStock(0), 14, "14 lots of bread left");
        helper.assertTrue(counter.isStocked(0) && counter.isStocked(1), "both offers still in stock");
        helper.succeed();
    }

    public static void failuresChangeNothing(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.BREAD, 4, 250), offer(Items.APPLE, 2, 100), CounterOffer.EMPTY));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 16));
        counter.stock().setChanged();
        ServerPlayer buyer = playerAt(helper, COUNTER);
        setBalance(ownerId, 0);

        // Not enough money
        setBalance(buyer.getUUID(), 249);
        State before = State.of(buyer, ownerId, counter);
        helper.assertFalse(CounterTrades.buy(buyer, counter, 0, 1).success(), "no money: refused");
        assertUnchanged(helper, before, buyer, ownerId, counter, "no money");

        // Out of stock (apples)
        setBalance(buyer.getUUID(), 10_000);
        before = State.of(buyer, ownerId, counter);
        helper.assertFalse(CounterTrades.buy(buyer, counter, 1, 1).success(), "out of stock: refused");
        assertUnchanged(helper, before, buyer, ownerId, counter, "out of stock");

        // Wrong offer index: an empty place, past the end, negative
        helper.assertFalse(CounterTrades.buy(buyer, counter, 2, 1).success(), "empty offer: refused");
        helper.assertFalse(CounterTrades.buy(buyer, counter, CounterSettings.MAX_OFFERS, 1).success(), "index past the end: refused");
        helper.assertFalse(CounterTrades.buy(buyer, counter, -1, 1).success(), "negative index: refused");
        helper.assertFalse(CounterTrades.buy(buyer, counter, 0, 0).success(), "zero trades: refused");
        assertUnchanged(helper, before, buyer, ownerId, counter, "wrong offer");

        // No room in the buyer's inventory
        for (int i = 0; i < buyer.getInventory().items.size(); i++) {
            buyer.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        before = State.of(buyer, ownerId, counter);
        helper.assertFalse(CounterTrades.buy(buyer, counter, 0, 1).success(), "no room: refused");
        assertUnchanged(helper, before, buyer, ownerId, counter, "no room");
        buyer.getInventory().clearContent();

        // The owner can't buy at their own counter
        ServerPlayer owner = playerAt(helper, COUNTER);
        MarketCounterBlockEntity own = counter(helper, COUNTER.east(), AccountId.player(owner.getUUID()), offers(offer(Items.BREAD, 1, 10)));
        own.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        own.stock().setChanged();
        setBalance(owner.getUUID(), 1_000);
        helper.assertFalse(CounterTrades.buy(owner, own, 0, 1).success(), "own counter: refused");
        GameTestAsserts.assertValueEqual(helper, count(own.stock(), Items.BREAD), 5, "own stock untouched");
        GameTestAsserts.assertValueEqual(helper, balance(owner.getUUID()), 1_000L, "owner paid nothing");

        // Not configured at all
        MarketCounterBlockEntity blank = counter(helper, COUNTER.west(), AccountId.player(ownerId), CounterSettings.EMPTY);
        before = State.of(buyer, ownerId, counter);
        helper.assertFalse(CounterTrades.buy(buyer, blank, 0, 1).success(), "not configured: refused");
        assertUnchanged(helper, before, buyer, ownerId, counter, "not configured");
        helper.succeed();
    }

    public static void lastItemGoesOnce(GameTestHelper helper) {
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(UUID.randomUUID()),
            offers(offer(Items.DIAMOND, 1, 100), offer(Items.DIAMOND, 1, 90)));
        counter.stock().setItem(5, new ItemStack(Items.DIAMOND, 1));
        counter.stock().setChanged();
        ServerPlayer first = playerAt(helper, COUNTER);
        ServerPlayer second = playerAt(helper, COUNTER);
        setBalance(first.getUUID(), 1_000);
        setBalance(second.getUUID(), 1_000);

        helper.assertTrue(CounterTrades.buy(first, counter, 0, 1).success(), "first buyer gets it");
        helper.assertFalse(CounterTrades.buy(second, counter, 1, 1).success(), "the second offer shares the stock: it's gone");
        GameTestAsserts.assertValueEqual(helper, count(first, Items.DIAMOND) + count(second, Items.DIAMOND) + count(counter.stock(), Items.DIAMOND), 1,
            "exactly one diamond in total");
        GameTestAsserts.assertValueEqual(helper, balance(second.getUUID()), 1_000L, "second buyer paid nothing");
        helper.assertFalse(counter.isStocked(0) || counter.isStocked(1), "both offers sold out");
        helper.succeed();
    }

    public static void ownerOnlySetup(GameTestHelper helper) {
        ServerPlayer owner = playerAt(helper, COUNTER);
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(owner.getUUID()), CounterSettings.EMPTY);
        CounterMenus.openOwner(owner, counter, false);
        helper.assertTrue(owner.containerMenu instanceof CounterOwnerMenu, "owner menu open");
        CounterOwnerMenu menu = (CounterOwnerMenu) owner.containerMenu;

        // Samples copy, never take
        menu.setCarried(new ItemStack(Items.BREAD, 5));
        menu.clicked(CounterOwnerMenu.SAMPLE_START, 0, ClickType.PICKUP, owner);
        GameTestAsserts.assertValueEqual(helper, menu.getCarried().getCount(), 5, "cursor keeps all 5 bread");
        helper.assertTrue(menu.sample(0).is(Items.BREAD) && menu.sample(0).getCount() == 5, "sample is a copy of 5");
        menu.setCarried(new ItemStack(Items.APPLE, 2));
        menu.clicked(CounterOwnerMenu.SAMPLE_START + 3, 1, ClickType.PICKUP, owner);
        GameTestAsserts.assertValueEqual(helper, menu.sample(3).getCount(), 1, "right click: a sample of one");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(menu.getSlot(CounterOwnerMenu.SAMPLE_START) instanceof GhostSlot, "offer slots are samples");
        helper.assertFalse(menu.getSlot(CounterOwnerMenu.SAMPLE_START).mayPickup(owner), "a sample can't be picked up");
        helper.assertTrue(menu.clickMenuButton(owner, CounterOwnerMenu.buttonId(3, CounterOwnerMenu.PLUS_8)), "count +8");
        GameTestAsserts.assertValueEqual(helper, menu.sample(3).getCount(), 9, "apples per trade: 9");
        helper.assertFalse(menu.clickMenuButton(owner, CounterOwnerMenu.buttonId(CounterSettings.MAX_OFFERS, 0)), "no seventh offer");

        // The stock takes only the offers' goods
        owner.getInventory().items.set(0, new ItemStack(Items.DIRT, 10));
        owner.getInventory().items.set(1, new ItemStack(Items.BREAD, 20));
        int hotbar = CounterOwnerMenu.INVENTORY_START + 27;
        menu.quickMoveStack(owner, hotbar);
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.DIRT), 0, "dirt isn't on offer: stays with the owner");
        menu.quickMoveStack(owner, hotbar + 1);
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 20, "bread goes into the stock");

        CounterConfigurator.save(owner, menu, List.of(250L, 0L, 0L, 120L, 0L, 0L));
        helper.assertTrue(counter.settings().offer(0).sameAs(offer(Items.BREAD, 5, 250)), "bread offer saved");
        helper.assertTrue(counter.settings().offer(3).sameAs(offer(Items.APPLE, 9, 120)), "apple offer saved");
        helper.assertFalse(counter.settings().offer(1).isConfigured(), "empty place stays empty");

        // A stranger can't use the owner's menu or save through it
        ServerPlayer stranger = playerAt(helper, COUNTER);
        CounterOwnerMenu foreign = new CounterOwnerMenu(77, stranger.getInventory(),
            new CounterOwnerMenu.Data(counter.getBlockPos(), counter.settings(), false, "2%"), counter);
        helper.assertFalse(foreign.stillValid(stranger), "not the owner: menu invalid");
        foreign.setCarried(new ItemStack(Items.DIAMOND));
        foreign.clicked(CounterOwnerMenu.SAMPLE_START, 0, ClickType.PICKUP, stranger);
        CounterConfigurator.save(stranger, foreign, List.of(1L, 1L, 1L, 1L, 1L, 1L));
        helper.assertTrue(counter.settings().offer(0).sameAs(offer(Items.BREAD, 5, 250)), "a stranger's save changed nothing");
        helper.assertFalse(foreign.clickMenuButton(stranger, CounterOwnerMenu.TOGGLE_VILLAGERS), "a stranger's clicks are refused");
        helper.assertTrue(counter.settings().villagers(), "villagers toggle unchanged");
        owner.closeContainer();
        helper.succeed();
    }

    public static void saveValidates(GameTestHelper helper) {
        ServerPlayer owner = playerAt(helper, COUNTER);
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(owner.getUUID()), offers(offer(Items.BREAD, 1, 100)));
        ServerPlayer buyer = playerAt(helper, COUNTER);
        CounterMenus.openBuyer(buyer, counter);
        helper.assertTrue(buyer.containerMenu instanceof CounterBuyerMenu, "buyer screen open");
        CounterMenus.openOwner(owner, counter, false);
        CounterOwnerMenu menu = (CounterOwnerMenu) owner.containerMenu;

        // Wrong number of prices: refused
        VendingTrades.forget(owner);
        CounterConfigurator.save(owner, menu, List.of(500L));
        GameTestAsserts.assertValueEqual(helper, counter.settings().offer(0).price(), 100L, "a short price list saves nothing");
        // A blacklisted item (bedrock by default; the sample slot refuses it, so it's forced in, as after a config reload)
        menu.setCarried(new ItemStack(Items.BEDROCK));
        menu.clicked(CounterOwnerMenu.SAMPLE_START + 1, 0, ClickType.PICKUP, owner);
        helper.assertTrue(menu.sample(1).isEmpty(), "the sample slot refuses a blacklisted item");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(CounterOwnerMenu.SAMPLE_START + 1).set(new ItemStack(Items.BEDROCK));
        CounterConfigurator.save(owner, menu, List.of(500L, 10L, 0L, 0L, 0L, 0L));
        GameTestAsserts.assertValueEqual(helper, counter.settings().offer(0).price(), 100L, "a blacklisted sample saves nothing");
        helper.assertTrue(buyer.containerMenu instanceof CounterBuyerMenu, "buyers keep their screen while nothing changed");
        // Negative prices become 0, huge ones the balance cap
        menu.clicked(CounterOwnerMenu.SAMPLE_START + 1, 0, ClickType.PICKUP, owner);
        CounterConfigurator.save(owner, menu, List.of(-5L, 0L, 0L, 0L, 0L, Long.MAX_VALUE));
        GameTestAsserts.assertValueEqual(helper, counter.settings().offer(0).price(), 0L, "a negative price becomes 0");
        helper.assertFalse(counter.settings().offer(1).isConfigured(), "the cleared sample is an empty offer");
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "a new price closed the buyer's screen");
        CounterConfigurator.save(owner, menu, List.of(Long.MAX_VALUE, 0L, 0L, 0L, 0L, 0L));
        long cap = EconomyService.INSTANCE.maxBalance();
        GameTestAsserts.assertValueEqual(helper, counter.settings().offer(0).price(), cap > 0 ? cap : Long.MAX_VALUE, "a huge price is capped");
        VendingTrades.forget(owner);
        owner.closeContainer();
        helper.succeed();
    }

    public static void menuRules(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.BREAD, 4, 250), CounterOffer.EMPTY, offer(Items.APPLE, 1, 50)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        counter.stock().setItem(1, new ItemStack(Items.APPLE, 64));
        counter.stock().setChanged();
        ServerPlayer buyer = playerAt(helper, COUNTER);
        setBalance(buyer.getUUID(), 10_000);
        VendingTrades.forget(buyer);
        CounterBuyerMenu menu = new CounterBuyerMenu(91, buyer.getInventory(),
            new CounterBuyerMenu.Data(counter.getBlockPos(), counter.settings(), "owner"), counter);
        helper.assertTrue(menu.slots.isEmpty(), "buyer menu has no slots");

        helper.assertFalse(menu.clickMenuButton(buyer, -1), "negative id");
        helper.assertFalse(menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(1, CounterBuyerMenu.BUY_ONE)), "empty offer");
        helper.assertFalse(menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(CounterSettings.MAX_OFFERS, CounterBuyerMenu.BUY_ONE)),
            "offer past the end");
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.BREAD) + count(buyer, Items.APPLE), 0, "nothing bought by bad clicks");

        helper.assertTrue(menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(2, CounterBuyerMenu.BUY_EIGHT)), "eight apples");
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.APPLE), 8, "got 8 apples through the window");
        menu.broadcastChanges();
        GameTestAsserts.assertValueEqual(helper, menu.available(2), 56, "the window shows the stock left");
        GameTestAsserts.assertValueEqual(helper, menu.affordable(0), (int) ((10_000 - 400) / 250), "and what the buyer can pay for");

        // The offer changed under the open window: refused, nothing moves
        counter.setSettings(offers(offer(Items.BREAD, 4, 900), CounterOffer.EMPTY, offer(Items.APPLE, 1, 50)));
        long before = balance(buyer.getUUID());
        helper.assertTrue(menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(0, CounterBuyerMenu.BUY_ONE)), "click answered");
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.BREAD), 0, "no bread at a price the buyer didn't see");
        GameTestAsserts.assertValueEqual(helper, balance(buyer.getUUID()), before, "no money taken");

        // Too far: refused
        buyer.setPos(buyer.getX() + 20, buyer.getY(), buyer.getZ());
        helper.assertFalse(menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(2, CounterBuyerMenu.BUY_ONE)), "too far: refused");
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.APPLE), 8, "nothing bought from afar");
        buyer.setPos(buyer.getX() - 20, buyer.getY(), buyer.getZ());

        // Rate limit: at most 10 requests a second
        VendingTrades.forget(buyer);
        for (int i = 0; i < 15; i++) {
            menu.clickMenuButton(buyer, CounterBuyerMenu.buttonId(2, CounterBuyerMenu.BUY_ONE));
        }
        GameTestAsserts.assertValueEqual(helper, count(buyer, Items.APPLE), 8 + 10, "only 10 requests went through");
        VendingTrades.forget(buyer);
        helper.succeed();
    }

    public static void breakDropsOnce(GameTestHelper helper) {
        ServerPlayer owner = playerAt(helper, COUNTER);
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(owner.getUUID()), offers(offer(Items.BREAD, 1, 100)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        counter.stock().setItem(1, new ItemStack(Items.BREAD, 5));
        counter.stock().setChanged();
        ServerPlayer buyer = playerAt(helper, COUNTER);
        CounterMenus.openBuyer(buyer, counter);
        CounterMenus.openOwner(owner, counter, false);
        GlobalPos key = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COUNTER));
        helper.assertTrue(VendorsData.get(helper.getLevel().getServer()).get(key) != null, "indexed");

        helper.getLevel().destroyBlock(helper.absolutePos(COUNTER), true);
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "buyer menu closed");
        helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "owner menu closed");
        int bread = 0;
        int blocks = 0;
        for (ItemEntity item : helper.getEntities(EntityType.ITEM, COUNTER, 3)) {
            ItemStack stack = item.getItem();
            if (stack.is(Items.BREAD)) {
                bread += stack.getCount();
            } else if (stack.is(TraderyBlocks.MARKET_COUNTER_ITEM.get())) {
                blocks += stack.getCount();
            }
        }
        GameTestAsserts.assertValueEqual(helper, bread, 69, "stock dropped exactly once");
        GameTestAsserts.assertValueEqual(helper, blocks, 1, "the counter itself dropped");
        helper.assertTrue(VendorsData.get(helper.getLevel().getServer()).get(key) == null, "gone from the index");
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    public static void protection(GameTestHelper helper) {
        ServerPlayer owner = playerAt(helper, COUNTER);
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(owner.getUUID()), offers(offer(Items.BREAD, 1, 100)));
        ServerPlayer stranger = playerAt(helper, COUNTER);
        Level level = helper.getLevel();
        BlockPos pos = helper.absolutePos(COUNTER);

        helper.assertTrue(VendingProtection.mayBreak(owner, level, pos), "the owner may break it");
        helper.assertFalse(VendingProtection.mayBreak(stranger, level, pos), "a stranger may not");
        GameTestAsserts.assertValueEqual(helper, level.getBlockState(pos).getDestroyProgress(stranger, level, pos), 0.0f, "no progress for strangers");
        GameTestAsserts.assertValueEqual(helper, level.getBlockState(pos).getPistonPushReaction(), PushReaction.BLOCK, "pistons can't move it");
        helper.assertFalse(level.getBlockEntity(pos) instanceof Container, "not a container");
        helper.assertTrue(HopperBlockEntity.getContainerAt(level, pos) == null, "hoppers see no container");

        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() - 1.5, 4.0f, Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(pos).is(TraderyBlocks.MARKET_COUNTER.get()), "explosions can't break it");
        helper.assertTrue(level.getBlockEntity(pos) == counter, "same block entity after the explosion");
        helper.succeed();
    }

    public static void villagerHooks(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.APPLE, 2, 0), offer(Items.BREAD, 4, 200), offer(Items.CARROT, 8, 100)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 10));
        counter.stock().setItem(1, new ItemStack(Items.APPLE, 10));
        counter.stock().setChanged();
        setBalance(ownerId, 0);
        long supplyBefore = supply();

        helper.assertTrue(CounterTrades.servesVillagers(counter), "a player's counter with villagers on serves villagers");
        helper.assertFalse(CounterTrades.servesVillagers(counter, 0), "a free offer doesn't");
        helper.assertTrue(CounterTrades.servesVillagers(counter, 1), "bread does");
        helper.assertFalse(CounterTrades.servesVillagers(counter, 2), "carrots are out of stock");
        GameTestAsserts.assertValueEqual(helper, CounterTrades.pickOffer(counter, stack -> stack.is(Items.BREAD) || stack.is(Items.CARROT)), 1,
            "the villager picks the offer it wants that is in stock");
        GameTestAsserts.assertValueEqual(helper, CounterTrades.pickOffer(counter, stack -> stack.is(Items.CARROT)), -1, "nothing to pick");

        helper.assertFalse(CounterTrades.sellToVillager(counter, 0, "Farmer", 1).success(), "free offer: refused");
        helper.assertFalse(CounterTrades.sellToVillager(counter, 7, "Farmer", 1).success(), "bad index: refused");
        VendingTrades.Outcome outcome = CounterTrades.sellToVillager(counter, 1, "Farmer", 5);
        GameTestAsserts.assertValueEqual(helper, outcome.trades(), 2, "stock for two lots only: " + outcome.message().getString());
        GameTestAsserts.assertValueEqual(helper, outcome.price(), 400L, "the villager pays 4.00");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 2, "8 bread left with the villager");
        long fee = 400 * 2 / 100;
        GameTestAsserts.assertValueEqual(helper, balance(ownerId), 400 - fee, "the owner got the price minus the fee");
        GameTestAsserts.assertValueEqual(helper, supply(), supplyBefore + 400 - fee, "the payment was created (the wallet lives outside the ledger)");
        helper.assertTrue(TradePersistence.waitsForSave(helper.getLevel(), helper.absolutePos(COUNTER)), "the stock waits for the chunk save");
        NotificationsData.get(helper.getLevel().getServer()).take(ownerId);

        counter.setSettings(counter.settings().withVillagers(false));
        helper.assertFalse(CounterTrades.servesVillagers(counter), "villagers off: not served");
        helper.assertFalse(CounterTrades.sellToVillager(counter, 1, "Farmer", 1).success(), "villagers off: refused");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 2, "nothing more sold");
        helper.succeed();
    }

    /**
     * A farmer at a counter decides like at a vending block, on the offer it came for: it buys the fairly priced bread
     * from its wallet; the owner gets the price minus the fee; nothing else on the counter moves.
     */
    public static void villagerBuysWantedOffer(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        UUID ownerId = UUID.randomUUID();
        long fair = EconomyService.INSTANCE.defaultCurrency().toMinor(
            dev.eliasnvx.tradery.village.VillagerMarket.fairPrice(new ItemStack(Items.BREAD)).orElseThrow());
        long price = Math.max(1, Math.round(fair * 4 * 0.9));
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.STONE, 8, 100), offer(Items.BREAD, 4, price)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 12));
        counter.stock().setItem(1, new ItemStack(Items.STONE, 16));
        counter.stock().setChanged();
        setBalance(ownerId, 0);

        net.minecraft.world.entity.npc.Villager farmer = helper.spawnWithNoFreeWill(EntityType.VILLAGER, COUNTER.north());
        farmer.setVillagerData(farmer.getVillagerData().setProfession(
            net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
        farmer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT,
            GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0))));
        long today = dev.eliasnvx.tradery.village.VillagerPurchases.today(server);
        dev.eliasnvx.tradery.village.VillagerLedger.get(server).setWallet(farmer.getUUID(), price * 10, today);

        int wanted = CounterTrades.pickOffer(counter, goods -> dev.eliasnvx.tradery.village.VillagerMarket.weight(
            VillagerCompat.profession(farmer), goods) > 0);
        GameTestAsserts.assertValueEqual(helper, wanted, 1, "the farmer wants the bread, not the stone");
        var visit = dev.eliasnvx.tradery.village.VillagerPurchases.visit(farmer, counter, wanted, 0.0);
        helper.assertTrue(visit.bought(), "bought: " + visit.result());
        long paid = visit.paid();
        GameTestAsserts.assertValueEqual(helper, paid, price * visit.lots(), "paid the offer's price per lot");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.BREAD), 12 - 4 * visit.lots(), "bread taken");
        GameTestAsserts.assertValueEqual(helper, count(counter.stock(), Items.STONE), 16, "the other offer untouched");
        long fee = dev.eliasnvx.tradery.util.Money.percentOf(paid, dev.eliasnvx.tradery.config.TraderyConfig.server().vending().feePercent());
        GameTestAsserts.assertValueEqual(helper, balance(ownerId), paid - fee, "the owner got the price minus the fee");
        GameTestAsserts.assertValueEqual(helper, dev.eliasnvx.tradery.village.VillagerPurchases.wallet(farmer, today), price * 10 - paid, "from the farmer's wallet");
        helper.assertFalse(dev.eliasnvx.tradery.village.VillagerPurchases.visit(farmer, counter, 0, 0.0).bought(), "stone: not wanted");
        NotificationsData.get(server).take(ownerId);
        farmer.discard();
        helper.succeed();
    }

    public static void tradesSavedWithChunk(GameTestHelper helper) {
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(UUID.randomUUID()), offers(offer(Items.BREAD, 4, 250)));
        counter.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        counter.stock().setChanged();
        ServerPlayer buyer = playerAt(helper, COUNTER);
        setBalance(buyer.getUUID(), 1_000);
        BlockPos pos = helper.absolutePos(COUNTER);

        helper.assertTrue(CounterTrades.buy(buyer, counter, 0, 1).success(), "bought");
        helper.assertTrue(TradePersistence.pending(helper.getLevel(), pos).contains(buyer.getUUID()), "the trader waits for the chunk save");
        helper.getLevel().getChunkSource().save(true);
        helper.assertTrue(TradePersistence.pending(helper.getLevel(), pos).isEmpty(), "saving the chunk saved the trade's money and trader");
        helper.succeed();
    }

    public static void inVendingIndex(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        MarketCounterBlockEntity counter = counter(helper, COUNTER, AccountId.player(ownerId),
            offers(offer(Items.BREAD, 4, 250), offer(Items.APPLE, 2, 100)));
        VendorsData index = VendorsData.get(helper.getLevel().getServer());
        GlobalPos key = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COUNTER));
        GameTestAsserts.assertValueEqual(helper, index.countOwnedBy(AccountId.player(ownerId)), 1, "counters count toward the shop limit");
        ShopSnapshot snapshot = index.get(key).snapshot();
        helper.assertTrue(snapshot != null && snapshot.kind() == ShopSnapshot.Kind.COUNTER, "a counter snapshot");
        GameTestAsserts.assertValueEqual(helper, snapshot.offers().size(), 2, "two offers in the snapshot");
        helper.assertTrue(snapshot.has(ShopSnapshot.EMPTY), "empty stock: alert");

        counter.stock().setItem(0, new ItemStack(Items.BREAD, 20));
        counter.stock().setItem(1, new ItemStack(Items.APPLE, 7));
        counter.stock().setChanged();
        snapshot = index.get(key).snapshot();
        GameTestAsserts.assertValueEqual(helper, snapshot.offers().get(0).stock(), 5, "the snapshot follows the stock: 5 lots of bread");
        GameTestAsserts.assertValueEqual(helper, snapshot.offers().get(1).stock(), 3, "3 lots of apples");
        GameTestAsserts.assertValueEqual(helper, snapshot.alerts(), 0, "stocked: no alerts");
        helper.assertTrue(counter.isStocked(0) && counter.isStocked(1), "both offers in stock");
        helper.succeed();
    }
}
