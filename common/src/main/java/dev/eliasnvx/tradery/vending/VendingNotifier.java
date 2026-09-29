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
import java.util.UUID;

/**
 * Tells owners about their vending blocks: straight away when online (the client filters by its settings), or
 * stored for a summary on the next join.
 */
public final class VendingNotifier {
    /** Latest stored messages shown after the summary on join. */
    static final int SUMMARY_LINES = 5;

    private VendingNotifier() {
    }

    /** Marks a notification argument as a translation key for the client. */
    public static String translatable(String key) {
        return TraderyPayloads.TRANSLATABLE_PREFIX + key;
    }

    static void sold(VendingBlockEntity vendor, ServerPlayer buyer, ItemStack goods, int count, long moneyEarned,
                     ItemStack priceItem, int priceItems) {
        String price = moneyEarned >= 0
            ? EconomyService.INSTANCE.defaultCurrency().formatPlain(moneyEarned)
            : priceItems + " × " + priceItem.getHoverName().getString();
        send(vendor, NotificationKind.SALE, "tradery.notify.sale",
            List.of(String.valueOf(count), translatable(goods.getItem().getDescriptionId()), price, buyer.nameAndId().name()),
            true, Math.max(0, moneyEarned));
    }

    static void boughtBack(VendingBlockEntity vendor, ServerPlayer seller, ItemStack goods, int count, long paid) {
        send(vendor, NotificationKind.SALE, "tradery.notify.buyback",
            List.of(String.valueOf(count), translatable(goods.getItem().getDescriptionId()), seller.nameAndId().name(),
                EconomyService.INSTANCE.defaultCurrency().formatPlain(paid)),
            false, 0);
    }

    static void empty(VendingBlockEntity vendor) {
        send(vendor, NotificationKind.EMPTY, "tradery.notify.empty", List.of(where(vendor.getBlockPos())), false, 0);
    }

    /** The revenue slots are full (item price): the block can't sell until the owner empties them. Once per episode. */
    static void ownerFull(VendingBlockEntity vendor) {
        if (vendor.fullNotified()) {
            return;
        }
        vendor.setFullNotified(true);
        send(vendor, NotificationKind.NO_SPACE, "tradery.notify.no_space", List.of(where(vendor.getBlockPos())), false, 0);
    }

    private static String where(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void send(VendingBlockEntity vendor, NotificationKind kind, String key, List<String> args, boolean sale, long earned) {
        if (!(vendor.owner() instanceof AccountId.Player owner) || !(vendor.getLevel() instanceof ServerLevel level)) {
            return; // server-owned vendors have nobody to notify
        }
        MinecraftServer server = level.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner.uuid());
        if (online != null) {
            Platform.get().sendToPlayer(online, new TraderyPayloads.NotificationPayload(kind, key, args));
        } else {
            NotificationsData.get(server).add(owner.uuid(), new NotificationsData.Line(kind, key, args), sale, earned);
        }
    }

    /** On join: "While you were away: 12 sales, +480 ₮" and the latest few messages. */
    public static void onJoin(ServerPlayer player) {
        UUID id = player.getUUID();
        NotificationsData.get(player.level().getServer()).take(id).ifPresent(pending -> {
            List<NotificationsData.Line> lines = pending.lines();
            if (pending.sales() > 0) {
                Platform.get().sendToPlayer(player, new TraderyPayloads.NotificationPayload(NotificationKind.OFFLINE_SUMMARY,
                    "tradery.notify.offline_summary",
                    List.of(String.valueOf(pending.sales()), "+" + EconomyService.INSTANCE.defaultCurrency().formatPlain(pending.earned()))));
            }
            int from = Math.max(0, lines.size() - SUMMARY_LINES);
            for (NotificationsData.Line line : lines.subList(from, lines.size())) {
                Platform.get().sendToPlayer(player, new TraderyPayloads.NotificationPayload(line.kind(), line.key(), line.args()));
            }
            if (from > 0) {
                Platform.get().sendToPlayer(player, new TraderyPayloads.NotificationPayload(NotificationKind.OFFLINE_SUMMARY,
                    "tradery.notify.offline_more", List.of(String.valueOf(from))));
            }
        });
    }
}
