package dev.eliasnvx.tradery.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A configured reward is about to be paid. Cancel to pay nothing, or change the amount. */
public final class RewardGrantedEvent extends CancellableEvent {
    public static final Event<RewardGrantedEvent> EVENT = Event.create("RewardGrantedEvent");

    /** Reward kinds, matching the keys of {@code rewards.json5}. */
    public enum Type {
        KILL, MINE, CRAFT, FISH, ADVANCEMENT
    }

    private final ServerPlayer player;
    private final Type type;
    private final ResourceLocation source;
    private long amount;

    public RewardGrantedEvent(ServerPlayer player, Type type, ResourceLocation source, long amount) {
        this.player = player;
        this.type = type;
        this.source = source;
        this.amount = amount;
    }

    public ServerPlayer player() {
        return player;
    }

    public Type type() {
        return type;
    }

    /** @return what earned it: entity type, block, item or advancement id */
    public ResourceLocation source() {
        return source;
    }

    /** @return amount, minor units (after daily cap and diminishing returns) */
    public long amount() {
        return amount;
    }

    /** @param amount new amount, minor units, {@code >= 0} */
    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
    }
}
