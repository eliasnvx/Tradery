package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;

/**
 * The balance on screen: a small panel in a corner with a coin icon, a number that rolls to its new value, and
 * +/- popups that float away from the corner and fade out.
 */
public final class BalanceHud {
    /** No GUI sprite atlas on 1.20.1: the coin is drawn as a plain texture. */
    static final ResourceLocation COIN_TEXTURE = Tradery.id("textures/gui/sprites/hud/coin.png");
    /** Size of {@link #COIN_TEXTURE} in pixels; drawn 1:1. */
    private static final int COIN_TEXTURE_SIZE = 9;
    private static final int ICON = 9;
    private static final int PAD = 3;
    private static final int GAP = 2;
    private static final long COUNT_UP_MS = 300;
    private static final long POPUP_MS = 2000;
    private static final int POPUP_RISE = 14;
    private static final int MAX_POPUPS = 3;
    /** Vanilla hotbar half width plus the offhand slot / attack indicator next to it. */
    private static final int HOTBAR_HALF_WIDTH = 91 + 29;
    /** Hotbar, health/food rows and the experience bar. */
    private static final int HOTBAR_CLEARANCE = 50;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int PANEL = 0x90000000;
    private static final int GAIN = 0x55FF55;
    private static final int LOSS = 0xFF5555;

    private record Popup(long delta, long start) {
    }

    private static final Popup[] popups = new Popup[MAX_POPUPS];
    private static long shownFrom;
    private static long shownTo;
    private static long animStart;
    private static long lastShown = Long.MIN_VALUE;
    private static String text = "";
    private static boolean textDirty = true;
    /** Format the cached text was built with; a config change (file, key, command) rebuilds it. */
    private static ClientConfig.Format textFormat = ClientConfig.Format.FULL;

    private BalanceHud() {
    }

    static void onBalance(long balance, boolean jump) {
        long now = Util.getMillis();
        boolean animate = !jump && TraderyConfig.client().hud().countUp();
        shownFrom = animate ? current(now) : balance;
        shownTo = balance;
        animStart = now;
        textDirty = true;
    }

    static void addPopup(long delta) {
        if (delta == 0 || !TraderyConfig.client().hud().popups()) {
            return;
        }
        System.arraycopy(popups, 0, popups, 1, MAX_POPUPS - 1);
        popups[0] = new Popup(delta, Util.getMillis());
    }

    static void invalidateText() {
        textDirty = true;
    }

    static void reset() {
        java.util.Arrays.fill(popups, null);
        shownFrom = shownTo = 0;
        lastShown = Long.MIN_VALUE;
        text = "";
        textDirty = true;
    }

    private static long current(long now) {
        long elapsed = now - animStart;
        if (elapsed >= COUNT_UP_MS || shownFrom == shownTo) {
            return shownTo;
        }
        double t = elapsed / (double) COUNT_UP_MS;
        double eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
        return shownFrom + Math.round((shownTo - shownFrom) * eased);
    }

    public static void render(GuiGraphics graphics) {
        ClientConfig.Hud config = TraderyConfig.client().hud();
        Minecraft minecraft = Minecraft.getInstance();
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (!config.enabled() || currency == null || !ClientEconomy.isActive() || minecraft.player == null
            || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        long now = Util.getMillis();
        long shown = current(now);
        if (textDirty || shown != lastShown || config.format() != textFormat) {
            // No symbol: the coin icon already says what the number is
            text = config.format() == ClientConfig.Format.SHORT ? currency.formatShortNumber(shown) : currency.formatNumber(shown);
            lastShown = shown;
            textFormat = config.format();
            textDirty = false;
        }

        Font font = minecraft.font;
        float scale = (float) config.scale();
        int width = PAD + ICON + GAP + font.width(text) + PAD;
        int height = PAD + ICON + PAD - 1;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int scaledWidth = Mth.ceil(width * scale);
        int scaledHeight = Mth.ceil(height * scale);
        ClientConfig.Corner corner = config.corner();
        int x = corner.right() ? screenWidth - config.offsetX() - scaledWidth : config.offsetX();
        int y = corner.bottom() ? screenHeight - config.offsetY() - scaledHeight : config.offsetY();
        if (corner.bottom() && overlapsHotbar(x, scaledWidth, screenWidth)) {
            y = Math.min(y, screenHeight - HOTBAR_CLEARANCE - scaledHeight);
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.fill(0, 0, width, height, PANEL);
        graphics.blit(COIN_TEXTURE, PAD, PAD - 1, ICON, ICON, 0, 0, COIN_TEXTURE_SIZE, COIN_TEXTURE_SIZE, COIN_TEXTURE_SIZE, COIN_TEXTURE_SIZE);
        graphics.drawString(font, text, PAD + ICON + GAP, PAD, TEXT, true);

        // Popups float away from the corner: up from a bottom corner, down from a top one
        int direction = corner.bottom() ? -1 : 1;
        int baseY = corner.bottom() ? -font.lineHeight - 1 : height + 1;
        for (int i = 0; i < MAX_POPUPS; i++) {
            Popup popup = popups[i];
            if (popup == null) {
                continue;
            }
            long age = now - popup.start();
            if (age >= POPUP_MS) {
                popups[i] = null;
                continue;
            }
            float progress = age / (float) POPUP_MS;
            int alpha = Mth.clamp((int) (255 * (1 - progress * progress)), 8, 255);
            String line = (popup.delta() > 0 ? "+" : "-")
                + (config.format() == ClientConfig.Format.SHORT ? currency.formatShortNumber(Math.abs(popup.delta())) : currency.formatNumber(Math.abs(popup.delta())));
            int color = (alpha << 24) | (popup.delta() > 0 ? GAIN : LOSS);
            int lineX = corner.right() ? width - font.width(line) : 0;
            int lineY = baseY + direction * (i * (font.lineHeight + 1) + Math.round(progress * POPUP_RISE));
            graphics.drawString(font, line, lineX, lineY, color, true);
        }
        pose.popPose();
    }

    private static boolean overlapsHotbar(int x, int width, int screenWidth) {
        int left = screenWidth / 2 - HOTBAR_HALF_WIDTH;
        int right = screenWidth / 2 + HOTBAR_HALF_WIDTH;
        return x < right && x + width > left;
    }
}
