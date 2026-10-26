package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.HistoryData;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.NotificationsData;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerCustomers;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerMarket;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerSupplies;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

/**
 * Village economy: villagers selling their produce to players' buyback blocks (SPEC "Жители-поставщики"). Money and
 * items are counted on every side before and after (the owner's balance, the money supply, the block's stock, the
 * villager's produce and wallet); every refusal must leave all of them as they were. Every test has its own village
 * (a bell at its own origin); villagers of the one-tick tests are removed at the end.
 */
public final class SupplierGameTests {
    private static final BlockPos VENDOR = new BlockPos(2, 1, 2);
    private static final Item WHEAT = Items.WHEAT;
    private static final String WHEAT_KEY = "minecraft:wheat";
    private static final int PER_TRADE = 8;

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("supplier_walks_up_and_sells", SupplierGameTests::walksUpAndSells),
        new TraderyGameTests.Entry("supplier_sale_atomic", SupplierGameTests::saleAtomic),
        new TraderyGameTests.Entry("supplier_price_threshold", SupplierGameTests::priceThreshold),
        new TraderyGameTests.Entry("supplier_stock_and_money_limits", SupplierGameTests::stockAndMoneyLimits),
        new TraderyGameTests.Entry("supplier_room_limit", SupplierGameTests::roomLimit),
        new TraderyGameTests.Entry("supplier_not_served", SupplierGameTests::notServed),
        new TraderyGameTests.Entry("supplier_production_piles_up", SupplierGameTests::productionPilesUp),
        new TraderyGameTests.Entry("supplier_default_supplies", SupplierGameTests::defaultSupplies));

    private SupplierGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** A buyback block facing north (suppliers stand at {@code VENDOR.north()}), with an empty stock. */
    private static VendingBlockEntity vendor(GameTestHelper helper, AccountId owner, VendingSettings settings) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(VENDOR, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = helper.getBlockEntity(VENDOR, VendingBlockEntity.class);
        vendor.setOwner(owner, "owner");
        vendor.setSettings(settings);
        return vendor;
    }

    /** Buyback of {@code PER_TRADE} wheat for {@code price} minor units ("Villagers can sell" on). */
    private static VendingSettings buyback(long price) {
        return new VendingSettings(new ItemStack(WHEAT, PER_TRADE), PriceMode.CURRENCY, price, ItemStack.EMPTY, true, DisplayAnimation.STATIC);
    }

    /**
     * An adult villager with a bed, a job site and the bell of this test's own village, in a daytime activity.
     *
     * @param ai false: no AI (the one-tick tests call the visit directly)
     */
    private static Villager villager(GameTestHelper helper, BlockPos at, boolean ai, ResourceKey<VillagerProfession> profession, int level) {
        Villager villager = ai ? helper.spawn(EntityTypes.VILLAGER, at) : helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, at);
        ServerLevel serverLevel = helper.getLevel();
        villager.setVillagerData(villager.getVillagerData().withProfession(serverLevel.registryAccess(), profession).withLevel(level));
        ResourceKey<Level> dimension = serverLevel.dimension();
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(0, 1, 4))));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(4, 1, 4))));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(dimension, helper.absolutePos(BlockPos.ZERO)));
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return villager;
    }

    /** An apprentice farmer (a novice without experience may lose its job when its job site isn't a real workstation). */
    private static Villager farmer(GameTestHelper helper, BlockPos at, boolean ai) {
        return villager(helper, at, ai, VillagerProfession.FARMER, 2);
    }

    /** The lowest price of a trade the villager's village accepts. */
    private static long minPrice(GameTestHelper helper, Villager villager) {
        return VillagerSupplies.minLotPrice(new ItemStack(WHEAT), PER_TRADE, Villages.of(villager)).orElseThrow();
    }

    private static VillagerLedger ledger(GameTestHelper helper) {
        return VillagerLedger.get(server(helper));
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(server(helper));
    }

    /** Sets the villager's wheat and an empty wallet (no wage due today). */
    private static void prepare(GameTestHelper helper, Villager villager, int wheat) {
        ledger(helper).setWallet(villager.getUUID(), 0, today(helper));
        ledger(helper).setStock(villager.getUUID(), WHEAT_KEY, wheat, today(helper));
    }

    private static int produce(GameTestHelper helper, Villager villager) {
        return ledger(helper).stock(villager.getUUID(), WHEAT_KEY);
    }

    private static long wallet(GameTestHelper helper, Villager villager) {
        return VillagerPurchases.wallet(villager, today(helper));
    }

    private static long balance(UUID player) {
        return EconomyService.INSTANCE.account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(UUID player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static long supply() {
        return EconomyService.INSTANCE.ledger().total(EconomyService.INSTANCE.defaultCurrency().id());
    }

    private static long fee(long total) {
        return Money.percentOf(total, TraderyConfig.server().vending().feePercent());
    }

    private static int stock(VendingBlockEntity vendor, Item item) {
        int total = 0;
        for (ItemStack stack : vendor.stock().getItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Everything a supply moves, to compare before and after a refusal. */
    private record Snapshot(long ownerBalance, long moneySupply, int vendorWheat, int villagerWheat, long villagerWallet) {
        static Snapshot of(GameTestHelper helper, UUID owner, VendingBlockEntity vendor, Villager villager) {
            return new Snapshot(balance(owner), supply(), stock(vendor, WHEAT), produce(helper, villager), wallet(helper, villager));
        }
    }

    private static void assertUnchanged(GameTestHelper helper, Snapshot before, UUID owner, VendingBlockEntity vendor, Villager villager, String why) {
        helper.assertValueEqual(Snapshot.of(helper, owner, vendor, villager), before, why + ": nothing changed");
    }

    private static ServerConfig withSuppliers(ServerConfig config, VillagesConfig.Suppliers suppliers) {
        VillagesConfig v = config.villages();
        VillagesConfig villages = new VillagesConfig(v.censusIntervalTicks(), v.prosperityDecayPerDay(), v.treasuryPerVillager(), v.treasuryDays(),
            v.tradeTaxPercent(), v.boardRadius(), v.levels(), v.orders(), suppliers, v.caravans(), v.marketDay(), v.rent(), v.threats(), v.buildings(), v.festival());
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(), config.ore(), config.history(),
            config.log(), config.villagers(), config.market(), config.villagerTrading(), villages);
    }

    // ------------------------------------------------------------------ tests

    /** The whole visit: invited, walks to the buyback, looks, sells; the owner pays, the villager's wallet and produce change. */
    public static void walksUpAndSells(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), buyback(1));
        Villager villager = farmer(helper, new BlockPos(0, 1, 0), true);
        // Well above the threshold: the census may set the village's type (and regional prices) during the walk
        long price = minPrice(helper, villager) * 2;
        vendor.setSettings(buyback(price));
        int lots = TraderyConfig.server().villages().suppliers().maxLotsPerVisit();
        long total = price * lots;
        prepare(helper, villager, PER_TRADE * lots + 5);
        setBalance(ownerId, total * 3);

        helper.assertTrue(VillagerCustomers.isEligible(villager), "eligible");
        helper.assertTrue(VillagerCustomers.invite(villager, vendor), "invited to the buyback");
        helper.assertTrue(VillagerCustomers.isSupplier(villager.getUUID()), "on the way to sell");
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "one visit at a time");

        helper.succeedWhen(() -> {
            helper.assertFalse(VillagerCustomers.isCustomer(villager.getUUID()), "the visit is over");
            helper.assertValueEqual(stock(vendor, WHEAT), PER_TRADE * lots, "the block bought the lots");
            helper.assertValueEqual(balance(ownerId), total * 2, "the owner paid the price");
            helper.assertValueEqual(produce(helper, villager), 5, "the villager's produce shrank by the goods sold");
            helper.assertValueEqual(wallet(helper, villager), total - fee(total), "the villager got the price minus the fee");
            helper.assertTrue(ledger(helper).onCooldown(villager.getUUID(), helper.getLevel().getGameTime()), "cooldown after the visit");
            NotificationsData.Pending pending = NotificationsData.get(server(helper)).take(ownerId).orElse(null);
            helper.assertTrue(pending != null && pending.supplies() == 1 && pending.sales() == 0, "the offline owner's summary counts the supply");
            helper.assertValueEqual(pending.supplySpent(), total, "the summary has the money spent");
        });
    }

    /** One sale moves exactly: money owner → wallet (fee burnt), goods produce → block; saved together; reason, notification, prosperity. */
    public static void saleAtomic(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = farmer(helper, new BlockPos(2, 1, 1), false);
        long price = minPrice(helper, villager);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), buyback(price));
        int lots = TraderyConfig.server().villages().suppliers().maxLotsPerVisit();
        long total = price * lots;
        prepare(helper, villager, 100);
        setBalance(ownerId, total * 5);
        long supplyBefore = supply();
        VillageState village = Villages.of(villager);
        helper.assertTrue(village != null, "the farmer has a village");
        double prosperityBefore = village.prosperity();

        VillagerSupplies.Visit visit = VillagerSupplies.visit(villager, vendor);
        helper.assertTrue(visit.sold(), "sold: " + visit.result());
        helper.assertValueEqual(visit.lots(), lots, "maxLotsPerVisit lots");
        helper.assertValueEqual(visit.earned(), total - fee(total), "earned the price minus the fee");
        helper.assertValueEqual(stock(vendor, WHEAT), PER_TRADE * lots, "the block got the goods");
        helper.assertValueEqual(produce(helper, villager), 100 - PER_TRADE * lots, "made from the villager's produce: no more, no less");
        helper.assertValueEqual(balance(ownerId), total * 4, "the owner paid the price");
        helper.assertValueEqual(wallet(helper, villager), total - fee(total), "the wallet got the price minus the fee");
        helper.assertValueEqual(supplyBefore - supply(), total, "the owner's money left the ledger (to the wallet, the fee burnt)");
        helper.assertTrue(TradePersistence.waitsForSave(helper.getLevel(), helper.absolutePos(VENDOR)),
            "the money and the villager's produce are saved together with the chunk's stock");
        HistoryData.Entry last = EconomyService.INSTANCE.history(ownerId).getFirst();
        helper.assertValueEqual(last.reason(), Reasons.VILLAGER_SUPPLY, "reason tradery:villager/supply");
        helper.assertValueEqual(last.delta(), -total, "one transaction for the whole visit");
        helper.assertTrue(village.prosperity() > prosperityBefore, "the village gained prosperity");
        NotificationsData.Pending pending = NotificationsData.get(server(helper)).take(ownerId).orElse(null);
        helper.assertTrue(pending != null && pending.supplies() == 1, "one supply in the summary");
        helper.assertValueEqual(pending.lines().getLast().key(), "tradery.notify.villager_supply", "\"Farmer sold 16 × Wheat for …\"");
        villager.discard();
        helper.succeed();
    }

    /** Below fair price × regional factor × minPriceRatio: no sale; at it: sold. A fair price earns TRADING gossip. */
    public static void priceThreshold(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = farmer(helper, new BlockPos(1, 1, 1), false);
        long min = minPrice(helper, villager);
        helper.assertTrue(min > 1, "a threshold above one minor unit: " + min);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), buyback(min - 1));
        prepare(helper, villager, 100);
        setBalance(ownerId, min * 100);

        Snapshot before = Snapshot.of(helper, ownerId, vendor, villager);
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.TOO_CHEAP, "one unit below: too cheap");
        assertUnchanged(helper, before, ownerId, vendor, villager, "too cheap");
        helper.assertFalse(VillagerSupplies.canSupply(villager, vendor, today(helper)), "the scan wouldn't send it either");

        vendor.setSettings(buyback(min));
        helper.assertTrue(VillagerSupplies.visit(villager, vendor).sold(), "at the threshold: sold");
        helper.assertValueEqual(villager.getGossips().getReputation(ownerId, type -> true), 0, "below the fair price: no TRADING gossip");

        Villager fairFarmer = farmer(helper, new BlockPos(3, 1, 1), false);
        prepare(helper, fairFarmer, 100);
        double ratio = TraderyConfig.server().villages().suppliers().minPriceRatio();
        long fair = (long) Math.ceil(min / ratio) + 1;
        vendor.setSettings(buyback(fair));
        helper.assertTrue(VillagerSupplies.visit(fairFarmer, vendor).sold(), "fair price: sold");
        helper.assertTrue(fairFarmer.getGossips().getReputation(ownerId, type -> true) > 0, "a fair price: TRADING gossip about the owner");
        villager.discard();
        fairFarmer.discard();
        helper.succeed();
    }

    /** The villager's produce and the owner's money limit the lots; with neither, nothing changes. */
    public static void stockAndMoneyLimits(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = farmer(helper, new BlockPos(1, 1, 1), false);
        long price = minPrice(helper, villager);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), buyback(price));

        // Produce for one trade and a bit
        prepare(helper, villager, PER_TRADE + 3);
        setBalance(ownerId, price * 100);
        VillagerSupplies.Visit visit = VillagerSupplies.visit(villager, vendor);
        helper.assertValueEqual(visit.lots(), 1, "produce for one trade: one lot (" + visit.result() + ")");
        helper.assertValueEqual(produce(helper, villager), 3, "the rest stays with the villager");
        Snapshot before = Snapshot.of(helper, ownerId, vendor, villager);
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NO_STOCK, "not a trade's worth left");
        assertUnchanged(helper, before, ownerId, vendor, villager, "no produce");

        // Money for one trade and a half
        Villager rich = farmer(helper, new BlockPos(3, 1, 1), false);
        prepare(helper, rich, 100);
        setBalance(ownerId, price + price / 2);
        int vendorBefore = stock(vendor, WHEAT);
        visit = VillagerSupplies.visit(rich, vendor);
        helper.assertValueEqual(visit.lots(), 1, "the owner pays for one lot only (" + visit.result() + ")");
        helper.assertValueEqual(balance(ownerId), price / 2, "the change stays with the owner");
        helper.assertValueEqual(stock(vendor, WHEAT), vendorBefore + PER_TRADE, "one lot in the block");
        helper.assertValueEqual(produce(helper, rich), 100 - PER_TRADE, "one lot from the produce");
        before = Snapshot.of(helper, ownerId, vendor, rich);
        helper.assertValueEqual(VillagerSupplies.visit(rich, vendor).result(), VillagerSupplies.Result.FAILED, "the owner can't pay");
        assertUnchanged(helper, before, ownerId, vendor, rich, "owner short of money");
        helper.assertFalse(VendingTrades.buyFromVillager(vendor, "test", 1).success(), "the trade itself refuses too");
        assertUnchanged(helper, before, ownerId, vendor, rich, "owner short of money (direct)");
        villager.discard();
        rich.discard();
        helper.succeed();
    }

    /** The block's room limits the lots; a full block buys nothing and nothing changes. */
    public static void roomLimit(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = farmer(helper, new BlockPos(1, 1, 1), false);
        long price = minPrice(helper, villager);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), buyback(price));
        prepare(helper, villager, 100);
        setBalance(ownerId, price * 100);
        // Room for exactly one trade: every slot taken but one, which has space for PER_TRADE wheat
        List<ItemStack> slots = vendor.stock().getItems();
        for (int i = 1; i < slots.size(); i++) {
            slots.set(i, new ItemStack(Items.DIRT, 64));
        }
        slots.set(0, new ItemStack(WHEAT, 64 - PER_TRADE));
        vendor.stock().setChanged();

        VillagerSupplies.Visit visit = VillagerSupplies.visit(villager, vendor);
        helper.assertValueEqual(visit.lots(), 1, "room for one lot (" + visit.result() + ")");
        helper.assertValueEqual(stock(vendor, WHEAT), 64, "the slot is full now");
        helper.assertValueEqual(produce(helper, villager), 100 - PER_TRADE, "only what fitted left the produce");
        helper.assertValueEqual(balance(ownerId), price * 99, "only what fitted was paid");

        Snapshot before = Snapshot.of(helper, ownerId, vendor, villager);
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.FAILED, "full: no sale");
        assertUnchanged(helper, before, ownerId, vendor, villager, "no room");
        helper.assertFalse(VendingTrades.buyFromVillager(vendor, "test", 3).success(), "the trade itself refuses too");
        assertUnchanged(helper, before, ownerId, vendor, villager, "no room (direct)");
        villager.discard();
        helper.succeed();
    }

    /**
     * Blocks and goods suppliers don't sell to: toggle off, a sale, an item price, a server-owned block, admin
     * infinite stock, suppliers turned off, a villager who doesn't make the goods, named goods. Nothing changes; at
     * the end the same villager is invited to a proper buyback, so it was eligible all along.
     */
    public static void notServed(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = farmer(helper, new BlockPos(0, 1, 0), false);
        long price = minPrice(helper, villager);
        VendingSettings proper = buyback(price);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), proper);
        prepare(helper, villager, 100);
        setBalance(ownerId, price * 100);
        Snapshot before = Snapshot.of(helper, ownerId, vendor, villager);

        List<VendingSettings> refused = List.of(
            proper.withVillagers(false),
            proper.withBuyback(false),
            proper.withPriceMode(PriceMode.ITEM).withPriceItem(new ItemStack(Items.EMERALD)),
            proper.withPrice(0));
        for (VendingSettings settings : refused) {
            vendor.setSettings(settings);
            String what = "villagers " + settings.villagers() + ", buyback " + settings.buyback() + ", " + settings.priceMode() + ", price " + settings.price();
            helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NOT_SERVED, what);
            helper.assertFalse(VendingTrades.buyFromVillager(vendor, "test", 1).success(), what + ": the trade refuses too");
            helper.assertFalse(VillagerCustomers.isSupplier(villager.getUUID()), what + ": not on the way to sell");
            assertUnchanged(helper, before, ownerId, vendor, villager, what);
        }
        vendor.setSettings(proper.withVillagers(false));
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "toggle off: not invited");

        vendor.setSettings(proper);
        vendor.setOwner(AccountId.system(EconomyService.SERVER_ACCOUNT), "Server");
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NOT_SERVED, "server-owned block");
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "not invited to a server-owned block");
        vendor.setOwner(AccountId.player(ownerId), "owner");
        vendor.setAdmin(new AdminFlags(true, false, false));
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NOT_SERVED, "admin infinite stock");
        vendor.setAdmin(AdminFlags.NONE);
        assertUnchanged(helper, before, ownerId, vendor, villager, "server-owned, infinite stock");

        ServerConfig original = TraderyConfig.server();
        try {
            VillagesConfig.Suppliers s = original.villages().suppliers();
            TraderyConfig.setServerForTests(withSuppliers(original, new VillagesConfig.Suppliers(false, s.minPriceRatio(), s.maxLotsPerVisit(), s.stockDays())));
            helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NOT_SERVED, "suppliers turned off");
            helper.assertFalse(VendingTrades.buyFromVillager(vendor, "test", 1).success(), "suppliers off: the trade refuses too");
            helper.assertFalse(VillagerCustomers.invite(villager, vendor), "suppliers off: not invited");
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        assertUnchanged(helper, before, ownerId, vendor, villager, "suppliers off");

        Villager librarian = villager(helper, new BlockPos(4, 1, 0), false, VillagerProfession.LIBRARIAN, 5);
        helper.assertValueEqual(VillagerSupplies.visit(librarian, vendor).result(), VillagerSupplies.Result.NOT_PRODUCED, "librarians make no wheat");
        ItemStack named = new ItemStack(WHEAT, PER_TRADE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Golden harvest"));
        vendor.setSettings(new VendingSettings(named, PriceMode.CURRENCY, price, ItemStack.EMPTY, true, DisplayAnimation.STATIC));
        helper.assertValueEqual(VillagerSupplies.visit(villager, vendor).result(), VillagerSupplies.Result.NOT_PRODUCED, "villagers make plain stacks only");
        assertUnchanged(helper, before, ownerId, vendor, villager, "not produced");
        helper.assertTrue(NotificationsData.get(server(helper)).take(ownerId).isEmpty(), "the owner heard of nothing");

        vendor.setSettings(proper);
        helper.assertTrue(VillagerCustomers.invite(villager, vendor), "a proper buyback: invited (the villager was eligible all along)");
        VillagerCustomers.cancel(villager);
        villager.discard();
        librarian.discard();
        helper.succeed();
    }

    /** One day's produce at first, a day's more each day, up to stockDays days; more with the profession level. */
    public static void productionPilesUp(GameTestHelper helper) {
        Villager villager = villager(helper, new BlockPos(1, 1, 1), false, VillagerProfession.FARMER, 1);
        VillagerSupplies.Product wheat = VillagerSupplies.product(villager.getVillagerData().profession(), new ItemStack(WHEAT)).orElse(null);
        helper.assertTrue(wheat != null && wheat.key().equals(WHEAT_KEY), "farmers make wheat");
        int stockDays = TraderyConfig.server().villages().suppliers().stockDays();
        int daily = VillagerSupplies.dailyAmount(wheat, 1);
        helper.assertValueEqual(daily, wheat.perDay(), "a novice makes per_day");
        long today = today(helper);

        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today), daily, "first count: one day's produce");
        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today), daily, "the same day: nothing more");
        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today + 1), Math.min(2, stockDays) * daily, "a day later: a day's more");
        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today + 30), stockDays * daily, "piles up to stockDays days, no more");

        ledger(helper).setStock(villager.getUUID(), WHEAT_KEY, 3, today + 30);
        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today + 31), Math.min(stockDays * daily, 3 + daily), "sold some: refills a day at a time");

        villager.setVillagerData(villager.getVillagerData().withLevel(2));
        int apprentice = VillagerSupplies.dailyAmount(wheat, 2);
        helper.assertValueEqual(apprentice, (int) Math.round(wheat.perDay() * 1.25), "an apprentice makes 25% more");
        helper.assertValueEqual(VillagerSupplies.dailyAmount(wheat, 5), wheat.perDay() * 2, "a master makes twice as much");
        helper.assertValueEqual(VillagerSupplies.stock(villager, wheat, today + 60), stockDays * apprentice, "the cap grows with the level");

        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.LIBRARIAN));
        VillagerSupplies.Product paper = VillagerSupplies.product(villager.getVillagerData().profession(), new ItemStack(Items.PAPER)).orElseThrow();
        VillagerSupplies.stock(villager, paper, today + 61);
        helper.assertValueEqual(ledger(helper).stock(villager.getUUID(), WHEAT_KEY), 0, "a new profession: the wheat is gone");
        helper.assertTrue(ledger(helper).stock(villager.getUUID(), paper.key()) > 0, "and it makes paper now");

        ledger(helper).forget(villager.getUUID());
        helper.assertValueEqual(ledger(helper).stock(villager.getUUID(), paper.key()), 0, "a dead villager's produce goes with it");
        villager.discard();
        helper.succeed();
    }

    /** Every vanilla working profession supplies something, and everything supplied has a fair price (else nobody sells it). */
    public static void defaultSupplies(GameTestHelper helper) {
        List<ResourceKey<VillagerProfession>> working = List.of(VillagerProfession.ARMORER, VillagerProfession.BUTCHER,
            VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC, VillagerProfession.FARMER, VillagerProfession.FISHERMAN,
            VillagerProfession.FLETCHER, VillagerProfession.LEATHERWORKER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON,
            VillagerProfession.SHEPHERD, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH);
        for (ResourceKey<VillagerProfession> key : working) {
            Holder<VillagerProfession> profession = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(key);
            List<VillagerSupplies.Product> products = VillagerSupplies.products(profession);
            helper.assertFalse(products.isEmpty(), key.identifier() + " supplies something");
            for (VillagerSupplies.Product product : products) {
                ItemStack sample = sample(product);
                helper.assertFalse(sample.isEmpty(), product.key() + " has an item");
                helper.assertTrue(VillagerMarket.fairPrice(sample).isPresent(), product.key() + " has a fair price");
                helper.assertTrue(VillagerSupplies.anyoneProduces(sample), product.key() + " is found");
            }
        }
        helper.assertTrue(VillagerSupplies.products(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NITWIT)).isEmpty(),
            "nitwits make nothing");
        helper.assertTrue(VillagerSupplies.product(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.SHEPHERD),
            new ItemStack(Items.WOOL.pick(DyeColor.LIME))).isPresent(), "a shepherd sells any wool (tag)");
        helper.succeed();
    }

    private static ItemStack sample(VillagerSupplies.Product product) {
        if (product.item() != null) {
            return new ItemStack(product.item());
        }
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(product.tag())) {
            return new ItemStack(item.value());
        }
        return ItemStack.EMPTY;
    }
}
