package dev.eliasnvx.tradery.mixin;

import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every chunk write (eager, unload, autosave) goes through {@code save(ChunkAccess)}; see {@link TradePersistence}. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("RETURN"))
    private void tradery$saveTradesWithChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            TradePersistence.chunkSaved(level, chunk.getPos());
        }
    }
}
