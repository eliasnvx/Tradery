package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendorKeyItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Which clicks become quick trades: sneak + use (buy) and sneak + attack (sell) on someone else's vending block. The
 * loaders call in on every use and every attack tick; a handled click skips vanilla (no block placing, no mining)
 * and sends at most one request every {@link #INTERVAL_TICKS}, so holding the button trades about 5 times a second.
 */
public final class QuickTradeInput {
    static final int INTERVAL_TICKS = 4;
    private static long lastSent = Long.MIN_VALUE;
    private static @Nullable BlockPos lastPos;
    private static boolean lastSell;

    private QuickTradeInput() {
    }

    /** The attack key was pressed or is held (every tick). True: Tradery handled it. */
    public static boolean onAttack(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && handle(minecraft, hit.getBlockPos(), true);
    }

    /** The use key fired on a block (on press, then every 4 ticks while held). True: Tradery handled it. */
    public static boolean onUse(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.getMainHandItem().getItem() instanceof VendorKeyItem) {
            return false;
        }
        return handle(minecraft, pos, false);
    }

    /** Whether sneak + click on this block means a quick trade for this player (also used by the hint). */
    public static boolean applies(@Nullable LocalPlayer player, @Nullable VendingBlockEntity vendor) {
        return player != null && vendor != null && !player.isSpectator() && !vendor.isOwnedBy(player.getUUID());
    }

    private static boolean handle(Minecraft minecraft, BlockPos pos, boolean sell) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isShiftKeyDown()
            || !(minecraft.level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) || !applies(player, vendor)
            || !Platform.get().canSendToServer(TraderyPayloads.VendingQuickTradePayload.TYPE)) {
            return false;
        }
        long now = minecraft.level.getGameTime();
        if (now - lastSent >= INTERVAL_TICKS || now < lastSent || !pos.equals(lastPos) || sell != lastSell) {
            Platform.get().sendToServer(new TraderyPayloads.VendingQuickTradePayload(pos, sell));
            ItemStack held = player.getMainHandItem();
            player.swing(InteractionHand.MAIN_HAND, sell ? held.getAttackAnimation() : held.getInteractAnimation(), false);
            lastSent = now;
            lastPos = pos.immutable();
            lastSell = sell;
        }
        return true;
    }
}
