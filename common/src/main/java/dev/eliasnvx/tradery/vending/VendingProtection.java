package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;

/** Who may break or administer a vending block. */
public final class VendingProtection {
    private VendingProtection() {
    }

    /** {@code tradery.admin.vendors} (op level 2 by default). */
    public static boolean isAdmin(ServerPlayer player) {
        return Platform.get().hasPermission(player, TraderyPermission.ADMIN_VENDORS);
    }

    public static boolean holdsKey(Player player) {
        return player.getMainHandItem().getItem() instanceof VendorKeyItem || player.getOffhandItem().getItem() instanceof VendorKeyItem;
    }

    /**
     * The owner, or an admin holding the vendor key. On the client (prediction) holding the key is enough; the
     * server checks the permission.
     */
    public static boolean canBreak(Player player, OwnedBlockEntity block) {
        if (block.owner() == null || block.isOwner(player)) {
            return true;
        }
        if (!holdsKey(player)) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer) || isAdmin(serverPlayer);
    }

    /** For the loaders' break events (creative mode breaks instantly, skipping destroy progress). */
    public static boolean mayBreak(Player player, BlockGetter level, BlockPos pos) {
        return !(level.getBlockEntity(pos) instanceof OwnedBlockEntity owned) || canBreak(player, owned);
    }
}
