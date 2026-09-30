package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.registry.TraderyItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Coin ores and coins as the player sees them (screenshot {@code ore_*}, for a human look). */
public final class OreClientTest implements FabricClientGameTest {
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
            world.getServer().runCommand("gamerule advance_time false");
            BlockPos origin = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().blockPosition());
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                int x = -3;
                for (CoinTier tier : CoinTier.values()) {
                    level.setBlockAndUpdate(origin.offset(x, 1, 4), TraderyItems.ore(tier, false).defaultBlockState());
                    level.setBlockAndUpdate(origin.offset(x, 0, 4), TraderyItems.ore(tier, true).defaultBlockState());
                    x += 2;
                }
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(0, new ItemStack(TraderyItems.coin(CoinTier.COPPER), 12));
                player.getInventory().setItem(1, new ItemStack(TraderyItems.coin(CoinTier.SILVER), 5));
                player.getInventory().setItem(2, new ItemStack(TraderyItems.coin(CoinTier.GOLD), 2));
            });
            world.getServer().runCommand("tp @p " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5)
                + " facing " + (origin.getX() + 0.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 4.5));
            context.waitTicks(20);
            context.takeScreenshot("ore_and_coins");
        }
    }
}
