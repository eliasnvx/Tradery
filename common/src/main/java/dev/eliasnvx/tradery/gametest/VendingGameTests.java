package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.GhostSlot;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.NotificationsData;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.vending.VendingQuickTrade;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Vending trades and the anti-dupe table of the spec, on a real server. Items and money are counted before and after. */
public final class VendingGameTests {
    private static final BlockPos VENDOR = new BlockPos(2, 1, 2);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("vending_sale_moves_goods_and_money", VendingGameTests::saleMovesGoodsAndMoney),
        new TraderyGameTests.Entry("vending_last_item_goes_once", VendingGameTests::lastItemGoesOnce),
        new TraderyGameTests.Entry("vending_no_room_takes_nothing", VendingGameTests::noRoomTakesNothing),
        new TraderyGameTests.Entry("vending_item_price_and_full_revenue", VendingGameTests::itemPriceAndFullRevenue),
        new TraderyGameTests.Entry("vending_buyback", VendingGameTests::buyback),
        new TraderyGameTests.Entry("vending_menu_rules", VendingGameTests::menuRules),
        new TraderyGameTests.Entry("vending_break_closes_menus_and_drops_once", VendingGameTests::breakClosesMenusAndDropsOnce),
        new TraderyGameTests.Entry("vending_protection", VendingGameTests::protection),
        new TraderyGameTests.Entry("vending_rate_limit", VendingGameTests::rateLimit),
        new TraderyGameTests.Entry("vending_offline_owner_gets_summary", VendingGameTests::offlineOwnerGetsSummary),
        new TraderyGameTests.Entry("vending_new_price_closes_buyer_screens", VendingGameTests::newPriceClosesBuyerScreens),
        new TraderyGameTests.Entry("vending_quick_trade_buys_one_lot", VendingGameTests::quickTradeBuysOneLot),
        new TraderyGameTests.Entry("vending_quick_trade_wrong_button", VendingGameTests::quickTradeWrongButton),
        new TraderyGameTests.Entry("vending_quick_trade_sells_to_buyback", VendingGameTests::quickTradeSellsToBuyback),
        new TraderyGameTests.Entry("vending_quick_trade_needs_reach", VendingGameTests::quickTradeNeedsReach));

    private VendingGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static VendingBlockEntity vendor(GameTestHelper helper, AccountId owner, VendingSettings settings) {
        helper.setBlock(VENDOR, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = helper.getBlockEntity(VENDOR, VendingBlockEntity.class);
        vendor.setOwner(owner, "owner");
        vendor.setSettings(settings);
        return vendor;
    }

    private static VendingSettings sell(Item goods, int perTrade, long price) {
        return new VendingSettings(new ItemStack(goods, perTrade), PriceMode.CURRENCY, price, ItemStack.EMPTY, false, DisplayAnimation.STATIC);
    }

    /** A mock player standing next to the vendor, with an empty inventory. */
    @SuppressWarnings("removal")
    private static ServerPlayer playerAtVendor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(VENDOR.north()));
        player.setPos(at.x, at.y, at.z);
        player.getInventory().clearContent();
        return player;
    }

    private static long balance(UUID player) {
        return EconomyService.INSTANCE.account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(UUID player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ tests

    public static void saleMovesGoodsAndMoney(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sell(Items.BREAD, 4, 250));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 1_000);
        setBalance(ownerId, 0);
        long supplyBefore = EconomyService.INSTANCE.ledger().total(EconomyService.INSTANCE.defaultCurrency().id());

        VendingTrades.Outcome outcome = VendingTrades.execute(buyer, vendor, 3);
        helper.assertValueEqual(outcome.trades(), 3, "three trades: " + outcome.message().getString());
        helper.assertValueEqual(count(buyer, Items.BREAD), 12, "buyer got 12 bread");
        helper.assertValueEqual(count(vendor.stock(), Items.BREAD), 52, "stock lost 12 bread");
        helper.assertValueEqual(balance(buyer.getUUID()), 250L, "buyer paid 7.50");
        long fee = 750 * 2 / 100;
        helper.assertValueEqual(balance(ownerId), 750 - fee, "owner got the price minus the 2% fee");
        helper.assertValueEqual(EconomyService.INSTANCE.ledger().total(EconomyService.INSTANCE.defaultCurrency().id()), supplyBefore - fee,
            "the fee left the economy");
        helper.assertTrue(helper.getBlockState(VENDOR).getValue(VendingBlock.STOCKED), "green light while stocked");
        helper.succeed();
    }

    public static void lastItemGoesOnce(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sell(Items.DIAMOND, 1, 100));
        vendor.stock().setItem(5, new ItemStack(Items.DIAMOND, 1));
        vendor.stock().setChanged();
        ServerPlayer first = playerAtVendor(helper);
        ServerPlayer second = playerAtVendor(helper);
        setBalance(first.getUUID(), 1_000);
        setBalance(second.getUUID(), 1_000);

        helper.assertTrue(VendingTrades.execute(first, vendor, 1).success(), "first buyer gets it");
        helper.assertFalse(VendingTrades.execute(second, vendor, 1).success(), "second buyer finds it gone");
        helper.assertValueEqual(count(first, Items.DIAMOND) + count(second, Items.DIAMOND) + count(vendor.stock(), Items.DIAMOND), 1,
            "exactly one diamond in total");
        helper.assertValueEqual(balance(second.getUUID()), 1_000L, "second buyer paid nothing");
        helper.assertFalse(helper.getBlockState(VENDOR).getValue(VendingBlock.STOCKED), "light off when empty");
        helper.succeed();
    }

    public static void noRoomTakesNothing(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sell(Items.BREAD, 4, 100));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 16));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 1_000);
        for (int i = 0; i < buyer.getInventory().getNonEquipmentItems().size(); i++) {
            buyer.getInventory().getNonEquipmentItems().set(i, new ItemStack(Items.DIRT, 64));
        }
        helper.assertFalse(VendingTrades.execute(buyer, vendor, 1).success(), "no room: refused");
        helper.assertValueEqual(balance(buyer.getUUID()), 1_000L, "no money taken");
        helper.assertValueEqual(count(vendor.stock(), Items.BREAD), 16, "no goods taken");

        setBalance(buyer.getUUID(), 150);
        buyer.getInventory().getNonEquipmentItems().set(0, ItemStack.EMPTY);
        VendingTrades.Outcome outcome = VendingTrades.execute(buyer, vendor, 8);
        helper.assertValueEqual(outcome.trades(), 1, "money for one trade only");
        helper.assertValueEqual(balance(buyer.getUUID()), 50L, "paid for one trade");
        helper.succeed();
    }

    public static void itemPriceAndFullRevenue(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()),
            new VendingSettings(new ItemStack(Items.GOLDEN_APPLE), PriceMode.ITEM, 0, new ItemStack(Items.DIAMOND, 2), false, DisplayAnimation.STATIC));
        vendor.stock().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 10));
        vendor.stock().setChanged();
        for (int i = 0; i < 8; i++) {
            vendor.revenue().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        vendor.revenue().setItem(8, new ItemStack(Items.DIAMOND, 62));
        ServerPlayer buyer = playerAtVendor(helper);
        buyer.getInventory().add(new ItemStack(Items.DIAMOND, 6));

        VendingTrades.Outcome outcome = VendingTrades.execute(buyer, vendor, 3);
        helper.assertValueEqual(outcome.trades(), 1, "revenue has room for one payment only");
        helper.assertValueEqual(count(buyer, Items.DIAMOND), 4, "paid two diamonds");
        helper.assertValueEqual(count(buyer, Items.GOLDEN_APPLE), 1, "got one apple");
        helper.assertValueEqual(count(vendor.revenue(), Items.DIAMOND), 64, "diamonds in the revenue");

        helper.assertFalse(VendingTrades.execute(buyer, vendor, 1).success(), "revenue full: refused");
        helper.assertValueEqual(count(buyer, Items.DIAMOND), 4, "nothing taken when refused");
        helper.assertValueEqual(count(vendor.stock(), Items.GOLDEN_APPLE), 9, "stock untouched when refused");
        helper.succeed();
    }

    public static void buyback(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId),
            new VendingSettings(new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true, DisplayAnimation.STATIC));
        setBalance(ownerId, 200);
        ServerPlayer seller = playerAtVendor(helper);
        setBalance(seller.getUUID(), 0);
        seller.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));

        VendingTrades.Outcome outcome = VendingTrades.execute(seller, vendor, VendingTrades.UNLIMITED);
        helper.assertValueEqual(outcome.trades(), 2, "the owner can pay for two trades");
        helper.assertValueEqual(count(seller, Items.COBBLESTONE), 16, "sold 16 cobblestone");
        helper.assertValueEqual(count(vendor.stock(), Items.COBBLESTONE), 16, "stock got 16 cobblestone");
        helper.assertValueEqual(balance(ownerId), 0L, "owner paid 2.00");
        helper.assertValueEqual(balance(seller.getUUID()), 200L - 200 * 2 / 100, "seller got 2.00 minus the fee");
        helper.assertFalse(VendingTrades.execute(seller, vendor, 1).success(), "owner broke: refused");
        helper.assertValueEqual(count(seller, Items.COBBLESTONE), 16, "nothing taken when refused");
        helper.succeed();
    }

    public static void menuRules(GameTestHelper helper) {
        ServerPlayer owner = playerAtVendor(helper);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(owner.getUUID()), sell(Items.BREAD, 1, 100));
        VendingMenus.openOwner(owner, vendor, false);
        helper.assertTrue(owner.containerMenu instanceof VendingOwnerMenu, "owner menu open");
        VendingOwnerMenu menu = (VendingOwnerMenu) owner.containerMenu;

        // Sample slots copy, never take
        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clicked(VendingOwnerMenu.GOODS_SLOT, 0, ContainerInput.PICKUP, owner);
        helper.assertValueEqual(menu.getCarried().getCount(), 5, "cursor keeps all 5 diamonds");
        helper.assertTrue(menu.goodsSample().is(Items.DIAMOND) && menu.goodsSample().getCount() == 5, "sample is a copy of 5");
        menu.clicked(VendingOwnerMenu.GOODS_SLOT, 0, ContainerInput.QUICK_MOVE, owner);
        menu.clicked(VendingOwnerMenu.GOODS_SLOT, 0, ContainerInput.THROW, owner);
        helper.assertTrue(menu.goodsSample().is(Items.DIAMOND), "shift-click and drop do nothing on a sample");
        helper.assertFalse(menu.getSlot(VendingOwnerMenu.GOODS_SLOT).mayPickup(owner), "a sample can't be picked up");
        helper.assertTrue(menu.getSlot(VendingOwnerMenu.GOODS_SLOT) instanceof GhostSlot, "goods slot is a sample");
        menu.setCarried(ItemStack.EMPTY);

        // Stock takes only the goods; revenue takes nothing by hand
        owner.getInventory().getNonEquipmentItems().set(0, new ItemStack(Items.DIRT, 10));
        int dirtSlot = VendingOwnerMenu.INVENTORY_START + 27; // first hotbar slot
        menu.quickMoveStack(owner, dirtSlot);
        helper.assertValueEqual(count(vendor.stock(), Items.DIRT), 0, "dirt can't go into a diamond vendor");
        helper.assertValueEqual(count(owner, Items.DIRT), 10, "dirt stays with the owner");
        owner.getInventory().getNonEquipmentItems().set(1, new ItemStack(Items.DIAMOND, 3));
        menu.quickMoveStack(owner, dirtSlot + 1);
        helper.assertValueEqual(count(vendor.stock(), Items.DIAMOND), 3, "diamonds go into the stock");
        helper.assertFalse(menu.getSlot(VendingOwnerMenu.REVENUE_START).mayPlace(new ItemStack(Items.DIAMOND)), "revenue is take-only");
        owner.closeContainer();

        // Buyers have no slots, and a far buyer can't trade
        ServerPlayer far = playerAtVendor(helper);
        setBalance(far.getUUID(), 10_000);
        VendingBuyerMenu buyerMenu = new VendingBuyerMenu(99, far.getInventory(),
            new VendingBuyerMenu.Data(vendor.getBlockPos(), vendor.settings(), "owner"), vendor);
        helper.assertTrue(buyerMenu.slots.isEmpty(), "buyer menu has no slots");
        far.setPos(far.getX() + 20, far.getY(), far.getZ());
        helper.assertFalse(buyerMenu.clickMenuButton(far, VendingBuyerMenu.BUY_ONE), "too far: refused");
        helper.assertValueEqual(count(far, Items.DIAMOND), 0, "nothing bought from afar");
        helper.succeed();
    }

    public static void breakClosesMenusAndDropsOnce(GameTestHelper helper) {
        ServerPlayer owner = playerAtVendor(helper);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(owner.getUUID()), sell(Items.BREAD, 1, 100));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setItem(1, new ItemStack(Items.BREAD, 5));
        vendor.revenue().setItem(0, new ItemStack(Items.EMERALD, 7));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        VendingMenus.openBuyer(buyer, vendor);
        VendingMenus.openOwner(owner, vendor, false);
        helper.assertTrue(buyer.containerMenu instanceof VendingBuyerMenu && owner.containerMenu instanceof VendingOwnerMenu, "menus open");

        helper.getLevel().destroyBlock(helper.absolutePos(VENDOR), true);
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "buyer menu closed");
        helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "owner menu closed");
        int bread = 0;
        int emeralds = 0;
        int blocks = 0;
        for (ItemEntity item : helper.getEntities(EntityTypes.ITEM, VENDOR, 3)) {
            ItemStack stack = item.getItem();
            if (stack.is(Items.BREAD)) {
                bread += stack.getCount();
            } else if (stack.is(Items.EMERALD)) {
                emeralds += stack.getCount();
            } else if (stack.is(TraderyBlocks.VENDING_BLOCK_ITEM.get())) {
                blocks += stack.getCount();
            }
        }
        helper.assertValueEqual(bread, 69, "stock dropped exactly once");
        helper.assertValueEqual(emeralds, 7, "revenue dropped exactly once");
        helper.assertValueEqual(blocks, 1, "the block itself dropped");
        helper.succeed();
    }

    public static void protection(GameTestHelper helper) {
        ServerPlayer owner = playerAtVendor(helper);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(owner.getUUID()), sell(Items.BREAD, 1, 100));
        ServerPlayer stranger = playerAtVendor(helper);
        Level level = helper.getLevel();
        BlockPos pos = helper.absolutePos(VENDOR);

        helper.assertTrue(VendingProtection.mayBreak(owner, level, pos), "the owner may break it");
        helper.assertFalse(VendingProtection.mayBreak(stranger, level, pos), "a stranger may not");
        helper.assertValueEqual(level.getBlockState(pos).getDestroyProgress(stranger, level, pos), 0.0f, "no progress for strangers");
        helper.assertValueEqual(level.getBlockState(pos).getPistonPushReaction(), PushReaction.IMMOVEABLE, "pistons can't move it");
        helper.assertFalse(level.getBlockEntity(pos) instanceof Container, "not a container");
        helper.assertTrue(HopperBlockEntity.getContainerAt(level, pos) == null, "hoppers see no container");

        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() - 1.5, 4.0f, Level.ExplosionInteraction.TNT);
        helper.assertTrue(level.getBlockState(pos).is(TraderyBlocks.VENDING_BLOCK.get()), "explosions can't break it");
        helper.assertTrue(level.getBlockEntity(pos) == vendor, "same block entity after the explosion");
        helper.succeed();
    }

    public static void rateLimit(GameTestHelper helper) {
        ServerPlayer player = playerAtVendor(helper);
        VendingTrades.forget(player);
        int allowed = 0;
        for (int i = 0; i < 15; i++) {
            if (VendingTrades.allow(player)) {
                allowed++;
            }
        }
        helper.assertValueEqual(allowed, 10, "10 requests per second");
        VendingTrades.forget(player);
        helper.succeed();
    }

    public static void offlineOwnerGetsSummary(GameTestHelper helper) {
        UUID offlineOwner = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(offlineOwner), sell(Items.APPLE, 2, 500));
        vendor.stock().setItem(0, new ItemStack(Items.APPLE, 10));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 10_000);
        helper.assertTrue(VendingTrades.execute(buyer, vendor, 2).success(), "sale to an offline owner");

        NotificationsData notifications = NotificationsData.get(helper.getLevel().getServer());
        NotificationsData.Pending pending = notifications.take(offlineOwner).orElse(null);
        helper.assertTrue(pending != null, "the sale waits for the owner");
        helper.assertValueEqual(pending.sales(), 1, "one sale");
        helper.assertValueEqual(pending.earned(), 1_000L - 1_000 * 2 / 100, "earned the price minus the fee");
        helper.succeed();
    }

    public static void newPriceClosesBuyerScreens(GameTestHelper helper) {
        ServerPlayer owner = playerAtVendor(helper);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(owner.getUUID()), sell(Items.BREAD, 1, 100));
        ServerPlayer buyer = playerAtVendor(helper);
        VendingMenus.openBuyer(buyer, vendor);
        VendingMenus.openOwner(owner, vendor, false);
        VendingConfigurator.save(owner, (VendingOwnerMenu) owner.containerMenu, 900);
        helper.assertValueEqual(vendor.settings().price(), 900L, "new price saved");
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "the buyer's screen closed: no buying at a price they didn't see");
        helper.assertTrue(owner.containerMenu instanceof VendingOwnerMenu, "the owner keeps editing");
        helper.succeed();
    }

    // ------------------------------------------------------------------ quick trade (sneak + click)

    public static void quickTradeBuysOneLot(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sell(Items.BREAD, 4, 250));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 1_000);
        BlockPos pos = helper.absolutePos(VENDOR);

        VendingQuickTrade.handle(buyer, pos, false);
        helper.assertValueEqual(count(buyer, Items.BREAD), 4, "one lot per request");
        helper.assertValueEqual(balance(buyer.getUUID()), 750L, "paid for one lot");
        VendingQuickTrade.handle(buyer, pos, false);
        helper.assertValueEqual(count(buyer, Items.BREAD), 8, "a held button trades again");
        helper.assertValueEqual(VendingQuickTrade.streakGoods(buyer), 8, "the action bar adds the streak up");
        helper.assertValueEqual(count(vendor.stock(), Items.BREAD), 56, "stock lost 8 bread");
        VendingQuickTrade.forget(buyer);
        VendingTrades.forget(buyer);
        helper.succeed();
    }

    public static void quickTradeWrongButton(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sell(Items.BREAD, 4, 250));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 1_000);
        buyer.getInventory().add(new ItemStack(Items.BREAD, 4));

        VendingQuickTrade.handle(buyer, helper.absolutePos(VENDOR), true);
        helper.assertValueEqual(count(buyer, Items.BREAD), 4, "sneak + attack doesn't buy or sell at a selling block");
        helper.assertValueEqual(balance(buyer.getUUID()), 1_000L, "no money moved");
        helper.assertValueEqual(count(vendor.stock(), Items.BREAD), 64, "stock untouched");
        VendingTrades.forget(buyer);
        helper.succeed();
    }

    public static void quickTradeSellsToBuyback(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId),
            new VendingSettings(new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true, DisplayAnimation.STATIC));
        setBalance(ownerId, 1_000);
        ServerPlayer seller = playerAtVendor(helper);
        setBalance(seller.getUUID(), 0);
        seller.getInventory().add(new ItemStack(Items.COBBLESTONE, 20));
        BlockPos pos = helper.absolutePos(VENDOR);

        VendingQuickTrade.handle(seller, pos, false);
        helper.assertValueEqual(count(seller, Items.COBBLESTONE), 20, "sneak + use doesn't sell");
        VendingQuickTrade.handle(seller, pos, true);
        helper.assertValueEqual(count(seller, Items.COBBLESTONE), 12, "sold one lot of 8");
        helper.assertValueEqual(count(vendor.stock(), Items.COBBLESTONE), 8, "the block got the 8");
        helper.assertValueEqual(balance(ownerId), 900L, "the owner paid 1.00");
        helper.assertValueEqual(balance(seller.getUUID()), 100L - 100 * 2 / 100, "the seller got 1.00 minus the fee");
        VendingQuickTrade.forget(seller);
        VendingTrades.forget(seller);
        helper.succeed();
    }

    public static void quickTradeNeedsReach(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sell(Items.BREAD, 4, 250));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        ServerPlayer buyer = playerAtVendor(helper);
        setBalance(buyer.getUUID(), 1_000);
        buyer.setPos(buyer.getX(), buyer.getY(), buyer.getZ() - 20);

        VendingQuickTrade.handle(buyer, helper.absolutePos(VENDOR), false);
        helper.assertValueEqual(count(buyer, Items.BREAD), 0, "out of reach: refused");
        helper.assertValueEqual(balance(buyer.getUUID()), 1_000L, "no money moved");
        VendingTrades.forget(buyer);
        helper.succeed();
    }
}
