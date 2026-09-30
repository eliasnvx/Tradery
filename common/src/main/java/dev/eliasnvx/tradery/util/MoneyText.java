package dev.eliasnvx.tradery.util;

import dev.eliasnvx.tradery.Tradery;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Amounts in text with the coin icon in place of the currency symbol. The icon is an object text component: an 8x8
 * glyph from the GUI atlas, so it works in any {@code Component} (screens, tooltips). Plain-text consumers (logs,
 * narration, {@code getString()}) get the symbol instead.
 */
public final class MoneyText {
    /** 7x7 coin on an 8x8 canvas: cap height of the default font, with a 1 px gap after it. */
    public static final Identifier COIN_GLYPH = Tradery.id("icon/coin");
    private static final int WHITE = 0xFFFFFF;

    private MoneyText() {
    }

    /** The coin glyph. White, so the text color around it doesn't tint it. */
    public static MutableComponent coin(String symbol) {
        return Component.object(new AtlasSprite(AtlasIds.GUI, COIN_GLYPH), Component.literal(symbol)).withColor(WHITE);
    }

    /** Coin + number: "(coin)2.50". The number takes the style of the surrounding text. */
    public static MutableComponent of(String number, String symbol) {
        return Component.empty().append(coin(symbol)).append(number);
    }
}
