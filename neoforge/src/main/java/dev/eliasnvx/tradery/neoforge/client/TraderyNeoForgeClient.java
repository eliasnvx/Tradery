package dev.eliasnvx.tradery.neoforge.client;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.client.TraderyClient;
import dev.eliasnvx.tradery.client.TraderyKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Tradery.MOD_ID, dist = Dist.CLIENT)
public final class TraderyNeoForgeClient {
    public TraderyNeoForgeClient(IEventBus modBus) {
        TraderyClient.init();
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
    }
}
