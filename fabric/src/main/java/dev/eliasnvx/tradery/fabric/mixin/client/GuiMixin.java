package dev.eliasnvx.tradery.fabric.mixin.client;

import dev.eliasnvx.tradery.client.TraderyClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The balance HUD and the vending hint, drawn right before the subtitles, so the subtitles and the chat (drawn after
 * them in 1.20.1) stay readable where they overlap the panel (Forge: an overlay below {@code SUBTITLES}). The
 * subtitles are drawn only while the GUI is shown, so F1 hides the HUD with them.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/SubtitleOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void tradery$renderHudUnderSubtitles(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        TraderyClient.renderHud(graphics);
    }
}
