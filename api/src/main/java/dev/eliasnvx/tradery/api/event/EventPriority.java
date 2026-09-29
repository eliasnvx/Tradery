package dev.eliasnvx.tradery.api.event;

/** Order in which listeners run: {@link #HIGHEST} first, {@link #LOWEST} last. */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
