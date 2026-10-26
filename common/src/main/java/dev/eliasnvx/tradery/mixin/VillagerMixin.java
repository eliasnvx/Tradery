package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.village.VillagerTrading;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trading for money: where vanilla starts trading (after its checks for babies, sleep and empty offers), Tradery's
 * window opens instead of vanilla's (SPEC "Торговля с жителями за валюту").
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Shadow
    private void updateSpecialPrices(Player player) {
        throw new AssertionError();
    }

    @Inject(method = "startTrading", at = @At("HEAD"), cancellable = true)
    private void tradery$tradeForMoney(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && VillagerTrading.enabled()) {
            updateSpecialPrices(player);
            VillagerTrading.open(serverPlayer, (Villager) (Object) this);
            ci.cancel();
        }
    }
}
