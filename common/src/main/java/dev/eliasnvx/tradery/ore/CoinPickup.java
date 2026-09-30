package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.event.CoinPickedUpEvent;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Direct-to-balance coin pickup, called from {@code ItemEntity#playerTouch}. */
public final class CoinPickup {
    private CoinPickup() {
    }

    /** @return true when the coins went to the balance (the item entity is gone) */
    public static boolean tryDeposit(ItemEntity entity, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || entity.isRemoved() || serverPlayer.isSpectator()
            || !TraderyConfig.server().ore().directToBalance() || Platform.get().isFakePlayer(serverPlayer)
            || !EconomyService.INSTANCE.isReady()) {
            return false;
        }
        ItemStack stack = entity.getItem();
        long amount = Coins.value(stack);
        if (amount <= 0) {
            return false;
        }
        CoinPickedUpEvent event = CoinPickedUpEvent.EVENT.post(new CoinPickedUpEvent(serverPlayer, stack, amount));
        if (event.isCancelled()) {
            return false; // a normal pickup follows
        }
        EconomyService economy = EconomyService.INSTANCE;
        if (event.amount() > 0 && !economy.deposit(economy.account(player.getUUID()), event.amount(), Reason.of(Reasons.COIN_DEPOSIT)).isSuccess()) {
            return false; // e.g. over the balance ceiling: keep them as items
        }
        player.take(entity, stack.getCount());
        entity.discard();
        return true;
    }
}
