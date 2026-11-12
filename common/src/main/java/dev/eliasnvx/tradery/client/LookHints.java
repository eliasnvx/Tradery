package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.platform.Platform;
import org.jetbrains.annotations.Nullable;

/**
 * Whether Tradery's own look-at hints (vending block, market counter, bell) are drawn ({@code vending.hint} in
 * client.json5). {@code AUTO} leaves them to Jade or WTHIT when one is installed: both draw the same lines in the same
 * place at the top of the screen.
 */
public final class LookHints {
    private static @Nullable Boolean tooltipMod;

    private LookHints() {
    }

    public static boolean enabled() {
        ClientConfig.HintMode mode = TraderyConfig.client().vending().hint();
        if (mode != ClientConfig.HintMode.AUTO) {
            return mode == ClientConfig.HintMode.ON;
        }
        if (tooltipMod == null) {
            tooltipMod = Platform.get().isModLoaded("jade") || Platform.get().isModLoaded("wthit");
        }
        return !tooltipMod;
    }
}
