package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.TradeCrateBlock;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.TradeCrates;
import dev.eliasnvx.tradery.village.VillagerCustomers;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerTrading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Trade crates (SPEC "Торговый ящик"): villagers' own trades with what the crate holds, by the trade window's rules. */
public final class TradeCrateGameTests {
    private static final BlockPos CRATE = new BlockPos(1, 1, 2);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("crate_buys_with_money", TradeCrateGameTests::buysWithMoney),
        new TraderyGameTests.Entry("crate_sells_for_money", TradeCrateGameTests::sellsForMoney),
        new TraderyGameTests.Entry("crate_village_tax", TradeCrateGameTests::villageTax),
        new TraderyGameTests.Entry("crate_emeralds_as_items", TradeCrateGameTests::emeraldsAsItems),
        new TraderyGameTests.Entry("crate_failures_change_nothing", TradeCrateGameTests::failuresChangeNothing),
        new TraderyGameTests.Entry("crate_owner_reputation_discount", TradeCrateGameTests::ownerReputationDiscount),
        new TraderyGameTests.Entry("crate_hopper_sides", TradeCrateGameTests::hopperSides),
        new TraderyGameTests.Entry("crate_break_drops_stores", TradeCrateGameTests::breakDropsStores),
        new TraderyGameTests.Entry("crate_villager_walks_and_trades", TradeCrateGameTests::villagerWalksAndTrades));

    private TradeCrateGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** A crate at {@link #CRATE} facing north, owned by {@code owner}, wanting {@code wants}. */
    private static TradeCrateBlockEntity crate(GameTestHelper helper, UUID owner, Item... wants) {
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(CRATE, TraderyBlocks.TRADE_CRATE.get().defaultBlockState().setValue(TradeCrateBlock.FACING, Direction.NORTH));
        TradeCrateBlockEntity crate = helper.getBlockEntity(CRATE, TradeCrateBlockEntity.class);
        crate.setOwner(AccountId.player(owner), "Owner");
        for (int i = 0; i < wants.length; i++) {
            crate.wants().setItem(i, new ItemStack(wants[i]));
        }
        return crate;
    }

    /** A librarian in front of the crate with exactly these offers ({@code ai}: it can walk). */
    private static Villager villager(GameTestHelper helper, boolean ai, MerchantOffer... offers) {
        BlockPos at = ai ? new BlockPos(3, 1, 0) : CRATE.north();
        Villager villager = ai ? helper.spawn(EntityTypes.VILLAGER, at) : helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, at);
        ServerLevel level = helper.getLevel();
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));
        ResourceKey<net.minecraft.world.level.Level> dimension = level.dimension();
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(0, 1, 4))));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(4, 1, 4))));
        if (ai) {
            // Its own meeting point, so a walking villager never takes the bell of a neighbouring test (its village's
            // tax doesn't change what the owner pays)
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(0, 1, 0))));
        }
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        // Some experience: vanilla's ResetProfession takes the job of a novice who never traded and has no job site
        villager.setVillagerXp(1);
        MerchantOffers list = villager.getOffers();
        list.clear();
        list.addAll(List.of(offers));
        return villager;
    }

    /** {@code emeralds} → {@code count} of {@code item}. */
    private static MerchantOffer buy(int emeralds, Item item, int count, int maxUses) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(), new ItemStack(item, count), 0, maxUses, 5, 0.05f);
    }

    /** {@code count} of {@code item} → one emerald. */
    private static MerchantOffer sell(Item item, int count, int maxUses) {
        return new MerchantOffer(new ItemCost(item, count), Optional.empty(), new ItemStack(Items.EMERALD), 0, maxUses, 2, 0.05f);
    }

    private static long emerald() {
        return VillagerTrading.emeraldValue();
    }

    private static long balance(UUID player) {
        return EconomyService.INSTANCE.account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(UUID player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
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

    private static long count(List<ItemStack> slots, Item item) {
        return StackMath.countMatching(slots, stack -> stack.is(item));
    }

    private static ServerConfig withTrading(ServerConfig config, boolean enabled) {
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(), config.ore(), config.history(),
            config.log(), config.villagers(), config.market(),
            new ServerConfig.VillagerTradingSection(enabled, config.villagerTrading().emeraldValue()), config.villages(), config.tradeCrates(), config.integrations());
    }

    // ------------------------------------------------------------------ tests

    /** The crate buys arrows: emeralds come off the owner's balance into the wallet, until the offer is used up. */
    public static void buysWithMoney(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW);
        Villager villager = villager(helper, false, buy(1, Items.ARROW, 16, 3));
        setBalance(owner, 10 * emerald());
        setWallet(helper, villager, 0);
        int xpBefore = villager.getVillagerXp();

        helper.assertValueEqual(TradeCrates.findOffer(villager, crate), 0, "the arrow offer is the one");
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 3, "three trades: the offer's uses");
        helper.assertValueEqual(count(crate.received(), Items.ARROW), 48L, "48 arrows received");
        helper.assertValueEqual(balance(owner), 7 * emerald(), "the owner paid three emeralds' worth");
        helper.assertValueEqual(wallet(helper, villager), 3 * emerald(), "into the villager's wallet (no village: no tax)");
        MerchantOffer offer = villager.getOffers().getFirst();
        helper.assertTrue(offer.isOutOfStock(), "the offer is used up");
        helper.assertValueEqual(villager.getVillagerXp() - xpBefore, 15, "vanilla experience for three trades");
        helper.assertTrue(villager.getGossips().getReputation(owner, type -> type == GossipType.TRADING) > 0, "the owner earns TRADING gossip");
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 0, "nothing more until it restocks");
        helper.assertValueEqual(TradeCrates.findOffer(villager, crate), -1, "no offer it can do");
        helper.succeed();
    }

    /** In a village the owner pays the full price; the village takes its tax out of it, the wallet gets the rest. */
    public static void villageTax(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW);
        Villager villager = villager(helper, false, buy(2, Items.ARROW, 16, 1));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3))));
        dev.eliasnvx.tradery.village.VillageState village = dev.eliasnvx.tradery.village.Villages.at(helper.getLevel().getServer(),
            helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3)));
        dev.eliasnvx.tradery.village.Villages.set(helper.getLevel().getServer(), village, 0, 0);
        setBalance(owner, 10 * emerald());
        setWallet(helper, villager, 0);
        long price = 2 * emerald();
        long tax = dev.eliasnvx.tradery.util.Money.percentOf(price, TraderyConfig.server().villages().tradeTaxPercent());
        helper.assertTrue(tax > 0, "a tax to test");

        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 1, "bought");
        helper.assertValueEqual(balance(owner), 8 * emerald(), "the full price");
        helper.assertValueEqual(wallet(helper, villager), price - tax, "the wallet gets the price less the tax");
        helper.assertValueEqual(village.treasury(), tax, "the tax in the treasury");
        helper.succeed();
    }

    /** "Wants: emerald" sells to villagers: the paper goes, the wallet pays the owner. */
    public static void sellsForMoney(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.EMERALD);
        Villager villager = villager(helper, false, sell(Items.PAPER, 24, 5));
        crate.setItem(0, new ItemStack(Items.PAPER, 50));
        setBalance(owner, 0);
        setWallet(helper, villager, 10 * emerald());

        helper.assertValueEqual(TradeCrates.visit(villager, crate, -1), 2, "two trades: paper for two");
        helper.assertValueEqual(count(crate.payment(), Items.PAPER), 2L, "48 paper went");
        helper.assertValueEqual(balance(owner), 2 * emerald(), "the owner got two emeralds' worth");
        helper.assertValueEqual(wallet(helper, villager), 8 * emerald(), "from the villager's wallet");
        helper.assertValueEqual(count(crate.received(), Items.EMERALD), 0L, "no emerald item: money");
        helper.succeed();
    }

    /** With trading for money off, emeralds are items: taken from the payment, put into the received store. */
    public static void emeraldsAsItems(GameTestHelper helper) {
        ServerConfig original = TraderyConfig.server();
        try {
            TraderyConfig.setServerForTests(withTrading(original, false));
            UUID owner = UUID.randomUUID();
            TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW, Items.EMERALD);
            Villager villager = villager(helper, false, buy(1, Items.ARROW, 16, 5), sell(Items.STICK, 32, 5));
            crate.setItem(0, new ItemStack(Items.EMERALD, 2));
            crate.setItem(1, new ItemStack(Items.STICK, 32));
            setBalance(owner, 100 * emerald());

            helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 2, "two arrow trades: two emeralds");
            helper.assertValueEqual(count(crate.payment(), Items.EMERALD), 0L, "the emeralds went");
            helper.assertValueEqual(count(crate.received(), Items.ARROW), 32L, "32 arrows came");
            helper.assertValueEqual(TradeCrates.visit(villager, crate, 1), 1, "the sticks for an emerald");
            helper.assertValueEqual(count(crate.received(), Items.EMERALD), 1L, "an emerald item received");
            helper.assertValueEqual(balance(owner), 100 * emerald(), "no money moved");
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        helper.succeed();
    }

    /** No room, no money, an empty wallet, a missing book: no trade, and nothing moves. */
    public static void failuresChangeNothing(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW, Items.EMERALD, Items.BOOKSHELF);
        MerchantOffer shelf = new MerchantOffer(new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.BOOKSHELF),
            0, 5, 5, 0.05f);
        Villager villager = villager(helper, false, buy(1, Items.ARROW, 16, 5), sell(Items.PAPER, 24, 5), shelf);
        crate.setItem(0, new ItemStack(Items.PAPER, 24));

        // No room for arrows; the owner can't pay; the wallet is empty; no book
        for (int i = 0; i < TradeCrateBlockEntity.STORE_SIZE; i++) {
            crate.setItem(TradeCrateBlockEntity.RECEIVED_START + i, new ItemStack(Items.DIRT, 64));
        }
        setBalance(owner, 5 * emerald());
        setWallet(helper, villager, 0);
        helper.assertValueEqual(TradeCrates.findOffer(villager, crate), -1, "nothing it can do");
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 0, "a full crate takes no arrows");
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 1), 0, "an empty wallet buys no paper");
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 2), 0, "no book, no bookshelf");
        helper.assertValueEqual(balance(owner), 5 * emerald(), "balance unchanged");
        helper.assertValueEqual(count(crate.payment(), Items.PAPER), 24L, "paper unchanged");
        helper.assertValueEqual(wallet(helper, villager), 0L, "wallet unchanged");

        // Room again, but no money
        crate.received().replaceAll(stack -> ItemStack.EMPTY);
        setBalance(owner, 0);
        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 0, "the owner can't pay");
        helper.assertValueEqual(count(crate.received(), Items.ARROW), 0L, "no arrows");
        helper.assertTrue(villager.getOffers().stream().allMatch(offer -> offer.getUses() == 0), "no offer was used");
        helper.succeed();
    }

    /** The owner's good name lowers the price, as vanilla does for a player; the discount is gone afterwards. */
    public static void ownerReputationDiscount(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ENCHANTED_BOOK);
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 10), Optional.empty(), new ItemStack(Items.ENCHANTED_BOOK), 0, 1, 5, 0.2f);
        Villager villager = villager(helper, false, offer);
        villager.getGossips().add(owner, GossipType.MAJOR_POSITIVE, 4);
        int reputation = villager.getGossips().getReputation(owner, type -> true);
        int discount = (int) Math.floor(reputation * 0.2f);
        helper.assertTrue(discount > 0, "a discount to test");
        setBalance(owner, 10 * emerald());

        helper.assertValueEqual(TradeCrates.visit(villager, crate, 0), 1, "bought");
        helper.assertValueEqual(balance(owner), discount * emerald(), "paid the discounted price");
        helper.assertValueEqual(offer.getSpecialPriceDiff(), 0, "the discount doesn't stay on the offer");
        helper.succeed();
    }

    /** Hoppers: in from above into the payment only, out from below from the received store only. */
    public static void hopperSides(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW);
        int payment = 0;
        int received = TradeCrateBlockEntity.RECEIVED_START;
        ItemStack any = new ItemStack(Items.EMERALD);
        helper.assertTrue(crate.canPlaceItemThroughFace(payment, any, Direction.UP), "in from the top into the payment");
        helper.assertTrue(crate.canPlaceItemThroughFace(payment, any, Direction.EAST), "in from a side into the payment");
        helper.assertFalse(crate.canPlaceItemThroughFace(received, any, Direction.UP), "never into the received store");
        helper.assertFalse(crate.canPlaceItemThroughFace(payment, any, Direction.DOWN), "nothing in from below");
        helper.assertTrue(crate.canTakeItemThroughFace(received, any, Direction.DOWN), "out from below from the received store");
        helper.assertFalse(crate.canTakeItemThroughFace(payment, any, Direction.DOWN), "never out of the payment");
        helper.assertFalse(crate.canTakeItemThroughFace(received, any, Direction.NORTH), "nothing out from a side");

        // Real hoppers: one above feeds emeralds in, one below pulls the arrows out and leaves the payment alone
        crate.setItem(received, new ItemStack(Items.ARROW, 5));
        crate.setItem(1, new ItemStack(Items.PAPER, 7));
        helper.setBlock(CRATE.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(CRATE.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 3));
        BlockPos below = CRATE.below();
        helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.succeedWhen(() -> {
            HopperBlockEntity hopper = helper.getBlockEntity(below, HopperBlockEntity.class);
            helper.assertValueEqual(count(contents(hopper), Items.ARROW) + count(itemsNear(helper), Items.ARROW), 5L,
                "the arrows came out below");
            helper.assertValueEqual(count(contents(hopper), Items.PAPER), 0L, "the payment stayed");
            helper.assertValueEqual(count(crate.payment(), Items.EMERALD), 3L, "the emeralds went into the payment");
            helper.assertValueEqual(count(crate.payment(), Items.PAPER), 7L, "the paper is still there");
        });
    }

    private static List<ItemStack> contents(net.minecraft.world.Container container) {
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(container.getItem(i));
        }
        return items;
    }

    private static List<ItemStack> itemsNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CRATE)).inflate(4)).stream()
            .map(ItemEntity::getItem).toList();
    }

    /** Broken, the crate drops both stores and leaves the shop index. */
    public static void breakDropsStores(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW);
        ServerLevel level = helper.getLevel();
        VendorsData.get(level.getServer()).add(level, helper.absolutePos(CRATE), AccountId.player(owner), true);
        crate.setItem(0, new ItemStack(Items.PAPER, 10));
        crate.setItem(TradeCrateBlockEntity.RECEIVED_START, new ItemStack(Items.ARROW, 20));
        level.destroyBlock(helper.absolutePos(CRATE), false);
        List<ItemStack> dropped = itemsNear(helper);
        helper.assertValueEqual(count(dropped, Items.PAPER), 10L, "the payment dropped");
        helper.assertValueEqual(count(dropped, Items.ARROW), 20L, "the received items dropped");
        helper.assertTrue(VendorsData.get(level.getServer()).get(GlobalPos.of(level.dimension(), helper.absolutePos(CRATE))) == null,
            "gone from the index");
        helper.succeed();
    }

    /** A villager with a bed and a job walks to the crate and trades there. */
    public static void villagerWalksAndTrades(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradeCrateBlockEntity crate = crate(helper, owner, Items.ARROW);
        Villager villager = villager(helper, true, buy(1, Items.ARROW, 16, 2));
        setBalance(owner, 10 * emerald());
        helper.assertTrue(VillagerCustomers.invite(villager, crate), "invited to the crate");
        helper.assertFalse(VillagerCustomers.invite(villager, crate), "one visit at a time");
        helper.succeedWhen(() -> {
            helper.assertFalse(VillagerCustomers.isCustomer(villager.getUUID()), "the visit is over");
            helper.assertValueEqual(count(crate.received(), Items.ARROW), 32L, "two trades of arrows");
            helper.assertValueEqual(balance(owner), 8 * emerald(), "paid for both");
        });
    }
}
