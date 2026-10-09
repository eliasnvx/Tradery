package dev.eliasnvx.tradery.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Why money moved. Shown in the transaction log, in {@code /tradery history} and to listeners.
 *
 * @param type a stable id, {@code <modid>:<category>/<action>}; Tradery's own are in {@link Reasons}
 * @param note optional free text (a player name, an item), may be {@code null}
 */
public record Reason(ResourceLocation type, @Nullable String note) {
    /** Longest note kept; longer notes are cut. */
    public static final int MAX_NOTE = 256;

    public Reason {
        if (note != null && note.length() > MAX_NOTE) {
            note = note.substring(0, MAX_NOTE);
        }
    }

    /**
     * @param type the reason type
     * @return a reason without a note
     */
    public static Reason of(ResourceLocation type) {
        return new Reason(type, null);
    }

    /**
     * @param type the reason type
     * @param note the note
     * @return a reason with a note
     */
    public static Reason of(ResourceLocation type, @Nullable String note) {
        return new Reason(type, note);
    }

    /**
     * @param note the note
     * @return a copy with another note
     */
    public Reason withNote(@Nullable String note) {
        return new Reason(type, note);
    }
}
