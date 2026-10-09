package dev.eliasnvx.tradery.util;

import dev.eliasnvx.tradery.Tradery;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Amounts in text with the coin icon in place of the currency symbol. The icon is a glyph of Tradery's own bitmap
 * font ({@code assets/tradery/font/coin.json}): one private-use character drawn from the 8x8 coin texture, so it
 * works in any {@code Component} (screens, tooltips, chat). Plain-text consumers ({@code getString()}) get that
 * private-use character, not the symbol.
 */
public final class MoneyText {
    /** Bitmap font with the coin: 7x7 coin on an 8x8 canvas, cap height of the default font, 1 px gap after it. */
    public static final ResourceLocation COIN_FONT = Tradery.id("coin");
    /** The coin's character in {@link #COIN_FONT} (private use area, so no real text maps to it). */
    public static final String COIN_GLYPH = "\uE000";
    private static final int WHITE = 0xFFFFFF;
    /** White, so the text color around it doesn't tint the coin. */
    private static final Style COIN_STYLE = Style.EMPTY.withFont(COIN_FONT).withColor(WHITE);

    private MoneyText() {
    }

    /** The coin glyph. White, so the text color around it doesn't tint it. {@code symbol} is kept for the API: the glyph replaces it. */
    public static MutableComponent coin(String symbol) {
        return Component.literal(COIN_GLYPH).setStyle(COIN_STYLE);
    }

    /** Coin + number: "(coin)2.50". The number takes the style of the surrounding text. */
    public static MutableComponent of(String number, String symbol) {
        return Component.empty().append(coin(symbol)).append(number);
    }
}
