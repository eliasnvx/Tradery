package dev.eliasnvx.tradery.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Coin ore was mined, before money is credited or coins drop. Cancel to give nothing. */
public final class CoinOreMinedEvent extends CancellableEvent {
    public static final Event<CoinOreMinedEvent> EVENT = Event.create("CoinOreMinedEvent");

    /** Where the money goes. */
    public enum Payout {
        /** Straight to the miner's balance. */
        BALANCE,
        /** As coin items dropped at the block. */
        ITEMS
    }

    private final @Nullable ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private long amount;
    private Payout payout;

    public CoinOreMinedEvent(@Nullable ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, long amount, Payout payout) {
        this.player = player;
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.amount = amount;
        this.payout = payout;
    }

    /** @return the miner, or {@code null} if it wasn't a player (explosion, machine) */
    public @Nullable ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    /** @return value of the coins, minor units */
    public long amount() {
        return amount;
    }

    /** @param amount new value, minor units, {@code >= 0}; with {@link Payout#ITEMS} it is paid in the largest coins that fit */
    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
    }

    public Payout payout() {
        return payout;
    }

    /** @param payout where the money goes; {@link Payout#BALANCE} needs a player */
    public void setPayout(Payout payout) {
        this.payout = payout;
    }
}
