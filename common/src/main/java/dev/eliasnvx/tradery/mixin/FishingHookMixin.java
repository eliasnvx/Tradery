package dev.eliasnvx.tradery.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eliasnvx.tradery.rewards.Rewards;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fishing rewards: sees the catch the moment it is rolled. */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> tradery$fishReward(LootTable table, LootParams params, Operation<ObjectArrayList<ItemStack>> original) {
        ObjectArrayList<ItemStack> items = original.call(table, params);
        Player owner = ((FishingHook) (Object) this).getPlayerOwner();
        if (owner != null) {
            Rewards.onFished(owner, items);
        }
        return items;
    }
}
