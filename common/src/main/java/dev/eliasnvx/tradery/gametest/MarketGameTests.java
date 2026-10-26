package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.market.MarketPools;
import dev.eliasnvx.tradery.market.MarketStockProcessor;
import dev.eliasnvx.tradery.market.MarketTables;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Village market stalls on a real server: every stall template placed the way village generation places it (rigid
 * legacy element with the {@code tradery:market_stock} processor list), the changer's prices, and the pool injection.
 */
public final class MarketGameTests {
    private static final ResourceLocation BUILDING_ENTRANCE = new ResourceLocation("building_entrance");
    /** A vanilla house whose street entrance the stalls copy. */
    private static final ResourceLocation VANILLA_HOUSE = new ResourceLocation("village/desert/houses/desert_small_house_1");

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("market_stalls_stock_offers", MarketGameTests::stallsStockOffers),
        new TraderyGameTests.Entry("market_changers_use_config_prices", MarketGameTests::changersUseConfigPrices),
        new TraderyGameTests.Entry("market_pools_get_stalls", MarketGameTests::poolsGetStalls));

    private MarketGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static Holder<StructureProcessorList> processors(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(MarketPools.PROCESSORS);
    }

    /** Places a stall at the test origin like village generation does; returns its vending blocks. */
    private static List<VendingBlockEntity> place(GameTestHelper helper, String villageType, String stall) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getServer().getStructureManager();
        StructurePoolElement element = MarketPools.element(MarketPools.template(villageType, stall), processors(level));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BoundingBox box = element.getBoundingBox(templates, origin, Rotation.NONE);
        // The legacy element skips air, so clear what the previous stall left
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        boolean placed = element.place(templates, level, level.structureManager(), level.getChunkSource().getGenerator(), origin, origin,
            Rotation.NONE, box, RandomSource.create(), false);
        helper.assertTrue(placed, villageType + " " + stall + " placed");
        List<VendingBlockEntity> vendors = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
                vendors.add(vendor);
            }
            helper.assertFalse(level.getBlockState(pos).is(Blocks.JIGSAW), villageType + " " + stall + ": jigsaw replaced by its final state");
        }
        return vendors;
    }

    /** Server-owned, infinite, burns the payment, no fee, set up, green light, facing the street, unbreakable for players. */
    private static void assertMarketVendor(GameTestHelper helper, VendingBlockEntity vendor, ServerPlayer stranger, String what) {
        helper.assertTrue(vendor.owner() instanceof AccountId.System system && system.id().equals(EconomyService.SERVER_ACCOUNT),
            what + ": owned by the server, got " + vendor.owner());
        GameTestAsserts.assertValueEqual(helper, vendor.admin(), new AdminFlags(true, true, true), what + ": infinite stock, burned payment, no fee");
        helper.assertTrue(vendor.settings().isConfigured(), what + ": has an offer");
        helper.assertTrue(vendor.getBlockState().getValue(VendingBlock.STOCKED), what + ": green light");
        GameTestAsserts.assertValueEqual(helper, vendor.getBlockState().getValue(VendingBlock.FACING), Direction.NORTH, what + ": faces the street");
        helper.assertFalse(VendingProtection.mayBreak(stranger, helper.getLevel(), vendor.getBlockPos()), what + ": players can't break it");
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.create(helper);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 0)));
        player.setPos(at.x, at.y, at.z);
        player.getInventory().clearContent();
        return player;
    }

    private static long balance(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static ServerConfig withMarket(ServerConfig config, ServerConfig.MarketSection market) {
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(), config.ore(), config.history(),
            config.log(), config.villagers(), market, config.villagerTrading(), config.villages());
    }

    // ------------------------------------------------------------------ tests

    public static void stallsStockOffers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int decimals = MarketStockProcessor.decimals(server);
        helper.assertTrue(MarketTables.problems(server).isEmpty(), "market tables load cleanly: " + MarketTables.problems(server));
        ServerPlayer stranger = player(helper);
        for (String type : MarketPools.VILLAGE_TYPES) {
            Optional<MarketTables.Table> table = MarketTables.get(server, type);
            helper.assertTrue(table.isPresent() && table.get().offers().size() >= 5, type + ": a market table with offers");
            List<VendingBlockEntity> vendors = place(helper, type, MarketPools.STALL);
            GameTestAsserts.assertValueEqual(helper, vendors.size(), 3, type + " stall: three vending blocks");
            for (VendingBlockEntity vendor : vendors) {
                String what = type + " stall vendor at " + helper.relativePos(vendor.getBlockPos());
                assertMarketVendor(helper, vendor, stranger, what);
                VendingSettings settings = vendor.settings();
                helper.assertFalse(settings.isBuyback(), what + ": sells");
                helper.assertTrue(table.get().offers().stream().anyMatch(offer -> offer.couldProduce(settings.goods(), settings.price(), decimals)),
                    what + ": " + settings.goods() + " for " + settings.price() + " is an offer of the table");
            }
        }
        helper.succeed();
    }

    public static void changersUseConfigPrices(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int decimals = MarketStockProcessor.decimals(level.getServer());
        ServerConfig original = TraderyConfig.server();
        long sell = Money.toMinor(original.market().emeraldSellPrice(), decimals);
        long buy = Math.min(Money.toMinor(original.market().emeraldBuyPrice(), decimals), sell);
        ServerPlayer player = player(helper);
        VendingBlockEntity sale = null;
        VendingBlockEntity buyback = null;
        for (String type : MarketPools.VILLAGE_TYPES) {
            List<VendingBlockEntity> vendors = place(helper, type, MarketPools.CHANGER);
            GameTestAsserts.assertValueEqual(helper, vendors.size(), 2, type + " changer: two vending blocks");
            sale = vendors.stream().filter(v -> !v.settings().isBuyback()).findFirst().orElse(null);
            buyback = vendors.stream().filter(v -> v.settings().isBuyback()).findFirst().orElse(null);
            helper.assertTrue(sale != null && buyback != null, type + " changer: one sells, one buys back");
            assertMarketVendor(helper, sale, player, type + " changer, selling");
            assertMarketVendor(helper, buyback, player, type + " changer, buying");
            helper.assertTrue(sale.settings().goods().is(Items.EMERALD) && sale.settings().perTrade() == 1, type + ": sells one emerald");
            helper.assertTrue(buyback.settings().goods().is(Items.EMERALD) && buyback.settings().perTrade() == 1, type + ": buys one emerald");
            GameTestAsserts.assertValueEqual(helper, sale.settings().price(), sell, type + ": market.emeraldSellPrice");
            GameTestAsserts.assertValueEqual(helper, buyback.settings().price(), buy, type + ": market.emeraldBuyPrice");
        }

        // Round trip at the last changer: buying an emerald and selling it back never makes money
        long start = sell + 1_000;
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), start, Reason.of(Reasons.ADMIN_SET, "test"));
        VendingTrades.Outcome bought = VendingTrades.execute(player, sale, 1);
        helper.assertTrue(bought.success(), "bought an emerald: " + bought.message().getString());
        GameTestAsserts.assertValueEqual(helper, player.getInventory().countItem(Items.EMERALD), 1, "one emerald in the inventory");
        GameTestAsserts.assertValueEqual(helper, balance(player), start - sell, "paid the sell price (burned)");
        VendingTrades.Outcome sold = VendingTrades.execute(player, buyback, 1);
        helper.assertTrue(sold.success(), "sold it back: " + sold.message().getString());
        GameTestAsserts.assertValueEqual(helper, player.getInventory().countItem(Items.EMERALD), 0, "the emerald is gone");
        GameTestAsserts.assertValueEqual(helper, balance(player), start - sell + buy, "got the buy price, no fee");
        helper.assertTrue(balance(player) <= start, "no profit from a round trip");

        // A buy price above the sell price would print money: the changer pays the sell price instead
        try {
            TraderyConfig.setServerForTests(withMarket(original, new ServerConfig.MarketSection(true, original.market().weight(),
                new BigDecimal("1"), new BigDecimal("5"))));
            for (VendingBlockEntity vendor : place(helper, "plains", MarketPools.CHANGER)) {
                GameTestAsserts.assertValueEqual(helper, vendor.settings().price(), Money.toMinor(BigDecimal.ONE, decimals),
                    "buy price above sell price: both at the sell price (" + (vendor.settings().isBuyback() ? "buying" : "selling") + ")");
            }
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        helper.succeed();
    }

    public static void poolsGetStalls(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerConfig.MarketSection market = TraderyConfig.server().market();
        MarketPools.inject(server, market);
        int expected = market.enabled() ? market.weight() : 0;
        Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        for (String type : MarketPools.VILLAGE_TYPES) {
            StructureTemplatePool houses = pools.get(MarketPools.housesPool(type));
            StructureTemplatePool zombieHouses = pools.get(new ResourceLocation("village/" + type + "/zombie/houses"));
            helper.assertTrue(houses != null && zombieHouses != null, type + ": vanilla house pools exist");
            for (String stall : MarketPools.STALLS) {
                ResourceLocation template = MarketPools.template(type, stall);
                GameTestAsserts.assertValueEqual(helper, MarketPools.weightOf(houses, template), expected, template + " in " + type + " houses with market.weight");
                GameTestAsserts.assertValueEqual(helper, MarketPools.weightOf(zombieHouses, template), 0, template + " not in zombie villages");
            }
        }
        GameTestAsserts.assertValueEqual(helper, MarketPools.inject(server, market), 0, "injecting again adds nothing");
        helper.assertTrue(processors(helper.getLevel()).value().list().contains(MarketStockProcessor.INSTANCE),
            "processor list tradery:market_stock runs tradery:market_stock");

        // The street entrance is the vanilla houses' one
        StructureTemplateManager templates = server.getStructureManager();
        StructureTemplate.StructureBlockInfo vanilla = templates.get(VANILLA_HOUSE).orElseThrow().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW).stream()
            .filter(jigsaw -> BUILDING_ENTRANCE.toString().equals(jigsaw.nbt().getString("name"))).findFirst().orElseThrow();
        for (String type : MarketPools.VILLAGE_TYPES) {
            for (String stall : MarketPools.STALLS) {
                ResourceLocation id = MarketPools.template(type, stall);
                Optional<StructureTemplate> template = templates.get(id);
                helper.assertTrue(template.isPresent(), id + " exists");
                List<StructureTemplate.StructureBlockInfo> jigsaws = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
                GameTestAsserts.assertValueEqual(helper, jigsaws.size(), 1, id + ": one jigsaw");
                StructureTemplate.StructureBlockInfo entrance = jigsaws.get(0);
                for (String key : List.of("name", "target", "pool", "joint")) {
                    GameTestAsserts.assertValueEqual(helper, entrance.nbt().getString(key), vanilla.nbt().getString(key), id + ": jigsaw " + key);
                }
                GameTestAsserts.assertValueEqual(helper, JigsawBlock.getFrontFacing(entrance.state()), Direction.NORTH, id + ": entrance faces the street");
                GameTestAsserts.assertValueEqual(helper, entrance.pos(), new BlockPos(2, 1, 0), id + ": entrance on the street side, a block above the floor");
            }
        }
        helper.succeed();
    }
}
