package dev.eliasnvx.tradery.neoforge.client;

import dev.eliasnvx.tradery.Tradery;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

@Mod(value = Tradery.MOD_ID, dist = Dist.CLIENT)
public final class TraderyNeoForgeClient {
    public TraderyNeoForgeClient(IEventBus modBus) {
        TraderyClient.init();
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(TraderyMenus.VENDING_BUYER.get(), VendingBuyerScreen::new);
            event.register(TraderyMenus.VENDING_OWNER.get(), VendingOwnerScreen::new);
            event.register(TraderyMenus.DISPLAY.get(), DisplayScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(TraderyBlocks.VENDING_BLOCK_ENTITY.get(), VendingRenderer::new);
            event.registerBlockEntityRenderer(TraderyBlocks.DISPLAY_BLOCK_ENTITY.get(), DisplayRenderer::new);
        });
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(TraderyKeyMappings.CATEGORY);
            for (KeyMapping key : TraderyKeyMappings.ALL) {
                event.register(key);
            }
        });
        // Under the chat and subtitles, so they stay readable when they overlap the panel
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(VanillaGuiLayers.CHAT, Tradery.id("balance"),
            (graphics, deltaTracker) -> TraderyClient.renderHud(graphics)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> TraderyClient.onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TraderyClient.onDisconnect());
        // Quick trades: fired on attack press and every held tick, and on each use (vanilla repeats it every 4 ticks)
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
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
