package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.rewards.Rewards;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Advancement rewards: at the moment vanilla grants the advancement's own rewards (first completion only). */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void tradery$advancementReward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        Rewards.onAdvancement(player, holder.id());
    }
}
