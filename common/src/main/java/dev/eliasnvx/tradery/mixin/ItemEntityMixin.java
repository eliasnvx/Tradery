package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.ore.CoinPickup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Coins picked up from the ground go straight to the balance (direct-to-balance mode), even with a full inventory. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    private int pickupDelay;
    @Shadow
    private @Nullable UUID target;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void tradery$depositCoins(Player player, CallbackInfo ci) {
        if (pickupDelay == 0 && (target == null || target.equals(player.getUUID())) && CoinPickup.tryDeposit((ItemEntity) (Object) this, player)) {
            ci.cancel();
        }
    }
}
