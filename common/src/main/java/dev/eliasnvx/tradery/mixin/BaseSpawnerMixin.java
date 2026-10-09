package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.rewards.Rewards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/**
 * Tags mobs made by monster spawners (and their passengers), so kill rewards can skip mob farms. The spawn reason
 * isn't passed to {@code EntityType.loadEntityRecursive} on this version, so the spawner's own call is the hook:
 * its per-entity function (applied to the mob and to every passenger) is wrapped to add the tag.
 */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {
    @ModifyArg(method = "serverTick", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/EntityType;loadEntityRecursive(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;Ljava/util/function/Function;)Lnet/minecraft/world/entity/Entity;"))
    private Function<Entity, Entity> tradery$tagSpawnerMobs(Function<Entity, Entity> place) {
        return entity -> {
            Entity placed = place.apply(entity);
            if (placed != null) {
                placed.addTag(Rewards.SPAWNER_TAG);
            }
            return placed;
        };
    }
}
