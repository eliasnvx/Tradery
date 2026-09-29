package dev.eliasnvx.tradery.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Vanilla-looking panels and slot frames drawn with fills: no GUI texture to keep in sync with the layout. */
public final class Panels {
    public static final int LABEL = 0xFF404040;
    public static final int BODY = 0xFFC6C6C6;
    private static final int OUTLINE = 0xFF000000;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_BODY = 0xFF8B8B8B;
    private static final int INSET_DARK = 0xFF8B8B8B;

    private Panels() {
    }

    /** A raised panel with rounded corners, like container backgrounds. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, BODY);
        g.fill(x + 2, y + 2, x + 3, y + 3, LIGHT);
        g.fill(x + w - 3, y + h - 3, x + w - 2, y + h - 2, SHADOW);
    }

    /** A sunken area (for text fields and info boxes). */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET_DARK);
        g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
        g.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
    }

    /** A slot frame; {@code x, y} is the slot's item position (as in {@code Slot}). */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        int fx = x - 1;
        int fy = y - 1;
        g.fill(fx, fy, fx + 17, fy + 1, SLOT_DARK);
        g.fill(fx, fy, fx + 1, fy + 17, SLOT_DARK);
        g.fill(fx + 1, fy + 17, fx + 18, fy + 18, LIGHT);
        g.fill(fx + 17, fy + 1, fx + 18, fy + 18, LIGHT);
        g.fill(fx + 1, fy + 1, fx + 17, fy + 17, SLOT_BODY);
    }

    /** A sample slot: like a slot, with a dashed gold border so it reads as "copy, not storage". */
    public static void ghostSlot(GuiGraphicsExtractor g, int x, int y) {
        slot(g, x, y);
        int gold = 0xFFB88A1B;
        for (int i = 0; i < 18; i += 3) {
            g.fill(x - 1 + i, y - 2, x + i, y - 1, gold);
            g.fill(x - 1 + i, y + 17, x + i, y + 18, gold);
            g.fill(x - 2, y - 1 + i, x - 1, y + i, gold);
            g.fill(x + 17, y - 1 + i, x + 18, y + i, gold);
        }
    }

    /** Frames for the 27 + 9 player inventory slots at {@code (x, y)} (the first main slot). */
    public static void inventory(GuiGraphicsExtractor g, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + col * 18, y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + col * 18, y + 58);
        }
    }
}
