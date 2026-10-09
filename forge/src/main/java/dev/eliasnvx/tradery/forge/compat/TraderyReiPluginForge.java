package dev.eliasnvx.tradery.forge.compat;

import dev.eliasnvx.tradery.compat.rei.TraderyReiPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/** REI on Forge discovers client plugins by this annotation; the plugin itself is shared. */
@REIPluginClient
public final class TraderyReiPluginForge extends TraderyReiPlugin {
}
