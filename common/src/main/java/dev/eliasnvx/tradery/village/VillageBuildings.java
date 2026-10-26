package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import dev.eliasnvx.tradery.market.MarketPools;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.CodecJsonReloadListener;
import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Level buildings (SPEC "Деревня строит сама"): data pack {@code data/<ns>/tradery/village_buildings/*.json}, one
 * building per level from the village's own money. While the next building is pending, {@link Villages#touch} puts
 * {@code villages.buildings.savingsShare} of the daily income into the village's {@link VillageState#savings fund};
 * once the fund covers the cost and the bell is loaded, the village builds it by the bell the way funded projects
 * are built ({@link VillageFunding#findSpot}), once a day at most, and names any sign in it after itself. Server
 * thread.
 */
public final class VillageBuildings {
    static final ResourceLocation LISTENER = Tradery.id("village_buildings");
    /** Loaded villages try to build every this many ticks. */
    static final int CHECK_TICKS = 200;
    /** Players this close to the bell hear of a new building (and a festival). */
    static final int ANNOUNCE_RADIUS = 64;
    private static final int SIGN_LINE = 15;

    /**
     * A building.
     *
     * @param level     village level that unlocks it
     * @param cost      major units, from the building fund
     * @param structure template name under {@code structure/village/<type>/}
     * @param market    the template has market vendors to stock (the market processor runs)
     */
    public record Building(ResourceLocation id, int level, BigDecimal cost, String structure, boolean market) {
    }

    record BuildingFile(int level, BigDecimal cost, String structure, boolean market) {
        static final Codec<BuildingFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("level", 2).forGetter(BuildingFile::level),
            ConfigCodecs.NON_NEGATIVE.fieldOf("cost").forGetter(BuildingFile::cost),
            Codec.STRING.fieldOf("structure").forGetter(BuildingFile::structure),
            Codec.BOOL.optionalFieldOf("market", false).forGetter(BuildingFile::market)
        ).apply(i, BuildingFile::new));
    }

    private static volatile List<Building> buildings = List.of();

    private VillageBuildings() {
    }

    /** Registers the data pack listener; called once by each loader at startup. */
    public static void registerReloadListeners() {
        Platform.get().registerServerReloadListener(LISTENER, new Loader());
    }

    /** Every building, by level then id. */
    public static List<Building> all() {
        return buildings;
    }

    public static Optional<Building> get(ResourceLocation id) {
        for (Building building : buildings) {
            if (building.id().equals(id)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /** The building the village saves for: the first unbuilt one its level allows, or null. */
    public static @Nullable Building next(VillageState village) {
        if (!Villages.config().buildings().enabled()) {
            return null;
        }
        int level = Villages.level(village);
        for (Building building : buildings) {
            if (building.level() <= level && !village.buildings.contains(building.id().toString())) {
                return building;
            }
        }
        return null;
    }

    public static boolean savingUp(VillageState village) {
        return next(village) != null;
    }

    /** The building's cost in minor units ({@code Long.MAX_VALUE} when it doesn't fit). */
    public static long costMinor(Building building) {
        return VillagerPurchases.toMinor(building.cost());
    }

    /** A building's name: lang {@code tradery.building.<ns>.<path>}, the path when a data pack didn't add one. */
    public static Component name(ResourceLocation building) {
        return Component.translatableWithFallback("tradery.building." + building.getNamespace() + "." + building.getPath(), building.getPath());
    }

    /** End of a level tick: every ten seconds, every loaded village with a pending building it can afford tries to build it. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0 || !Villages.config().buildings().enabled()) {
            return;
        }
        for (VillageState village : VillageEconomy.get(level.getServer()).all()) {
            if (village.dimension() != level.dimension() || !village.census().known() || !level.isLoaded(village.bell())) {
                continue;
            }
            Building next = next(village);
            if (next != null && village.savings >= costMinor(next)) {
                tryBuild(level, village, VillageFunding.STALL_RADIUS, null);
            }
        }
    }

    /**
     * Builds the pending building if the fund covers it and the village hasn't tried today: a spot within
     * {@code radius} of the bell, inside {@code area} if given. Returns what was built, or null (no spot: again
     * tomorrow).
     */
    public static @Nullable Building tryBuild(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area) {
        MinecraftServer server = level.getServer();
        Building next = next(village);
        long today = VillagerPurchases.today(server);
        if (next == null || village.buildDay == today) {
            return null;
        }
        long cost = costMinor(next);
        if (cost == Long.MAX_VALUE || village.savings < cost) {
            return null;
        }
        village.buildDay = today;
        VillageEconomy.get(server).setDirty();
        Holder<StructureProcessorList> processors = VillageFunding.NO_PROCESSORS;
        if (next.market()) {
            Optional<Holder.Reference<StructureProcessorList>> market = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(MarketPools.PROCESSORS);
            if (market.isPresent()) {
                processors = market.get();
            }
        }
        ResourceLocation template = MarketPools.template(VillageFunding.pool(village), next.structure());
        VillageFunding.BuildSpot spot = VillageFunding.findSpot(level, village, template, processors, radius, area);
        if (spot == null) {
            Tradery.LOGGER.debug("No spot for {} in {}; again tomorrow", next.id(), village.key());
            return null;
        }
        if (!VillageFunding.place(level, spot)) {
            Tradery.LOGGER.warn("{} could not be placed at {} for {}", next.id(), spot.origin(), village.key());
            return null;
        }
        village.savings -= cost;
        village.buildings.add(next.id().toString());
        VillageEconomy.get(server).setDirty();
        TradePersistence.traded(level, spot.origin());
        VillageStreets.connect(level, village, spot.box());
        nameSigns(level, spot.box(), village);
        Villages.addProsperity(server, village, Villages.points(cost, VillageFunding.POINTS_PER_UNIT));
        announce(level, village, next);
        return next;
    }

    /** Every sign in the box says the village's name, on both sides, waxed. */
    static void nameSigns(ServerLevel level, BoundingBox box, VillageState village) {
        List<Component> lines = nameLines(village);
        Component[] array = lines.toArray(Component[]::new);
        SignText text = new SignText(array, array.clone(), DyeColor.BLACK, false);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(text, true);
                sign.setText(text, false);
                sign.setWaxed(true);
                sign.setChanged();
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    /** The name in four sign lines of up to {@link #SIGN_LINE} characters, the coordinates on their own line. */
    static List<Component> nameLines(VillageState village) {
        String name = Villages.displayName(village).getString();
        List<String> parts = new ArrayList<>();
        int paren = name.indexOf(" (");
        if (paren > 0) {
            parts.add(name.substring(0, paren));
            parts.add(name.substring(paren + 1));
        } else {
            parts.add(name);
        }
        List<Component> lines = new ArrayList<>();
        for (String part : parts) {
            StringBuilder line = new StringBuilder();
            for (String word : part.split(" ")) {
                if (!line.isEmpty() && line.length() + 1 + word.length() > SIGN_LINE) {
                    lines.add(Component.literal(line.toString()));
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word.length() > SIGN_LINE ? word.substring(0, SIGN_LINE) : word);
            }
            if (!line.isEmpty()) {
                lines.add(Component.literal(line.toString()));
            }
        }
        while (lines.size() < 4) {
            lines.add(Component.empty());
        }
        return List.copyOf(lines.subList(0, 4));
    }

    private static void announce(ServerLevel level, VillageState village, Building building) {
        double radiusSqr = (double) ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
        BlockPos bell = village.bell();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5) <= radiusSqr) {
                player.sendSystemMessage(Messages.tr("tradery.village.built", "%s built %s", Villages.displayName(village), name(building.id())));
            }
        }
    }

    static void apply(Map<ResourceLocation, BuildingFile> files) {
        Map<ResourceLocation, Building> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BuildingFile> entry : new TreeMap<>(files).entrySet()) {
            BuildingFile file = entry.getValue();
            loaded.put(entry.getKey(), new Building(entry.getKey(), file.level(), file.cost(), file.structure(), file.market()));
        }
        List<Building> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparingInt(Building::level).thenComparing(b -> b.id().toString()));
        buildings = List.copyOf(sorted);
        Tradery.LOGGER.debug("Loaded {} village buildings", sorted.size());
    }

    private static final class Loader extends CodecJsonReloadListener<BuildingFile> {
        Loader() {
            super(BuildingFile.CODEC, FileToIdConverter.json("tradery/village_buildings"));
        }

        @Override
        protected void apply(Map<ResourceLocation, BuildingFile> files, ResourceManager manager, ProfilerFiller profiler) {
            VillageBuildings.apply(files);
        }
    }
}
