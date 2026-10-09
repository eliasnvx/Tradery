package dev.eliasnvx.tradery.forge.client;

import dev.eliasnvx.tradery.client.QuickTradeInput;
import dev.eliasnvx.tradery.client.TraderyClient;
import dev.eliasnvx.tradery.client.TraderyKeyMappings;
import dev.eliasnvx.tradery.client.render.DisplayRenderer;
import dev.eliasnvx.tradery.client.render.VendingRenderer;
import dev.eliasnvx.tradery.client.screen.DisplayScreen;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge client glue; called from the mod constructor on the physical client only. */
public final class TraderyForgeClient {
    private TraderyForgeClient() {
    }

    public static void init(IEventBus modBus) {
        TraderyClient.init();
        // MenuScreens isn't thread-safe: register on the main thread
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(TraderyMenus.VENDING_BUYER.get(), VendingBuyerScreen::new);
            MenuScreens.register(TraderyMenus.VENDING_OWNER.get(), VendingOwnerScreen::new);
            MenuScreens.register(TraderyMenus.DISPLAY.get(), DisplayScreen::new);
        }));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(TraderyBlocks.VENDING_BLOCK_ENTITY.get(), VendingRenderer::new);
            event.registerBlockEntityRenderer(TraderyBlocks.DISPLAY_BLOCK_ENTITY.get(), DisplayRenderer::new);
        });
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping key : TraderyKeyMappings.ALL) {
                event.register(key);
            }
        });
        // Under the subtitles and the chat (drawn after them), so they stay readable when they overlap the panel.
        // The HUD hides itself with F1 (custom overlays aren't hidden by Forge).
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerBelow(VanillaGuiOverlay.SUBTITLES.id(), "balance",
            (gui, graphics, partialTick, screenWidth, screenHeight) -> TraderyClient.renderHud(graphics)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                TraderyClient.onTick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TraderyClient.onDisconnect());
        // Quick trades: fired on attack press and every held tick, and on each use (vanilla repeats it every 4 ticks)
        MinecraftForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            boolean handled = event.isAttack() ? QuickTradeInput.onAttack(minecraft)
                : event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND
                    && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && QuickTradeInput.onUse(minecraft, hit.getBlockPos());
            if (handled) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        });
    }
}
