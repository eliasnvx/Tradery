package dev.eliasnvx.tradery.fabric.mixin.client;

import dev.eliasnvx.tradery.client.TraderyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The balance HUD and the vending hint, drawn right before the chat layer, so the chat and the subtitles stay
 * readable where they overlap the panel (NeoForge: a GUI layer below {@code CHAT}). The chat layer is part of the
 * group F1 hides, so the HUD hides with it.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void tradery$renderHudUnderChat(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        TraderyClient.renderHud(graphics);
    }
}
