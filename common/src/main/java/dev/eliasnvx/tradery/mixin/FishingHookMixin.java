package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.rewards.Rewards;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;

/**
 * Fishing rewards: sees the catch when the "fishing rod hooked" trigger gets it, right after it is rolled (an
 * entity pulled in passes an empty list, which pays nothing). The list is passed on unchanged.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @ModifyArg(method = "retrieve", index = 3, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V"))
    private Collection<ItemStack> tradery$fishReward(Collection<ItemStack> items) {
        Player owner = ((FishingHook) (Object) this).getPlayerOwner();
        if (owner != null && !items.isEmpty()) {
            Rewards.onFished(owner, items);
        }
        return items;
    }
}
