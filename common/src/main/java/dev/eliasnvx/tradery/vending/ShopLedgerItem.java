package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The shop ledger book: use it to open the list of your vending blocks and market counters, the same window as
 * {@code /tradery shops} (and under the same permission, {@code tradery.shops}).
 */
public class ShopLedgerItem extends Item {
    public ShopLedgerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Platform.get().hasPermission(serverPlayer, TraderyPermission.SHOPS)) {
                serverPlayer.displayClientMessage(Messages.tr("tradery.ledger.no_permission", "You may not use the shop ledger here"), true);
                return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            if (VendingTrades.allow(serverPlayer)) {
                ShopLedger.open(serverPlayer);
            }
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
