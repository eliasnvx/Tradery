package dev.eliasnvx.tradery.api.event;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A loader-independent event: listeners are registered once and work the same on Fabric and NeoForge.
 *
 * <p>Listeners run on the thread that posts the event (the server thread for every Tradery event), in priority
 * order, and in registration order within one priority. A listener that throws is logged and skipped. Once a
 * {@link CancellableEvent} is cancelled, later listeners are skipped unless they registered with
 * {@code receiveCancelled = true} (they may un-cancel it).
 *
 * @param <E> the event type
 */
public final class Event<E> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Tradery Events");

    private record Listener<E>(EventPriority priority, boolean receiveCancelled, Consumer<? super E> action) {
    }

    private final String name;
    /** Replaced on registration (rare), read on every post (often). */
    private volatile List<Listener<E>> listeners = List.of();

    private Event(String name) {
        this.name = name;
    }

    /**
     * Creates an event. Addons may create their own.
     *
     * @param name a name for log messages
     * @param <E>  the event type
     * @return a new event with no listeners
     */
    public static <E> Event<E> create(String name) {
        return new Event<>(name);
    }

    /**
     * Registers a listener with {@link EventPriority#NORMAL}.
     *
     * @param listener the listener
     */
    public void register(Consumer<? super E> listener) {
        register(EventPriority.NORMAL, false, listener);
    }

    /**
     * Registers a listener.
     *
     * @param priority when it runs relative to other listeners
     * @param listener the listener
     */
    public void register(EventPriority priority, Consumer<? super E> listener) {
        register(priority, false, listener);
    }

    /**
     * Registers a listener.
     *
     * @param priority         when it runs relative to other listeners
     * @param receiveCancelled whether it still runs after the event was cancelled
     * @param listener         the listener
     */
    public synchronized void register(EventPriority priority, boolean receiveCancelled, Consumer<? super E> listener) {
        List<Listener<E>> next = new ArrayList<>(listeners);
        next.add(new Listener<>(priority, receiveCancelled, listener));
        next.sort(Comparator.comparing(Listener::priority)); // stable: registration order within a priority
        listeners = List.copyOf(next);
    }

    /**
     * @return whether anyone listens, so callers can skip building an event nobody reads
     */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Runs the listeners. Called by Tradery; addons only post their own events.
     *
     * @param event the event
     * @return the same event, to read results such as cancellation
     */
    @ApiStatus.Internal
    public E post(E event) {
        for (Listener<E> listener : listeners) {
            if (event instanceof CancellableEvent cancellable && cancellable.isCancelled() && !listener.receiveCancelled()) {
                continue;
            }
            try {
                listener.action().accept(event);
            } catch (Throwable t) {
                LOGGER.error("A {} listener failed", name, t);
            }
        }
        return event;
    }

    @Override
    public String toString() {
        return "Event[" + name + "]";
    }
}
