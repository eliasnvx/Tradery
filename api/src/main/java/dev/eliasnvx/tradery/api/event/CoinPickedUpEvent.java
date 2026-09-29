package dev.eliasnvx.tradery.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player picks up coin items that are credited straight to the balance. Cancel to keep them as items
 * (normal pickup).
 */
public final class CoinPickedUpEvent extends CancellableEvent {
    public static final Event<CoinPickedUpEvent> EVENT = Event.create("CoinPickedUpEvent");

    private final ServerPlayer player;
    private final ItemStack coins;
    private long amount;

    public CoinPickedUpEvent(ServerPlayer player, ItemStack coins, long amount) {
        this.player = player;
        this.coins = coins.copy();
        this.amount = amount;
    }

    public ServerPlayer player() {
        return player;
    }

    /** @return the picked-up stack (a copy) */
    public ItemStack coins() {
        return coins.copy();
    }

    /** @return amount credited, minor units */
    public long amount() {
        return amount;
    }

    /** @param amount new amount, minor units, {@code >= 0} */
    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
    }
}
