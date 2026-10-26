package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The village's name and level, fading in at the top of the screen when the player enters a village or it reaches a
 * new level (SPEC "Доска деревни"): the server sends {@link TraderyPayloads.VillageTitlePayload}, the rows are
 * measured then, and drawing allocates nothing. Position and size from {@code villageTitle} in the client config;
 * the middle of the screen stays clear of minimaps in the corners.
 */
public final class VillageTitle {
    private static final int NAME = 0xFFD75E;
    private static final int LEVEL = 0xFFFFFF;
    private static final int FESTIVAL = 0xFF7AE0;
    private static final long FADE_IN_MS = 400;
    private static final long HOLD_MS = 3200;
    private static final long FADE_OUT_MS = 1200;
    private static final int ROW = 12;

    private static Component name = Component.empty();
    private static Component level = Component.empty();
    private static int nameWidth;
    private static int levelWidth;
    private static long shownAt = -1;
    private static boolean festival;

    private VillageTitle() {
    }

    /** The server says the player entered a village (or left: nothing to show). */
    public static void onTitle(TraderyPayloads.VillageTitlePayload payload) {
        if (!payload.known()) {
            shownAt = -1;
            return;
        }
        Font font = Minecraft.getInstance().font;
        name = payload.name();
        level = payload.festival() ? Component.translatable("tradery.festival.title") : Villages.levelName(payload.level());
        nameWidth = font.width(name);
        levelWidth = font.width(level);
        festival = payload.festival();
        shownAt = System.currentTimeMillis();
    }

    public static void render(GuiGraphics graphics) {
        if (shownAt < 0) {
            return;
        }
        ClientConfig.VillageTitle config = TraderyConfig.client().villageTitle();
        Minecraft minecraft = Minecraft.getInstance();
        long age = System.currentTimeMillis() - shownAt;
        if (age > FADE_IN_MS + HOLD_MS + FADE_OUT_MS || age < 0) {
            shownAt = -1;
            return;
        }
        if (!config.enabled() || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        float opacity = age < FADE_IN_MS ? age / (float) FADE_IN_MS
            : age < FADE_IN_MS + HOLD_MS ? 1
            : 1 - (age - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
        int alpha = Mth.clamp((int) (255 * opacity), 0, 255);
        if (alpha < 8) {
            // The font treats a tiny alpha as opaque
            return;
        }
        Font font = minecraft.font;
        float scale = (float) config.scale();
        int width = Math.max(nameWidth, levelWidth);
        int x = Math.round((graphics.guiWidth() - width * scale) / 2);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, config.offsetY(), 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, name, (width - nameWidth) / 2, 0, (alpha << 24) | NAME, true);
        graphics.drawString(font, level, (width - levelWidth) / 2, ROW, (alpha << 24) | (festival ? FESTIVAL : LEVEL), true);
        pose.popPose();
    }

    public static void reset() {
        shownAt = -1;
    }
}
