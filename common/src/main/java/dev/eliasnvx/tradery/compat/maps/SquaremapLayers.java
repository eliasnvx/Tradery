package dev.eliasnvx.tradery.compat.maps;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/** squaremap: a layer per kind on every enabled world, rebuilt from {@link MapPins}. Loaded only with squaremap. */
final class SquaremapLayers {
    static final Key VILLAGES = Key.of("tradery_villages");
    static final Key SHOPS = Key.of("tradery_shops");
    /** A shop is a gold coin on the map: a few blocks' circle disappears into the grass. */
    static final Key SHOP_ICON = Key.of("tradery_shop");
    private static final String SHOP_ICON_PATH = "/assets/tradery/textures/item/gold_coin.png";
    private static final int SHOP_ICON_SIZE = 16;
    private static final Color VILLAGE = new Color(0xE0A800);
    private static final Color SHOP = new Color(0x3FA34D);

    private SquaremapLayers() {
    }

    private static Optional<Squaremap> api() {
        try {
            return Optional.of(SquaremapProvider.get());
        } catch (IllegalStateException notLoadedYet) {
            return Optional.empty();
        }
    }

    static void update(MinecraftServer server, List<MapPins.Pin> villages, boolean villagesOn, List<MapPins.Pin> shops, boolean shopsOn) {
        Optional<Squaremap> api = api();
        if (api.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Optional<MapWorld> world = api.get().getWorldIfEnabled(WorldIdentifier.parse(level.dimension().location().toString()));
            if (world.isEmpty()) {
                continue;
            }
            Registry<LayerProvider> layers = world.get().layerRegistry();
            fill(layers, VILLAGES, "Tradery villages", villagesOn, villages, level, 12, VILLAGE, false);
            fill(layers, SHOPS, "Tradery shops", shopsOn, shops, level, 3, SHOP, shopsOn && shopIcon(api.get()));
        }
    }

    private static void fill(Registry<LayerProvider> layers, Key key, String label, boolean on, List<MapPins.Pin> pins, ServerLevel level,
                             double radius, Color color, boolean icon) {
        if (!on) {
            if (layers.hasEntry(key)) {
                layers.unregister(key);
            }
            return;
        }
        if (!layers.hasEntry(key)) {
            layers.register(key, SimpleLayerProvider.builder(label).showControls(true).defaultHidden(false).layerPriority(10).zIndex(250).build());
        }
        if (!(layers.get(key) instanceof SimpleLayerProvider layer)) {
            return;
        }
        layer.clearMarkers();
        for (MapPins.Pin pin : pins) {
            if (pin.dimension() != level.dimension()) {
                continue;
            }
            Point point = Point.of(pin.pos().getX() + 0.5, pin.pos().getZ() + 0.5);
            Marker marker = icon
                ? Marker.icon(point, SHOP_ICON, SHOP_ICON_SIZE).markerOptions(MarkerOptions.builder().hoverTooltip(pin.html()))
                : Marker.circle(point, radius)
                    .markerOptions(MarkerOptions.builder().hoverTooltip(pin.html()).strokeColor(color).fillColor(color).fillOpacity(0.35));
            layer.addMarker(Key.of(pin.id()), marker);
        }
    }

    /** Registers the shop icon with squaremap once; false (circles then) if the image can't be read. */
    private static boolean shopIcon(Squaremap api) {
        if (api.iconRegistry().hasEntry(SHOP_ICON)) {
            return true;
        }
        try (InputStream in = SquaremapLayers.class.getResourceAsStream(SHOP_ICON_PATH)) {
            BufferedImage image = in == null ? null : ImageIO.read(in);
            if (image == null) {
                return false;
            }
            api.iconRegistry().register(SHOP_ICON, image);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Takes the layers off every world (the server stops). */
    static void clear() {
        api().ifPresent(api -> {
            for (MapWorld world : api.mapWorlds()) {
                if (world.layerRegistry().hasEntry(VILLAGES)) {
                    world.layerRegistry().unregister(VILLAGES);
                }
                if (world.layerRegistry().hasEntry(SHOPS)) {
                    world.layerRegistry().unregister(SHOPS);
                }
            }
        });
    }
}
