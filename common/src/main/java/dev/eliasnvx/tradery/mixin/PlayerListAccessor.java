package dev.eliasnvx.tradery.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Saves one player's data, as the autosave does for everyone. */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
    @Invoker("save")
    void tradery$save(ServerPlayer player);
}
