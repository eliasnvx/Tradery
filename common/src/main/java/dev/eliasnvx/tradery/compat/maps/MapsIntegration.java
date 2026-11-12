package dev.eliasnvx.tradery.compat.maps;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Server web maps (SPEC "Интеграции 1.2", config {@code integrations.maps}): BlueMap and squaremap get a layer of
 * Tradery's villages and one of players' shops, rebuilt every {@code updateSeconds} on the server thread. The map
 * classes load only when their mod is installed; a failing map never breaks the server tick.
 */
public final class MapsIntegration {
    public static final String BLUEMAP = "bluemap";
    public static final String SQUAREMAP = "squaremap";
    private static long nextUpdate;
    private static boolean failed;

    private MapsIntegration() {
    }

    private static boolean bluemap() {
        return Platform.get().isModLoaded(BLUEMAP);
    }

    private static boolean squaremap() {
        return Platform.get().isModLoaded(SQUAREMAP);
    }

    /** Overworld tick: rebuilds the layers when it's time. */
    public static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || failed || !(bluemap() || squaremap())) {
            return;
        }
        long now = level.getGameTime();
        ServerConfig.MapsSection config = TraderyConfig.server().integrations().maps();
        if (now < nextUpdate && nextUpdate - now <= config.updateSeconds() * 20L) {
            return;
        }
        nextUpdate = now + config.updateSeconds() * 20L;
        update(level.getServer(), config);
    }

    static void update(MinecraftServer server, ServerConfig.MapsSection config) {
        try {
            List<MapPins.Pin> villages = config.villages() ? MapPins.villages(server) : List.of();
            List<MapPins.Pin> shops = config.shops() ? MapPins.shops(server) : List.of();
            if (bluemap()) {
                BlueMapLayers.update(server, villages, config.villages(), shops, config.shops());
            }
            if (squaremap()) {
                SquaremapLayers.update(server, villages, config.villages(), shops, config.shops());
            }
        } catch (RuntimeException | LinkageError e) {
            // Another version of the map's API, or a bug: log once and leave the maps alone for this run
            failed = true;
            Tradery.LOGGER.error("Tradery's web map layers failed and are off until the next start", e);
        }
    }

    public static void onServerStopped() {
        try {
            if (bluemap()) {
                BlueMapLayers.clear();
            }
            if (squaremap()) {
                SquaremapLayers.clear();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The map is going away too
        }
        nextUpdate = 0;
        failed = false;
    }
}
