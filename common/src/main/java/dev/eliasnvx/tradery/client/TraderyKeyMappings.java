package dev.eliasnvx.tradery.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.client.KeyMapping;

import java.util.List;

/** Tradery's keys, in their own section of the Controls screen. The loaders register {@link #ALL}. */
public final class TraderyKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Tradery.id("main"));

    /** Unbound by default: every free key is taken by some other mod in a pack. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.tradery.toggle_hud", InputConstants.Type.KEYBOARD,
        InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final List<KeyMapping> ALL = List.of(TOGGLE_HUD);

    private TraderyKeyMappings() {
    }
}
