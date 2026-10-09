package dev.eliasnvx.tradery.neoforge.clienttest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
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
 * Dev-only NeoForge client check (NeoForge has no client test framework): creates a flat world, sets up vending
 * blocks, holds the real key mappings through the normal client tick (sneak + use, sneak + attack) and checks items,
 * money and that nothing was placed or broken. Screenshots go to {@code build/run/clientTest/screenshots}; the result
 * to {@code build/run/clientTest/tradery-client-test.txt}. A failure halts the game with exit code 1.
 *
 * <p>Run: {@code ./gradlew :neoforge:runClientTest}
 */
@Mod(value = TraderyClientTest.MOD_ID, dist = Dist.CLIENT)
public final class TraderyClientTest {
    static final String MOD_ID = "tradery_client_test";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID SHOPKEEPER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final int TIMEOUT_TICKS = 20 * 60;
    private static final AtomicInteger TICKS = new AtomicInteger();
    private static volatile boolean started;

    /** Set when the test starts: the mod is constructed before the game instance exists. */
    private Minecraft minecraft;
    private final List<String> notes = new ArrayList<>();

    public TraderyClientTest() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            TICKS.incrementAndGet();
            // Start once loading is over, whatever screen is up (title, onboarding, loading warnings)
            Minecraft minecraft = Minecraft.getInstance();
            if (!started && minecraft.isGameLoadFinished() && minecraft.screen != null) {
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
        return server.getPlayerList().getPlayers().getFirst();
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
