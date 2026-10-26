package dev.eliasnvx.tradery.forge.clienttest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.CounterBuyerScreen;
import dev.eliasnvx.tradery.client.screen.CounterOwnerScreen;
import dev.eliasnvx.tradery.client.screen.ShopLedgerScreen;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.client.screen.VillageBoardScreen;
import dev.eliasnvx.tradery.client.screen.VillagerTradeScreen;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.CounterMenus;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.MarketCounterBlock;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.ShopLedger;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.VillageBoardBlock;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Dev-only Forge client check (Forge has no client test framework): creates a flat world, sets up vending
 * blocks, holds the real key mappings through the normal client tick (sneak + use, sneak + attack) and checks items,
 * money and that nothing was placed or broken. Screenshots go to {@code build/run/clientTest/screenshots}; the result
 * to {@code build/run/clientTest/tradery-client-test.txt}. A failure halts the game with exit code 1.
 *
 * <p>Run: {@code ./gradlew :forge:runClientTest}. Started by {@link TraderyTestMod} on the physical client.
 */
final class TraderyClientTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID SHOPKEEPER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final int TIMEOUT_TICKS = 20 * 60;
    private static final AtomicInteger TICKS = new AtomicInteger();
    private static volatile boolean started;

    /** Set when the test starts: the mod is constructed before the game instance exists. */
    private Minecraft minecraft;
    private final List<String> notes = new ArrayList<>();

    TraderyClientTest() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            TICKS.incrementAndGet();
            // Start once loading is over (the loading overlay is gone), whatever screen is up (title, onboarding, warnings)
            Minecraft minecraft = Minecraft.getInstance();
            if (!started && minecraft.getOverlay() == null && minecraft.screen != null) {
                started = true;
                Thread thread = new Thread(this::runAndExit, "tradery-client-test");
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    private void runAndExit() {
        minecraft = Minecraft.getInstance();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000L);
                finish(false, "timed out after 5 minutes");
            } catch (InterruptedException ignored) {
            }
        }, "tradery-client-test-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            run();
            finish(true, "all checks passed");
        } catch (Throwable t) {
            LOGGER.error("Tradery client test failed", t);
            finish(false, t.toString());
        }
    }

    private void finish(boolean passed, String summary) {
        String report = (passed ? "PASS: " : "FAIL: ") + summary + "\n" + String.join("\n", notes) + "\n";
        LOGGER.info("Tradery client test {}", report);
        try {
            Files.writeString(Path.of("tradery-client-test.txt"), report);
        } catch (Exception e) {
            LOGGER.error("Can't write the report", e);
        }
        if (passed) {
            minecraft.execute(minecraft::stop);
        } else {
            Runtime.getRuntime().halt(1);
        }
    }

    // ------------------------------------------------------------------ the scenario

    private void run() {
        createWorld();
        BlockPos origin = server(server -> player(server).blockPosition());
        BlockPos bread = origin.offset(-1, 0, 3);
        BlockPos cobble = origin.offset(-4, 0, 3);
        BlockPos own = origin.offset(3, 0, 3);
        server(server -> {
            setUp(server, bread, cobble, own);
            return null;
        });

        // The hint, looking at the bread vendor
        aimAt(bread);
        screenshot("hint");

        // A plain click opens the buyer screen
        openScreen("buyer", VendingBuyerScreen.class);

        // Sneak + hold use with a block in hand: buys a lot every 4 ticks, never places the block
        command("item replace entity @p hotbar.0 with minecraft:cobblestone 16");
        client(() -> {
            minecraft.player.getInventory().selected = 0;
        });
        waitTicks(5);
        long balanceBefore = server(this::balance);
        int breadBefore = server(server -> count(server, Items.BREAD));
        hold(List.of(minecraft.options.keyShift, minecraft.options.keyUse), 18);
        int bought = server(server -> count(server, Items.BREAD)) - breadBefore;
        long paid = balanceBefore - server(this::balance);
        check(bought >= 8 && bought % 4 == 0, "quick buy: bought " + bought + " bread");
        check(paid == bought / 4 * 250L, "quick buy: paid " + paid + " for " + bought + " bread");
        check(server(server -> count(server, Items.COBBLESTONE)) == 16, "the cobblestone in hand was not placed");
        check(server(server -> cobblestoneAround(server, bread)) == 0, "no cobblestone block next to the vendor");
        notes.add("quick buy (held sneak + use, block in hand): " + bought + " bread for " + paid + " minor units, nothing placed");
        screenshot("quick_buy");

        // Sneak + attack on a selling block: refused, nothing bought, the block stays
        int breadBeforeWrong = server(server -> count(server, Items.BREAD));
        grabMouse();
        hold(List.of(minecraft.options.keyShift, minecraft.options.keyAttack), 6);
        check(server(server -> count(server, Items.BREAD)) == breadBeforeWrong, "sneak + attack on a selling block buys nothing");
        check(server(server -> server.overworld().getBlockEntity(bread) instanceof VendingBlockEntity), "the selling block is still there");
        screenshot("wrong_button");

        // Sneak + hold attack on the buyback vendor: sells a lot every 4 ticks
        aimAt(cobble);
        command("give @p minecraft:cobblestone 32");
        waitTicks(5);
        int cobbleBefore = server(server -> count(server, Items.COBBLESTONE));
        boolean grabbed = grabMouse();
        if (grabbed) {
            hold(List.of(minecraft.options.keyShift, minecraft.options.keyAttack), 14);
        } else {
            // Without a grabbed mouse vanilla doesn't continue an attack; press it instead
            notes.add("window not focused: sneak + attack pressed 4 times instead of held");
            client(() -> minecraft.options.keyShift.setDown(true));
            // The player sneaks one tick after the key goes down (keybinds run before the player tick)
            waitTicks(2);
            for (int i = 0; i < 4; i++) {
                client(() -> KeyMapping.click(InputConstants.getKey(minecraft.options.keyAttack.saveString())));
                waitTicks(5);
            }
            client(() -> minecraft.options.keyShift.setDown(false));
            waitTicks(4);
        }
        int sold = cobbleBefore - server(server -> count(server, Items.COBBLESTONE));
        check(sold >= 16 && sold % 8 == 0, "quick sell: sold " + sold + " cobblestone");
        check(server(server -> server.overworld().getBlockEntity(cobble) instanceof VendingBlockEntity), "the buyback block is still there");
        notes.add("quick sell (" + (grabbed ? "held" : "pressed") + " sneak + attack): " + sold + " cobblestone");
        screenshot("quick_sell");

        // The owner's own block: sneak + attack stays vanilla, so the owner breaks it in creative
        aimAt(own);
        screenshot("hint_owner");
        openScreen("owner", VendingOwnerScreen.class);
        command("gamemode creative @p");
        waitTicks(5);
        client(() -> minecraft.options.keyShift.setDown(true));
        waitTicks(2);
        client(() -> KeyMapping.click(InputConstants.getKey(minecraft.options.keyAttack.saveString())));
        waitTicks(8);
        client(() -> minecraft.options.keyShift.setDown(false));
        check(server(server -> server.overworld().getBlockState(own).isAir()), "the owner's sneak + attack breaks the owner's block");
        notes.add("owner: sneak + attack broke the own block (not intercepted)");
        command("gamemode survival @p");
        aimAt(bread);
        screenshot("end");

        livingMarket(origin);
    }

    // ------------------------------------------------------------------ Living Market (1.1)

    /**
     * The 1.1 screens and renderers on a 1.21.1 client: a village around a bell (title on entering, the board's three
     * tabs), a villager's trade window priced in money, a market counter (its shelves, buyer and owner windows) and the
     * shop ledger. Each opens for real through the server and is screenshotted.
     */
    private void livingMarket(BlockPos origin) {
        BlockPos bell = origin.offset(0, 0, 12);
        BlockPos board = bell.offset(3, 0, 0);
        BlockPos counter = bell.offset(-4, 0, 0);
        BlockPos villagerAt = bell.offset(0, 0, 4);
        command("tp @p " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() - 30.5) + " 0 0");
        waitTicks(5);
        server(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
            for (int i = 0; i < 4; i++) {
                Villager villager = EntityType.VILLAGER.create(level);
                villager.moveTo(bell.getX() - 2 + i + 0.5, bell.getY(), bell.getZ() + 2.5, 180, 0);
                villager.setNoAi(true);
                villager.setVillagerData(villager.getVillagerData()
                    .setProfession(i == 3 ? VillagerProfession.LIBRARIAN : VillagerProfession.FARMER).setLevel(2));
                level.addFreshEntity(villager);
            }
            Villager customer = EntityType.VILLAGER.create(level);
            customer.moveTo(villagerAt.getX() + 0.5, villagerAt.getY(), villagerAt.getZ() + 0.5, 180, 0);
            customer.setNoAi(true);
            customer.setVillagerData(customer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
            level.addFreshEntity(customer);
            Villages.census(level);
            VillageState village = Villages.at(server, level.dimension(), bell);
            Villages.set(server, village, TraderyConfig.server().villages().levels().thresholds().get(2) + 5, 4_000);
            level.setBlockAndUpdate(board, TraderyBlocks.VILLAGE_BOARD.get().defaultBlockState().setValue(VillageBoardBlock.FACING, Direction.NORTH));
            EconomyService.INSTANCE.deposit(EconomyService.INSTANCE.account(player(server).getUUID()), 50_000,
                Reason.of(Reasons.ADMIN_GIVE, "client test"));
            return null;
        });

        // Walking into the village shows its name and level
        command("tp @p " + (bell.getX() + 0.5) + " " + bell.getY() + " " + (bell.getZ() - 6.5) + " 0 10");
        waitTicks(50); // the presence check runs every 40 ticks
        screenshot("village_title");
        notes.add("village title shown on entering");

        // The board: Orders, Village, Market
        aimAt(board);
        client(() -> KeyMapping.click(InputConstants.getKey(minecraft.options.keyUse.saveString())));
        waitFor("board screen", mc -> mc.screen instanceof VillageBoardScreen, TIMEOUT_TICKS);
        waitTicks(10);
        screenshot("board_orders");
        for (int tab = 1; tab <= 2; tab++) {
            int index = tab;
            client(() -> {
                var screen = minecraft.screen;
                int left = (screen.width - 300) / 2;
                int top = (screen.height - 208) / 2;
                int tabW = (284 - 4) / 3;
                screen.mouseClicked(left + 8 + index * (tabW + 2) + tabW / 2.0, top + 18 + 7, InputConstants.MOUSE_BUTTON_LEFT);
            });
            waitTicks(5);
            screenshot(tab == 1 ? "board_village" : "board_market");
        }
        client(() -> minecraft.player.closeContainer());
        waitFor("board closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        notes.add("village board: three tabs");

        // A villager's own trades, priced in money
        command("tp @p " + (villagerAt.getX() + 0.5) + " " + villagerAt.getY() + " " + (villagerAt.getZ() - 1.5) + " 0 20");
        waitTicks(5);
        server(server -> {
            ServerLevel level = server.overworld();
            Villager customer = level.getEntitiesOfClass(Villager.class, new AABB(villagerAt).inflate(1)).get(0);
            customer.mobInteract(player(server), InteractionHand.MAIN_HAND);
            return null;
        });
        waitFor("villager trade screen", mc -> mc.screen instanceof VillagerTradeScreen, TIMEOUT_TICKS);
        waitTicks(10);
        client(() -> {
            var screen = minecraft.screen;
            int left = (screen.width - 306) / 2;
            int top = (screen.height - 176) / 2;
            screen.mouseClicked(left + 36, top + 18 + 10, InputConstants.MOUSE_BUTTON_LEFT);
        });
        waitTicks(3);
        screenshot("villager_trade");
        client(() -> minecraft.player.closeContainer());
        waitFor("trade closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        notes.add("villager trade window opened");

        // A market counter: the goods on its shelves, the buyer's and the owner's windows
        server(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(counter, TraderyBlocks.MARKET_COUNTER.get().defaultBlockState().setValue(MarketCounterBlock.FACING, Direction.NORTH));
            MarketCounterBlockEntity entity = (MarketCounterBlockEntity) level.getBlockEntity(counter);
            entity.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
            entity.setSettings(new CounterSettings(List.of(
                new CounterOffer(new ItemStack(Items.BREAD, 4), 240),
                new CounterOffer(new ItemStack(Items.APPLE, 8), 300),
                new CounterOffer(new ItemStack(Items.COOKED_BEEF, 4), 500),
                new CounterOffer(new ItemStack(Items.CARROT, 16), 160),
                new CounterOffer(new ItemStack(Items.CAKE, 1), 450),
                new CounterOffer(new ItemStack(Items.GOLDEN_CARROT, 4), 1200)), true));
            net.minecraft.world.item.Item[] goods = {Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.CARROT, Items.CAKE, Items.GOLDEN_CARROT};
            for (int i = 0; i < goods.length; i++) {
                entity.stock().setItem(i, new ItemStack(goods[i], goods[i] == Items.CAKE ? 1 : 64));
            }
            entity.stock().setChanged();
            return null;
        });
        aimAt(counter);
        waitTicks(10);
        screenshot("counter_block");
        // From above without the HUD (no hint over it): the goods on both shelves
        client(() -> minecraft.options.hideGui = true);
        command("tp @p " + (counter.getX() + 0.5) + " " + (counter.getY() + 1) + " " + (counter.getZ() - 1.8) + " 0 40");
        waitTicks(10);
        screenshot("counter_top");
        client(() -> minecraft.options.hideGui = false);
        aimAt(counter);
        server(server -> {
            CounterMenus.openBuyer(player(server), (MarketCounterBlockEntity) server.overworld().getBlockEntity(counter));
            return null;
        });
        waitFor("counter buyer screen", mc -> mc.screen instanceof CounterBuyerScreen, TIMEOUT_TICKS);
        waitTicks(5);
        screenshot("counter_buyer");
        client(() -> minecraft.player.closeContainer());
        waitFor("buyer closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        // The player's own counter: the owner's window (the test player isn't an admin), then the shop ledger
        server(server -> {
            ServerLevel level = server.overworld();
            ServerPlayer p = player(server);
            MarketCounterBlockEntity entity = (MarketCounterBlockEntity) level.getBlockEntity(counter);
            entity.setOwner(AccountId.player(p.getUUID()), p.getGameProfile().getName());
            VendorsData.get(server).add(level, counter, AccountId.player(p.getUUID()), true);
            CounterMenus.openOwner(p, entity, false);
            return null;
        });
        waitFor("counter owner screen", mc -> mc.screen instanceof CounterOwnerScreen, TIMEOUT_TICKS);
        waitTicks(5);
        check(client(() -> minecraft.screen instanceof CounterOwnerScreen), "the owner's window stays open");
        screenshot("counter_owner");
        client(() -> minecraft.player.closeContainer());
        waitFor("owner closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        notes.add("market counter: block, buyer and owner windows");

        server(server -> {
            ShopLedger.open(player(server));
            return null;
        });
        waitFor("shop ledger screen", mc -> mc.screen instanceof ShopLedgerScreen, TIMEOUT_TICKS);
        waitTicks(5);
        screenshot("shop_ledger");
        client(() -> minecraft.player.closeContainer());
        waitFor("ledger closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        notes.add("shop ledger opened");
    }

    private void setUp(MinecraftServer server, BlockPos bread, BlockPos cobble, BlockPos own) {
        ServerLevel level = server.overworld();
        VendingBlockEntity breadVendor = place(level, bread);
        breadVendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
        breadVendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB));
        breadVendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        breadVendor.stock().setChanged();

        VendingBlockEntity cobbleVendor = place(level, cobble);
        cobbleVendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
        cobbleVendor.setSettings(new VendingSettings(new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true,
            DisplayAnimation.STATIC));
        EconomyService.INSTANCE.deposit(EconomyService.INSTANCE.account(SHOPKEEPER), 10_000, Reason.of(Reasons.ADMIN_GIVE, "client test"));

        ServerPlayer player = player(server);
        VendingBlockEntity ownVendor = place(level, own);
        ownVendor.setOwner(AccountId.player(player.getUUID()), player.getGameProfile().getName());
        ownVendor.setSettings(new VendingSettings(new ItemStack(Items.APPLE, 2), PriceMode.CURRENCY, 100, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN));
        player.getInventory().clearContent();
    }

    private static VendingBlockEntity place(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        return (VendingBlockEntity) level.getBlockEntity(pos);
    }

    // ------------------------------------------------------------------ world

    private void createWorld() {
        client(() -> {
            minecraft.options.onboardAccessibility = false;
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.tutorialStep = TutorialSteps.NONE;
            minecraft.options.guiScale().set(2);
            CreateWorldScreen.openFresh(minecraft, new TitleScreen());
        });
        waitFor("create world screen", mc -> mc.screen instanceof CreateWorldScreen, TIMEOUT_TICKS);
        client(() -> {
            CreateWorldScreen screen = (CreateWorldScreen) minecraft.screen;
            WorldCreationUiState ui = screen.getUiState();
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(
                ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)));
            ui.setSeed("1");
            ui.setGenerateStructures(false);
            ui.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            ui.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            ui.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            String create = Component.translatable("selectWorld.create").getString();
            boolean pressed = false;
            for (var child : screen.children()) {
                if (child instanceof Button button && create.equals(button.getMessage().getString())) {
                    button.onPress();
                    pressed = true;
                    break;
                }
            }
            check(pressed, "found the Create World button");
        });
        waitFor("world loaded", mc -> mc.level != null && mc.player != null && mc.screen == null
            && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().getPlayerList().getPlayers().isEmpty(), TIMEOUT_TICKS);
        command("time set 6000");
        waitTicks(60);
    }

    /** Stand 1.6 blocks south of {@code pos}, facing it from slightly above (as a player does at a shop). */
    private void aimAt(BlockPos pos) {
        command("tp @p " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() - 1.6) + " 0 25");
        waitTicks(5);
        client(() -> {
            minecraft.player.setYRot(0);
            minecraft.player.setXRot(25);
        });
        waitTicks(10);
        check(client(() -> minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
            && hit.getBlockPos().equals(pos)), "the crosshair is on the block at " + pos);
    }

    /** Clicks use on the block under the crosshair, waits for the screen, takes a screenshot and closes it. */
    private void openScreen(String name, Class<?> screen) {
        client(() -> KeyMapping.click(InputConstants.getKey(minecraft.options.keyUse.saveString())));
        waitFor(name + " screen", mc -> screen.isInstance(mc.screen), TIMEOUT_TICKS);
        waitTicks(10);
        screenshot("screen_" + name);
        client(() -> minecraft.player.closeContainer());
        waitFor(name + " screen closed", mc -> mc.screen == null, TIMEOUT_TICKS);
        notes.add("plain use opened the " + name + " screen");
    }

    // ------------------------------------------------------------------ input

    /** Grabs the mouse like a click into the window; vanilla only continues an attack with a grabbed mouse. */
    private boolean grabMouse() {
        return client(() -> {
            minecraft.mouseHandler.grabMouse();
            return minecraft.mouseHandler.isMouseGrabbed();
        });
    }

    /** Holds the keys through the normal client tick, then lets go (the order they were pressed in, reversed). */
    private void hold(List<KeyMapping> keys, int ticks) {
        for (KeyMapping key : keys) {
            client(() -> key.setDown(true));
            waitTicks(2);
        }
        waitTicks(ticks);
        for (int i = keys.size() - 1; i >= 0; i--) {
            KeyMapping key = keys.get(i);
            client(() -> key.setDown(false));
        }
        waitTicks(6);
    }

    // ------------------------------------------------------------------ helpers

    private ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private long balance(MinecraftServer server) {
        return EconomyService.INSTANCE.account(player(server).getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private int count(MinecraftServer server, net.minecraft.world.item.Item item) {
        return player(server).getInventory().countItem(item);
    }

    private static int cobblestoneAround(MinecraftServer server, BlockPos center) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -3), center.offset(2, 2, 2))) {
            if (server.overworld().getBlockState(pos).is(Blocks.COBBLESTONE)) {
                found++;
            }
        }
        return found;
    }

    private void command(String command) {
        server(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            return null;
        });
    }

    private void screenshot(String name) {
        waitTicks(2);
        client(() -> Screenshot.grab(minecraft.gameDirectory, "tradery_" + name + ".png", minecraft.getMainRenderTarget(), message -> {
        }));
        waitTicks(3);
    }

    private <T> T client(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, minecraft).join();
    }

    private void client(Runnable action) {
        CompletableFuture.runAsync(action, minecraft).join();
    }

    private <T> T server(Function<MinecraftServer, T> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        return CompletableFuture.supplyAsync(() -> action.apply(server), server).join();
    }

    private void waitTicks(int ticks) {
        int target = TICKS.get() + ticks;
        while (TICKS.get() < target) {
            sleep();
        }
    }

    private void waitFor(String what, Predicate<Minecraft> condition, int timeoutTicks) {
        int deadline = TICKS.get() + timeoutTicks;
        while (!client(() -> condition.test(minecraft))) {
            if (TICKS.get() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
