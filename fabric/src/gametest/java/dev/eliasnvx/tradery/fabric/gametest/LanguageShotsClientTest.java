package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Translations in place ({@code lang_<code>_*}): the hint, the buyer screen and the owner screen in languages with
 * long words or other scripts, to catch labels that don't fit. Only with {@code TRADERY_LANG_SHOTS=1}.
 */
public final class LanguageShotsClientTest implements FabricClientGameTest {
    private static final List<String> LANGUAGES = List.of("ru_ru", "uk_ua", "be_by", "pl_pl", "de_de", "nl_nl", "sv_se", "fr_fr", "es_es", "pt_br", "ja_jp", "zh_cn", "zh_tw", "zh_hk");
    private static final UUID MIRA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!"1".equals(System.getenv("TRADERY_LANG_SHOTS"))) {
            return;
        }
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(2);
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("time set 6000");
            server.runCommand("gamerule send_command_feedback false");
            BlockPos o = server.computeOnServer(s -> player(s).blockPosition());
            BlockPos bread = o.offset(0, 0, 3);
            server.runOnServer(s -> {
                s.overworld().setBlockAndUpdate(bread, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
                VendingBlockEntity vendor = vendor(s, bread);
                vendor.setOwner(AccountId.player(MIRA), "Mira");
                vendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
                    DisplayAnimation.SPIN_BOB));
                vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
                vendor.stock().setChanged();
            });
            server.runCommand("tp @p " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 1.4) + " 0 30");
            context.waitTicks(10);
            context.runOnClient(mc -> {
                mc.player.setYRot(0);
                mc.player.setXRot(30);
            });
            for (String language : LANGUAGES) {
                switchLanguage(context, language);
                context.takeScreenshot("lang_" + language + "_hint");
                server.runOnServer(s -> VendingMenus.openBuyer(player(s), vendor(s, bread)));
                context.waitForScreen(VendingBuyerScreen.class);
                context.waitTicks(3);
                context.takeScreenshot("lang_" + language + "_buyer");
                context.runOnClient(mc -> mc.player.closeContainer());
                server.runOnServer(s -> VendingMenus.openOwner(player(s), vendor(s, bread), true));
                context.waitForScreen(VendingOwnerScreen.class);
                context.waitTicks(3);
                context.takeScreenshot("lang_" + language + "_owner");
                context.runOnClient(mc -> mc.player.closeContainer());
            }
            switchLanguage(context, "en_us");
        }
    }

    private static void switchLanguage(ClientGameTestContext context, String language) {
        var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(mc -> {
            mc.options.languageCode = language;
            mc.getLanguageManager().setSelected(language);
            reload.set(mc.reloadResourcePacks());
        });
        context.waitFor(mc -> reload.get().isDone(), 2400);
        // The loading overlay fades out by wall-clock time, not by ticks
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.waitTicks(10);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static VendingBlockEntity vendor(MinecraftServer server, BlockPos pos) {
        return (VendingBlockEntity) server.overworld().getBlockEntity(pos);
    }
}
