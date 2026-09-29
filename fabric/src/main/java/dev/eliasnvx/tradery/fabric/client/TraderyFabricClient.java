package dev.eliasnvx.tradery.fabric.client;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.client.TraderyClient;
import dev.eliasnvx.tradery.client.TraderyKeyMappings;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import dev.eliasnvx.tradery.client.render.DisplayRenderer;
import dev.eliasnvx.tradery.client.render.VendingRenderer;
import dev.eliasnvx.tradery.client.screen.DisplayScreen;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TraderyFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TraderyClient.init();
        MenuScreens.register(TraderyMenus.VENDING_BUYER.get(), VendingBuyerScreen::new);
        MenuScreens.register(TraderyMenus.VENDING_OWNER.get(), VendingOwnerScreen::new);
        MenuScreens.register(TraderyMenus.DISPLAY.get(), DisplayScreen::new);
        BlockEntityRenderers.register(TraderyBlocks.VENDING_BLOCK_ENTITY.get(), VendingRenderer::new);
        BlockEntityRenderers.register(TraderyBlocks.DISPLAY_BLOCK_ENTITY.get(), DisplayRenderer::new);
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerReceiver(entry.type());
        }
        for (KeyMapping key : TraderyKeyMappings.ALL) {
            KeyMappingHelper.registerKeyMapping(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(TraderyClient::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TraderyClient.onDisconnect());
        // Under the chat and subtitles, so they stay readable when they overlap the panel
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Tradery.id("balance"),
            (graphics, deltaTracker) -> TraderyClient.renderHud(graphics));
    }

    /** Fabric runs play payload handlers on the client thread. */
    private static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> TraderyClient.handle(payload));
    }
}
