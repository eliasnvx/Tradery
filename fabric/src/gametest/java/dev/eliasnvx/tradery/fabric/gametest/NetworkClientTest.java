package dev.eliasnvx.tradery.fabric.gametest;

import com.mojang.authlib.GameProfile;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * The same flows over a real connection to a dedicated server, so every Tradery payload and the synced block entity
 * data go through their codecs (singleplayer skips serialization). A server-side fake player, Bob, is the second
 * player: he receives a /pay and buys from the test player's vending block, whose owner gets the sale notification.
 * Run by hand: ./gradlew :fabric:runClientGameTest
 */
public final class NetworkClientTest implements FabricClientGameTest {
    private static final UUID SHOPKEEPER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(2);
        });
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestDedicatedServerConnection connection = server.connect()) {
            connection.waitForChunksRender();
            server.runCommand("time set 6000");

            // Joining: currency and balance arrive before the HUD shows anything
            context.waitFor(mc -> ClientEconomy.currency() != null && ClientEconomy.isActive());
            long joined = server.computeOnServer(NetworkClientTest::balance);
            check(context.computeOnClient(mc -> ClientEconomy.balance()) == joined, "HUD balance = server balance after joining");
            String name = server.computeOnServer(s -> player(s).nameAndId().name());
            server.runCommand("eco give " + name + " 50");
            context.waitFor(mc -> ClientEconomy.balance() == joined + 5_000);
            context.waitTicks(5);
            context.takeScreenshot("net_hud_eco_give");

            // /pay to Bob, typed by the client: both balances move, the HUD follows the server
            server.runOnServer(s -> EconomyService.INSTANCE.onPlayerJoin(bob(s)));
            long bobBefore = server.computeOnServer(s -> balanceOf(BOB));
            context.runOnClient(mc -> mc.player.connection.sendCommand("pay Bob 12.50"));
            server.waitFor(s -> balanceOf(BOB) == bobBefore + 1_250);
            long afterPay = server.computeOnServer(NetworkClientTest::balance);
            check(afterPay < joined + 5_000 - 1_250 + 1, "the payer paid at least 12.50");
            context.waitFor(mc -> ClientEconomy.balance() == afterPay);

            // Vending blocks over the network
            BlockPos origin = server.computeOnServer(s -> player(s).blockPosition());
            BlockPos bread = origin.offset(-1, 0, 3);
            BlockPos own = origin.offset(2, 0, 3);
            server.runOnServer(s -> setUp(s, bread, own));
            aim(context, server, bread);
            context.takeScreenshot("net_hint");

            // Quick buy: the C2S quick trade payload and the action bar with the coin glyph
            int breadBefore = server.computeOnServer(s -> count(s, Items.BREAD));
            long moneyBefore = server.computeOnServer(NetworkClientTest::balance);
            context.getInput().holdShift();
            context.waitTicks(2);
            context.getInput().holdKeyFor(options -> options.keyUse, 18);
            context.waitTicks(4);
            context.takeScreenshot("net_quick_buy");
            context.getInput().releaseShift();
            int bought = server.computeOnServer(s -> count(s, Items.BREAD)) - breadBefore;
            long paid = moneyBefore - server.computeOnServer(NetworkClientTest::balance);
            check(bought >= 8 && bought % 4 == 0 && paid == bought / 4 * 250L, "quick buy over the network: " + bought + " bread, paid " + paid);
            context.waitFor(mc -> ClientEconomy.balance() == moneyBefore - paid);

            // The buyer screen: menu data slots, a button click, the result payload back
            server.runOnServer(s -> VendingMenus.openBuyer(player(s), vendor(s, bread)));
            context.waitForScreen(VendingBuyerScreen.class);
            int breadBeforeScreen = server.computeOnServer(s -> count(s, Items.BREAD));
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, VendingBuyerMenu.BUY_EIGHT));
            server.waitFor(s -> count(s, Items.BREAD) == breadBeforeScreen + 32);
            context.waitTicks(5);
            context.takeScreenshot("net_buyer_screen");
            context.runOnClient(mc -> mc.player.closeContainer());

            // Bob buys from the player's own block: the owner's sale notification arrives in chat
            boolean sold = server.computeOnServer(s -> VendingTrades.execute(bob(s), vendor(s, own), 1).success());
            check(sold, "Bob bought from the test player's block");
            context.waitFor(mc -> chatContains(mc, "Bob"));
            aim(context, server, own);
            context.takeScreenshot("net_sale_notification");
        }
    }

    private static void setUp(MinecraftServer server, BlockPos bread, BlockPos own) {
        ServerLevel level = server.overworld();
        level.setBlockAndUpdate(bread, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity breadVendor = vendor(server, bread);
        breadVendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
        breadVendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB));
        breadVendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        breadVendor.stock().setItem(1, new ItemStack(Items.BREAD, 64));
        breadVendor.stock().setChanged();

        ServerPlayer player = player(server);
        level.setBlockAndUpdate(own, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity ownVendor = vendor(server, own);
        ownVendor.setOwner(AccountId.player(player.getUUID()), player.nameAndId().name());
        ownVendor.setSettings(new VendingSettings(new ItemStack(Items.APPLE, 2), PriceMode.CURRENCY, 100, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN));
        ownVendor.stock().setItem(0, new ItemStack(Items.APPLE, 16));
        ownVendor.stock().setChanged();
    }

    private static void aim(ClientGameTestContext context, TestDedicatedServerContext server, BlockPos pos) {
        server.runCommand("tp @a " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() - 1.6) + " 0 25");
        context.waitTicks(10);
        context.runOnClient(mc -> {
            mc.player.setYRot(0);
            mc.player.setXRot(25);
        });
        context.waitTicks(10);
    }

    private static FakePlayer bob(MinecraftServer server) {
        return FakePlayer.get(server.overworld(), new GameProfile(BOB, "Bob"));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static VendingBlockEntity vendor(MinecraftServer server, BlockPos pos) {
        return (VendingBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private static long balance(MinecraftServer server) {
        return balanceOf(player(server).getUUID());
    }

    private static long balanceOf(UUID uuid) {
        return EconomyService.INSTANCE.account(uuid).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static int count(MinecraftServer server, net.minecraft.world.item.Item item) {
        return player(server).getInventory().countItem(item);
    }

    @SuppressWarnings("unchecked")
    private static boolean chatContains(Minecraft minecraft, String text) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<GuiMessage> messages = (List<GuiMessage>) field.get(minecraft.gui.hud.getChat());
            return messages.stream().anyMatch(message -> message.content().getString().contains(text));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("can't read the chat", e);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
