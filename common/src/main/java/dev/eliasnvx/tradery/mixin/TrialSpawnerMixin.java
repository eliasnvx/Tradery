package dev.eliasnvx.tradery.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.eliasnvx.tradery.rewards.Rewards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tags mobs made by trial spawners (and their passengers), like {@link BaseSpawnerMixin} does for monster spawners. */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
    @ModifyExpressionValue(method = "spawnMob", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/EntityType;loadEntityRecursive(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;Ljava/util/function/Function;)Lnet/minecraft/world/entity/Entity;"))
    private @Nullable Entity tradery$tagSpawnerMobs(@Nullable Entity entity) {
        if (entity != null) {
            entity.getSelfAndPassengers().forEach(spawned -> spawned.addTag(Rewards.SPAWNER_TAG));
        }
        return entity;
    }
}
