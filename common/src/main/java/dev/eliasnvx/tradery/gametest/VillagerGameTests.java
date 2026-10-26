package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.NotificationsData;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.village.VillagerCustomers;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerMarket;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Living Market: villagers buying from players' vending blocks. Money and items are counted before and after; every
 * test has its own village (a bell at its own origin), so demand and the daily cap of one test never reach another.
 * Villagers of the one-tick tests are removed at the end, so the customer scan can't send them anywhere else.
 */
public final class VillagerGameTests {
    private static final BlockPos VENDOR = new BlockPos(2, 1, 2);
    private static final Item BREAD = Items.BREAD;
    private static final String BREAD_KEY = "minecraft:bread";
    /** Below the fair price (buys for sure), and in the falling-chance band above the gouging ratio. */
    private static final double CHEAP = 0.9;
    private static final double GOUGING = 1.3;
    private static final double TOO_DEAR = 1.6;

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("villager_walks_up_and_buys", VillagerGameTests::walksUpAndBuys),
        new TraderyGameTests.Entry("villager_wallet_limits_lots", VillagerGameTests::walletLimitsLots),
        new TraderyGameTests.Entry("villager_price_and_reputation", VillagerGameTests::priceAndReputation),
        new TraderyGameTests.Entry("villager_ignores_other_vendors", VillagerGameTests::ignoresOtherVendors),
        new TraderyGameTests.Entry("villager_demand_drops_and_recovers", VillagerGameTests::demandDropsAndRecovers),
        new TraderyGameTests.Entry("villager_daily_cap_per_village", VillagerGameTests::dailyCapPerVillage),
        new TraderyGameTests.Entry("villager_cooldown", VillagerGameTests::cooldown),
        new TraderyGameTests.Entry("villager_toggle_saved_and_synced", VillagerGameTests::toggleSavedAndSynced));

    private VillagerGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** A vending block facing north (customers stand at {@code VENDOR.north()}), stocked with {@code stock} goods. */
    private static VendingBlockEntity vendor(GameTestHelper helper, AccountId owner, VendingSettings settings, int stock) {
        floor(helper);
        helper.setBlock(VENDOR, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) helper.getBlockEntity(VENDOR);
        vendor.setOwner(owner, "owner");
        vendor.setSettings(settings);
        if (stock > 0) {
            vendor.stock().setItem(0, new ItemStack(settings.goods().getItem(), stock));
            vendor.stock().setChanged();
        }
        return vendor;
    }

    /** Fair price of one item from the default data pack, in minor units. */
    private static long fair(GameTestHelper helper, Item item) {
        BigDecimal fair = VillagerMarket.fairPrice(new ItemStack(item)).orElse(null);
        helper.assertTrue(fair != null, "the default data pack has a fair price for " + item);
        return EconomyService.INSTANCE.defaultCurrency().toMinor(fair);
    }

    /** Money sale of {@code perTrade} items at {@code ratio} times the fair price. */
    private static VendingSettings sellAt(GameTestHelper helper, Item goods, int perTrade, double ratio) {
        long price = Math.max(1, Math.round(fair(helper, goods) * perTrade * ratio));
        return new VendingSettings(new ItemStack(goods, perTrade), PriceMode.CURRENCY, price, ItemStack.EMPTY, false, DisplayAnimation.STATIC);
    }

    /**
     * An adult farmer with a bed, a job site and the bell of this test's own village, in a daytime activity.
     *
     * @param ai false: no AI (the one-tick tests call the purchase directly)
     */
    private static Villager customer(GameTestHelper helper, BlockPos at, boolean ai) {
        Villager villager = ai ? helper.spawn(EntityType.VILLAGER, at) : helper.spawnWithNoFreeWill(EntityType.VILLAGER, at);
        ServerLevel level = helper.getLevel();
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        ResourceKey<net.minecraft.world.level.Level> dimension = level.dimension();
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(0, 1, 4))));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(dimension, helper.absolutePos(new BlockPos(4, 1, 4))));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(dimension, helper.absolutePos(BlockPos.ZERO)));
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return villager;
    }

    private static VillagerLedger ledger(GameTestHelper helper) {
        return VillagerLedger.get(helper.getLevel().getServer());
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(helper.getLevel().getServer());
    }

    private static void setWallet(GameTestHelper helper, Villager villager, long amount) {
        ledger(helper).setWallet(villager.getUUID(), amount, today(helper));
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

    private static int itemsAround(GameTestHelper helper, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getEntities(EntityType.ITEM, VENDOR, 6)) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static int reputation(Villager villager, UUID owner) {
        return villager.getGossips().getReputation(owner, type -> true);
    }

    private static ServerConfig withVillagers(ServerConfig config, BigDecimal dailyCap) {
        ServerConfig.VillagersSection v = config.villagers();
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(), config.ore(), config.history(),
            config.log(), new ServerConfig.VillagersSection(v.enabled(), v.requireHomeAndJob(), v.visitRadius(), v.checkIntervalTicks(),
            v.visitChance(), v.browseTicks(), v.walkTimeoutTicks(), v.customerCooldownTicks(), v.maxLotsPerVisit(), v.wages(), v.walletDays(),
            dailyCap, v.maxPriceRatio(), v.gougingRatio(), v.demandDropPerLot(), v.demandRecoveryPerDay()), config.market(), config.villagerTrading(), config.villages());
    }

    // ------------------------------------------------------------------ tests

    /** The whole visit: invited, walks to the stall, browses, buys; the goods vanish and the money is minted to the owner. */
    public static void walksUpAndBuys(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sellAt(helper, BREAD, 2, CHEAP), 20);
        long price = vendor.settings().price();
        int lots = TraderyConfig.server().villagers().maxLotsPerVisit();
        long total = price * lots;
        Villager villager = customer(helper, new BlockPos(0, 1, 0), true);
        setWallet(helper, villager, total + 7);
        setBalance(ownerId, 0);

        helper.assertTrue(VillagerCustomers.isEligible(villager), "a farmer with a bed and a job, awake, in the day: eligible");
        helper.assertTrue(VillagerCustomers.invite(villager, vendor), "invited to the stall");
        helper.assertTrue(VillagerCustomers.isCustomer(villager.getUUID()), "on the way");
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "one visit at a time");

        helper.succeedWhen(() -> {
            helper.assertFalse(VillagerCustomers.isCustomer(villager.getUUID()), "the visit is over");
            GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 20 - 2 * lots, "the stock lost the lots bought");
            // The payer is the server account, so this is all the money made (the supply is checked in the one-tick tests:
            // tests running alongside move it too)
            GameTestAsserts.assertValueEqual(helper, balance(ownerId), total - fee(total), "the owner got the price minus the fee");
            GameTestAsserts.assertValueEqual(helper, wallet(helper, villager), 7L, "the villager paid from its wallet");
            GameTestAsserts.assertValueEqual(helper, villager.getInventory().countItem(BREAD), 0, "the bread vanished, not into the villager's inventory");
            GameTestAsserts.assertValueEqual(helper, itemsAround(helper, BREAD), 0, "no bread dropped");
            helper.assertTrue(ledger(helper).demand(VillagerPurchases.villageKey(villager), BREAD_KEY, today(helper), 0) < 1.0,
                "the village's demand dropped");
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(ledger(helper).onCooldown(villager.getUUID(), now), "cooldown after the visit");
            helper.assertFalse(VillagerCustomers.isEligible(villager), "not invited again during the cooldown");
            helper.assertTrue(reputation(villager, ownerId) > 0, "a fair price earns TRADING gossip");
            NotificationsData.Pending pending = NotificationsData.get(helper.getLevel().getServer()).take(ownerId).orElse(null);
            helper.assertTrue(pending != null && pending.villagerSales() == 1 && pending.sales() == 1, "the offline owner's summary counts it");
            GameTestAsserts.assertValueEqual(helper, pending.earned(), total - fee(total), "the summary has the money earned");
        });
    }

    public static void walletLimitsLots(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sellAt(helper, BREAD, 1, CHEAP), 10);
        long price = vendor.settings().price();
        Villager villager = customer(helper, new BlockPos(2, 1, 1), false);
        setWallet(helper, villager, 2 * price + price / 2);
        setBalance(ownerId, 0);
        long supplyBefore = supply();

        VillagerPurchases.Visit visit = VillagerPurchases.visit(villager, vendor, 0.0);
        helper.assertTrue(visit.bought(), "bought: " + visit.result());
        GameTestAsserts.assertValueEqual(helper, visit.lots(), 2, "the wallet pays for two lots only");
        GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 8, "two bread left the stock");
        GameTestAsserts.assertValueEqual(helper, wallet(helper, villager), price / 2, "the change stays in the wallet");
        GameTestAsserts.assertValueEqual(helper, balance(ownerId), 2 * price - fee(2 * price), "the owner got two lots minus the fee");
        GameTestAsserts.assertValueEqual(helper, supply() - supplyBefore, 2 * price - fee(2 * price), "minted exactly what the owner got");
        helper.assertTrue(TradePersistence.waitsForSave(helper.getLevel(), helper.absolutePos(VENDOR)),
            "the money is saved together with the chunk's stock");

        VillagerPurchases.Visit broke = VillagerPurchases.visit(villager, vendor, 0.0);
        GameTestAsserts.assertValueEqual(helper, broke.result(), VillagerPurchases.Result.NO_MONEY, "can't afford another lot");
        GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 8, "nothing taken when broke");
        GameTestAsserts.assertValueEqual(helper, balance(ownerId), 2 * price - fee(2 * price), "nothing paid when broke");
        villager.discard();
        helper.succeed();
    }

    public static void priceAndReputation(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        setBalance(ownerId, 0);

        // Above maxPriceRatio: never bought, and the owner's name suffers
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sellAt(helper, BREAD, 1, TOO_DEAR), 10);
        Villager first = customer(helper, new BlockPos(1, 1, 1), false);
        setWallet(helper, first, 100_000);
        long supplyBefore = supply();
        VillagerPurchases.Visit tooDear = VillagerPurchases.visit(first, vendor, 0.0);
        GameTestAsserts.assertValueEqual(helper, tooDear.result(), VillagerPurchases.Result.DECLINED, "above maxPriceRatio: declined even with the luckiest roll");
        GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 10, "nothing taken");
        GameTestAsserts.assertValueEqual(helper, supply(), supplyBefore, "no money made");
        helper.assertTrue(reputation(first, ownerId) < 0, "gouging: MINOR_NEGATIVE gossip about the owner");

        // Between the fair price and maxPriceRatio: a lucky roll buys, a high roll doesn't
        vendor.setSettings(sellAt(helper, BREAD, 1, GOUGING));
        Villager second = customer(helper, new BlockPos(2, 1, 1), false);
        setWallet(helper, second, 100_000);
        double chance = VillagerPurchases.priceChance(VillagerPurchases.priceRatio(vendor.settings()).orElseThrow(),
            TraderyConfig.server().villagers().maxPriceRatio());
        helper.assertTrue(chance > 0 && chance < 1, "the chance falls between the fair price and maxPriceRatio: " + chance);
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.visit(second, vendor, 0.999).result(), VillagerPurchases.Result.DECLINED, "an unlucky roll");
        helper.assertTrue(VillagerPurchases.visit(second, vendor, 0.0).bought(), "a lucky roll buys");
        helper.assertTrue(reputation(second, ownerId) < 0, "still gouging: no TRADING gossip, MINOR_NEGATIVE instead");

        // At a fair price: TRADING gossip, and that reputation raises the visit chance
        vendor.setSettings(sellAt(helper, BREAD, 1, CHEAP));
        Villager third = customer(helper, new BlockPos(3, 1, 1), false);
        setWallet(helper, third, 100_000);
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.reputationFactor(third, ownerId), 1.0, "no gossip yet: normal chance");
        helper.assertTrue(VillagerPurchases.visit(third, vendor, 0.0).bought(), "fair price: bought");
        helper.assertTrue(reputation(third, ownerId) > 0, "TRADING gossip about the owner");
        helper.assertTrue(VillagerPurchases.reputationFactor(third, ownerId) > 1.0, "good reputation: more visits");
        helper.assertTrue(VillagerPurchases.reputationFactor(first, ownerId) < 1.0, "bad reputation: fewer visits");
        first.discard();
        second.discard();
        third.discard();
        helper.succeed();
    }

    public static void ignoresOtherVendors(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        VendingSettings fair = sellAt(helper, BREAD, 1, CHEAP);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), fair, 10);
        Villager villager = customer(helper, new BlockPos(0, 1, 0), false);
        setWallet(helper, villager, 100_000);
        long supplyBefore = supply();

        List<VendingSettings> ignored = List.of(
            fair.withBuyback(true),
            fair.withVillagers(false),
            fair.withPriceMode(PriceMode.ITEM).withPriceItem(new ItemStack(Items.EMERALD)));
        for (VendingSettings settings : ignored) {
            vendor.setSettings(settings);
            GameTestAsserts.assertValueEqual(helper, VillagerPurchases.visit(villager, vendor, 0.0).result(), VillagerPurchases.Result.NOT_SERVED,
                "not served: buyback " + settings.buyback() + ", villagers " + settings.villagers() + ", " + settings.priceMode());
            helper.assertFalse(VillagerCustomers.invite(villager, vendor), "not invited either");
        }
        vendor.setSettings(fair);
        vendor.setOwner(AccountId.system(EconomyService.SERVER_ACCOUNT), "Server");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.visit(villager, vendor, 0.0).result(), VillagerPurchases.Result.NOT_SERVED,
            "server-owned vendors (village markets) don't serve villagers");
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "not invited to a server vendor");

        vendor.setOwner(AccountId.player(ownerId), "owner");
        vendor.setSettings(new VendingSettings(new ItemStack(Items.DIRT), PriceMode.CURRENCY, 10, ItemStack.EMPTY, false, DisplayAnimation.STATIC));
        vendor.stock().setItem(1, new ItemStack(Items.DIRT, 10));
        VillagerPurchases.Result unwanted = VillagerPurchases.visit(villager, vendor, 0.0).result();
        helper.assertTrue(unwanted == VillagerPurchases.Result.NOT_WANTED || unwanted == VillagerPurchases.Result.NO_FAIR_PRICE,
            "nobody wants dirt: " + unwanted);

        GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 10, "no bread taken");
        GameTestAsserts.assertValueEqual(helper, supply(), supplyBefore, "no money made");
        villager.discard();
        helper.succeed();
    }

    public static void demandDropsAndRecovers(GameTestHelper helper) {
        ServerConfig.VillagersSection config = TraderyConfig.server().villagers();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sellAt(helper, BREAD, 1, CHEAP), 20);
        Villager villager = customer(helper, new BlockPos(2, 1, 1), false);
        setWallet(helper, villager, 100_000);
        VillagerLedger ledger = ledger(helper);
        String village = VillagerPurchases.villageKey(villager);
        long today = today(helper);
        GameTestAsserts.assertValueEqual(helper, ledger.demand(village, BREAD_KEY, today, config.demandRecoveryPerDay()), 1.0, "full demand at first");

        VillagerPurchases.Visit visit = VillagerPurchases.visit(villager, vendor, 0.0);
        helper.assertTrue(visit.bought(), "bought: " + visit.result());
        double expected = 1.0 - visit.lots() * config.demandDropPerLot();
        helper.assertTrue(Math.abs(ledger.demand(village, BREAD_KEY, today, config.demandRecoveryPerDay()) - expected) < 1e-9,
            "each lot lowers the demand by demandDropPerLot");
        double tomorrow = Math.min(1.0, expected + config.demandRecoveryPerDay());
        helper.assertTrue(Math.abs(ledger.demand(village, BREAD_KEY, today + 1, config.demandRecoveryPerDay()) - tomorrow) < 1e-9,
            "a day later the demand recovered by demandRecoveryPerDay");

        // A saturated village buys nothing, however cheap
        ledger.setDemand(village, BREAD_KEY, 0.0, today);
        int stockBefore = stock(vendor, BREAD);
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.visit(villager, vendor, 0.0).result(), VillagerPurchases.Result.DECLINED, "no demand: declined");
        GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), stockBefore, "nothing taken without demand");
        villager.discard();
        helper.succeed();
    }

    public static void dailyCapPerVillage(GameTestHelper helper) {
        ServerConfig original = TraderyConfig.server();
        UUID ownerId = UUID.randomUUID();
        VendingBlockEntity vendor = vendor(helper, AccountId.player(ownerId), sellAt(helper, BREAD, 1, CHEAP), 20);
        long price = vendor.settings().price();
        Villager first = customer(helper, new BlockPos(1, 1, 1), false);
        Villager second = customer(helper, new BlockPos(3, 1, 1), false);
        setWallet(helper, first, 100_000);
        setWallet(helper, second, 100_000);
        setBalance(ownerId, 0);
        String village = VillagerPurchases.villageKey(first);
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.villageKey(second), village, "same bell, same village");
        try {
            // A cap for two lots a day
            BigDecimal cap = EconomyService.INSTANCE.defaultCurrency().toMajor(2 * price + price / 2);
            TraderyConfig.setServerForTests(withVillagers(original, cap));
            VillagerPurchases.Visit visit = VillagerPurchases.visit(first, vendor, 0.0);
            GameTestAsserts.assertValueEqual(helper, visit.lots(), 2, "the village's daily cap allows two lots: " + visit.result());
            VillagerPurchases.Visit capped = VillagerPurchases.visit(second, vendor, 0.0);
            GameTestAsserts.assertValueEqual(helper, capped.result(), VillagerPurchases.Result.DAILY_CAP, "another villager of the village: capped");
            GameTestAsserts.assertValueEqual(helper, stock(vendor, BREAD), 18, "only the first two lots left the stock");
            GameTestAsserts.assertValueEqual(helper, balance(ownerId), 2 * price - fee(2 * price), "only the first two lots were paid");
            VillagerLedger ledger = ledger(helper);
            GameTestAsserts.assertValueEqual(helper, ledger.spentToday(village, today(helper)), 2 * price, "spent today");
            GameTestAsserts.assertValueEqual(helper, ledger.spentToday(village, today(helper) + 1), 0L, "a new day, a new cap");
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        first.discard();
        second.discard();
        helper.succeed();
    }

    public static void cooldown(GameTestHelper helper) {
        VendingBlockEntity vendor = vendor(helper, AccountId.player(UUID.randomUUID()), sellAt(helper, BREAD, 1, CHEAP), 10);
        Villager villager = customer(helper, new BlockPos(0, 1, 0), false);
        helper.assertTrue(VillagerCustomers.isEligible(villager), "eligible");

        Villager homeless = customer(helper, new BlockPos(4, 1, 0), false);
        homeless.getBrain().eraseMemory(MemoryModuleType.HOME);
        helper.assertFalse(VillagerCustomers.isEligible(homeless), "requireHomeAndJob: no bed, no shopping");
        homeless.discard();

        helper.assertTrue(VillagerCustomers.invite(villager, vendor), "invited");
        VillagerCustomers.cancel(villager);
        helper.assertFalse(VillagerCustomers.isCustomer(villager.getUUID()), "visit ended");
        long now = helper.getLevel().getGameTime();
        int cooldown = TraderyConfig.server().villagers().customerCooldownTicks();
        helper.assertTrue(ledger(helper).onCooldown(villager.getUUID(), now), "a visit starts the cooldown");
        helper.assertFalse(VillagerCustomers.isEligible(villager), "on cooldown: not eligible");
        helper.assertFalse(VillagerCustomers.invite(villager, vendor), "on cooldown: not invited");
        helper.assertFalse(ledger(helper).onCooldown(villager.getUUID(), now + cooldown), "the cooldown ends after customerCooldownTicks");
        villager.discard();
        helper.succeed();
    }

    /** "Villagers can buy": defaults on, survives the save file and the network, and the owner menu toggles it on Save. */
    public static void toggleSavedAndSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VendingSettings off = sellAt(helper, BREAD, 4, CHEAP).withVillagers(false);
        helper.assertTrue(VendingSettings.EMPTY.villagers(), "new blocks: villagers may buy");

        var ops = net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        Tag saved = VendingSettings.CODEC.encodeStart(ops, off).getOrThrow(false, error -> { });
        helper.assertFalse(VendingSettings.CODEC.parse(ops, saved).getOrThrow(false, error -> { }).villagers(), "saved and loaded: off");
        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("villagers");
        helper.assertTrue(VendingSettings.CODEC.parse(ops, old).getOrThrow(false, error -> { }).villagers(), "blocks saved before 1.1: on");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            VendingSettings.STREAM_CODEC.encode(buf, off);
            helper.assertFalse(VendingSettings.STREAM_CODEC.decode(buf).villagers(), "sent to the client: off");
        } finally {
            buf.release();
        }

        @SuppressWarnings("removal")
        ServerPlayer owner = MockPlayers.create(helper);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(VENDOR.north()));
        owner.setPos(at.x, at.y, at.z);
        VendingBlockEntity vendor = vendor(helper, AccountId.player(owner.getUUID()), sellAt(helper, BREAD, 4, CHEAP), 0);
        VendingMenus.openOwner(owner, vendor, false);
        helper.assertTrue(owner.containerMenu instanceof VendingOwnerMenu, "owner menu open");
        VendingOwnerMenu menu = (VendingOwnerMenu) owner.containerMenu;
        helper.assertTrue(menu.villagers(), "the menu starts from the saved setting");
        helper.assertTrue(menu.clickMenuButton(owner, VendingOwnerMenu.TOGGLE_VILLAGERS), "toggle accepted");
        helper.assertFalse(menu.villagers(), "draft: off");
        helper.assertTrue(vendor.settings().villagers(), "not applied before Save");
        VendingConfigurator.save(owner, menu, vendor.settings().price());
        helper.assertFalse(vendor.settings().villagers(), "applied on Save");
        owner.closeContainer();
        helper.succeed();
    }
}
