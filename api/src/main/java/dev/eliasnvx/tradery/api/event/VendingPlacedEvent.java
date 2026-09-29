package dev.eliasnvx.tradery.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** A player is placing a vending block. Cancel to forbid it (regions, clan limits). */
public final class VendingPlacedEvent extends CancellableEvent {
    public static final Event<VendingPlacedEvent> EVENT = Event.create("VendingPlacedEvent");

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;

    public VendingPlacedEvent(ServerPlayer player, ServerLevel level, BlockPos pos) {
        this.player = player;
        this.level = level;
        this.pos = pos.immutable();
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }
}
