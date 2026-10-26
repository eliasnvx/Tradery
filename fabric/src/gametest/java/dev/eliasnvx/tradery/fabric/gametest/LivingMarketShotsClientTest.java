package dev.eliasnvx.tradery.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.client.screen.VillagerTradeScreen;
import dev.eliasnvx.tradery.client.screen.VillageBoardScreen;
import dev.eliasnvx.tradery.client.screen.CounterBuyerScreen;
import dev.eliasnvx.tradery.client.screen.CounterOwnerScreen;
import dev.eliasnvx.tradery.client.screen.ShopLedgerScreen;
import dev.eliasnvx.tradery.vending.CounterMenus;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlock;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.ShopLedger;
import dev.eliasnvx.tradery.vending.VendorsData;
import java.util.UUID;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.village.Caravans;
import dev.eliasnvx.tradery.village.VillageBoard;
import dev.eliasnvx.tradery.village.VillageBuildings;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillagePresence;
import dev.eliasnvx.tradery.village.VillageBoardBlock;
import dev.eliasnvx.tradery.village.VillageOrders;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.village.TradeTerms;
import dev.eliasnvx.tradery.village.VillagerCustomers;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Living Market shots (1.1): a generated village market stall, a villager of that village walking to a player's
 * stall and buying, and a villager's trade window in money. Only with {@code TRADERY_MARKET_SHOTS=1}
 * (TRADERY_MARKET_SHOTS=1 ./gradlew :fabric:runClientGameTest), or {@code =trade} for the trade window alone.
 * For the run only, server.json5 makes stalls frequent and customers quick (deleted at the end); the world generation,
 * the villager AI and the villagers' offers are the real ones.
 */
public final class LivingMarketShotsClientTest implements FabricClientGameTest {
    private static final String SHOTS_CONFIG = """
        {
          villagers: { checkIntervalTicks: 20, visitChance: 1.0, customerCooldownTicks: 200 },
          market: { weight: 60 }
        }
        """;

    @Override
    public void runTest(ClientGameTestContext context) {
        String mode = System.getenv("TRADERY_MARKET_SHOTS");
        if (!"1".equals(mode) && !"trade".equals(mode) && !"village".equals(mode) && !"shops".equals(mode)) {
            return;
        }
        Path config = Platform.get().configDir().resolve("tradery").resolve("server.json5");
        try {
            Files.createDirectories(config.getParent());
            Files.writeString(config, SHOTS_CONFIG);
            run(context, mode);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(config);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private static void run(ClientGameTestContext context, String mode) {
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(3);
        });
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui -> {
            ui.setWorldType(new net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry(
                ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL)));
            ui.setSeed("tradery");
            ui.setGenerateStructures(true);
        }).create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear 1000000");
            server.runCommand("time set 1000");
            server.runCommand("gamemode creative @p");

            BlockPos village = server.computeOnServer(LivingMarketShotsClientTest::findVillage);
            server.runCommand(String.format(Locale.ROOT, "tp @p %d 200 %d", village.getX(), village.getZ()));
            context.runOnClient(mc -> {
                mc.player.getAbilities().flying = true;
            });
            context.waitTicks(200);
            world.getConnection().waitForChunksRender();

            // A generated market stall: from the street, no GUI
            BlockPos stall = server.computeOnServer(s -> findMarketVendor(s.overworld(), village));
            if (stall == null) {
                throw new AssertionError("no market stall generated around the village at " + village);
            }
            Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(stall).getValue(VendingBlock.FACING));
            if ("trade".equals(mode)) {
                tradeWindow(context, server, stall, facing);
                return;
            }
            if ("village".equals(mode)) {
                villageEconomy(context, server, stall, facing);
                return;
            }
            if ("shops".equals(mode)) {
                shops(context, server, stall);
                return;
            }
            setHudHidden(context, true);
            lookFrom(context, server, stall, facing, 5.0, 1.2, 0.5);
            context.waitTicks(40);
            shot(context, "market_stall");
            setHudHidden(context, false);
            server.runCommand("gamemode survival @p");
            lookFrom(context, server, stall, facing, 1.6, 0.0, 0.5);
            context.waitTicks(20);
            shot(context, "market_hint");

            // A player's bread stall next to the market; the village's own villagers come by
            BlockPos shop = server.computeOnServer(s -> placeShop(s, stall, facing));
            server.runCommand("gamemode creative @p");
            setHudHidden(context, false);
            lookFrom(context, server, shop, facing, 7.0, 2.5, 0.5);
            System.out.println("Tradery market shots: villagers near the shop " + server.computeOnServer(s -> s.overworld()
                .getEntitiesOfClass(Villager.class, new AABB(shop).inflate(32)).size()) + ", eligible " + server.computeOnServer(s -> s.overworld()
                .getEntitiesOfClass(Villager.class, new AABB(shop).inflate(32), VillagerCustomers::isEligible).size()));
            // A fresh village's villagers may not have taken their jobs yet: give the nearest two a composter and the farmer's job
            if (server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Villager.class, new AABB(shop).inflate(32), VillagerCustomers::isEligible)
                .isEmpty())) {
                System.out.println("Tradery market shots: hired " + server.computeOnServer(s -> hireFarmers(s, shop, 2)));
            }
            for (int i = 0; i < 6 && server.computeOnServer(s -> customerNear(s, shop) == null); i++) {
                System.out.println("Tradery market shots: " + server.computeOnServer(s -> describeVillagers(s, shop)));
                context.waitTicks(20 * 30);
            }
            int before = server.computeOnServer(s -> vendor(s, shop).tradesInStock());
            int waited = waitOnServer(context, server, s -> customerNear(s, shop) != null, 20 * 120);
            shot(context, "villager_walking");
            // The customer may have bought already while walking up (a quick decision): then it's done
            waitOnServer(context, server, s -> vendor(s, shop).tradesInStock() < before, 20 * 60);
            context.waitTicks(10);
            shot(context, "villager_bought");

            // The owner screen with the villager toggle
            server.runOnServer(s -> VendingMenus.openOwner(player(s), vendor(s, shop), false));
            context.waitForScreen(VendingOwnerScreen.class);
            context.waitTicks(5);
            shot(context, "owner_villagers_toggle");
            context.runOnClient(mc -> mc.player.closeContainer());
            tradeWindow(context, server, shop, facing);
            villageEconomy(context, server, stall, facing);
            shops(context, server, stall);
            System.out.println("Tradery market shots: village " + village + ", stall " + stall + ", shop " + shop + ", waited " + waited);
        }
    }

    // ------------------------------------------------------------------ villager trade window

    /**
     * A villager of the village, with its own offers, in front of {@code block}; the player right-clicks it, picks
     * an offer that pays emeralds (sells to it), then one that costs emeralds (buys from it).
     */
    private static void tradeWindow(ClientGameTestContext context, TestServerContext server, BlockPos block, Direction facing) {
        server.runCommand("gamemode survival @p");
        lookFrom(context, server, block, facing, 3.0, 0.0, 0.0);
        int[] rows = server.computeOnServer(s -> openTrade(s, block.relative(facing)));
        context.waitForScreen(VillagerTradeScreen.class);
        context.waitTicks(5);
        clickRow(context, rows[0]);
        context.waitTicks(5);
        shot(context, "villager_trade_window");
        context.clickScreenButton("tradery.villager_trade.trade");
        context.waitTicks(10);
        shot(context, "villager_trade_sold");
        if (rows[1] >= 0) {
            clickRow(context, rows[1]);
            context.clickScreenButton("tradery.villager_trade.trade");
            context.waitTicks(10);
            shot(context, "villager_trade_bought");
        }
        System.out.println("Tradery market shots: trade window " + server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            return "balance " + EconomyService.INSTANCE.account(p.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency())
                + ", menu " + p.containerMenu.getClass().getSimpleName();
        }));
        context.runOnClient(mc -> mc.player.closeContainer());
    }

    /**
     * Brings a villager with a profession to {@code at}, gives the player money, the items its first emerald-paying
     * offer wants and a few for the second cost of its first emerald-costing one, and right-clicks it.
     *
     * @return list rows of the offer to sell and the offer to buy (-1: none)
     */
    private static int[] openTrade(MinecraftServer server, BlockPos at) {
        ServerLevel level = server.overworld();
        ServerPlayer player = player(server);
        Villager chosen = null;
        int sell = -1;
        int buy = -1;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(64), v -> !v.isBaby() && !v.isSleeping())) {
            var profession = v.getVillagerData().profession();
            if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) {
                continue;
            }
            MerchantOffers offers = v.getOffers();
            int s = -1;
            int b = -1;
            for (int i = 0; i < Math.min(offers.size(), 7); i++) {
                if (s < 0 && TradeTerms.isEmerald(offers.get(i).getResult())) {
                    s = i;
                }
                if (b < 0 && TradeTerms.isEmerald(offers.get(i).getCostA())) {
                    b = i;
                }
            }
            if (s >= 0 && (chosen == null || b >= 0 && buy < 0)) {
                chosen = v;
                sell = s;
                buy = b;
            }
        }
        if (chosen == null) {
            // Villagers of a fresh village take their jobs over time: hire the nearest adult as a librarian
            Villager nearest = null;
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(64), v -> !v.isBaby() && !v.isSleeping())) {
                if (nearest == null || v.distanceToSqr(Vec3.atCenterOf(at)) < nearest.distanceToSqr(Vec3.atCenterOf(at))) {
                    nearest = v;
                }
            }
            if (nearest == null) {
                throw new AssertionError("no adult villager near " + at);
            }
            // Novice offers are two random picks of the profession's pool: try a few until one sells and buys
            for (var profession : List.of(VillagerProfession.FARMER, VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN,
                VillagerProfession.LIBRARIAN, VillagerProfession.FARMER, VillagerProfession.BUTCHER)) {
                nearest.setVillagerData(nearest.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
                nearest.setVillagerData(nearest.getVillagerData().withProfession(level.registryAccess(), profession).withLevel(1));
                MerchantOffers offers = nearest.getOffers();
                sell = -1;
                buy = -1;
                for (int i = 0; i < offers.size(); i++) {
                    if (sell < 0 && TradeTerms.isEmerald(offers.get(i).getResult())) {
                        sell = i;
                    }
                    if (buy < 0 && TradeTerms.isEmerald(offers.get(i).getCostA())) {
                        buy = i;
                    }
                }
                if (sell >= 0 && buy >= 0) {
                    break;
                }
            }
            if (sell < 0) {
                throw new AssertionError("no profession gave an emerald-paying offer");
            }
            chosen = nearest;
        }
        chosen.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        long today = VillagerPurchases.today(server);
        var currency = EconomyService.INSTANCE.defaultCurrency();
        VillagerLedger.get(server).setWallet(chosen.getUUID(), currency.toMinor(java.math.BigDecimal.valueOf(9)), today);
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), currency.toMinor(java.math.BigDecimal.valueOf(250)),
            Reason.of(Reasons.ADMIN_SET, "shots"));
        player.getInventory().clearContent();
        MerchantOffer sold = chosen.getOffers().get(sell);
        player.getInventory().add(sold.getCostA().copyWithCount(sold.getCostA().getMaxStackSize()));
        if (buy >= 0 && !chosen.getOffers().get(buy).getCostB().isEmpty()) {
            ItemStack second = chosen.getOffers().get(buy).getCostB();
            player.getInventory().add(second.copyWithCount(Math.min(second.getMaxStackSize(), second.getCount() * 2)));
        }
        chosen.mobInteract(player, InteractionHand.MAIN_HAND);
        System.out.println("Tradery market shots: trading with " + chosen.getVillagerData().profession().unwrapKey()
            .map(k -> k.identifier().getPath()).orElse("?") + " level " + chosen.getVillagerData().level() + ", offers "
            + chosen.getOffers().size() + ", sell row " + sell + ", buy row " + buy + ", sleeping " + chosen.isSleeping()
            + ", distance " + String.format(Locale.ROOT, "%.1f", Math.sqrt(chosen.distanceToSqr(player))) + ", menu "
            + player.containerMenu.getClass().getSimpleName());
        return new int[] {sell, buy};
    }

    // ------------------------------------------------------------------ village economy

    /**
     * The village economy of the market's village: a Town with money in its treasury; the board (one generated on a
     * stall, or placed by the stall) with its three tabs and an order delivered; the bell hint; a caravan from a
     * desert village (made for the shot: one villager of its own bell) with its trade window.
     */
    private static void villageEconomy(ClientGameTestContext context, TestServerContext server, BlockPos stall, Direction facing) {
        server.runCommand("gamemode survival @p");
        server.runOnServer(s -> Villages.census(s.overworld()));
        BlockPos bell = server.computeOnServer(s -> Villages.nearestBell(s.overworld(), stall, 96).orElse(null));
        if (bell == null) {
            throw new AssertionError("no bell near the market stall " + stall);
        }
        BlockPos board = server.computeOnServer(s -> boardNear(s, stall, facing));
        Direction boardFacing = server.computeOnServer(s -> s.overworld().getBlockState(board).getValue(VillageBoardBlock.FACING));
        server.runOnServer(s -> {
            VillageState village = Villages.at(s, s.overworld().dimension(), bell);
            var thresholds = TraderyConfig.server().villages().levels().thresholds();
            Villages.set(s, village, thresholds.get(Math.min(2, thresholds.size() - 1)) + 300,
                EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(400)));
            // A week of treasury history for the chart: wages and tax in, orders and a caravan purse out
            long today = VillagerPurchases.today(s);
            long[][] week = {{60, 20}, {45, 80}, {90, 10}, {30, 0}, {120, 150}, {75, 40}};
            for (int i = 0; i < week.length; i++) {
                Villages.recordFlow(s, village, today - week.length + i, EconomyService.INSTANCE.defaultCurrency().toMinor(
                    java.math.BigDecimal.valueOf(week[i][0])), EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(week[i][1])));
            }
            VillageOrders.refresh(s, village);
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            village.orders().stream().filter(o -> !o.restoration()).findFirst().ifPresent(order -> {
                Item item = BuiltInRegistries.ITEM.getValue(order.item());
                p.getInventory().add(new ItemStack(item, Math.min(item.getDefaultMaxStackSize(), order.remaining())));
            });
            EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(p.getUUID()),
                EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(1500)), Reason.of(Reasons.ADMIN_SET, "shots"));
            System.out.println("Tradery market shots: village " + Villages.displayName(village).getString() + " level " + Villages.level(village)
                + ", population " + village.census().population() + ", orders " + village.orders().size() + ", board " + board);
        });
        lookFrom(context, server, board, boardFacing, 2.5, 0.0, 0.0);
        server.runOnServer(s -> VillageBoard.open(player(s), board));
        context.waitForScreen(VillageBoardScreen.class);
        context.waitTicks(5);
        shot(context, "board_orders");
        if (context.tryClickScreenButton("tradery.board.deliver")) {
            context.waitTicks(10);
            shot(context, "board_delivered");
        }
        clickBoardTab(context, 1);
        // The donation row is selected by default: type an amount, shoot (hovering the treasury chart), donate, shoot
        clickBoard(context, 156 + 136 / 2.0, 147 + 7);
        context.getInput().typeChars("250");
        context.waitTicks(3);
        int professionLines = server.computeOnServer(s -> Math.min(4, Villages.at(s, s.overworld().dimension(), bell).census().professions().size()));
        hoverBoard(context, 8 + 60, 107 + 7 + 10 * professionLines);
        shot(context, "board_village");
        clickBoard(context, 156 + 136 / 2.0, 164 + 10);
        context.waitTicks(10);
        shot(context, "board_donated");
        clickBoardTab(context, 2);
        shot(context, "board_market");
        context.runOnClient(mc -> mc.player.closeContainer());

        // The bell: its hint over the crosshair, from a free spot on the ground next to it
        BlockPos stand = server.computeOnServer(s -> openGround(s.overworld(), bell, 2, 4));
        lookAt(context, server, stand, Vec3.atCenterOf(bell));
        context.waitTicks(60);
        shot(context, "bell_hint");

        // The village title: as if the player had just arrived (the presence check runs every two seconds)
        server.runOnServer(s -> VillagePresence.forget(player(s)));
        context.waitTicks(50);
        shot(context, "village_title");

        // Projects you can see: lamp posts and a granary by the bell, from a few blocks up
        server.runOnServer(s -> {
            VillageState village = Villages.at(s, s.overworld().dimension(), bell);
            ServerPlayer p = player(s);
            EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(p.getUUID()),
                EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(2000)), Reason.of(Reasons.ADMIN_SET, "shots"));
            for (String id : List.of("street_lamps", "granary", "workshops", "watch_post", "paved_streets")) {
                VillageBoard.Outcome outcome = VillageFunding.fund(p, village, Identifier.fromNamespaceAndPath("tradery", id));
                System.out.println("Tradery market shots: project " + id + ": " + outcome.success() + " " + outcome.message().getString());
            }
        });
        lookFrom(context, server, bell, Direction.SOUTH, 16, 9, 0);
        context.waitTicks(20);
        shot(context, "village_projects");
        // The whole village from above: every street paved (spectator: nobody falls)
        server.runCommand("gamemode spectator @p");
        lookFrom(context, server, bell, Direction.SOUTH, 30, 45, 0);
        context.waitTicks(40);
        shot(context, "village_paved_above");
        server.runCommand("gamemode survival @p");

        // A street light up close, at night
        BlockPos lantern = server.computeOnServer(s -> {
            BlockPos found = null;
            double best = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(bell.offset(-40, -8, -40), bell.offset(40, 12, 40))) {
                var state = s.overworld().getBlockState(pos);
                if (state.is(net.minecraft.world.level.block.Blocks.LANTERN) && state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING)
                    && pos.distSqr(bell) > 36 && pos.distSqr(bell) < best) {
                    best = pos.distSqr(bell);
                    found = pos.immutable();
                }
            }
            System.out.println("Tradery market shots: street light at " + found);
            return found;
        });
        if (lantern != null) {
            server.runCommand("time set 14000");
            server.runCommand("gamemode spectator @p");
            BlockPos street = server.computeOnServer(s -> {
                for (int dy = 1; dy <= 6; dy++) {
                    if (!s.overworld().getBlockState(lantern.below(dy)).isAir()) {
                        return lantern.below(dy);
                    }
                }
                return lantern.below(4);
            });
            Direction along = server.computeOnServer(s -> {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (s.overworld().getBlockState(street.relative(side)).isFaceSturdy(s.overworld(), street.relative(side), Direction.UP)
                        && s.overworld().getBlockState(lantern.above().relative(side)).isAir()
                        && !s.overworld().getBlockState(street.relative(side).above()).isAir()) {
                        return side.getClockWise();
                    }
                }
                return Direction.SOUTH;
            });
            lookFrom(context, server, street.above(2), along, 7, 1, 2);
            context.waitTicks(30);
            shot(context, "village_street_light");
            server.runCommand("time set 6000");
            server.runCommand("gamemode survival @p");
        }

        // The village builds its well from a full fund: the sign bears its name
        BlockPos sign = server.computeOnServer(s -> {
            VillageState village = Villages.at(s, s.overworld().dimension(), bell);
            VillageBuildings.Building well = VillageBuildings.get(Identifier.fromNamespaceAndPath("tradery", "well")).orElseThrow();
            Villages.setSavings(s, village, VillageBuildings.costMinor(well));
            VillageBuildings.Building built = VillageBuildings.tryBuild(s.overworld(), village, 24, null);
            System.out.println("Tradery market shots: built " + built + ", savings " + village.savings() + ", buildings " + village.buildings());
            for (BlockPos pos : BlockPos.betweenClosed(bell.offset(-24, -6, -24), bell.offset(24, 6, 24))) {
                if (s.overworld().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity) {
                    return pos.immutable();
                }
            }
            return null;
        });
        if (sign != null) {
            // The well faces the bell: look at it from between the two
            Direction towardsBell = Direction.getApproximateNearest(bell.getX() - sign.getX(), 0, bell.getZ() - sign.getZ());
            lookFrom(context, server, sign, towardsBell, 5, 2, 1);
            context.waitTicks(10);
            shot(context, "village_well");
        }

        // A festival: dusk on a market day of a Town
        server.runCommand("time set 9600");
        server.runOnServer(s -> {
            VillageState village = Villages.at(s, s.overworld().dimension(), bell);
            long today = VillagerPurchases.today(s);
            VillageState.Events e = village.events();
            Villages.setEvents(s, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), today, e.caravanDay(), e.restorationUntil(),
                e.raidActive()));
        });
        lookFrom(context, server, bell, Direction.SOUTH, 10, 3, 0);
        context.waitTicks(130);
        shot(context, "village_festival");

        // A caravan from a desert village arrives at this one's bell
        BlockPos market = server.computeOnServer(s -> openGround(s.overworld(), bell, 3, 8));
        int caravan = server.computeOnServer(s -> caravan(s, bell, market));
        context.waitTicks(40);
        BlockPos viewer = server.computeOnServer(s -> openGround(s.overworld(), market, 5, 9));
        lookAt(context, server, viewer, Vec3.atCenterOf(market));
        context.waitTicks(20);
        shot(context, "caravan");
        server.runOnServer(s -> {
            if (s.overworld().getEntity(caravan) instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader) {
                ServerPlayer p = player(s);
                trader.teleportTo(p.getX() + 1.5, p.getY(), p.getZ());
                trader.mobInteract(p, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        });
        context.waitForScreen(VillagerTradeScreen.class);
        context.waitTicks(10);
        shot(context, "caravan_trade");
        context.runOnClient(mc -> mc.player.closeContainer());
    }

    // ------------------------------------------------------------------ market counter and shop ledger

    /**
     * A market counter with six offers next to the market (owned by "Mira", so the player sees the buyer side), its
     * buyer and owner windows, then the player's own shops in the shop ledger: the counter (now the player's) and an
     * empty vending block (an alert).
     */
    private static void shops(ClientGameTestContext context, TestServerContext server, BlockPos stall) {
        server.runCommand("gamemode survival @p");
        BlockPos at = server.computeOnServer(s -> openGround(s.overworld(), stall, 4, 10));
        Direction side = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            UUID mira = UUID.nameUUIDFromBytes("mira".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Direction facing = Direction.Plane.HORIZONTAL.stream()
                .filter(d -> level.isEmptyBlock(at.relative(d)) && level.getBlockState(at.relative(d).below()).isSolid()
                    && level.isEmptyBlock(at.relative(d, 2)) && level.isEmptyBlock(at.relative(d, 2).above()))
                .findFirst().orElse(Direction.NORTH);
            level.setBlockAndUpdate(at, TraderyBlocks.MARKET_COUNTER.get().defaultBlockState().setValue(MarketCounterBlock.FACING, facing));
            MarketCounterBlockEntity counter = (MarketCounterBlockEntity) level.getBlockEntity(at);
            counter.setOwner(AccountId.player(mira), "Mira");
            VendorsData.get(s).add(level, at, AccountId.player(mira), true);
            var currency = EconomyService.INSTANCE.defaultCurrency();
            java.util.function.Function<String, Long> money = v -> currency.toMinor(new java.math.BigDecimal(v));
            counter.setSettings(new CounterSettings(List.of(
                new CounterOffer(new ItemStack(Items.BREAD, 4), money.apply("2.40")),
                new CounterOffer(new ItemStack(Items.APPLE, 8), money.apply("3.00")),
                new CounterOffer(new ItemStack(Items.COOKED_BEEF, 4), money.apply("5.00")),
                new CounterOffer(new ItemStack(Items.CARROT, 16), money.apply("1.60")),
                new CounterOffer(new ItemStack(Items.CAKE, 1), money.apply("4.50")),
                new CounterOffer(new ItemStack(Items.GOLDEN_CARROT, 4), money.apply("12.00"))), true));
            Item[] goods = {Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.CARROT, Items.CAKE, Items.GOLDEN_CARROT};
            for (int i = 0; i < goods.length; i++) {
                counter.stock().setItem(i, new ItemStack(goods[i], goods[i] == Items.CAKE ? 1 : 64));
            }
            counter.stock().setChanged();
            return facing;
        });
        lookFrom(context, server, at, side, 2.4, 0.4, 0.0);
        context.waitTicks(30);
        shot(context, "counter_block");
        // From above, without the HUD (no hint over it): the offers on both shelves
        setHudHidden(context, true);
        lookFrom(context, server, at, side, 2.2, 1.4, 0.6);
        context.waitTicks(20);
        shot(context, "counter_top");
        setHudHidden(context, false);
        server.runOnServer(s -> CounterMenus.openBuyer(player(s), (MarketCounterBlockEntity) s.overworld().getBlockEntity(at)));
        context.waitForScreen(CounterBuyerScreen.class);
        context.waitTicks(5);
        shot(context, "counter_buyer");
        context.runOnClient(mc -> mc.player.closeContainer());
        server.runOnServer(s -> CounterMenus.openOwner(player(s), (MarketCounterBlockEntity) s.overworld().getBlockEntity(at), true));
        context.waitForScreen(CounterOwnerScreen.class);
        context.waitTicks(5);
        shot(context, "counter_owner");
        context.runOnClient(mc -> mc.player.closeContainer());

        // The player's own shops: this counter and an empty vending block
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            ServerPlayer p = player(s);
            MarketCounterBlockEntity counter = (MarketCounterBlockEntity) level.getBlockEntity(at);
            counter.setOwner(AccountId.player(p.getUUID()), p.nameAndId().name());
            VendorsData.get(s).add(level, at, AccountId.player(p.getUUID()), true);
            BlockPos empty = openGround(level, at, 3, 8);
            level.setBlockAndUpdate(empty, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState());
            VendingBlockEntity vendor = vendor(s, empty);
            vendor.setOwner(AccountId.player(p.getUUID()), p.nameAndId().name());
            VendorsData.get(s).add(level, empty, AccountId.player(p.getUUID()), true);
            vendor.setSettings(new VendingSettings(new ItemStack(Items.TORCH, 16), PriceMode.CURRENCY, 150, ItemStack.EMPTY, false,
                DisplayAnimation.SPIN_BOB));
            ShopLedger.open(p);
        });
        context.waitForScreen(ShopLedgerScreen.class);
        context.waitTicks(5);
        shot(context, "shop_ledger");
        context.runOnClient(mc -> mc.player.closeContainer());
    }

    /** A village board on a stall near {@code stall}, or one placed next to the stall. */
    private static BlockPos boardNear(MinecraftServer server, BlockPos stall, Direction facing) {
        ServerLevel level = server.overworld();
        for (BlockPos pos : BlockPos.betweenClosed(stall.offset(-12, -4, -12), stall.offset(12, 6, 12))) {
            if (level.getBlockState(pos).is(TraderyBlocks.VILLAGE_BOARD.get())) {
                return pos.immutable();
            }
        }
        BlockPos pos = stall.relative(facing, 2).relative(facing.getClockWise(), 3);
        pos = new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
        level.setBlockAndUpdate(pos, TraderyBlocks.VILLAGE_BOARD.get().defaultBlockState().setValue(VillageBoardBlock.FACING, facing));
        return pos;
    }

    /**
     * The nearest spot {@code min}..{@code max} blocks (horizontally) from {@code center} where a player can stand in the
     * open: solid ground, two blocks of air, sky above; falls back to {@code center} raised above the ground.
     */
    private static BlockPos openGround(ServerLevel level, BlockPos center, int min, int max) {
        for (int r = min; r <= max; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (Math.abs(y - center.getY()) <= 3 && level.getBlockState(feet.below()).isSolid()
                        && level.getFluidState(feet.below()).isEmpty() && level.isEmptyBlock(feet) && level.isEmptyBlock(feet.above())) {
                        return feet;
                    }
                }
            }
        }
        return new BlockPos(center.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()), center.getZ());
    }

    /** Stands the player at {@code feet} looking at {@code target}. */
    private static void lookAt(ClientGameTestContext context, TestServerContext server, BlockPos feet, Vec3 target) {
        double x = feet.getX() + 0.5;
        double y = feet.getY();
        double z = feet.getZ() + 0.5;
        double dx = target.x - x;
        double dy = target.y - (y + 1.62);
        double dz = target.z - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitTicks(5);
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.setYHeadRot(yaw);
        });
        context.waitTicks(5);
    }

    /** A desert village of one villager 48 blocks away, made a Town, sends a caravan that arrives at {@code at} now. */
    private static int caravan(MinecraftServer server, BlockPos bell, BlockPos at) {
        ServerLevel level = server.overworld();
        BlockPos home = bell.offset(48, 0, 0);
        home = new BlockPos(home.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ()), home.getZ());
        Villager settler = EntityTypes.VILLAGER.spawn(level, home, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        settler.setVillagerData(settler.getVillagerData().withType(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerType.DESERT)
            .withProfession(level.registryAccess(), VillagerProfession.MASON).withLevel(3));
        settler.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT,
            net.minecraft.core.GlobalPos.of(level.dimension(), home.above(3)));
        Villages.census(level);
        VillageState origin = Villages.at(server, level.dimension(), home.above(3));
        var thresholds = TraderyConfig.server().villages().levels().thresholds();
        Villages.set(server, origin, thresholds.get(Math.min(2, thresholds.size() - 1)) + 300,
            EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(500)));
        VillageState destination = Villages.at(server, level.dimension(), bell);
        var sent = Caravans.send(server, origin, destination);
        boolean arrived = Caravans.arrive(level, sent, at);
        settler.discard();
        var trader = level.getEntitiesOfClass(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader.class,
            new AABB(at).inflate(8), t -> Caravans.of(t) != null).stream().findFirst().orElse(null);
        System.out.println("Tradery market shots: caravan from " + Villages.displayName(origin).getString() + " arrived " + arrived
            + ", trader " + trader);
        return trader == null ? -1 : trader.getId();
    }

    /** Clicks a tab of the village board (0 Orders, 1 Village, 2 Market; layout as in {@link VillageBoardScreen}). */
    private static void clickBoardTab(ClientGameTestContext context, int tab) {
        double[] pos = context.computeOnClient(mc -> {
            var window = mc.getWindow();
            int left = (window.getGuiScaledWidth() - 300) / 2;
            int top = (window.getGuiScaledHeight() - 208) / 2;
            int tabW = (284 - 4) / 3;
            double guiX = left + 8 + tab * (tabW + 2) + tabW / 2.0;
            double guiY = top + 18 + 7;
            double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
            double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
            return new double[] {guiX * scaleX, guiY * scaleY};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // SDL numbering in 26.3: left is 1
        context.waitTicks(3);
        context.getInput().setCursorPos(pos[0] / 4, pos[1] / 4);
        context.waitTicks(2);
    }

    /** Clicks a row of the trade list (rows 0..6; the window is centred, layout as in {@link VillagerTradeScreen}). */
    /** Screen coordinates of a point of the board window (300×208), relative to its top-left corner. */
    private static double[] boardPoint(ClientGameTestContext context, double guiXOffset, double guiYOffset) {
        return context.computeOnClient(mc -> {
            var window = mc.getWindow();
            int left = (window.getGuiScaledWidth() - 300) / 2;
            int top = (window.getGuiScaledHeight() - 208) / 2;
            double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
            double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
            return new double[] {(left + guiXOffset) * scaleX, (top + guiYOffset) * scaleY};
        });
    }

    private static void clickBoard(ClientGameTestContext context, double guiXOffset, double guiYOffset) {
        double[] pos = boardPoint(context, guiXOffset, guiYOffset);
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // SDL numbering in 26.3: left is 1
        context.waitTicks(3);
    }

    /** Leaves the cursor over a point of the board (a tooltip in the shot). */
    private static void hoverBoard(ClientGameTestContext context, double guiXOffset, double guiYOffset) {
        double[] pos = boardPoint(context, guiXOffset, guiYOffset);
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTicks(2);
    }

    private static void clickRow(ClientGameTestContext context, int row) {
        double[] pos = context.computeOnClient(mc -> {
            var window = mc.getWindow();
            int left = (window.getGuiScaledWidth() - 306) / 2;
            int top = (window.getGuiScaledHeight() - 176) / 2;
            double guiX = left + 6 + 30;
            double guiY = top + 18 + row * 20 + 10;
            double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
            double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
            return new double[] {guiX * scaleX, guiY * scaleY};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // SDL numbering in 26.3: left is 1
        context.waitTicks(2);
        // Away from the list, so no row hover tint and no tooltip in the shot
        context.getInput().setCursorPos(pos[0] / 4, pos[1] / 4);
    }

    // ------------------------------------------------------------------ world

    private static BlockPos findVillage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var plains = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.VILLAGE_PLAINS);
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(plains),
            player(server).blockPosition(), 100, false);
        if (found == null) {
            throw new AssertionError("no plains village");
        }
        return found.getFirst();
    }

    /** The server-owned vending block nearest to the village centre, in the loaded chunks around it. */
    private static BlockPos findMarketVendor(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        ChunkPos middle = ChunkPos.containing(center);
        for (int cx = middle.x() - 6; cx <= middle.x() + 6; cx++) {
            for (int cz = middle.z() - 6; cz <= middle.z() + 6; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof VendingBlockEntity vendor && vendor.owner() instanceof AccountId.System) {
                        double d = be.getBlockPos().distSqr(center);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = be.getBlockPos();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** A bread stall of the player's on open ground a few blocks along the street from the market stall. */
    private static BlockPos placeShop(MinecraftServer server, BlockPos stall, Direction facing) {
        ServerLevel level = server.overworld();
        Direction side = facing.getClockWise();
        BlockPos pos = null;
        for (int step = 6; step <= 20 && pos == null; step++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos column = stall.relative(side, step * sign).relative(facing, 1);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
                BlockPos front = candidate.relative(facing);
                if (level.getBlockState(candidate).isAir() && level.getBlockState(front).isAir()
                    && level.getBlockState(front.above()).isAir() && !level.getBlockState(front.below()).isAir()) {
                    pos = candidate;
                    break;
                }
            }
        }
        if (pos == null) {
            throw new AssertionError("no free spot for the player's stall near " + stall);
        }
        level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, facing));
        VendingBlockEntity vendor = vendor(server, pos);
        ServerPlayer p = player(server);
        vendor.setOwner(AccountId.player(p.getUUID()), p.nameAndId().name());
        // As placing it by hand does (VendingBlock#setPlacedBy): customers are found through the vending index
        dev.eliasnvx.tradery.vending.VendorsData.get(server).add(level, pos, AccountId.player(p.getUUID()), true);
        vendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 2), PriceMode.CURRENCY, 120, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        EconomyService.INSTANCE.account(p.getUUID());
        return pos;
    }

    /** Gives up to {@code count} adult villagers with a home near the shop a composter next to them and the farmer's job. */
    private static int hireFarmers(MinecraftServer server, BlockPos shop, int count) {
        ServerLevel level = server.overworld();
        List<Villager> candidates = new java.util.ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(shop).inflate(32),
            v -> !v.isBaby() && v.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)));
        candidates.sort(java.util.Comparator.comparingDouble(v -> v.blockPosition().distSqr(shop)));
        int hired = 0;
        for (Villager villager : candidates) {
            if (hired >= count) {
                break;
            }
            BlockPos spot = null;
            for (BlockPos offset : BlockPos.betweenClosed(-3, -1, -3, 3, 1, 3)) {
                BlockPos pos = villager.blockPosition().offset(offset);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && pos.distSqr(villager.blockPosition()) > 1) {
                    spot = pos.immutable();
                    break;
                }
            }
            if (spot == null) {
                continue;
            }
            BlockPos site = spot;
            level.setBlockAndUpdate(site, net.minecraft.world.level.block.Blocks.COMPOSTER.defaultBlockState());
            level.getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER), (type, pos) -> pos.equals(site),
                site, 1);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(2));
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
                net.minecraft.core.GlobalPos.of(level.dimension(), site));
            hired++;
        }
        return hired;
    }

    private static Villager customerNear(MinecraftServer server, BlockPos shop) {
        List<Villager> near = server.overworld().getEntitiesOfClass(Villager.class, new AABB(shop).inflate(6),
            v -> VillagerCustomers.isCustomer(v.getUUID()));
        return near.isEmpty() ? null : near.get(0);
    }

    /** Why the villagers around may not shop: memories, activity and state. */
    private static String describeVillagers(MinecraftServer server, BlockPos shop) {
        StringBuilder out = new StringBuilder();
        for (Villager v : server.overworld().getEntitiesOfClass(Villager.class, new AABB(shop).inflate(48))) {
            var brain = v.getBrain();
            out.append(String.format(Locale.ROOT, "[%s d=%.0f baby=%s sleep=%s act=%s home=%s job=%s eligible=%s] ",
                v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"),
                Math.sqrt(v.blockPosition().distSqr(shop)), v.isBaby(), v.isSleeping(),
                brain.getActiveNonCoreActivity().map(Object::toString).orElse("-"),
                brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME),
                brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE),
                VillagerCustomers.isEligible(v)));
        }
        return out.toString();
    }

    /** Polls a server-side condition from the test thread (predicates of waitFor run on the client thread). */
    private static int waitOnServer(ClientGameTestContext context, TestServerContext server,
                                    java.util.function.Predicate<MinecraftServer> condition, int timeoutTicks) {
        for (int waited = 0; waited < timeoutTicks; waited += 10) {
            if (server.computeOnServer(condition::test)) {
                return waited;
            }
            context.waitTicks(10);
        }
        throw new AssertionError("timed out after " + timeoutTicks + " ticks");
    }

    // ------------------------------------------------------------------ camera

    /**
     * Eyes {@code distance} blocks out from the vending block's front (its facing side), {@code up} above it and
     * {@code side} along it, looking at the block.
     */
    private static void lookFrom(ClientGameTestContext context, TestServerContext server, BlockPos target, Direction facing,
                                 double distance, double up, double side) {
        Direction along = facing.getClockWise();
        double x = target.getX() + 0.5 + facing.getStepX() * distance + along.getStepX() * side;
        double z = target.getZ() + 0.5 + facing.getStepZ() * distance + along.getStepZ() * side;
        double y = target.getY() + up;
        double dx = target.getX() + 0.5 - x;
        double dy = target.getY() + 0.5 - (y + 1.62);
        double dz = target.getZ() + 0.5 - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitTicks(5);
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.setYHeadRot(yaw);
        });
        context.waitTicks(5);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.waitTicks(2);
        context.takeScreenshot("market_" + name);
    }

    private static void setHudHidden(ClientGameTestContext context, boolean hidden) {
        context.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != hidden) {
                mc.gui.hud.toggle();
            }
        });
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static VendingBlockEntity vendor(MinecraftServer server, BlockPos pos) {
        return (VendingBlockEntity) server.overworld().getBlockEntity(pos);
    }
}
