package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Release checklist item "50 vending blocks in one chunk": 50 stocked blocks with spinning, bobbing goods in view.
 * Measures the server's average tick and the wall time per client frame against the same scene without them.
 * Vending blocks never tick, so the server cost must stay near zero; the renderer is the only per-frame cost.
 * The numbers go to the log and {@code fabric/build/run/clientGameTest/tradery-performance.txt}.
 */
public final class PerformanceClientTest implements FabricClientGameTest {
    private static final int FRAMES = 200;
    private static final UUID SHOPKEEPER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final List<Item> GOODS = List.of(Items.BREAD, Items.DIAMOND_SWORD, Items.GOLDEN_APPLE, Items.OAK_LOG, Items.POTION,
        Items.ENCHANTED_BOOK, Items.COOKED_BEEF, Items.IRON_PICKAXE, Items.EMERALD, Items.SHIELD);

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
            BlockPos origin = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
            ChunkPos chunk = ChunkPos.containing(origin.offset(0, 0, 16));
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            String look = "tp @p " + (minX + 8) + " " + origin.getY() + " " + (minZ - 3) + " 0 20";
            world.getServer().runCommand(look);
            context.waitTicks(20);
            double[] baseline = measure(context, world);

            world.getServer().runOnServer(s -> place(s, origin.getY(), minX, minZ));
            world.getServer().runCommand(look);
            world.getConnection().waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("performance_50_vendors");
            double[] vendors = measure(context, world);

            String report = String.format(
                "50 vending blocks in chunk %s, %d frames each%n"
                    + "server tick: %.3f ms without, %.3f ms with%n"
                    + "client render per frame (CPU): %.2f ms without, %.2f ms with (%+.2f ms)%n",
                chunk, FRAMES, baseline[0], vendors[0], baseline[1], vendors[1], vendors[1] - baseline[1]);
            System.out.print("[Tradery performance] " + report);
            Files.writeString(Path.of("tradery-performance.txt"), report);
            if (vendors[0] > 10.0) {
                throw new AssertionError("server tick with 50 vending blocks: " + vendors[0] + " ms\n" + report);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * [average server tick ms, average client render ms per frame] over {@link #FRAMES} frames. The render time is
     * {@code Minecraft#getFrameTimeNs}: the CPU work of a frame, without waiting for the next one (the test client
     * draws once per tick, so wall time per frame would only show the 50 ms tick).
     */
    private static double[] measure(ClientGameTestContext context, TestSingleplayerContext world) {
        context.waitTicks(20);
        long renderNs = 0;
        for (int i = 0; i < FRAMES; i++) {
            context.waitTick();
            renderNs += context.computeOnClient(mc -> mc.getFrameTimeNs());
        }
        double tickMs = world.getServer().computeOnServer(MinecraftServer::getAverageTickTimeNanos) / 1e6;
        return new double[] {tickMs, renderNs / 1e6 / FRAMES};
    }

    private static void place(MinecraftServer server, int y, int minX, int minZ) {
        ServerLevel level = server.overworld();
        int i = 0;
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 10; x++) {
                BlockPos pos = new BlockPos(minX + 3 + x, y, minZ + 4 + z * 2);
                level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
                VendingBlockEntity vendor = (VendingBlockEntity) level.getBlockEntity(pos);
                Item goods = GOODS.get(i++ % GOODS.size());
                vendor.setOwner(AccountId.player(SHOPKEEPER), "Shopkeeper");
                vendor.setSettings(new VendingSettings(new ItemStack(goods), PriceMode.CURRENCY, 100, ItemStack.EMPTY, false,
                    DisplayAnimation.SPIN_BOB));
                vendor.stock().setItem(0, new ItemStack(goods, goods.getDefaultMaxStackSize()));
                vendor.stock().setChanged();
            }
        }
    }
}
