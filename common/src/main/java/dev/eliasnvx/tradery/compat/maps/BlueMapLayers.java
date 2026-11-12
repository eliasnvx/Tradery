package dev.eliasnvx.tradery.compat.maps;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** BlueMap: a marker set per layer on every map of every world, rebuilt from {@link MapPins}. Loaded only with BlueMap. */
final class BlueMapLayers {
    static final String VILLAGES = "tradery_villages";
    static final String SHOPS = "tradery_shops";

    private BlueMapLayers() {
    }

    static void update(MinecraftServer server, List<MapPins.Pin> villages, boolean villagesOn, List<MapPins.Pin> shops, boolean shopsOn) {
        Optional<BlueMapAPI> api = BlueMapAPI.getInstance();
        if (api.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Optional<BlueMapWorld> world = api.get().getWorld(level);
            if (world.isEmpty()) {
                continue;
            }
            for (BlueMapMap map : world.get().getMaps()) {
                fill(map.getMarkerSets(), VILLAGES, "Tradery villages", villagesOn, villages, level);
                fill(map.getMarkerSets(), SHOPS, "Tradery shops", shopsOn, shops, level);
            }
        }
    }

    private static void fill(Map<String, MarkerSet> sets, String id, String label, boolean on, List<MapPins.Pin> pins, ServerLevel level) {
        if (!on) {
            sets.remove(id);
            return;
        }
        MarkerSet set = sets.computeIfAbsent(id, key -> MarkerSet.builder().label(label).toggleable(true).defaultHidden(false).build());
        set.getMarkers().clear();
        for (MapPins.Pin pin : pins) {
            if (pin.dimension() != level.dimension()) {
                continue;
            }
            set.put(pin.id(), POIMarker.builder()
                .label(pin.label())
                .detail(pin.html())
                .position(pin.pos().getX() + 0.5, pin.pos().getY() + 1.0, pin.pos().getZ() + 0.5)
                .build());
        }
    }

    /** Takes the layers off every map (the server stops). */
    static void clear() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapMap map : api.getMaps()) {
                map.getMarkerSets().remove(VILLAGES);
                map.getMarkerSets().remove(SHOPS);
            }
        });
    }
}
