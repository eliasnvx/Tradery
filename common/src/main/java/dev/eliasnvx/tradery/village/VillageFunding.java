package dev.eliasnvx.tradery.village;

import java.util.HashMap;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.market.MarketPools;
import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Funding village projects from the board (SPEC "Вложения"): the player pays the cost (burned: withdrawn to
 * {@code tradery:server}), the project joins the village for good (once per village, from its level), the village
 * gains prosperity and its villagers remember the gift. A project's one-time action is planned before any money
 * moves: the iron golem needs room by the bell, the market stall and the other buildings a flat free spot within
 * {@link #STALL_RADIUS} blocks of it, the street lights dark streets ({@link VillageLamps}); without one, nothing is charged.
 * Server thread.
 */
public final class VillageFunding {
    /** The new market stall goes this close to the bell, or not at all. */
    public static final int STALL_RADIUS = 24;
    /** The golem appears this close to the bell (horizontally), up to this far above or below it. */
    static final int GOLEM_RANGE = 8;
    static final int GOLEM_HEIGHT_RANGE = 4;
    /** The stall's floor may be this far above or below the bell's block. */
    static final int STALL_HEIGHT_RANGE = 6;
    /** Ground is this many solid blocks thick: a roof or an awning (air underneath) is never ground. */
    static final int GROUND_DEPTH = 3;
    /** Village pieces taller than this are buildings (streets are flat); nothing is built inside them. */
    static final int STREET_HEIGHT = 2;
    /** Project buildings are placed as they are: no processor rolls anything. */
    static final Holder<StructureProcessorList> NO_PROCESSORS = Holder.direct(new StructureProcessorList(List.of()));
    /** Prosperity points per major unit of a project's cost. */
    static final double POINTS_PER_UNIT = 0.5;

    private VillageFunding() {
    }

    /** Funds project {@code projectId} for the village. */
    public static VillageBoard.Outcome fund(ServerPlayer player, VillageState village, ResourceLocation projectId) {
        return fund(player, village, projectId, STALL_RADIUS, null);
    }

    /**
     * Funds a project; what it builds may go within {@code radius} of the bell and, if given, only inside
     * {@code area} (tests keep it in their own space). Checks, then plans the action, then charges (the step that
     * may be refused), then applies.
     */
    public static VillageBoard.Outcome fund(ServerPlayer player, VillageState village, ResourceLocation projectId, int radius,
                                            @Nullable BoundingBox area) {
        MinecraftServer server = player.level().getServer();
        EconomyService economy = EconomyService.INSTANCE;
        Optional<VillageProjects.Project> found = VillageProjects.get(projectId);
        if (found.isEmpty() || !economy.isReady()) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.project_unknown", "This project is no longer offered"));
        }
        VillageProjects.Project project = found.get();
        Component name = name(projectId);
        Villages.touch(server, village);
        if (village.projects.contains(projectId.toString())) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.project_done", "%s is already built here", name));
        }
        if (Villages.level(village) < project.level()) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.project_level", "%s needs the village to be a %s", name,
                Villages.levelName(project.level())));
        }
        long cost = VillagerPurchases.toMinor(project.cost());
        if (cost == Long.MAX_VALUE) {
            return VillageBoard.Outcome.fail(Messages.failure(FailReason.LIMIT_EXCEEDED));
        }
        LedgerAccount account = economy.account(player.getUUID());
        if (account.balance(economy.defaultCurrency()) < cost) {
            return VillageBoard.Outcome.fail(Messages.failure(FailReason.INSUFFICIENT_FUNDS));
        }
        ServerLevel level = server.getLevel(village.dimension());
        if (level == null) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_village", "There's no village here"));
        }

        // Plan the one-time action before any money moves
        IronGolem golem = null;
        BuildSpot building = null;
        List<VillageLamps.Lamp> lamps = List.of();
        switch (project.action()) {
            case IRON_GOLEM -> {
                golem = prepareGolem(level, village.bell());
                if (golem == null) {
                    return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_room_golem", "There's no room for an iron golem by the bell"));
                }
            }
            case MARKET_STALL -> {
                building = findStallSpot(level, village, radius, area);
                if (building == null) {
                    return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_room_stall",
                        "There's no flat free spot for a market stall near the bell. Nothing was charged"));
                }
            }
            case STRUCTURE -> {
                // Built below, like the structure of any other project
            }
            case STREET_LAMPS -> {
                lamps = VillageLamps.plan(level, village, radius, area, true);
                if (lamps.isEmpty()) {
                    return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_room_lamps",
                        "There's no spot for lamp posts near the bell. Nothing was charged"));
                }
            }
            case PAVE_STREETS -> {
                if (VillageStreets.countStreets(level, village, Villages.config().boardRadius(), area) == 0) {
                    return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_streets",
                        "There are no streets to pave near the bell. Nothing was charged"));
                }
            }
            case NONE -> {
            }
        }
        // A project with a structure builds it too (the watch post next to its golem)
        if (building == null && !project.structure().isEmpty()) {
            building = findSpot(level, village, MarketPools.template(pool(village), project.structure()), NO_PROCESSORS, radius, area);
            if (building == null) {
                if (golem != null) {
                    golem.discard();
                }
                return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_room_building",
                    "There's no flat free spot for %s near the bell. Nothing was charged", name));
            }
        }

        if (cost > 0) {
            Reason reason = Reason.of(Reasons.VILLAGE_PROJECT, projectId + " @ " + village.key());
            TransactionResult result = economy.transfer(account, economy.serverAccount(), cost, 0, reason);
            if (result instanceof TransactionResult.Failure failure) {
                if (golem != null) {
                    golem.discard();
                }
                return VillageBoard.Outcome.fail(Messages.failure(failure));
            }
        }

        // Paid: nothing below refuses (a building that fails to place after all is refunded)
        if (building != null && !place(level, building)) {
            Tradery.LOGGER.error("The building for {} ({}) could not be placed at {}; refunding", village.key(), projectId, building.origin());
            if (cost > 0) {
                economy.transfer(economy.serverAccount(), account, cost, 0, Reason.of(Reasons.VILLAGE_PROJECT, "refund " + projectId));
            }
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_room_building",
                "There's no flat free spot for %s near the bell. Nothing was charged", name));
        }
        village.projects.add(projectId.toString());
        VillageEconomy.get(server).setDirty();
        if (golem != null) {
            if (building != null) {
                // The golem stands by its post, when there's room there
                BoundingBox box = building.box();
                BlockPos post = golemSpot(level, new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2));
                if (post != null) {
                    golem.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, golem.getYRot(), 0.0f);
                }
            }
            level.addFreshEntity(golem);
        }
        if (building != null) {
            // The building (chunk) and the money (saved data) reach the disk together
            TradePersistence.traded(level, building.origin());
            VillageStreets.connect(level, village, building.box());
        }
        if (project.action() == VillageProjects.Action.PAVE_STREETS) {
            VillageStreets.pave(level, village, Villages.config().boardRadius(), area);
            TradePersistence.traded(level, village.bell());
        }
        VillageLamps.place(level, village, lamps);
        Villages.addProsperity(server, village, Villages.points(cost, POINTS_PER_UNIT));
        VillageBoard.gossip(server, village, player.getUUID(), GossipType.MINOR_POSITIVE, GossipType.REPUTATION_CHANGE_PER_EVENT);
        return VillageBoard.Outcome.done(Messages.tr("tradery.board.funded", "You funded %s for %s. The village thanks you", name,
            Messages.coins(cost)));
    }

    /**
     * A donation to the village's treasury from the board: the player pays {@code amount} (burned: withdrawn to
     * {@code tradery:server}), the treasury grows by it above its income cap, the village gains prosperity as for a
     * project and its villagers remember the gift. Refused, with nothing moved, for a non-positive amount or one the
     * player doesn't have.
     */
    public static VillageBoard.Outcome donate(ServerPlayer player, VillageState village, long amount) {
        MinecraftServer server = player.level().getServer();
        EconomyService economy = EconomyService.INSTANCE;
        if (!economy.isReady()) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_village", "There's no village here"));
        }
        if (amount <= 0) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.donate_invalid", "Enter an amount to donate"));
        }
        Villages.touch(server, village);
        LedgerAccount account = economy.account(player.getUUID());
        if (account.balance(economy.defaultCurrency()) < amount) {
            return VillageBoard.Outcome.fail(Messages.failure(FailReason.INSUFFICIENT_FUNDS));
        }
        Reason reason = Reason.of(Reasons.VILLAGE_DONATION, village.key());
        TransactionResult result = economy.transfer(account, economy.serverAccount(), amount, 0, reason);
        if (result instanceof TransactionResult.Failure failure) {
            return VillageBoard.Outcome.fail(Messages.failure(failure));
        }
        Villages.addToTreasury(server, village, amount);
        Villages.addProsperity(server, village, Villages.points(amount, POINTS_PER_UNIT));
        VillageBoard.gossip(server, village, player.getUUID(), GossipType.MINOR_POSITIVE, GossipType.REPUTATION_CHANGE_PER_EVENT);
        return VillageBoard.Outcome.done(Messages.tr("tradery.board.donated", "You gave %s to the treasury of %s. The village thanks you",
            Messages.coins(amount), Villages.displayName(village)));
    }

    /** A project's name: lang {@code tradery.project.<ns>.<path>}, the path when a data pack didn't add one. */
    public static Component name(ResourceLocation project) {
        return Component.translatableWithFallback("tradery.project." + project.getNamespace() + "." + project.getPath(), project.getPath());
    }

    // ------------------------------------------------------------------ iron golem

    /** A village golem (not player-made) standing on solid ground near the bell, not in the world yet; null without room. */
    static @Nullable IronGolem prepareGolem(ServerLevel level, BlockPos bell) {
        BlockPos spot = golemSpot(level, bell);
        if (spot == null) {
            return null;
        }
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem == null) {
            return null;
        }
        golem.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        golem.setPlayerCreated(false);
        return golem;
    }

    /** Nearest spot to the bell with a solid floor and room for a golem. */
    static @Nullable BlockPos golemSpot(ServerLevel level, BlockPos bell) {
        for (BlockPos offset : ringOffsets(GOLEM_RANGE)) {
            // Nearest the given height first, so the ground beats a roof above it
            for (int step = 0; step <= 2 * GOLEM_HEIGHT_RANGE; step++) {
                int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
                BlockPos pos = bell.offset(offset.getX(), dy, offset.getZ());
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState floor = level.getBlockState(pos.below());
                if (floor.isFaceSturdy(level, pos.below(), Direction.UP) && floor.getFluidState().isEmpty()
                    && level.getBlockState(pos).getFluidState().isEmpty()
                    && level.noCollision(EntityType.IRON_GOLEM.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static final Map<Integer, List<BlockPos>> RINGS = new HashMap<>();

    /** Horizontal offsets within {@code radius} (a square), nearest first; built once per radius. */
    static List<BlockPos> ringOffsets(int radius) {
        return RINGS.computeIfAbsent(radius, VillageFunding::buildRing);
    }

    private static List<BlockPos> buildRing(int radius) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new BlockPos(dx, 0, dz));
            }
        }
        offsets.sort(Comparator.comparingInt((BlockPos p) -> p.getX() * p.getX() + p.getZ() * p.getZ())
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    // ------------------------------------------------------------------ market stall

    /**
     * Where a building goes.
     *
     * @param origin   the template's origin (its floor is at the ground's top layer)
     * @param rotation the template's rotation; the template's north side faces the bell when it can
     * @param box      blocks the building takes
     */
    public record BuildSpot(StructurePoolElement element, BlockPos origin, Rotation rotation, BoundingBox box) {
    }

    /** The template pool of the village's type ({@code plains} for types without templates of their own). */
    static String pool(VillageState village) {
        String type = village.census().type();
        String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        String pool = path.equals("snow") ? "snowy" : path;
        return MarketPools.VILLAGE_TYPES.contains(pool) ? pool : "plains";
    }

    /** The village type's market stall template. */
    static ResourceLocation stallTemplate(VillageState village) {
        return MarketPools.template(pool(village), MarketPools.STALL);
    }

    /** Nearest flat free spot for the village's market stall (offers rolled by the market processor). */
    public static @Nullable BuildSpot findStallSpot(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area) {
        Optional<Holder.Reference<StructureProcessorList>> processors = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
            .getHolder(MarketPools.PROCESSORS);
        if (processors.isEmpty()) {
            Tradery.LOGGER.warn("No processor list {}", MarketPools.PROCESSORS.location());
            return null;
        }
        return findSpot(level, village, stallTemplate(village), processors.get(), radius, area);
    }

    /**
     * Nearest flat free spot for {@code template} within {@code radius} blocks of the bell, the way village houses
     * stand: every column of the footprint has its ground at the same height, near the bell's, with a sturdy dry top
     * (paths and farmland aren't sturdy, so streets and fields stay) that is real ground, {@link #GROUND_DEPTH} solid
     * blocks thick (a roof or an awning has air under it); no village building (a structure piece taller than a
     * street) under the footprint; above it only air or plants; nobody standing there. Spots beside a street (a dirt
     * path next to the footprint) come first, then any. With {@code area}, the building must fit inside it.
     */
    public static @Nullable BuildSpot findSpot(ServerLevel level, VillageState village, ResourceLocation template,
                                               Holder<StructureProcessorList> processors, int radius, @Nullable BoundingBox area) {
        StructureTemplateManager templates = level.getServer().getStructureManager();
        if (templates.get(template).isEmpty()) {
            Tradery.LOGGER.warn("No structure template {}", template);
            return null;
        }
        StructurePoolElement element = MarketPools.element(template, processors);
        Map<Rotation, BoundingBox> boxes = new EnumMap<>(Rotation.class);
        for (Rotation rotation : Rotation.values()) {
            boxes.put(rotation, element.getBoundingBox(templates, BlockPos.ZERO, rotation));
        }
        BlockPos bell = village.bell();
        for (boolean byStreet : new boolean[] {true, false}) {
            for (BlockPos offset : ringOffsets(radius)) {
                int x = bell.getX() + offset.getX();
                int z = bell.getZ() + offset.getZ();
                for (Rotation rotation : rotationsFacing(offset)) {
                    BoundingBox atZero = boxes.get(rotation);
                    BoundingBox flat = atZero.moved(x, 0, z);
                    if (area != null && (flat.minX() < area.minX() || flat.maxX() > area.maxX() || flat.minZ() < area.minZ() || flat.maxZ() > area.maxZ())) {
                        continue;
                    }
                    int ground = flatGround(level, flat);
                    if (ground == Integer.MIN_VALUE || Math.abs(ground - (bell.getY() - 1)) > STALL_HEIGHT_RANGE) {
                        continue;
                    }
                    BoundingBox box = atZero.moved(x, ground, z);
                    if (byStreet && !besideStreet(level, box, ground)) {
                        continue;
                    }
                    if ((area == null || area.isInside(box.minX(), box.minY(), box.minZ())) && free(level, box) && !inVillageBuilding(level, box)) {
                        return new BuildSpot(element, new BlockPos(x, ground, z), rotation, box);
                    }
                }
            }
        }
        return null;
    }

    /** A dirt path touches the footprint's edge at ground level. */
    private static boolean besideStreet(ServerLevel level, BoundingBox box, int ground) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            if (level.getBlockState(pos.set(x, ground, box.minZ() - 1)).is(Blocks.DIRT_PATH)
                || level.getBlockState(pos.set(x, ground, box.maxZ() + 1)).is(Blocks.DIRT_PATH)) {
                return true;
            }
        }
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            if (level.getBlockState(pos.set(box.minX() - 1, ground, z)).is(Blocks.DIRT_PATH)
                || level.getBlockState(pos.set(box.maxX() + 1, ground, z)).is(Blocks.DIRT_PATH)) {
                return true;
            }
        }
        return false;
    }

    /** Some column of the footprint lies in a village structure piece taller than a street: a house, a farm hut. */
    private static boolean inVillageBuilding(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (inVillageBuilding(level, pos.set(x, box.minY() + 1, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean inVillageBuilding(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        if (!start.isValid()) {
            return false;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece.getBoundingBox().isInside(pos) && piece.getBoundingBox().getYSpan() > STREET_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    /** Solid, dry blocks from {@code top} down for {@link #GROUND_DEPTH}: ground, not a roof. */
    static boolean solidGround(ServerLevel level, BlockPos top) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < GROUND_DEPTH; dy++) {
            BlockState state = level.getBlockState(pos.set(top.getX(), top.getY() - dy, top.getZ()));
            if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** The four rotations, the one whose counter (the template's north side) faces back to the bell first. */
    private static List<Rotation> rotationsFacing(BlockPos offsetFromBell) {
        Direction towardsBell = Direction.getNearest(-offsetFromBell.getX(), 0, -offsetFromBell.getZ());
        List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
        rotations.sort(Comparator.comparingInt((Rotation r) -> r.rotate(Direction.NORTH) == towardsBell ? 0 : 1));
        return rotations;
    }

    /** The top block's height when every column of the box's footprint has the same sturdy, dry top; else MIN_VALUE. */
    private static int flatGround(ServerLevel level, BoundingBox box) {
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return Integer.MIN_VALUE;
        }
        int ground = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (ground != Integer.MIN_VALUE && top != ground) {
                    return Integer.MIN_VALUE;
                }
                ground = top;
                pos.set(x, top, z);
                BlockState state = level.getBlockState(pos);
                if (!state.isFaceSturdy(level, pos, Direction.UP) || !state.getFluidState().isEmpty() || !solidGround(level, pos)) {
                    return Integer.MIN_VALUE;
                }
            }
        }
        return ground;
    }

    /** Above the floor only air and replaceable plants, no fluids, nobody in the way. */
    private static boolean free(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY() + 1; y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir() && (!state.canBeReplaced() || !state.getFluidState().isEmpty())) {
                        return false;
                    }
                }
            }
        }
        AABB area = new AABB(box.minX(), box.minY() + 1, box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        return level.getEntitiesOfClass(LivingEntity.class, area).isEmpty();
    }

    /** Clears the plants in the way and places the building like village generation does. */
    public static boolean place(ServerLevel level, BuildSpot spot) {
        BoundingBox box = spot.box();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY() + 1; y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        return spot.element().place(level.getServer().getStructureManager(), level, level.structureManager(),
            level.getChunkSource().getGenerator(), spot.origin(), spot.origin(), spot.rotation(), box, RandomSource.create(),
            LiquidSettings.APPLY_WATERLOGGING, false);
    }
}
