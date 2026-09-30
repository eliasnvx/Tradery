package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.rewards.Rewards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tags mobs made by spawners and trial spawners (both create them through this method with a spawner reason), so
 * kill rewards can skip mob farms. Passengers go through the same method and get tagged too.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    @Inject(method = "loadEntityRecursive(Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;Lnet/minecraft/world/entity/EntityProcessor;)Lnet/minecraft/world/entity/Entity;",
        at = @At("RETURN"))
    private static void tradery$tagSpawnerMobs(ValueInput input, Level level, EntitySpawnRequest request, EntityProcessor postLoad,
                                               CallbackInfoReturnable<Entity> cir) {
        Entity entity = cir.getReturnValue();
        if (entity != null && EntitySpawnReason.isSpawner(request.reason())) {
            entity.addTag(Rewards.SPAWNER_TAG);
        }
    }
}
