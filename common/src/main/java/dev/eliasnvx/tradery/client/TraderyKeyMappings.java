package dev.eliasnvx.tradery.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.List;

/** Tradery's keys, in their own section of the Controls screen. The loaders register {@link #ALL}. */
public final class TraderyKeyMappings {
    /** Translation key of the Controls section; key mappings name their category by it. */
    public static final String CATEGORY = "key.category.tradery.main";

    /** Unbound by default: every free key is taken by some other mod in a pack. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.tradery.toggle_hud", InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final List<KeyMapping> ALL = List.of(TOGGLE_HUD);

    private TraderyKeyMappings() {
    }
}
