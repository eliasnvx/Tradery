package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.network.TraderyPayloads.NotificationKind;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tells owners about their market counters, like {@link VendingNotifier} does for vending blocks: straight away when
 * online (the client filters by its notification settings), or stored for the summary on the next join.
 */
final class CounterNotifier {
    private CounterNotifier() {
    }

    /** "Sold 16 × Bread for 40.00 (Steve)". */
    static void sold(MarketCounterBlockEntity counter, ServerPlayer buyer, ItemStack goods, int count, long earned) {
        send(counter, NotificationKind.SALE, "tradery.notify.sale",
            List.of(String.valueOf(count), VendingNotifier.translatable(goods.getItem().getDescriptionId()), plain(earned),
                buyer.getGameProfile().getName()),
            true, earned, false);
    }

    /** "Farmer bought 4 × Bread for 9.80". */
    static void villagerBought(MarketCounterBlockEntity counter, String customer, ItemStack goods, int count, long earned) {
        send(counter, NotificationKind.SALE, "tradery.notify.villager_sale",
            List.of(customer, String.valueOf(count), VendingNotifier.translatable(goods.getItem().getDescriptionId()), plain(earned)),
            true, earned, true);
    }

    /** One offer just sold its last trade. */
    static void offerEmpty(MarketCounterBlockEntity counter, ItemStack goods) {
        send(counter, NotificationKind.EMPTY, "tradery.notify.counter_empty",
            List.of(where(counter.getBlockPos()), VendingNotifier.translatable(goods.getItem().getDescriptionId())), false, 0, false);
    }

    private static String plain(long amount) {
        return EconomyService.INSTANCE.defaultCurrency().formatPlain(Math.max(0, amount));
    }

    private static String where(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void send(MarketCounterBlockEntity counter, NotificationKind kind, String key, List<String> args, boolean sale, long earned,
                             boolean villager) {
        if (!(counter.owner() instanceof AccountId.Player owner) || !(counter.getLevel() instanceof ServerLevel level)) {
            return; // server-owned counters have nobody to notify
        }
        MinecraftServer server = level.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner.uuid());
        if (online != null) {
            Platform.get().sendToPlayer(online, new TraderyPayloads.NotificationPayload(kind, key, args));
        } else {
            NotificationsData.get(server).add(owner.uuid(), new NotificationsData.Line(kind, key, args), sale, Math.max(0, earned), villager);
        }
    }
}
