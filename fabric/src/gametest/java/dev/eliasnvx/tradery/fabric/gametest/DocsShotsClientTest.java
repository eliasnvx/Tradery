package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyItems;
import dev.eliasnvx.tradery.vending.AdminFlags;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Mod page screenshots ({@code docs_*}, 1920x1080): a small market street, the hint, a quick buy, the screens and a
 * coin ore wall. Only with {@code TRADERY_DOCS_SHOTS=1}; tools/docs/make_page_images.py picks them up.
 * Run: TRADERY_DOCS_SHOTS=1 ./gradlew :fabric:runClientGameTest
 */
public final class DocsShotsClientTest implements FabricClientGameTest {
    private static final UUID MIRA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID KAI = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID LENA = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!"1".equals(System.getenv("TRADERY_DOCS_SHOTS"))) {
            return;
        }
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(3);
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("weather clear");
            server.runCommand("time set 5000");
            BlockPos o = server.computeOnServer(s -> player(s).blockPosition());
            server.runOnServer(s -> buildMarket(s, o));
            server.runCommand("place feature minecraft:fancy_oak " + (o.getX() - 10) + " " + o.getY() + " " + (o.getZ() + 11));
            server.runCommand("place feature minecraft:birch " + (o.getX() + 9) + " " + o.getY() + " " + (o.getZ() + 12));
            server.runCommand("place feature minecraft:oak " + (o.getX() + 3) + " " + o.getY() + " " + (o.getZ() + 14));
            server.runCommand("place feature minecraft:spruce " + (o.getX() - 4) + " " + o.getY() + " " + (o.getZ() + 15));
            world.getConnection().waitForChunksRender();

            // The street, no GUI: day, sunset, night
            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            });
            look(context, server, o, 0.5, 0.2, 1.4, 0.5, 0.6, 5.5);
            setHudHidden(context, true);
            context.waitTicks(20);
            shot(context, "docs_market_day");
            server.runCommand("time set 12500");
            context.waitTicks(20);
            shot(context, "docs_market_sunset");
            server.runCommand("time set 5000");
            look(context, server, o, -7.5, 0.6, 2.6, -1.0, 0.6, 5.5);
            context.waitTicks(20);
            shot(context, "docs_market_side");

            // Walking up to the bread stall: the hint, then a held sneak + right-click
            server.runOnServer(s -> player(s).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
            setHudHidden(context, false);
            BlockPos bread = o.offset(-4, 0, 5);
            look(context, server, o, -3.5, 0.0, 3.0, -3.5, 0.5, 5.5);
            context.waitTicks(20);
            shot(context, "docs_hint");
            context.getInput().holdShift();
            context.waitTicks(4);
            context.getInput().holdKeyFor(options -> options.keyUse, 18);
            context.waitTicks(3);
            context.takeScreenshot("docs_quick_buy");
            context.getInput().releaseShift();
            context.waitTicks(10);
            // Sneaking at the potion stall: the hint adds the potion's effect lines
            look(context, server, o, 2.5, 0.0, 3.0, 2.5, 0.5, 5.5);
            context.getInput().holdShift();
            context.waitTicks(10);
            shot(context, "docs_hint_sneaking");
            context.getInput().releaseShift();
            context.waitTicks(5);

            // The screens
            server.runOnServer(s -> VendingMenus.openBuyer(player(s), vendor(s, o.offset(0, 0, 5))));
            context.waitForScreen(VendingBuyerScreen.class);
            context.waitTicks(5);
            shot(context, "docs_buyer_screen");
            context.runOnClient(mc -> mc.player.closeContainer());
            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                VendingBlockEntity mine = vendor(s, bread);
                mine.setOwner(AccountId.player(p.getUUID()), p.nameAndId().name());
                VendingMenus.openOwner(p, mine, false);
            });
            context.waitForScreen(VendingOwnerScreen.class);
            context.waitTicks(5);
            shot(context, "docs_owner_screen");
            context.runOnClient(mc -> mc.player.closeContainer());

            // A coin ore wall, away from the street
            BlockPos wall = o.offset(40, 0, 0);
            server.runOnServer(s -> buildOreWall(s, wall));
            server.runCommand("item replace entity @p hotbar.0 with minecraft:iron_pickaxe");
            server.runCommand("item replace entity @p hotbar.1 with tradery:gold_coin 3");
            server.runCommand("item replace entity @p hotbar.2 with tradery:silver_coin 14");
            server.runCommand("item replace entity @p hotbar.3 with tradery:copper_coin 37");
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            look(context, server, wall, 0.5, 0.0, -0.5, 0.5, 2.0, 4.0);
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            shot(context, "docs_ore_wall");
        }
    }

    // ------------------------------------------------------------------ scenes

    private static void buildMarket(MinecraftServer server, BlockPos o) {
        ServerLevel level = server.overworld();
        // Path in front of the stalls, flowers behind them
        for (int x = -8; x <= 8; x++) {
            for (int z = 2; z <= 4; z++) {
                level.setBlockAndUpdate(o.offset(x, -1, z), Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET};
        for (int x = -9; x <= 9; x++) {
            for (int z = 7; z <= 10; z++) {
                if ((x * 7 + z * 13) % 5 == 0) {
                    level.setBlockAndUpdate(o.offset(x, 0, z), flowers[Math.floorMod(x + z, flowers.length)].defaultBlockState());
                }
            }
        }
        // Lantern posts behind the stalls
        for (int x : new int[] {-7, -3, 1, 5}) {
            level.setBlockAndUpdate(o.offset(x, 0, 6), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 1, 6), Blocks.LANTERN.defaultBlockState());
        }

        stall(level, o.offset(-4, 0, 5), MIRA, "Mira", new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB, Blocks.SPRUCE_PLANKS.defaultBlockState(), new ItemStack(Items.BREAD, 64));
        stall(level, o.offset(-2, 0, 5), KAI, "Kai", new ItemStack(Items.DIAMOND_SWORD), PriceMode.ITEM, 0, new ItemStack(Items.DIAMOND, 2), false,
            DisplayAnimation.SPIN, null, new ItemStack(Items.DIAMOND_SWORD));
        VendingBlockEntity apples = stall(level, o.offset(0, 0, 5), null, "Server", new ItemStack(Items.GOLDEN_APPLE), PriceMode.CURRENCY, 1500,
            ItemStack.EMPTY, false, DisplayAnimation.SPIN_BOB, Blocks.POLISHED_ANDESITE.defaultBlockState(), ItemStack.EMPTY);
        apples.setAdmin(new AdminFlags(true, false, true));
        stall(level, o.offset(2, 0, 5), LENA, "Lena", PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), PriceMode.CURRENCY, 800,
            ItemStack.EMPTY, false, DisplayAnimation.BOB, Blocks.BIRCH_PLANKS.defaultBlockState(),
            PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS).copyWithCount(1));
        stall(level, o.offset(4, 0, 5), MIRA, "Mira", new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true,
            DisplayAnimation.STATIC, Blocks.COBBLESTONE.defaultBlockState(), ItemStack.EMPTY);
        dev.eliasnvx.tradery.economy.EconomyService.INSTANCE.deposit(dev.eliasnvx.tradery.economy.EconomyService.INSTANCE.account(MIRA),
            100_000, dev.eliasnvx.tradery.api.Reason.of(dev.eliasnvx.tradery.api.Reasons.ADMIN_GIVE, "docs"));

        showcase(level, o.offset(-6, 0, 5), new ItemStack(Items.TOTEM_OF_UNDYING));
        showcase(level, o.offset(6, 0, 5), new ItemStack(Items.TRIDENT));
    }

    private static VendingBlockEntity stall(ServerLevel level, BlockPos pos, UUID owner, String name, ItemStack goods, PriceMode mode,
                                            long price, ItemStack priceItem, boolean buyback, DisplayAnimation animation,
                                            BlockState facade, ItemStack stock) {
        level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) level.getBlockEntity(pos);
        vendor.setOwner(owner == null ? AccountId.system(dev.eliasnvx.tradery.economy.EconomyService.SERVER_ACCOUNT) : AccountId.player(owner), name);
        vendor.setSettings(new VendingSettings(goods, mode, price, priceItem, buyback, animation));
        if (facade != null) {
            vendor.setFacade(facade);
        }
        if (!stock.isEmpty()) {
            vendor.stock().setItem(0, stock);
            vendor.stock().setChanged();
        }
        return vendor;
    }

    private static void showcase(ServerLevel level, BlockPos pos, ItemStack item) {
        level.setBlockAndUpdate(pos, TraderyBlocks.DISPLAY_BLOCK.get().defaultBlockState().setValue(DisplayBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
            display.setOwner(AccountId.player(LENA));
            display.setShown(item);
            display.setAnimation(DisplayAnimation.SPIN_BOB);
        }
    }

    private static void buildOreWall(MinecraftServer server, BlockPos w) {
        ServerLevel level = server.overworld();
        for (int x = -5; x <= 6; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = 4; z <= 6; z++) {
                    level.setBlockAndUpdate(w.offset(x, y, z), (y <= 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState());
                }
            }
            level.setBlockAndUpdate(w.offset(x, 5, 3), Blocks.STONE.defaultBlockState());
        }
        int[][] ores = {{-3, 2, 0}, {-2, 2, 0}, {-2, 3, 0}, {1, 1, 1}, {2, 1, 1}, {2, 2, 1}, {4, 3, 2}, {-1, 0, 2}, {3, 0, 0}, {0, 4, 1}};
        for (int[] ore : ores) {
            CoinTier tier = CoinTier.values()[ore[2]];
            level.setBlockAndUpdate(w.offset(ore[0], ore[1], 4), TraderyItems.ore(tier, ore[1] <= 0).defaultBlockState());
        }
        level.setBlockAndUpdate(w.offset(-4, 3, 3), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(w.offset(5, 3, 3), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Puts the player's feet at {@code (fx, fy, fz)} relative to {@code o}, eyes on {@code (tx, ty, tz)}. The rotation is
     * computed here and set on the client too: {@code tp ... facing} alone doesn't turn the client camera reliably.
     */
    private static void look(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos o,
                             double fx, double fy, double fz, double tx, double ty, double tz) {
        double x = o.getX() + fx;
        double y = o.getY() + fy;
        double z = o.getZ() + fz;
        double dx = o.getX() + tx - x;
        double dy = o.getY() + ty - (y + 1.62);
        double dz = o.getZ() + tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitTicks(5);
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.setYHeadRot(yaw);
        });
        context.waitTicks(5);
    }

    /** A screenshot without chat lines or toasts. */
    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
        });
        context.waitTicks(2);
        context.takeScreenshot(name);
    }

    /** F1: the HUD toggle in 26.3 is {@code Hud#toggle}, there's no setter. */
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
