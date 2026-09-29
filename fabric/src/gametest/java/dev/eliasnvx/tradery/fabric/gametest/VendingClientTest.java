package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.DisplayScreen;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.DisplayBlock;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Vending in a real client: blocks with goods, a facade and a display case; the buyer screen with a real purchase;
 * the owner and admin screens. Screenshots ({@code vending_*}) are for a human look; the purchase is asserted.
 * Run by hand: ./gradlew :fabric:runClientGameTest
 */
public final class VendingClientTest implements FabricClientGameTest {
    private static final UUID SHOPKEEPER = UUID.fromString("00000000-0000-0000-0000-00000000beef");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(2);
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set 6000");
            world.getServer().runCommand("gamerule spawn_mobs false");
            world.getServer().runCommand("gamerule advance_time false");

            BlockPos origin = world.getServer().computeOnServer(server -> player(server).blockPosition());
            BlockPos bread = origin.offset(-1, 0, 3);
            BlockPos apple = origin.offset(1, 0, 3);
            BlockPos display = origin.offset(3, 0, 3);
            world.getServer().runOnServer(server -> setUp(server, bread, apple, display));
            world.getServer().runCommand("tp @p " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5)
                + " facing " + (origin.getX() + 1.5) + " " + (origin.getY() + 0.5) + " " + (origin.getZ() + 3.5));
            context.waitTicks(20);
            context.takeScreenshot("vending_world");

            long before = world.getServer().computeOnServer(server -> balance(server));
            world.getServer().runOnServer(server -> VendingMenus.openBuyer(player(server), vendor(server, bread)));
            context.waitForScreen(VendingBuyerScreen.class);
            context.waitTicks(3);
            context.takeScreenshot("vending_buyer");
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, VendingBuyerMenu.BUY_ONE));
            context.waitTicks(5);
            context.takeScreenshot("vending_buyer_after_purchase");
            long after = world.getServer().computeOnServer(server -> balance(server));
            int breadCount = world.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.BREAD));
            if (breadCount != 4 || before - after != 250) {
                throw new AssertionError("purchase: bread " + breadCount + ", paid " + (before - after));
            }
            context.runOnClient(mc -> mc.player.closeContainer());

            world.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                vendor(server, bread).setOwner(AccountId.player(player.getUUID()), player.nameAndId().name());
                VendingMenus.openOwner(player, vendor(server, bread), true);
            });
            context.waitForScreen(VendingOwnerScreen.class);
            context.waitTicks(3);
            context.takeScreenshot("vending_owner_admin");
            context.runOnClient(mc -> mc.player.closeContainer());

            world.getServer().runOnServer(server -> {
                if (server.overworld().getBlockEntity(display) instanceof DisplayBlockEntity case_) {
                    VendingMenus.openDisplay(player(server), case_);
                }
            });
            context.waitForScreen(DisplayScreen.class);
            context.takeScreenshot("vending_display_screen");
            context.runOnClient(mc -> mc.player.closeContainer());
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static long balance(MinecraftServer server) {
        return EconomyService.INSTANCE.account(player(server).getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static VendingBlockEntity vendor(MinecraftServer server, BlockPos pos) {
        return (VendingBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private static void setUp(MinecraftServer server, BlockPos bread, BlockPos apple, BlockPos display) {
        ServerLevel level = server.overworld();
        level.setBlockAndUpdate(bread, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity breadVendor = vendor(server, bread);
        breadVendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
        breadVendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB));
        breadVendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        breadVendor.stock().setChanged();

        level.setBlockAndUpdate(apple, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity appleVendor = vendor(server, apple);
        appleVendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
        appleVendor.setSettings(new VendingSettings(new ItemStack(Items.GOLDEN_APPLE), PriceMode.ITEM, 0, new ItemStack(Items.DIAMOND, 2), false,
            DisplayAnimation.SPIN));
        appleVendor.setFacade(Blocks.OAK_PLANKS.defaultBlockState());

        level.setBlockAndUpdate(display, TraderyBlocks.DISPLAY_BLOCK.get().defaultBlockState().setValue(DisplayBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(display) instanceof DisplayBlockEntity case_) {
            case_.setOwner(AccountId.player(player(server).getUUID()));
            case_.setShown(new ItemStack(Items.DIAMOND_SWORD));
            case_.setAnimation(DisplayAnimation.BOB);
        }
    }
}
