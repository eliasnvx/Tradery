package dev.eliasnvx.tradery.neoforge.compat;

import dev.eliasnvx.tradery.compat.rei.TraderyReiPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/** REI on NeoForge discovers client plugins by this annotation; the plugin itself is shared. */
@REIPluginClient
public final class TraderyReiPluginNeoForge extends TraderyReiPlugin {
}
