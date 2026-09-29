package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** A block entity with an owner who alone (with admins) may break and configure it. */
public interface OwnedBlockEntity {
    @Nullable AccountId owner();

    default boolean isOwner(Player player) {
        return owner() instanceof AccountId.Player p && p.uuid().equals(player.getUUID());
    }
}
