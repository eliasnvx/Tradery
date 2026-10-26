package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.village.Caravans;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caravans (SPEC "Торговля между деревнями"): right-clicking a caravan's trader opens Tradery's trade window in money
 * instead of vanilla's. Only traders tagged as caravans are touched; ordinary wandering traders keep their emerald
 * trades. Server side only: on the client vanilla's result goes on and asks the server.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void tradery$caravanTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = Caravans.interact((WanderingTrader) (Object) this, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
