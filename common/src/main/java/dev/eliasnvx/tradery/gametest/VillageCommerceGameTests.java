package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.village.CaravanData;
import dev.eliasnvx.tradery.village.Caravans;
import dev.eliasnvx.tradery.village.VillageCommerce;
import dev.eliasnvx.tradery.village.VillageProjects;
import dev.eliasnvx.tradery.village.VillageRaids;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerMarket;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerTrading;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Village commerce (SPEC "Торговля между деревнями", "Рыночный день", "Торговое место", "Угрозы"): regional prices
 * in villagers' purchases, market days and the spending cap, renting a trading place, caravans (sent, arriving,
 * trading in money, leaving) and raids. Money and items are counted before and after; failures must change nothing.
 * Every test has its own bells at its own origin, and puts its villages back to level 1 at the end so the level tick
 * never sends their caravans anywhere else.
 */
public final class VillageCommerceGameTests {
    private static final BlockPos BELL = new BlockPos(0, 1, 0);
    private static final BlockPos OTHER_BELL = new BlockPos(5, 1, 5);
    private static final BlockPos VENDOR = new BlockPos(2, 1, 2);
    private static final BlockPos TRADER_AT = new BlockPos(3, 1, 3);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("commerce_regional_prices", VillageCommerceGameTests::regionalPrices),
        new TraderyGameTests.Entry("commerce_market_days", VillageCommerceGameTests::marketDays),
        new TraderyGameTests.Entry("commerce_rent_licence", VillageCommerceGameTests::rentLicence),
        new TraderyGameTests.Entry("commerce_caravan_sent_and_arrives", VillageCommerceGameTests::caravanSentAndArrives),
        new TraderyGameTests.Entry("commerce_caravan_trades_in_money", VillageCommerceGameTests::caravanTradesInMoney),
        new TraderyGameTests.Entry("commerce_caravan_failures_change_nothing", VillageCommerceGameTests::caravanFailuresChangeNothing),
        new TraderyGameTests.Entry("commerce_caravan_leaves_purse_home", VillageCommerceGameTests::caravanLeavesPurseHome),
        new TraderyGameTests.Entry("commerce_raid_outcomes", VillageCommerceGameTests::raidOutcomes));

    private VillageCommerceGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static VillagesConfig config() {
        return TraderyConfig.server().villages();
    }

    private static long today(GameTestHelper helper) {
        return VillagerPurchases.today(server(helper));
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** An adult farmer of the given villager type whose village is the bell at {@code bell}. */
    private static Villager villager(GameTestHelper helper, BlockPos at, BlockPos bell, VillagerType type) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at);
        ServerLevel level = helper.getLevel();
        villager.setVillagerData(villager.getVillagerData().setType(type)
            .setProfession(VillagerProfession.FARMER).setLevel(2));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(bell)));
        return villager;
    }

    private static VillageState village(GameTestHelper helper, BlockPos bell) {
        return Villages.at(server(helper), helper.getLevel().dimension(), helper.absolutePos(bell));
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = MockPlayers.create(helper);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(at));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static long balance(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(ServerPlayer player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static void setBalance(UUID player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static long minor(BigDecimal major) {
        return EconomyService.INSTANCE.defaultCurrency().toMinor(major);
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents contents ? contents.getKey() : message.getString();
    }

    private static void setMarketDay(GameTestHelper helper, VillageState village, long day) {
        VillageState.Events e = village.events();
        Villages.setEvents(server(helper), village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), day, e.caravanDay(),
            e.restorationUntil(), e.raidActive()));
    }

    /** Lot price as the caravan works it out: fair price × regional factor × lot, rounded down. */
    private static long lotPrice(Item item, double factor, int lot) {
        BigDecimal fair = VillagerMarket.fairPrice(new ItemStack(item)).orElseThrow();
        return minor(fair.multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(lot)));
    }

    /** Two villages: a desert one of the caravans' level with {@code treasury}, and a plains one. */
    private record Setup(VillageState origin, VillageState destination, List<Villager> villagers) {
    }

    private static Setup twoVillages(GameTestHelper helper, long treasury) {
        floor(helper);
        List<Villager> villagers = new ArrayList<>();
        villagers.add(villager(helper, new BlockPos(1, 1, 0), BELL, VillagerType.DESERT));
        villagers.add(villager(helper, new BlockPos(2, 1, 0), BELL, VillagerType.DESERT));
        villagers.add(villager(helper, new BlockPos(3, 1, 0), BELL, VillagerType.DESERT));
        villagers.add(villager(helper, new BlockPos(6, 1, 6), OTHER_BELL, VillagerType.PLAINS));
        Villages.census(helper.getLevel());
        MinecraftServer server = server(helper);
        VillageState origin = village(helper, BELL);
        VillageState destination = village(helper, OTHER_BELL);
        List<Double> thresholds = config().levels().thresholds();
        int minLevel = config().caravans().minLevel();
        helper.assertTrue(minLevel <= thresholds.size(), "caravans are possible at all with this config");
        Villages.set(server, origin, thresholds.get(minLevel - 1), treasury);
        Villages.set(server, destination, 0, 0);
        return new Setup(origin, destination, villagers);
    }

    /** Ends this test's caravans, removes its traders, llamas and villagers, and puts the origin back to level 1. */
    private static void cleanUp(GameTestHelper helper, Setup setup) {
        MinecraftServer server = server(helper);
        for (CaravanData.Caravan caravan : CaravanData.get(server).all()) {
            if (caravan.origin().equals(setup.origin.key())) {
                Caravans.close(server, caravan);
            }
        }
        for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, GameTestAsserts.bounds(helper).inflate(8),
            e -> e instanceof WanderingTrader || e instanceof TraderLlama)) {
            entity.discard();
        }
        setup.villagers.forEach(Entity::discard);
        Villages.set(server, setup.origin, 0, 0);
    }

    private static WanderingTrader arrived(GameTestHelper helper, CaravanData.Caravan caravan) {
        helper.assertTrue(Caravans.arrive(helper.getLevel(), caravan, helper.absolutePos(TRADER_AT)), "the caravan arrived");
        Entity entity = helper.getLevel().getEntity(caravan.trader());
        helper.assertTrue(entity instanceof WanderingTrader, "its trader is in the world");
        return (WanderingTrader) entity;
    }

    private static int offerSelling(WanderingTrader trader, Item item) {
        List<MerchantOffer> offers = trader.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getResult().is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int offerBuying(WanderingTrader trader, Item item) {
        List<MerchantOffer> offers = trader.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getCostA().is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static VendingBlockEntity vendor(GameTestHelper helper, UUID owner, VendingSettings settings, int stock) {
        helper.setBlock(VENDOR, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) helper.getBlockEntity(VENDOR);
        vendor.setOwner(AccountId.player(owner), "owner");
        vendor.setSettings(settings);
        Item goods = settings.goods().getItem();
        for (int slot = 0; stock > 0; slot++) {
            int put = Math.min(stock, 64);
            vendor.stock().setItem(slot, new ItemStack(goods, put));
            stock -= put;
        }
        vendor.stock().setChanged();
        return vendor;
    }

    // ------------------------------------------------------------------ regional prices

    /** A desert village sells sand cheap and pays dear for fish: its villagers buy cod at a price gouging elsewhere. */
    public static void regionalPrices(GameTestHelper helper) {
        floor(helper);
        Villager customer = villager(helper, new BlockPos(1, 1, 1), BELL, VillagerType.DESERT);
        Villages.census(helper.getLevel());
        VillageState village = village(helper, BELL);
        GameTestAsserts.assertValueEqual(helper, village.census().type(), "minecraft:desert", "a desert village");
        helper.assertTrue(VillageCommerce.priceFactor(village, new ItemStack(Items.SAND)) < 1, "sand is cheap in the desert");
        helper.assertTrue(VillageCommerce.priceFactor(village, new ItemStack(Items.OAK_LOG)) > 1, "logs (a tag) are dear in the desert");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.priceFactor(village, new ItemStack(Items.BREAD)), 1.0, "unlisted goods: the usual price");

        List<VillageCommerce.PriceShift> shifts = VillageCommerce.priceShifts(village);
        helper.assertTrue(!shifts.isEmpty(), "the board has something to show");
        helper.assertTrue(shifts.get(0).factor() < 1 && shifts.get(shifts.size() - 1).factor() > 1, "cheap first, dear last");
        for (int i = 1; i < shifts.size(); i++) {
            helper.assertTrue(shifts.get(i - 1).factor() <= shifts.get(i).factor(), "sorted by factor");
        }

        // A stall selling cooked cod at 1.3 x the usual fair price: gouging elsewhere, a bargain in the desert
        UUID owner = UUID.randomUUID();
        setBalance(owner, 0);
        long fair = minor(VillagerMarket.fairPrice(new ItemStack(Items.COOKED_COD)).orElseThrow());
        VendingSettings settings = new VendingSettings(new ItemStack(Items.COOKED_COD), PriceMode.CURRENCY, Math.round(fair * 1.3), ItemStack.EMPTY,
            false, DisplayAnimation.STATIC);
        VendingBlockEntity vendor = vendor(helper, owner, settings, 10);
        double factor = VillageCommerce.priceFactor(village, settings.goods());
        helper.assertTrue(factor > 1.3, "cod is dear in the desert: " + factor);
        helper.assertTrue(VillagerPurchases.priceRatio(settings).orElseThrow() > TraderyConfig.server().villagers().gougingRatio(),
            "at the usual fair price this is gouging");
        helper.assertTrue(VillagerPurchases.priceRatio(settings, factor).orElseThrow() < 1, "at the desert's fair price it's cheap");
        VillagerLedger.get(server(helper)).setWallet(customer.getUUID(), 100_000, today(helper));
        VillagerPurchases.Visit visit = VillagerPurchases.visit(customer, vendor, 0.99);
        helper.assertTrue(visit.bought(), "a desert villager buys it even with an unlucky roll: " + visit.result());
        helper.assertTrue(visit.ratio() < 1, "the visit compared with the regional price");
        customer.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ market days

    /** Market days: planned per village, more visits on the day, the spending cap doubled in purchases. */
    public static void marketDays(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        floor(helper);
        Villager customer = villager(helper, new BlockPos(1, 1, 1), BELL, VillagerType.PLAINS);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, 0);
        long today = today(helper);
        int interval = VillageCommerce.marketDayInterval(village);
        GameTestAsserts.assertValueEqual(helper, interval, config().levels().marketDayEvery().get(0), "a hamlet's interval");

        long next = VillageCommerce.nextMarketDay(server, village);
        helper.assertTrue(next > today && next <= today + interval, "the first market day falls within the first interval: " + next);
        GameTestAsserts.assertValueEqual(helper, village.events().nextMarketDay(), next, "and is remembered");
        helper.assertFalse(VillageCommerce.isMarketDay(server, village), "not today");
        UUID owner = UUID.randomUUID();
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.visitFactor(server, village, owner), 1.0, "an ordinary day");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.spendCapFactor(server, village), 1.0, "the usual cap");

        if (today >= 1) {
            // (On day 0 there is no yesterday: -1 means "not planned")
            setMarketDay(helper, village, today - 1);
            GameTestAsserts.assertValueEqual(helper, VillageCommerce.nextMarketDay(server, village), today - 1 + interval, "after a market day, the next one an interval later");
        }
        // The rule itself, on days this world hasn't reached
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.planMarketDay(9, 10, 3, "k"), 12L, "yesterday's market: the next one an interval later");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.planMarketDay(3, 10, 3, "k"), 12L, "several missed: whole intervals on, today or later");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.planMarketDay(4, 10, 3, "k"), 10L, "a market day falling on today stays");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.planMarketDay(11, 10, 3, "k"), 11L, "a planned one stays");
        long fresh = VillageCommerce.planMarketDay(-1, 10, 3, "k");
        helper.assertTrue(fresh > 10 && fresh <= 13, "a new village: within the first interval");
        setMarketDay(helper, village, today + interval + 5);
        long replanned = VillageCommerce.nextMarketDay(server, village);
        helper.assertTrue(replanned > today && replanned <= today + interval, "clock set back: planned again");

        setMarketDay(helper, village, today);
        helper.assertTrue(VillageCommerce.isMarketDay(server, village), "market day today");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.visitFactor(server, village, owner), config().marketDay().visitMultiplier(), "customers come more often");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.spendCapFactor(server, village), 2.0, "the village spends twice as much");

        // The cap in purchases: one lot fits under the usual cap, a second one only on a market day
        long cap = minor(TraderyConfig.server().villagers().dailyCapPerVillage());
        long fair = minor(VillagerMarket.fairPrice(new ItemStack(Items.GOLDEN_CARROT)).orElseThrow());
        long lotPrice = cap * 6 / 7;
        helper.assertTrue(lotPrice > 0 && lotPrice <= fair * 64, "a 64-carrot lot can cost 6/7 of the cap at or below the fair price");
        VendingSettings settings = new VendingSettings(new ItemStack(Items.GOLDEN_CARROT, 64), PriceMode.CURRENCY, lotPrice, ItemStack.EMPTY,
            false, DisplayAnimation.STATIC);
        setBalance(owner, 0);
        VendingBlockEntity vendor = vendor(helper, owner, settings, 192);
        VillagerLedger.get(server).setWallet(customer.getUUID(), lotPrice * 10, today);

        setMarketDay(helper, village, today + 1);
        VillagerPurchases.Visit first = VillagerPurchases.visit(customer, vendor, 0.0);
        GameTestAsserts.assertValueEqual(helper, first.lots(), 1, "one lot under the usual cap: " + first.result());
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.visit(customer, vendor, 0.0).result(), VillagerPurchases.Result.DAILY_CAP,
            "an ordinary day: the cap is reached");
        setMarketDay(helper, village, today);
        VillagerPurchases.Visit marketDay = VillagerPurchases.visit(customer, vendor, 0.0);
        helper.assertTrue(marketDay.bought(), "market day: the doubled cap allows more: " + marketDay.result());
        customer.discard();
        setMarketDay(helper, village, -1);
        helper.succeed();
    }

    // ------------------------------------------------------------------ trading places

    /** Renting: the player pays rent × days into the treasury and gets a licence; refusals change nothing. */
    public static void rentLicence(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 0, 0);
        long today = today(helper);
        setMarketDay(helper, village, today + 1);
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        UUID id = player.getUUID();

        long perDay = VillageCommerce.rentPerDay(village);
        GameTestAsserts.assertValueEqual(helper, perDay, minor(config().levels().rentPerDay().get(0)), "a hamlet's rent");
        int days = config().rent().days();
        long total = perDay * days;
        helper.assertTrue(total > 0, "rent costs something");
        setBalance(player, total + 5);
        helper.assertTrue(VillageCommerce.licenceUntil(server, village, id).isEmpty(), "no licence yet");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.radiusFactor(server, village, id), 1.0, "the usual radius");

        VillageCommerce.Result rented = VillageCommerce.rent(player, village);
        helper.assertTrue(rented.success(), "rented: " + rented.message().getString());
        GameTestAsserts.assertValueEqual(helper, balance(player), 5L, "rent x days paid");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), total, "into the treasury");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.licenceUntil(server, village, id).orElseThrow(), today + days, "a licence for the rent days");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.visitFactor(server, village, id), config().rent().visitMultiplier(), "customers come more often");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.radiusFactor(server, village, id), 1.5, "and from farther");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.visitFactor(server, village, UUID.randomUUID()), 1.0, "only for the licensee");

        VillageCommerce.Result twice = VillageCommerce.rent(player, village);
        helper.assertFalse(twice.success(), "a full period left: not again");
        GameTestAsserts.assertValueEqual(helper, key(twice.message()), "tradery.rent.already", "says why");
        GameTestAsserts.assertValueEqual(helper, balance(player), 5L, "nothing paid twice");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), total, "treasury unchanged");

        VillageCommerce.setLicence(server, village, id, today + 1);
        setBalance(player, total);
        VillageCommerce.Result extended = VillageCommerce.rent(player, village);
        helper.assertTrue(extended.success(), "extended");
        GameTestAsserts.assertValueEqual(helper, VillageCommerce.licenceUntil(server, village, id).orElseThrow(), today + 1 + days, "added to the day left");
        GameTestAsserts.assertValueEqual(helper, balance(player), 0L, "paid");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), 2 * total, "treasury got it");

        VillageCommerce.setLicence(server, village, id, today);
        setBalance(player, total - 1);
        VillageCommerce.Result poor = VillageCommerce.rent(player, village);
        helper.assertFalse(poor.success(), "can't afford it");
        GameTestAsserts.assertValueEqual(helper, key(poor.message()), "tradery.error.insufficient_funds", "says why");
        GameTestAsserts.assertValueEqual(helper, balance(player), total - 1, "balance unchanged");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), 2 * total, "treasury unchanged");
        helper.assertTrue(VillageCommerce.licenceUntil(server, village, id).isEmpty(), "no licence");
        setMarketDay(helper, village, -1);
        helper.succeed();
    }

    // ------------------------------------------------------------------ caravans

    /** A town sends a caravan to a village of another type: purse from the treasury, goods by region, a named trader. */
    public static void caravanSentAndArrives(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        long treasury = 1_000_000;
        Setup setup = twoVillages(helper, treasury);
        VillageState origin = setup.origin;
        VillageState destination = setup.destination;
        try {
            helper.assertTrue(Caravans.canSend(origin), "a town of the right level sends caravans");
            helper.assertFalse(Caravans.canSend(destination), "a hamlet doesn't");
            List<VillageState> destinations = Caravans.destinations(server, origin);
            helper.assertTrue(destinations.contains(destination), "the plains village is a destination");
            helper.assertFalse(destinations.contains(origin), "not itself");

            long today = today(helper);
            int interval = Caravans.intervalDays(origin);
            long purse = Math.min(treasury, Villages.dailyIncome(origin) * interval);
            helper.assertTrue(purse > 0, "an income to fill a purse");
            CaravanData.Caravan caravan = Caravans.send(server, origin, destination);
            GameTestAsserts.assertValueEqual(helper, caravan.purse(), purse, "the purse is the income of one interval");
            GameTestAsserts.assertValueEqual(helper, origin.treasury(), treasury - purse, "taken from the origin's treasury");
            GameTestAsserts.assertValueEqual(helper, origin.events().caravanDay(), today, "sent today");
            GameTestAsserts.assertValueEqual(helper, Caravans.nextCaravanDay(origin), today + interval, "the next one an interval later");
            helper.assertTrue(CaravanData.get(server).get(caravan.id()) == caravan, "on the road");
            helper.assertFalse(Caravans.destinations(server, origin).contains(destination), "one caravan at a time per destination");
            helper.assertTrue(caravan.arriveDay() >= today && caravan.arriveDay() < today + interval, "arrives before the next is due");
            helper.assertTrue(VillageCommerce.caravanNews(server, destination).stream().map(VillageCommerceGameTests::key)
                .anyMatch(k -> k.equals("tradery.caravan.news.today") || k.equals("tradery.caravan.news.tomorrow")
                    || k.equals("tradery.caravan.news.coming")), "the destination's board knows it's coming");
            GameTestAsserts.assertValueEqual(helper, key(VillageCommerce.caravanNews(server, origin).get(0)), "tradery.caravan.news.sent", "the origin's board too");

            // Goods: the desert's cheap goods for sale, the plains' dear goods bought, never both
            Caravans.Goods goods = Caravans.goods(origin, destination, RandomSource.create(1));
            GameTestAsserts.assertValueEqual(helper, goods.offers().size(), goods.values().size(), "a value per offer");
            int sand = -1;
            int spruce = -1;
            for (int i = 0; i < goods.offers().size(); i++) {
                MerchantOffer offer = goods.offers().get(i);
                helper.assertTrue(goods.values().get(i) > 0, "every offer is worth something");
                if (offer.getResult().is(Items.SAND)) {
                    sand = i;
                }
                helper.assertFalse(offer.getCostA().is(Items.SAND), "doesn't buy back what it sells");
                if (offer.getCostA().is(Items.SPRUCE_LOG)) {
                    spruce = i;
                }
            }
            helper.assertTrue(sand >= 0, "sells sand");
            helper.assertTrue(spruce >= 0, "buys spruce logs");
            int sandLot = goods.offers().get(sand).getResult().getCount();
            GameTestAsserts.assertValueEqual(helper, goods.values().get(sand),
                Math.max(1, lotPrice(Items.SAND, VillageCommerce.priceFactor(origin, new ItemStack(Items.SAND)), sandLot)), "at the desert's price");
            int spruceLot = goods.offers().get(spruce).getCostA().getCount();
            double spruceFactor = VillageCommerce.priceFactor(destination, new ItemStack(Items.SPRUCE_LOG));
            helper.assertTrue(spruceFactor > 1, "spruce is dear in the plains");
            GameTestAsserts.assertValueEqual(helper, goods.values().get(spruce), lotPrice(Items.SPRUCE_LOG, spruceFactor, spruceLot), "at the plains' price");

            WanderingTrader trader = arrived(helper, caravan);
            helper.assertTrue(trader.getTags().contains(Caravans.TAG), "tagged as a caravan");
            helper.assertTrue(Caravans.of(trader) == caravan, "found by its trader");
            helper.assertTrue(trader.getCustomName() != null && key(trader.getCustomName()).equals("tradery.caravan.name"), "named after the origin");
            GameTestAsserts.assertValueEqual(helper, trader.getDespawnDelay(), config().caravans().stayTicks(), "leaves after stayTicks");
            helper.assertTrue(!trader.getOffers().isEmpty(), "has offers");
            GameTestAsserts.assertValueEqual(helper, trader.getOffers().size(), caravan.values().size(), "and their values");
            for (TraderLlama llama : helper.getLevel().getEntitiesOfClass(TraderLlama.class, trader.getBoundingBox().inflate(16))) {
                helper.assertTrue(llama.getLeashHolder() == trader, "its llamas are leashed to it");
            }
            GameTestAsserts.assertValueEqual(helper, key(VillageCommerce.caravanNews(server, destination).get(0)), "tradery.caravan.news.here", "at the market");
            Caravans.close(server, caravan);
            GameTestAsserts.assertValueEqual(helper, origin.treasury(), treasury, "closed: the unspent purse went home");
            helper.assertTrue(CaravanData.get(server).get(caravan.id()) == null, "and the caravan is gone");
        } finally {
            cleanUp(helper, setup);
        }
        helper.succeed();
    }

    /** Buying the caravan's goods pays into its purse, selling it dear goods is paid from it; the window shows its prices. */
    public static void caravanTradesInMoney(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        Setup setup = twoVillages(helper, 0);
        try {
            CaravanData.Caravan caravan = Caravans.send(server, setup.origin, setup.destination);
            GameTestAsserts.assertValueEqual(helper, caravan.purse(), 0L, "an empty treasury sends an empty purse");
            WanderingTrader trader = arrived(helper, caravan);
            ServerPlayer player = player(helper, TRADER_AT.east());
            int sell = offerSelling(trader, Items.SAND);
            int buy = offerBuying(trader, Items.SPRUCE_LOG);
            helper.assertTrue(sell >= 0 && buy >= 0, "sand for sale, spruce wanted");
            long sandPrice = caravan.values().get(sell);
            long logPayout = caravan.values().get(buy);
            int sandLot = trader.getOffers().get(sell).getResult().getCount();
            int logLot = trader.getOffers().get(buy).getCostA().getCount();
            Caravans.setPurse(server, caravan, logPayout);
            setBalance(player, sandPrice);
            double prosperity = setup.destination.prosperity();

            VillagerTrading.Outcome bought = VillagerTrading.trade(player, trader, sell, false);
            helper.assertTrue(bought.success(), "bought: " + bought.message().getString());
            GameTestAsserts.assertValueEqual(helper, balance(player), 0L, "the price came off the balance");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SAND), sandLot, "a lot of sand");
            GameTestAsserts.assertValueEqual(helper, caravan.purse(), logPayout + sandPrice, "the price went into the purse");
            GameTestAsserts.assertValueEqual(helper, trader.getOffers().get(sell).getUses(), 1, "the offer counted a use");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.EMERALD), 0, "no emeralds involved");

            player.getInventory().add(new ItemStack(Items.SPRUCE_LOG, logLot));
            VillagerTrading.Outcome sold = VillagerTrading.trade(player, trader, buy, false);
            helper.assertTrue(sold.success(), "sold: " + sold.message().getString());
            GameTestAsserts.assertValueEqual(helper, balance(player), logPayout, "paid the dear price");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SPRUCE_LOG), 0, "the logs were taken");
            GameTestAsserts.assertValueEqual(helper, caravan.purse(), sandPrice, "from the purse");
            helper.assertTrue(setup.destination.prosperity() > prosperity, "trade at the market makes the destination prosper");

            // "Trade all" stops at the money
            setBalance(player, sandPrice * 2 + sandPrice / 2);
            VillagerTrading.Outcome all = VillagerTrading.trade(player, trader, sell, true);
            GameTestAsserts.assertValueEqual(helper, all.trades(), 2, "as many lots as the balance pays for");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SAND), sandLot * 3, "three lots in all");

            // Right-click: Tradery's window with the caravan's own prices and purse
            trader.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof VillagerTradeMenu, "Tradery's window opened");
            VillagerTradeMenu.Data data = ((VillagerTradeMenu) player.containerMenu).data();
            helper.assertTrue(data.caravan(), "as a caravan");
            GameTestAsserts.assertValueEqual(helper, data.offerValues(), caravan.values(), "with its prices");
            GameTestAsserts.assertValueEqual(helper, data.emeraldValue(sell), sandPrice, "the sand's price");
            GameTestAsserts.assertValueEqual(helper, data.wallet(), caravan.purse(), "and its purse");
            player.closeContainer();
            helper.assertTrue(trader.getTradingPlayer() == null, "closing ends the trade");

            // An ordinary wandering trader keeps vanilla's emerald window
            WanderingTrader plain = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(5, 1, 1));
            helper.assertTrue(Caravans.of(plain) == null, "not a caravan");
            plain.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof MerchantMenu, "vanilla's window");
            player.closeContainer();
            // A caravan trader whose caravan is over refuses (its offers are placeholders)
            plain.addTag(Caravans.TAG);
            plain.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "no window for a caravan that packed up");
            plain.discard();
        } finally {
            cleanUp(helper, setup);
        }
        helper.succeed();
    }

    /** Short of money, items, purse or stock, a bad offer, a closed caravan: refused, and every count is as before. */
    public static void caravanFailuresChangeNothing(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        Setup setup = twoVillages(helper, 0);
        try {
            CaravanData.Caravan caravan = Caravans.send(server, setup.origin, setup.destination);
            WanderingTrader trader = arrived(helper, caravan);
            ServerPlayer player = player(helper, TRADER_AT.east());
            int sell = offerSelling(trader, Items.SAND);
            int buy = offerBuying(trader, Items.SPRUCE_LOG);
            long sandPrice = caravan.values().get(sell);
            long logPayout = caravan.values().get(buy);
            int logLot = trader.getOffers().get(buy).getCostA().getCount();
            Caravans.setPurse(server, caravan, logPayout - 1);
            setBalance(player, sandPrice - 1);
            player.getInventory().add(new ItemStack(Items.SPRUCE_LOG, logLot));
            Runnable unchanged = () -> {
                GameTestAsserts.assertValueEqual(helper, balance(player), sandPrice - 1, "balance unchanged");
                GameTestAsserts.assertValueEqual(helper, caravan.purse(), logPayout - 1, "purse unchanged");
                GameTestAsserts.assertValueEqual(helper, count(player, Items.SAND), 0, "no sand given");
                GameTestAsserts.assertValueEqual(helper, trader.getOffers().get(sell).getUses(), 0, "no use counted");
                GameTestAsserts.assertValueEqual(helper, trader.getOffers().get(buy).getUses(), 0, "no use counted");
            };

            VillagerTrading.Outcome poor = VillagerTrading.trade(player, trader, sell, false);
            helper.assertFalse(poor.success(), "not enough money");
            GameTestAsserts.assertValueEqual(helper, key(poor.message()), "tradery.error.insufficient_funds", "says why");
            unchanged.run();

            VillagerTrading.Outcome broke = VillagerTrading.trade(player, trader, buy, true);
            helper.assertFalse(broke.success(), "the purse can't pay");
            GameTestAsserts.assertValueEqual(helper, key(broke.message()), "tradery.caravan.no_money", "says why");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SPRUCE_LOG), logLot, "logs kept");
            unchanged.run();

            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.SPRUCE_LOG, logLot - 1));
            Caravans.setPurse(server, caravan, logPayout);
            VillagerTrading.Outcome fewLogs = VillagerTrading.trade(player, trader, buy, false);
            helper.assertFalse(fewLogs.success(), "one log short");
            GameTestAsserts.assertValueEqual(helper, key(fewLogs.message()), "tradery.vending.not_enough_items", "says why");
            GameTestAsserts.assertValueEqual(helper, caravan.purse(), logPayout, "nothing paid out");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SPRUCE_LOG), logLot - 1, "logs kept");
            Caravans.setPurse(server, caravan, logPayout - 1);
            unchanged.run();

            helper.assertFalse(VillagerTrading.trade(player, trader, -1, false).success(), "negative index");
            helper.assertFalse(VillagerTrading.trade(player, trader, trader.getOffers().size(), false).success(), "no such offer");
            unchanged.run();

            setBalance(player, sandPrice * 100);
            trader.getOffers().get(sell).setToOutOfStock();
            VillagerTrading.Outcome soldOut = VillagerTrading.trade(player, trader, sell, false);
            helper.assertFalse(soldOut.success(), "sold out");
            GameTestAsserts.assertValueEqual(helper, key(soldOut.message()), "tradery.caravan.sold_out", "says why");
            GameTestAsserts.assertValueEqual(helper, balance(player), sandPrice * 100, "nothing paid");

            Caravans.close(server, caravan);
            GameTestAsserts.assertValueEqual(helper, setup.origin.treasury(), logPayout - 1, "the purse went home");
            trader.getOffers().get(buy).resetUses();
            player.getInventory().add(new ItemStack(Items.SPRUCE_LOG, 1));
            VillagerTrading.Outcome closed = VillagerTrading.trade(player, trader, buy, false);
            helper.assertFalse(closed.success(), "the caravan is over");
            GameTestAsserts.assertValueEqual(helper, key(closed.message()), "tradery.caravan.closed", "says why");
            GameTestAsserts.assertValueEqual(helper, count(player, Items.SPRUCE_LOG), logLot, "logs kept");
            GameTestAsserts.assertValueEqual(helper, balance(player), sandPrice * 100, "nothing paid");
            GameTestAsserts.assertValueEqual(helper, setup.origin.treasury(), logPayout - 1, "nothing paid from anywhere");
        } finally {
            cleanUp(helper, setup);
        }
        helper.succeed();
    }

    /** The trader leaves (vanilla's despawn): the caravan is over and its purse, with what it earned, goes home. */
    public static void caravanLeavesPurseHome(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        long treasury = 5_000;
        Setup setup = twoVillages(helper, treasury);
        try {
            CaravanData.Caravan caravan = Caravans.send(server, setup.origin, setup.destination);
            long purse = caravan.purse();
            WanderingTrader trader = arrived(helper, caravan);
            Caravans.update(helper.getLevel(), caravan);
            helper.assertTrue(CaravanData.get(server).get(caravan.id()) != null, "still at the market while the trader is there");

            Caravans.setPurse(server, caravan, purse + 123);
            helper.getLevel().getEntitiesOfClass(TraderLlama.class, trader.getBoundingBox().inflate(16)).forEach(Entity::discard);
            trader.discard();
            Caravans.update(helper.getLevel(), caravan);
            helper.assertTrue(CaravanData.get(server).get(caravan.id()) == null, "gone with its trader");
            GameTestAsserts.assertValueEqual(helper, setup.origin.treasury(), treasury - purse + purse + 123, "the purse and its earnings went home");
            helper.assertFalse(VillageCommerce.caravanNews(server, setup.destination).stream().map(VillageCommerceGameTests::key)
                .anyMatch(k -> k.equals("tradery.caravan.news.here")), "no caravan at the destination's market any more");
        } finally {
            cleanUp(helper, setup);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ raids

    /** A lost raid takes prosperity, half the treasury and wallets; a won one brings prosperity and restoration orders. */
    public static void raidOutcomes(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        floor(helper);
        Villager a = villager(helper, new BlockPos(1, 1, 1), BELL, VillagerType.PLAINS);
        Villager b = villager(helper, new BlockPos(2, 1, 1), BELL, VillagerType.PLAINS);
        VillageState village = village(helper, BELL);
        Villages.set(server, village, 1000, 1000);
        long today = today(helper);
        VillagerLedger ledger = VillagerLedger.get(server);
        ledger.setWallet(a.getUUID(), 1000, today);
        ledger.setWallet(b.getUUID(), 1000, today);
        double raidLoss = VillageProjects.effects(village).raidLoss();

        VillageRaids.observe(level, village, VillageRaids.Seen.LOSS);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), 1000L, "an outcome without a raid seen going on changes nothing");

        VillageRaids.observe(level, village, VillageRaids.Seen.ONGOING);
        helper.assertTrue(village.events().raidActive(), "a raid is going on");
        VillageRaids.observe(level, village, VillageRaids.Seen.LOSS);
        helper.assertFalse(village.events().raidActive(), "over");
        double expected = 1000 * (1 - config().threats().raidLossProsperity() * raidLoss);
        helper.assertTrue(Math.abs(village.prosperity() - expected) < 1e-6, "prosperity lost: " + village.prosperity() + " vs " + expected);
        long kept = 1000 - (long) (1000 * 0.5 * raidLoss);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), kept, "half the treasury lost");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.wallet(a, today), kept, "half a villager's money lost");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.wallet(b, today), kept, "every villager's");
        VillageRaids.observe(level, village, VillageRaids.Seen.LOSS);
        GameTestAsserts.assertValueEqual(helper, village.treasury(), kept, "applied once");

        double before = village.prosperity();
        helper.assertFalse(Villages.restorationDue(server, village), "no restoration yet");
        VillageRaids.observe(level, village, VillageRaids.Seen.ONGOING);
        VillageRaids.observe(level, village, VillageRaids.Seen.VICTORY);
        helper.assertTrue(Math.abs(village.prosperity() - (before + config().threats().raidWinProsperity())) < 1e-6, "a won raid brings prosperity");
        helper.assertTrue(Villages.restorationDue(server, village), "restoration orders on the board");
        GameTestAsserts.assertValueEqual(helper, village.treasury(), kept, "the treasury is safe");

        VillageRaids.observe(level, village, VillageRaids.Seen.ONGOING);
        VillageRaids.observe(level, village, VillageRaids.Seen.NONE);
        helper.assertFalse(village.events().raidActive(), "a raid that stopped unseen is forgotten");
        GameTestAsserts.assertValueEqual(helper, VillagerPurchases.wallet(a, today), kept, "and changes nothing");
        a.discard();
        b.discard();
        Villages.set(server, village, 0, 0);
        helper.succeed();
    }
}
