package dev.eliasnvx.tradery.fabric.client;

import dev.eliasnvx.tradery.client.QuickTradeInput;
import dev.eliasnvx.tradery.client.TraderyClient;
import dev.eliasnvx.tradery.client.TraderyKeyMappings;
import dev.eliasnvx.tradery.client.render.DisplayRenderer;
import dev.eliasnvx.tradery.client.render.VendingRenderer;
import dev.eliasnvx.tradery.client.screen.DisplayScreen;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyItems;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Fabric client glue. The balance HUD is drawn by {@code mixin.client.GuiMixin}, right under the chat layer
 * (Fabric API 1.21.1 has no HUD layer API; {@code HudRenderCallback} would draw over the chat).
 */
public final class TraderyFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TraderyClient.init();
        MenuScreens.register(TraderyMenus.VENDING_BUYER.get(), VendingBuyerScreen::new);
        MenuScreens.register(TraderyMenus.VENDING_OWNER.get(), VendingOwnerScreen::new);
        MenuScreens.register(TraderyMenus.DISPLAY.get(), DisplayScreen::new);
        BlockEntityRenderers.register(TraderyBlocks.VENDING_BLOCK_ENTITY.get(), VendingRenderer::new);
        BlockEntityRenderers.register(TraderyBlocks.DISPLAY_BLOCK_ENTITY.get(), DisplayRenderer::new);
        registerRenderLayers();
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerReceiver(entry.type());
        }
        for (KeyMapping key : TraderyKeyMappings.ALL) {
            KeyBindingHelper.registerKeyBinding(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(TraderyClient::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TraderyClient.onDisconnect());
        // Quick trades: sneak + attack (fires every tick while held) and sneak + use (vanilla repeats it every 4 ticks)
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> QuickTradeInput.onAttack(client));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> level.isClientSide() && hand == InteractionHand.MAIN_HAND
            && QuickTradeInput.onUse(Minecraft.getInstance(), hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /**
     * 1.21.1 picks a block's render layer in code (NeoForge reads the models' {@code render_type}): the glass of the
     * vending and display blocks is translucent, the ore overlays are cut out.
     */
    private static void registerRenderLayers() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), TraderyBlocks.VENDING_BLOCK.get(), TraderyBlocks.DISPLAY_BLOCK.get());
        for (CoinTier tier : CoinTier.values()) {
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutoutMipped(), TraderyItems.ore(tier, false), TraderyItems.ore(tier, true));
        }
    }

    /** Fabric runs play payload handlers on the client thread. */
    private static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> TraderyClient.handle(payload));
    }
}
