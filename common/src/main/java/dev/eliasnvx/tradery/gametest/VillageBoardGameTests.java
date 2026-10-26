package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.market.MarketPools;
import dev.eliasnvx.tradery.menu.VillageBoardMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.TradePersistence;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageBoard;
import dev.eliasnvx.tradery.village.VillageBoardBlock;
import dev.eliasnvx.tradery.village.VillageCommerce;
import dev.eliasnvx.tradery.village.VillageEconomy;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillageOrders;
import dev.eliasnvx.tradery.village.VillageProjects;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillageLamps;
import dev.eliasnvx.tradery.village.VillageStreets;
import dev.eliasnvx.tradery.village.VillagerMarket;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.Villages;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The village board (SPEC "Доска деревни", "Заказы", "Вложения"): orders posted from the treasury and delivered
 * atomically, refunded when they expire, restoration orders; projects funded once with their actions (iron golem,
 * market stall) refused before charging; the board's window validating every click and bounding its data; the
 * board on generated stalls; the bell hint. Every test has its own bell inside its own space.
 */
public final class VillageBoardGameTests {
    private static final BlockPos BELL = new BlockPos(0, 1, 0);
    private static final Identifier STREET_LAMPS = Identifier.fromNamespaceAndPath("tradery", "street_lamps");
    private static final Identifier GRANARY = Identifier.fromNamespaceAndPath("tradery", "granary");
    private static final Identifier WORKSHOPS = Identifier.fromNamespaceAndPath("tradery", "workshops");
    private static final Identifier WATCH_POST = Identifier.fromNamespaceAndPath("tradery", "watch_post");
    private static final Identifier MARKET_STALL = Identifier.fromNamespaceAndPath("tradery", "market_stall");
    private static final Identifier PAVED_STREETS = Identifier.fromNamespaceAndPath("tradery", "paved_streets");

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("board_orders_posted_from_treasury", VillageBoardGameTests::ordersPostedFromTreasury),
        new TraderyGameTests.Entry("board_delivery_partial_then_full", VillageBoardGameTests::deliveryPartialThenFull),
        new TraderyGameTests.Entry("board_delivery_fails_cleanly", VillageBoardGameTests::deliveryFailsCleanly),
        new TraderyGameTests.Entry("board_orders_expire_refunded", VillageBoardGameTests::ordersExpireRefunded),
        new TraderyGameTests.Entry("board_restoration_orders", VillageBoardGameTests::restorationOrders),
        new TraderyGameTests.Entry("board_project_funding", VillageBoardGameTests::projectFunding),
        new TraderyGameTests.Entry("board_project_iron_golem", VillageBoardGameTests::projectIronGolem),
        new TraderyGameTests.Entry("board_project_market_stall", VillageBoardGameTests::projectMarketStall),
        new TraderyGameTests.Entry("board_menu_validates_clicks", VillageBoardGameTests::menuValidatesClicks),
        new TraderyGameTests.Entry("board_data_bounded", VillageBoardGameTests::dataBounded),
        new TraderyGameTests.Entry("board_on_market_stalls", VillageBoardGameTests::boardOnMarketStalls),
        new TraderyGameTests.Entry("board_bell_info", VillageBoardGameTests::bellInfo),
        new TraderyGameTests.Entry("board_donation", VillageBoardGameTests::donation),
        new TraderyGameTests.Entry("board_treasury_history", VillageBoardGameTests::treasuryHistory),
        new TraderyGameTests.Entry("board_project_street_lamps", VillageBoardGameTests::projectStreetLamps),
        new TraderyGameTests.Entry("board_project_granary", VillageBoardGameTests::projectGranary),
        new TraderyGameTests.Entry("board_project_paved_streets", VillageBoardGameTests::projectPavedStreets),
        new TraderyGameTests.Entry("streets_connect_building", VillageBoardGameTests::streetsConnectBuilding));

    private VillageBoardGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static VillageState village(GameTestHelper helper, BlockPos bell) {
        return Villages.at(server(helper), helper.getLevel().dimension(), helper.absolutePos(bell));
    }

    private static void floor(GameTestHelper helper) {
        // Three solid blocks deep: buildings and lamp posts want real ground, not a floor over the void
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = -2; y <= 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** An adult farmer of the village of {@code bell}. */
    private static Villager farmer(GameTestHelper helper, BlockPos at, BlockPos bell) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, at);
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.FARMER)
            .withLevel(2));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bell)));
        return villager;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
        player.setPos(pos.x, pos.y, pos.z);
        player.getInventory().clearContent();
        return player;
    }

    private static LedgerAccount account(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID());
    }

    private static long balance(ServerPlayer player) {
        return account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(ServerPlayer player, long amount) {
        EconomyService.INSTANCE.setBalance(account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static int decimals() {
        return EconomyService.INSTANCE.defaultCurrency().decimals();
    }

    private static long minor(double major) {
        return Math.round(major * Math.pow(10, decimals()));
    }

    private static double points(long amount, double perUnit) {
        return amount / Math.pow(10, decimals()) * perUnit;
    }

    private static VillagesConfig config() {
        return TraderyConfig.server().villages();
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(server(helper));
    }

    private static int count(ServerPlayer player, Item item, boolean plain) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item) && stack.getComponentsPatch().isEmpty() == plain) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack named(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom"));
        return stack;
    }

    /** Reward per item the way orders work it out. */
    private static long expectedReward(VillageState village, Item item, double markup) {
        ItemStack stack = new ItemStack(item);
        BigDecimal fair = VillagerMarket.fairPrice(stack).orElseThrow();
        BigDecimal major = fair.multiply(BigDecimal.valueOf(markup)).multiply(BigDecimal.valueOf(VillageCommerce.priceFactor(village, stack)));
        return EconomyService.INSTANCE.defaultCurrency().toMinor(major);
    }

    private static Optional<VillageState.Order> order(VillageState village, int id) {
        return village.orders().stream().filter(o -> o.id() == id).findFirst();
    }

    // ------------------------------------------------------------------ orders

    /** A new day: as many orders as the level gives, wanted goods, 8..64 items, every reward reserved from the treasury. */
    public static void ordersPostedFromTreasury(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        floor(helper);
        Villager a = farmer(helper, new BlockPos(1, 1, 1), BELL);
        Villager b = farmer(helper, new BlockPos(2, 1, 1), BELL);
        Villages.census(helper.getLevel());
        VillageState village = village(helper, BELL);
        long start = minor(1000);
        Villages.set(server, village, 0, start);

        int posted = VillageOrders.postNow(server, village, 42);
        int expected = config().levels().ordersPerDay().getFirst();
        helper.assertValueEqual(posted, expected, "orders of a hamlet");
        helper.assertValueEqual(village.orders().size(), expected, "on the board");
        Holder<VillagerProfession> farmer = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FARMER);
        long reserved = 0;
        for (VillageState.Order order : village.orders()) {
            Item item = BuiltInRegistries.ITEM.getValue(order.item());
            ItemStack stack = new ItemStack(item);
            boolean dear = VillageCommerce.priceShifts(village).stream().anyMatch(s -> s.factor() > 1 && s.icon().is(item));
            helper.assertTrue(VillagerMarket.weight(farmer, stack) > 0 || dear, order.item() + " is wanted here");
            helper.assertTrue(order.count() >= VillageOrders.MIN_AMOUNT && order.count() <= VillageOrders.MAX_AMOUNT, "8..64 items: " + order.count());
            helper.assertFalse(order.restoration(), "no restoration without a raid");
            helper.assertValueEqual(order.expiresDay(), today(helper) + config().orders().lifetimeDays(), "lives orders.lifetimeDays");
            helper.assertValueEqual(order.rewardPerItem(), expectedReward(village, item, config().orders().rewardMarkup()),
                order.item() + ": fair price x markup x the village's factor");
            reserved += order.count() * order.rewardPerItem();
        }
        helper.assertValueEqual(village.treasury(), start - reserved, "rewards reserved from the treasury");
        helper.assertValueEqual(village.events().ordersDay(), today(helper), "the day is marked");

        VillageOrders.refresh(server, village);
        helper.assertValueEqual(village.orders().size(), expected, "the same day posts nothing more");
        helper.assertValueEqual(VillageOrders.postNow(server, village, 7), 0, "a full board isn't topped up");

        // An empty treasury posts nothing
        VillageState poor = village(helper, new BlockPos(4, 1, 4));
        Villages.set(server, poor, 0, 0);
        helper.assertValueEqual(VillageOrders.postNow(server, poor, 42), 0, "nothing without money");
        a.discard();
        b.discard();
        helper.succeed();
    }

    /** Plain stacks only, partly then fully; the reward is minted to the balance; prosperity, gossip and a save follow. */
    public static void deliveryPartialThenFull(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        floor(helper);
        Villager villager = farmer(helper, new BlockPos(3, 1, 3), BELL);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(100));
        long reward = minor(0.5);
        VillageState.Order order = VillageOrders.postForTests(server, village, Items.BREAD, 16, reward, today(helper) + 2, false);
        helper.assertTrue(order != null, "posted");
        long treasury = village.treasury();
        helper.assertValueEqual(treasury, minor(100) - 16 * reward, "reserved");

        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        setBalance(player, 0);
        player.getInventory().add(new ItemStack(Items.BREAD, 10));
        player.getInventory().add(named(Items.BREAD, 5));

        VillageBoard.Outcome first = VillageOrders.deliver(player, village, order.id());
        helper.assertTrue(first.success(), "delivered: " + first.message().getString());
        helper.assertValueEqual(balance(player), 10 * reward, "paid for ten");
        helper.assertValueEqual(count(player, Items.BREAD, true), 0, "plain bread taken");
        helper.assertValueEqual(count(player, Items.BREAD, false), 5, "named bread kept");
        helper.assertValueEqual(order(village, order.id()).map(VillageState.Order::delivered).orElse(-1), 10, "ten delivered");
        helper.assertValueEqual(village.treasury(), treasury, "the reward was reserved already");
        helper.assertTrue(Math.abs(village.prosperity() - points(10 * reward, 1.0)) < 1e-6, "prosperity: " + village.prosperity());
        helper.assertTrue(villager.getGossips().getReputation(player.getUUID(), type -> type == GossipType.TRADING) > 0, "villagers heard of it");
        helper.assertTrue(TradePersistence.villagerTradePending(player), "the player is saved with the money");

        player.getInventory().add(new ItemStack(Items.BREAD, 10));
        VillageBoard.Outcome second = VillageOrders.deliver(player, village, order.id());
        helper.assertTrue(second.success(), "delivered the rest: " + second.message().getString());
        helper.assertValueEqual(balance(player), 16 * reward, "paid for all sixteen");
        helper.assertValueEqual(count(player, Items.BREAD, true), 4, "only what was needed taken");
        helper.assertTrue(order(village, order.id()).isEmpty(), "a done order leaves the board");
        helper.assertValueEqual(village.treasury(), treasury, "nothing refunded for a done order");
        villager.discard();
        helper.succeed();
    }

    /** No items, only special items, a refused transaction, an expired order: nothing changes. */
    public static void deliveryFailsCleanly(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(100));
        long reward = minor(0.5);
        VillageState.Order order = VillageOrders.postForTests(server, village, Items.BREAD, 8, reward, today(helper) + 2, false);
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        setBalance(player, 0);
        long treasury = village.treasury();

        helper.assertFalse(VillageOrders.deliver(player, village, order.id()).success(), "nothing to deliver");
        player.getInventory().add(named(Items.BREAD, 8));
        helper.assertFalse(VillageOrders.deliver(player, village, order.id()).success(), "only named bread");
        helper.assertValueEqual(count(player, Items.BREAD, false), 8, "named bread kept");

        player.getInventory().add(new ItemStack(Items.BREAD, 8));
        EconomyService.INSTANCE.setLocked(account(player), true);
        try {
            helper.assertFalse(VillageOrders.deliver(player, village, order.id()).success(), "locked account: refused");
        } finally {
            EconomyService.INSTANCE.setLocked(account(player), false);
        }
        helper.assertValueEqual(count(player, Items.BREAD, true), 8, "items untouched");
        helper.assertValueEqual(balance(player), 0L, "no money");
        helper.assertValueEqual(order(village, order.id()).map(VillageState.Order::delivered).orElse(-1), 0, "order untouched");
        helper.assertValueEqual(village.treasury(), treasury, "treasury untouched");

        helper.assertFalse(VillageOrders.deliver(player, village, 9999).success(), "unknown order");
        VillageState.Order old = VillageOrders.postForTests(server, village, Items.APPLE, 8, reward, today(helper), false);
        player.getInventory().add(new ItemStack(Items.APPLE, 8));
        long before = village.treasury();
        helper.assertFalse(VillageOrders.deliver(player, village, old.id()).success(), "expired order");
        helper.assertValueEqual(count(player, Items.APPLE, true), 8, "apples kept");
        helper.assertValueEqual(village.treasury(), before + 8 * reward, "its reward went back");
        helper.succeed();
    }

    /** Orders past their day leave the board on the next look; what wasn't delivered goes back to the treasury. */
    public static void ordersExpireRefunded(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper, BELL);
        long start = minor(100);
        Villages.set(server, village, 0, start);
        long reward = minor(0.25);
        VillageState.Order expired = VillageOrders.postForTests(server, village, Items.BREAD, 20, reward, today(helper), false);
        VillageState.Order alive = VillageOrders.postForTests(server, village, Items.APPLE, 10, reward, today(helper) + 1, false);
        helper.assertTrue(expired != null && alive != null, "posted");
        // Nothing is posted today: the board counts as filled
        VillageState.Events e = village.events();
        Villages.setEvents(server, village, new VillageState.Events(today(helper), e.nextOrderId(), e.nextMarketDay(), e.caravanDay(),
            e.restorationUntil(), e.raidActive()));

        VillageOrders.refresh(server, village);
        helper.assertTrue(order(village, expired.id()).isEmpty(), "expired order gone");
        helper.assertTrue(order(village, alive.id()).isPresent(), "live order stays");
        helper.assertValueEqual(village.treasury(), start - 10 * reward, "the expired reward is back, the live one still reserved");
        helper.succeed();
    }

    /** After a raid: planks of the village's wood, glass, cobblestone and torches, at the restoration markup. */
    public static void restorationOrders(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(1000));
        Villages.startRestoration(server, village, 3);
        VillageOrders.postNow(server, village, 1);
        double markup = config().orders().rewardMarkup() * config().threats().restorationMarkup();
        List<Item> expected = List.of(Items.OAK_PLANKS, Items.GLASS, Items.COBBLESTONE, Items.TORCH);
        for (Item item : expected) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            Optional<VillageState.Order> found = village.orders().stream().filter(o -> o.restoration() && o.item().equals(id)).findFirst();
            helper.assertTrue(found.isPresent(), "restoration order for " + id);
            helper.assertValueEqual(found.get().rewardPerItem(), expectedReward(village, item, markup), id + ": restoration reward");
        }
        long regular = village.orders().stream().filter(o -> !o.restoration()).count();
        helper.assertTrue(regular <= config().levels().ordersPerDay().getFirst(), "regular orders on top, as many as the level gives");
        helper.succeed();
    }

    // ------------------------------------------------------------------ projects

    /** Paid and burned once per village, from the right level; refused cleanly otherwise. */
    /** The test space is roofed with barriers at a height that differs between the loaders: open it to the sky. */
    private static void openRoof(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 2; y < 32; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.BARRIER)) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }
    }

    public static void projectFunding(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        floor(helper);
        openRoof(helper, 5);
        Villager villager = farmer(helper, new BlockPos(3, 1, 3), BELL);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, 0);
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        long start = minor(1000);
        setBalance(player, start);
        long cost = EconomyService.INSTANCE.defaultCurrency().toMinor(VillageProjects.get(STREET_LAMPS).orElseThrow().cost());

        BoundingBox space = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 4, 4)));
        VillageBoard.Outcome funded = VillageFunding.fund(player, village, STREET_LAMPS, 4, space);
        helper.assertTrue(funded.success(), "funded: " + funded.message().getString());
        helper.assertValueEqual(balance(player), start - cost, "paid");
        helper.assertTrue(village.projects().contains(STREET_LAMPS.toString()), "the village has it");
        helper.assertTrue(Math.abs(village.prosperity() - points(cost, 0.5)) < 1e-6, "prosperity: cost x 0.5");
        helper.assertTrue(Math.abs(VillageProjects.effects(village).visitChance() - 0.1) < 1e-9, "its effect counts");
        helper.assertTrue(villager.getGossips().getReputation(player.getUUID(), type -> type == GossipType.MINOR_POSITIVE) > 0,
            "the villagers are grateful");

        long after = balance(player);
        helper.assertFalse(VillageFunding.fund(player, village, STREET_LAMPS).success(), "once per village");
        helper.assertFalse(VillageFunding.fund(player, village, WORKSHOPS).success(), "workshops need a bigger village");
        helper.assertFalse(VillageFunding.fund(player, village, Identifier.fromNamespaceAndPath("tradery", "no_such_project")).success(),
            "unknown project");
        helper.assertValueEqual(balance(player), after, "refusals cost nothing");

        setBalance(player, 10);
        helper.assertFalse(VillageFunding.fund(player, village, GRANARY).success(), "not enough money");
        setBalance(player, start);
        EconomyService.INSTANCE.setLocked(account(player), true);
        try {
            helper.assertFalse(VillageFunding.fund(player, village, GRANARY).success(), "locked account");
        } finally {
            EconomyService.INSTANCE.setLocked(account(player), false);
        }
        helper.assertValueEqual(balance(player), start, "nothing charged");
        helper.assertFalse(village.projects().contains(GRANARY.toString()), "nothing built");
        villager.discard();
        helper.succeed();
    }

    /** The watch post brings a village iron golem to the bell. */
    public static void projectIronGolem(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper, 5);
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        Villages.set(server, village, config().levels().thresholds().get(1), 0);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 4));
        setBalance(player, minor(1000));
        AABB around = new AABB(helper.absolutePos(bell)).inflate(12);
        int golemsBefore = level.getEntitiesOfClass(IronGolem.class, around).size();
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 6, 4)));

        VillageBoard.Outcome funded = VillageFunding.fund(player, village, WATCH_POST, 4, area);
        helper.assertTrue(funded.success(), "funded: " + funded.message().getString());
        int pumpkins = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.CARVED_PUMPKIN)) {
                pumpkins++;
            }
        }
        helper.assertValueEqual(pumpkins, 1, "the watch post stands by the bell");
        List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, around);
        helper.assertValueEqual(golems.size(), golemsBefore + 1, "a golem by its post");
        IronGolem golem = golems.getLast();
        helper.assertFalse(golem.isPlayerCreated(), "the village's golem, not a player's");
        helper.assertTrue(VillageProjects.effects(village).raidLoss() < 1, "raids hurt less");
        golems.forEach(IronGolem::discard);
        helper.setBlock(bell, Blocks.AIR);
        helper.succeed();
    }

    /** A new market stall on flat free ground by the bell; without room, refused before charging. */
    public static void projectMarketStall(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        Villages.set(server, village, config().levels().thresholds().get(1), 0);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 4));
        long start = minor(1000);
        setBalance(player, start);
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 4, 4)));
        // Open to the sky like a village: the stall looks for ground by the heightmap
        openRoof(helper, 5);

        // A block in the middle: no flat free spot
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.COBBLESTONE);
        VillageBoard.Outcome refused = VillageFunding.fund(player, village, MARKET_STALL, 4, area);
        helper.assertFalse(refused.success(), "no room");
        helper.assertValueEqual(balance(player), start, "nothing charged");
        helper.assertFalse(village.projects().contains(MARKET_STALL.toString()), "nothing built");

        helper.setBlock(new BlockPos(2, 1, 1), Blocks.SHORT_GRASS);
        VillageBoard.Outcome built = VillageFunding.fund(player, village, MARKET_STALL, 4, area);
        helper.assertTrue(built.success(), "built: " + built.message().getString());
        long cost = EconomyService.INSTANCE.defaultCurrency().toMinor(VillageProjects.get(MARKET_STALL).orElseThrow().cost());
        helper.assertValueEqual(balance(player), start - cost, "paid");
        int vendors = 0;
        int boards = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
                vendors++;
                helper.assertTrue(vendor.settings().isConfigured(), "the stall's vendor has an offer");
            }
            if (level.getBlockState(pos).is(TraderyBlocks.VILLAGE_BOARD.get())) {
                boards++;
            }
            helper.assertFalse(level.getBlockState(pos).is(Blocks.JIGSAW), "no jigsaw left");
        }
        helper.assertValueEqual(vendors, 3, "three vendors");
        helper.assertValueEqual(boards, 1, "with a board");
        helper.assertTrue(level.getBlockState(helper.absolutePos(bell)).is(Blocks.BELL), "the bell stays");
        helper.assertTrue(TradePersistence.waitsForSave(level, helper.absolutePos(BlockPos.ZERO))
            || TradePersistence.waitsForSave(level, helper.absolutePos(new BlockPos(4, 0, 3))), "the stall is saved with the money");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the window

    /** Clicks name only what the window showed; out of range, out of reach or too fast does nothing. */
    /**
     * Street lights: by a dark street, a post (a wall base, a fence pole) with an arm over the street and a lantern
     * hanging from it, three free blocks under the lantern; the street itself stays clear; nothing where there's no
     * ground; a street already lit gets none.
     */
    public static void projectStreetLamps(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper, 5);
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        Villages.set(server, village, 0, 0);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 4));
        long start = minor(1000);
        setBalance(player, start);
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, -2, 0)), helper.absolutePos(new BlockPos(4, 8, 4)));

        // No ground to stand on: everything but the street and the bell's own block is water
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.DIRT_PATH);
            for (int z = 1; z < 5; z++) {
                if (x != bell.getX() || z != bell.getZ()) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
                }
            }
        }
        VillageBoard.Outcome refused = VillageFunding.fund(player, village, STREET_LAMPS, 4, area);
        helper.assertFalse(refused.success(), "no spot for lamps");
        helper.assertValueEqual(balance(player), start, "nothing charged");
        helper.assertFalse(village.projects().contains(STREET_LAMPS.toString()), "nothing built");

        floor(helper);
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.DIRT_PATH);
        }
        VillageBoard.Outcome built = VillageFunding.fund(player, village, STREET_LAMPS, 4, area);
        helper.assertTrue(built.success(), "built: " + built.message().getString());
        long cost = EconomyService.INSTANCE.defaultCurrency().toMinor(VillageProjects.get(STREET_LAMPS).orElseThrow().cost());
        helper.assertValueEqual(balance(player), start - cost, "paid");

        List<BlockPos> lanterns = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.LANTERN)) {
                lanterns.add(pos.immutable());
            }
        }
        helper.assertValueEqual(lanterns.size(), 1, "one light: lamps stand " + VillageLamps.SPACING + " apart");
        BlockPos lantern = lanterns.getFirst();
        helper.assertTrue(level.getBlockState(lantern).getValue(LanternBlock.HANGING), "the lantern hangs");
        helper.assertTrue(level.getBlockState(lantern.above()).is(Blocks.OAK_FENCE), "from the arm");
        helper.assertValueEqual(lantern.getZ(), helper.absolutePos(new BlockPos(0, 0, 0)).getZ(), "over the street");
        BlockPos street = new BlockPos(lantern.getX(), helper.absolutePos(BlockPos.ZERO).getY(), lantern.getZ());
        for (int dy = 1; dy <= 3; dy++) {
            helper.assertTrue(level.getBlockState(street.above(dy)).isAir(), "headroom over the street at +" + dy);
        }
        BlockPos post = street.relative(net.minecraft.core.Direction.SOUTH);
        helper.assertTrue(level.getBlockState(post.above()).is(Blocks.COBBLESTONE_WALL), "a wall for a base");
        for (int dy = 2; dy <= VillageLamps.HEIGHT; dy++) {
            helper.assertTrue(level.getBlockState(post.above(dy)).is(Blocks.OAK_FENCE), "the pole at +" + dy);
        }
        helper.assertTrue(level.getBlockState(post.above(VillageLamps.HEIGHT)).getValue(net.minecraft.world.level.block.FenceBlock.NORTH),
            "the pole joins the arm");
        helper.assertTrue(level.getBlockState(helper.absolutePos(bell)).is(Blocks.BELL), "the bell stays");

        // Lit now (once the light engine has caught up): planning again finds nothing to light
        helper.succeedWhen(() -> helper.assertTrue(VillageLamps.plan(level, village, 4, area, false).isEmpty(), "a lit street needs no lamp"));
    }

    /** The granary: a building from the village type's template on a flat free spot, like the market stall. */
    public static void projectGranary(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper, 5);
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        Villages.set(server, village, 0, 0);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 4));
        long start = minor(1000);
        setBalance(player, start);
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 5, 4)));

        helper.setBlock(new BlockPos(2, 1, 1), Blocks.COBBLESTONE);
        VillageBoard.Outcome refused = VillageFunding.fund(player, village, GRANARY, 4, area);
        helper.assertFalse(refused.success(), "no room");
        helper.assertValueEqual(balance(player), start, "nothing charged");

        helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR);
        VillageBoard.Outcome built = VillageFunding.fund(player, village, GRANARY, 4, area);
        helper.assertTrue(built.success(), "built: " + built.message().getString());
        long cost = EconomyService.INSTANCE.defaultCurrency().toMinor(VillageProjects.get(GRANARY).orElseThrow().cost());
        helper.assertValueEqual(balance(player), start - cost, "paid");
        helper.assertTrue(village.projects().contains(GRANARY.toString()), "the village has it");
        int hay = 0;
        int composters = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.HAY_BLOCK)) {
                hay++;
            }
            if (level.getBlockState(pos).is(Blocks.COMPOSTER)) {
                composters++;
            }
        }
        helper.assertValueEqual(hay, 5, "the granary's hay");
        helper.assertValueEqual(composters, 1, "and its composter");
        helper.assertTrue(level.getBlockState(helper.absolutePos(bell)).is(Blocks.BELL), "the bell stays");
        helper.assertTrue(TradePersistence.waitsForSave(level, helper.absolutePos(BlockPos.ZERO))
            || TradePersistence.waitsForSave(level, helper.absolutePos(new BlockPos(4, 0, 4))), "the building is saved with the money");
        helper.succeed();
    }

    /** Paved streets: every dirt path near the bell becomes stone, a one-block dip is filled; no streets, nothing charged. */
    public static void projectPavedStreets(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper, 5);
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        Villages.set(server, village, config().levels().thresholds().get(1), 0);
        ServerPlayer player = player(helper, new BlockPos(0, 1, 4));
        long start = minor(1000);
        setBalance(player, start);
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, -2, 0)), helper.absolutePos(new BlockPos(4, 4, 4)));

        VillageBoard.Outcome refused = VillageFunding.fund(player, village, PAVED_STREETS, 4, area);
        helper.assertFalse(refused.success(), "no streets to pave");
        helper.assertValueEqual(balance(player), start, "nothing charged");

        // A street along the north edge: a grass gap at x 1 (vanilla's street processor leaves those), a dip at x 3
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.DIRT_PATH);
        }
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 0, 0), Blocks.AIR);
        helper.setBlock(new BlockPos(3, -1, 0), Blocks.DIRT_PATH);
        VillageBoard.Outcome paved = VillageFunding.fund(player, village, PAVED_STREETS, 4, area);
        helper.assertTrue(paved.success(), "paved: " + paved.message().getString());
        long cost = EconomyService.INSTANCE.defaultCurrency().toMinor(VillageProjects.get(PAVED_STREETS).orElseThrow().cost());
        helper.assertValueEqual(balance(player), start - cost, "paid");
        helper.assertTrue(village.projects().contains(PAVED_STREETS.toString()), "the village has it");
        for (int x = 0; x < 5; x++) {
            BlockState state = level.getBlockState(helper.absolutePos(new BlockPos(x, 0, 0)));
            helper.assertTrue(state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS), "paved at x=" + x + ": " + state);
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 0))).isAir(), "the filled dip is level with the street");
        // Paving again changes nothing: it's done
        helper.assertValueEqual(VillageStreets.pave(level, village, 4, area), 0, "nothing left to pave");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(1, 0, 1))).is(Blocks.STONE), "the floor beside the street stays");
        helper.succeed();
    }

    /** A new building gets a path to the nearest street: one bend over natural ground, dirt before paving, stone after. */
    public static void streetsConnectBuilding(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        openRoof(helper, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        VillageState village = village(helper, BELL);
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(4, 0, z), Blocks.DIRT_PATH);
        }
        // A building's box at the west edge: its floor layer is y 0, like a placed template's
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 1)), helper.absolutePos(new BlockPos(1, 3, 3)));
        int laid = VillageStreets.connect(level, village, box);
        helper.assertValueEqual(laid, 2, "two blocks of path between the building and the street");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(2, 0, 1))).is(Blocks.DIRT_PATH)
            || level.getBlockState(helper.absolutePos(new BlockPos(2, 0, 2))).is(Blocks.DIRT_PATH)
            || level.getBlockState(helper.absolutePos(new BlockPos(2, 0, 3))).is(Blocks.DIRT_PATH), "the path starts at the building's edge");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(0, 0, 2))).is(Blocks.GRASS_BLOCK), "nothing inside the building");

        // No street in reach: nothing laid
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(4, 0, z), Blocks.GRASS_BLOCK);
        }
        for (int x = 2; x < 4; x++) {
            for (int z = 1; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        helper.assertValueEqual(VillageStreets.connect(level, village, box), 0, "no street near");
        helper.succeed();
    }

    public static void menuValidatesClicks(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        BlockPos board = new BlockPos(1, 1, 1);
        helper.setBlock(board, TraderyBlocks.VILLAGE_BOARD.get());
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(100));
        long reward = minor(0.5);
        VillageOrders.postForTests(server, village, Items.BREAD, 8, reward, today(helper) + 2, false);
        ServerPlayer player = player(helper, new BlockPos(2, 1, 1));
        setBalance(player, 0);
        VillageBoardMenu menu = new VillageBoardMenu(1, player.getInventory(), VillageBoard.snapshot(player, village, helper.absolutePos(board)),
            level, village.key());

        helper.assertFalse(menu.clickMenuButton(player, -1), "negative id");
        helper.assertFalse(menu.clickMenuButton(player, VillageBoardMenu.buttonId(VillageBoardMenu.DELIVER, 1)), "no second order shown");
        helper.assertFalse(menu.clickMenuButton(player, VillageBoardMenu.buttonId(VillageBoardMenu.FUND, VillageBoardMenu.MAX_PROJECTS)),
            "project index out of range");
        helper.assertFalse(menu.clickMenuButton(player, VillageBoardMenu.buttonId(VillageBoardMenu.RENT, 1)), "rent takes index 0");
        helper.assertFalse(menu.clickMenuButton(player, VillageBoardMenu.buttonId(9, 0)), "unknown action");

        player.getInventory().add(new ItemStack(Items.BREAD, 8));
        Vec3 far = Vec3.atCenterOf(helper.absolutePos(board)).add(20, 0, 0);
        player.setPos(far.x, far.y, far.z);
        helper.assertFalse(menu.stillValid(player), "out of reach");
        helper.assertFalse(menu.clickMenuButton(player, VillageBoardMenu.buttonId(VillageBoardMenu.DELIVER, 0)), "no clicks from afar");
        helper.assertValueEqual(count(player, Items.BREAD, true), 8, "nothing delivered from afar");

        Vec3 near = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 1)));
        player.setPos(near.x, near.y, near.z);
        helper.assertTrue(menu.clickMenuButton(player, VillageBoardMenu.buttonId(VillageBoardMenu.DELIVER, 0)), "delivered through the window");
        helper.assertValueEqual(count(player, Items.BREAD, true), 0, "bread handed over");
        helper.assertValueEqual(balance(player), 8 * reward, "paid");
        helper.assertTrue(menu.data().orders().isEmpty(), "the window got fresh data");

        // Too fast: the trade rate limit (10 a second) drops the rest
        VillageOrders.postForTests(server, village, Items.APPLE, 8, reward, today(helper) + 2, false);
        ServerPlayer fast = player(helper, new BlockPos(2, 1, 1));
        VillageBoardMenu fastMenu = new VillageBoardMenu(2, fast.getInventory(), VillageBoard.snapshot(fast, village, helper.absolutePos(board)),
            level, village.key());
        for (int i = 0; i < 10; i++) {
            fastMenu.clickMenuButton(fast, VillageBoardMenu.buttonId(VillageBoardMenu.DELIVER, 0));
        }
        fast.getInventory().add(new ItemStack(Items.APPLE, 8));
        fastMenu.clickMenuButton(fast, VillageBoardMenu.buttonId(VillageBoardMenu.DELIVER, 0));
        helper.assertValueEqual(count(fast, Items.APPLE, true), 8, "the eleventh click in a second is dropped");
        VendingTrades.forget(fast);
        VendingTrades.forget(player);

        helper.setBlock(board, Blocks.AIR);
        helper.assertFalse(menu.stillValid(player), "the board is gone");
        helper.succeed();
    }

    /** The window's data is cut to what its codec carries and survives the trip. */
    /**
     * A donation from the board: the money leaves the player and lands in the treasury (above its cap), with
     * prosperity and gratitude as for a project; bad amounts, an empty balance and a locked account move nothing;
     * through the window it needs an open, valid menu and keeps to the trade rate limit.
     */
    public static void donation(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        Villager villager = farmer(helper, new BlockPos(3, 1, 3), BELL);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, 0);
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        long start = minor(1000);
        setBalance(player, start);
        long gift = minor(250);

        VillageBoard.Outcome given = VillageFunding.donate(player, village, gift);
        helper.assertTrue(given.success(), "donated: " + given.message().getString());
        helper.assertValueEqual(balance(player), start - gift, "paid");
        helper.assertValueEqual(village.treasury(), gift, "in the treasury, above the income cap");
        helper.assertTrue(Math.abs(village.prosperity() - points(gift, 0.5)) < 1e-6, "prosperity: amount x 0.5");
        helper.assertTrue(villager.getGossips().getReputation(player.getUUID(), type -> type == GossipType.MINOR_POSITIVE) > 0,
            "the villagers are grateful");
        List<VillageState.DayFlow> history = village.history();
        helper.assertValueEqual(history.size(), 1, "one day in the history");
        helper.assertValueEqual(history.getFirst().day(), today(helper), "today");
        helper.assertValueEqual(history.getFirst().in(), gift, "the gift came in");
        helper.assertValueEqual(history.getFirst().out(), 0L, "nothing went out");

        long after = balance(player);
        helper.assertFalse(VillageFunding.donate(player, village, 0).success(), "nothing");
        helper.assertFalse(VillageFunding.donate(player, village, -5).success(), "negative");
        helper.assertFalse(VillageFunding.donate(player, village, after + 1).success(), "more than the balance");
        EconomyService.INSTANCE.setLocked(account(player), true);
        try {
            helper.assertFalse(VillageFunding.donate(player, village, 1).success(), "locked account");
        } finally {
            EconomyService.INSTANCE.setLocked(account(player), false);
        }
        helper.assertValueEqual(balance(player), after, "refusals cost nothing");
        helper.assertValueEqual(village.treasury(), gift, "and bring nothing");

        // Through the window: the payload's checks
        BlockPos board = new BlockPos(1, 1, 2);
        helper.setBlock(board, TraderyBlocks.VILLAGE_BOARD.get());
        VillageBoardMenu menu = new VillageBoardMenu(1, player.getInventory(), VillageBoard.snapshot(player, village, helper.absolutePos(board)),
            level, village.key());
        helper.assertValueEqual(menu.data().info().history().size(), 1, "the window shows the history");
        helper.assertValueEqual(menu.data().info().history().getFirst().in(), gift, "with the gift");
        menu.donate(player, minor(100));
        helper.assertValueEqual(village.treasury(), gift + minor(100), "donated through the window");
        helper.assertValueEqual(menu.data().info().treasury(), gift + minor(100), "the window got fresh data");
        Vec3 far = Vec3.atCenterOf(helper.absolutePos(board)).add(20, 0, 0);
        player.setPos(far.x, far.y, far.z);
        menu.donate(player, minor(100));
        helper.assertValueEqual(village.treasury(), gift + minor(100), "nothing from afar");
        Vec3 near = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setPos(near.x, near.y, near.z);
        for (int i = 0; i < 12; i++) {
            menu.donate(player, 1);
        }
        helper.assertTrue(village.treasury() < gift + minor(100) + 12, "the trade rate limit holds for donations too");
        VendingTrades.forget(player);
        villager.discard();
        helper.succeed();
    }

    /**
     * The treasury's history: every movement lands in the day's entry (income, deposits, reservations, raid losses),
     * a new day starts a new entry, only the last seven days are kept, the clock set back folds into the latest
     * entry, and the window's data carries it all.
     */
    public static void treasuryHistory(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        Villager villager = farmer(helper, new BlockPos(3, 1, 3), BELL);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(1));
        long today = today(helper);
        helper.assertTrue(village.history().isEmpty(), "setting the treasury directly writes no history");

        Villages.addToTreasury(server, village, minor(3));
        helper.assertTrue(Villages.takeFromTreasury(server, village, minor(2)), "taken");
        long lost = Villages.loseTreasury(server, village, 0.5);
        helper.assertValueEqual(lost, minor(2) / 2, "half of 2 lost");
        List<VillageState.DayFlow> history = village.history();
        helper.assertValueEqual(history.size(), 1, "one entry for today");
        helper.assertValueEqual(history.getFirst().day(), today, "today");
        helper.assertValueEqual(history.getFirst().in(), minor(3), "deposits in");
        helper.assertValueEqual(history.getFirst().out(), minor(2) + lost, "reservations and losses out");

        // Daily income lands as money in, on the day it's applied (what the cap lets through)
        Villages.census(level);
        helper.assertTrue(Villages.dailyIncome(village) > 0, "one villager earns something");
        long before = village.treasury();
        Villages.backdate(server, village, 2);
        Villages.touch(server, village);
        long applied = village.treasury() - before;
        helper.assertTrue(applied > 0, "two days of income applied");
        helper.assertValueEqual(village.history().getFirst().in(), minor(3) + applied, "the income applied is in");

        // Days before the latest entry fold into it; later days start entries; only seven are kept
        Villages.recordFlow(server, village, today - 1, minor(1), 0);
        helper.assertValueEqual(village.history().size(), 1, "the clock set back folds into today");
        helper.assertValueEqual(village.history().getFirst().in(), minor(4) + applied, "folded");
        for (int d = 1; d <= 9; d++) {
            Villages.recordFlow(server, village, today + d, d, 0);
        }
        history = village.history();
        helper.assertValueEqual(history.size(), VillageState.HISTORY_DAYS, "seven days kept");
        helper.assertValueEqual(history.getFirst().day(), today + 3, "the oldest dropped");
        helper.assertValueEqual(history.getLast().day(), today + 9, "the newest last");
        Villages.recordFlow(server, village, today + 20, 0, 0);
        helper.assertValueEqual(village.history().size(), VillageState.HISTORY_DAYS, "nothing moved: no entry");

        // The window carries it, bounded by the codec
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        VillageBoardMenu.Data data = VillageBoard.snapshot(player, village, helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertValueEqual(data.info().history().size(), VillageState.HISTORY_DAYS, "all seven sent");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        VillageBoardMenu.Data.STREAM_CODEC.encode(buf, data);
        VillageBoardMenu.Data decoded = VillageBoardMenu.Data.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.info().history(), data.info().history(), "the history survives the wire");
        helper.assertValueEqual(buf.readableBytes(), 0, "nothing left over");
        villager.discard();
        helper.succeed();
    }

    public static void dataBounded(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, minor(1000));
        for (int i = 0; i < VillageBoardMenu.MAX_ORDERS + 6; i++) {
            VillageOrders.postForTests(server, village, Items.BREAD, 8, 1, today(helper) + 2, false);
        }
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        VillageBoardMenu.Data data = VillageBoard.snapshot(player, village, helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertValueEqual(data.orders().size(), VillageBoardMenu.MAX_ORDERS, "orders cut to the codec's bound");
        helper.assertValueEqual(data.projects().size(), Math.min(VillageBoardMenu.MAX_PROJECTS, VillageProjects.all().size()), "projects listed");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        VillageBoardMenu.Data.STREAM_CODEC.encode(buf, data);
        VillageBoardMenu.Data decoded = VillageBoardMenu.Data.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.orders().size(), data.orders().size(), "orders survive");
        helper.assertValueEqual(decoded.orders().getFirst().id(), data.orders().getFirst().id(), "order id");
        helper.assertTrue(decoded.orders().getFirst().item().is(Items.BREAD), "order item");
        helper.assertValueEqual(decoded.info().treasury(), data.info().treasury(), "treasury");
        helper.assertValueEqual(decoded.projects().size(), data.projects().size(), "projects survive");
        helper.assertValueEqual(buf.readableBytes(), 0, "nothing left over");
        helper.succeed();
    }

    // ------------------------------------------------------------------ world

    /** Generated stalls carry a protected board at the front corner; a player's board anyone may break. */
    public static void boardOnMarketStalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getServer().getStructureTemplateManager();
        ServerPlayer stranger = player(helper, new BlockPos(2, 1, 0));
        for (String type : MarketPools.VILLAGE_TYPES) {
            for (String stall : MarketPools.STALLS) {
                StructurePoolElement element = MarketPools.element(MarketPools.template(type, stall),
                    level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(MarketPools.PROCESSORS));
                BlockPos origin = helper.absolutePos(BlockPos.ZERO);
                BoundingBox box = element.getBoundingBox(templates, origin, Rotation.NONE);
                for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                helper.assertTrue(element.place(templates, level, level.structureManager(), level.getChunkSource().getGenerator(), origin, origin,
                    Rotation.NONE, box, RandomSource.create(), LiquidSettings.APPLY_WATERLOGGING, false), type + " " + stall + " placed");
                BlockState state = level.getBlockState(helper.absolutePos(new BlockPos(4, 1, 0)));
                helper.assertTrue(state.is(TraderyBlocks.VILLAGE_BOARD.get()), type + " " + stall + ": a board at the front corner");
                helper.assertTrue(state.getValue(VillageBoardBlock.MARKET), type + " " + stall + ": a market board");
                helper.assertValueEqual(state.getValue(VillageBoardBlock.FACING), Direction.NORTH, type + " " + stall + ": facing the street");
                helper.assertFalse(VillageBoardBlock.mayBreak(stranger, level, helper.absolutePos(new BlockPos(4, 1, 0))),
                    type + " " + stall + ": players can't break it");
            }
        }
        BlockPos placed = new BlockPos(0, 1, 4);
        helper.setBlock(placed, TraderyBlocks.VILLAGE_BOARD.get());
        helper.assertTrue(VillageBoardBlock.mayBreak(stranger, level, helper.absolutePos(placed)), "a player's board: anyone breaks it");
        helper.succeed();
    }

    /** The bell hint knows villages, and looking at a bell never creates one. */
    public static void bellInfo(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        BlockPos lonely = new BlockPos(4, 1, 4);
        helper.setBlock(lonely, Blocks.BELL);
        helper.assertTrue(VillageBoard.bellInfo(level, helper.absolutePos(lonely)).isEmpty(), "unknown bell: no hint");
        helper.assertTrue(VillageEconomy.get(server).get(Villages.key(level.dimension(), helper.absolutePos(lonely))) == null,
            "looking didn't create a village");

        BlockPos bell = new BlockPos(1, 1, 1);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = village(helper, bell);
        double threshold = config().levels().thresholds().get(1);
        Villages.set(server, village, threshold + 12.7, 0);
        Optional<VillageBoard.BellInfo> info = VillageBoard.bellInfo(level, helper.absolutePos(bell));
        helper.assertTrue(info.isPresent(), "a known village");
        helper.assertValueEqual(info.get().level(), 2, "its level");
        helper.assertValueEqual(info.get().prosperity(), (int) Math.floor(threshold + 12.7), "its prosperity");
        helper.assertTrue(VillageBoard.bellInfo(level, helper.absolutePos(new BlockPos(2, 1, 2))).isEmpty(), "not a bell");
        helper.setBlock(lonely, Blocks.AIR);
        helper.setBlock(bell, Blocks.AIR);
        helper.succeed();
    }
}
