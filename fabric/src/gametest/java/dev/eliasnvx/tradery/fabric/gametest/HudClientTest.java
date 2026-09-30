package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;

/**
 * The balance HUD in a real client: synced on join, updated by a transaction, hidden by /tradery hud.
 * Screenshots ({@code hud_*}) in fabric/build/run/clientGameTest/screenshots are for a human look.
 * Run by hand: ./gradlew :fabric:runClientGameTest
 */
public final class HudClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(2);
        });
        ClientConfig original = context.computeOnClient(mc -> TraderyConfig.client());
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set 6000");
            context.waitTicks(5);
            long start = context.computeOnClient(mc -> ClientEconomy.balance());
            if (!context.computeOnClient(mc -> ClientEconomy.isActive())) {
                throw new AssertionError("HUD state not synced on join");
            }
            String name = context.computeOnClient(mc -> mc.player.nameAndId().name());

            world.getServer().runCommand("eco give " + name + " 1234.5");
            context.waitTicks(4);
            long after = context.computeOnClient(mc -> ClientEconomy.balance());
            if (after != start + 123_450) {
                throw new AssertionError("balance after /eco give: expected " + (start + 123_450) + ", got " + after);
            }
            context.takeScreenshot("hud_bottom_right_popup");
            context.waitTicks(50);
            context.takeScreenshot("hud_bottom_right");

            world.getServer().runCommand("eco take " + name + " 34.5");
            context.waitTicks(4);
            context.takeScreenshot("hud_loss_popup");

            // Acceptance: the HUD stays clear of the hotbar at every GUI scale on 1280x720, even with a long balance
            world.getServer().runCommand("eco set " + name + " 1234567.89");
            context.waitTicks(40);
            for (int scale = 1; scale <= 4; scale++) {
                int guiScale = scale;
                context.runOnClient(mc -> {
                    mc.options.guiScale().set(guiScale);
                    mc.resizeGui();
                });
                context.waitTicks(3);
                context.takeScreenshot("hud_gui_scale_" + scale);
            }
            context.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });

            context.runOnClient(mc -> TraderyConfig.saveClient(original.withHud(new ClientConfig.Hud(true,
                ClientConfig.Corner.TOP_LEFT, 4, 4, 1.5, ClientConfig.Format.SHORT, true, true))));
            context.waitTicks(2);
            context.takeScreenshot("hud_top_left_short_scaled");

            world.getServer().runCommand("execute as " + name + " run tradery hud");
            context.waitTicks(4);
            if (context.computeOnClient(mc -> TraderyConfig.client().hud().enabled())) {
                throw new AssertionError("/tradery hud did not hide the HUD");
            }
            context.takeScreenshot("hud_hidden");
        } finally {
            context.runOnClient(mc -> TraderyConfig.saveClient(original));
        }
    }
}
