package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.command.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trading without the window, like the original Vending Block: sneak + use buys one lot from a selling block, sneak +
 * attack sells one lot to a buying one. Holding the button repeats (the client asks at most every 4 ticks; the trade
 * rate limit still applies). Feedback is a sound at the block and a running total in the action bar.
 */
public final class VendingQuickTrade {
    /** Trades this close (in ticks) to the previous one on the same block add up in the action bar. */
    static final int STREAK_TICKS = 30;
    private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    private record Streak(BlockPos pos, boolean sell, long lastTick, int goods, long price) {
    }

    private VendingQuickTrade() {
    }

    /** One request from {@code VendingQuickTradePayload}, on the server thread. */
    public static void handle(ServerPlayer player, BlockPos pos, boolean sell) {
        if (player.isSpectator() || !player.isAlive() || !player.level().isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1.0)
            || !(player.level().getBlockEntity(pos) instanceof VendingBlockEntity vendor)) {
            return;
        }
        if (!VendingTrades.allow(player)) {
            return;
        }
        // Others see the arm move; the trader's own client already swung it
        ItemStack held = player.getMainHandItem();
        player.swing(InteractionHand.MAIN_HAND, sell ? held.getAttackAnimation() : held.getInteractAnimation(), false);
        VendingSettings settings = vendor.settings();
        if (settings.isConfigured() && settings.isBuyback() != sell) {
            player.sendOverlayMessage(wrongButton(settings.isBuyback()));
            refuse(player, pos);
            return;
        }
        VendingTrades.Outcome outcome = VendingTrades.execute(player, vendor, 1);
        if (!outcome.success()) {
            STREAKS.remove(player.getUUID());
            player.sendOverlayMessage(outcome.message().copy().withStyle(ChatFormatting.RED));
            refuse(player, pos);
            return;
        }
        long now = player.level().getGameTime();
        Streak previous = STREAKS.get(player.getUUID());
        Streak streak = previous != null && previous.pos().equals(pos) && previous.sell() == sell && now - previous.lastTick() <= STREAK_TICKS
            ? new Streak(pos, sell, now, previous.goods() + outcome.goods(), previous.price() + outcome.price())
            : new Streak(pos, sell, now, outcome.goods(), outcome.price());
        STREAKS.put(player.getUUID(), streak);
        player.sendOverlayMessage(VendingTrades.describe(settings, streak.goods(), streak.price()));
        player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4f,
            1.3f + player.getRandom().nextFloat() * 0.2f);
    }

    /** Goods added up in the player's current streak (for tests and the action bar). */
    public static int streakGoods(ServerPlayer player) {
        Streak streak = STREAKS.get(player.getUUID());
        return streak == null ? 0 : streak.goods();
    }

    public static void forget(ServerPlayer player) {
        STREAKS.remove(player.getUUID());
    }

    /**
     * The block trades the other way: say which keys do. The client normally says this itself (with short key names)
     * and doesn't send the request; this covers clients that do.
     */
    private static Component wrongButton(boolean blockBuys) {
        Component keys = blockBuys
            ? Messages.tr("tradery.hint.sell_keys", "%1$s + %2$s: sell", Component.keybind("key.sneak"), Component.keybind("key.attack"))
            : Messages.tr("tradery.hint.buy_keys", "%1$s + %2$s: buy", Component.keybind("key.sneak"), Component.keybind("key.use"));
        return blockBuys
            ? Messages.tr("tradery.vending.wrong_button_buys", "This vending block buys: %s", keys)
            : Messages.tr("tradery.vending.wrong_button_sells", "This vending block sells: %s", keys);
    }

    private static void refuse(ServerPlayer player, BlockPos pos) {
        player.level().playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 1.2f);
    }
}
