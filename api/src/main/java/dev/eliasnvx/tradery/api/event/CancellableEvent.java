package dev.eliasnvx.tradery.api.event;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Base of events a listener can cancel, optionally with a message shown to the player. */
public abstract class CancellableEvent {
    private boolean cancelled;
    private @Nullable Component message;

    /** Cancels without a message; the player sees a generic text. */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Cancels with a message for the player.
     *
     * @param message why, e.g. "Trading is not allowed in spawn"
     */
    public void cancel(Component message) {
        cancelled = true;
        this.message = message;
    }

    /**
     * @param cancelled the new state; listeners registered with {@code receiveCancelled} may un-cancel
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        if (!cancelled) {
            message = null;
        }
    }

    /**
     * @return whether the event is cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return the cancelling listener's message, or {@code null}
     */
    public @Nullable Component cancelMessage() {
        return message;
    }
}
