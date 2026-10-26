package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The village's streets (SPEC "Улицы"): new buildings get a path from their edge to the nearest street
 * ({@link #connect}), and the "Paved Streets" project turns every dirt path of the village into stone in the
 * village type's style, filling one-block dips and gaps ({@link #pave}), then again once a day for parts loaded
 * later. Vanilla streets are jigsaw pieces bent onto the terrain at generation; nothing here touches generation, it
 * only works on the loaded blocks. Server thread.
 */
public final class VillageStreets {
    /** The project that paves the streets; its id in {@link VillageState#projects()}. */
    public static final ResourceLocation PAVED = Tradery.id("paved_streets");
    /** A new building looks this far for a street to connect to. */
    static final int CONNECT_RANGE = 12;
    /** Blocks around the village structure's pieces that still count as its streets. */
    static final int VILLAGE_MARGIN = 4;
    static final int REPAVE_CHECK_TICKS = 1200;
    /** Village key → day its streets were last paved (in memory: after a restart they're simply checked again). */
    private static final Map<String, Long> PAVED_DAY = new HashMap<>();

    private VillageStreets() {
    }

    /** Whether the village has a funded project that paves its streets. */
    public static boolean paved(VillageState village) {
        for (String id : village.projects) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && VillageProjects.get(parsed).map(p -> p.action() == VillageProjects.Action.PAVE_STREETS).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** The village type's pavement, with an accent block now and then. */
    static BlockState pavement(VillageState village, RandomSource random) {
        Block block = switch (VillageFunding.pool(village)) {
            case "desert" -> random.nextInt(10) == 0 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE;
            case "taiga", "snowy" -> random.nextInt(8) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE;
            default -> random.nextInt(8) == 0 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE;
        };
        return block.defaultBlockState();
    }

    /** A dirt path or any of the pavements. */
    static boolean isStreet(BlockState state) {
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
            || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.CUT_SANDSTONE);
    }

    /** Ground a path can be laid on: grass and dirt, sand, gravel. */
    static boolean natural(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
            || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
    }

    private static boolean underSky(ServerLevel level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
    }

    /**
     * Lays a path from the edge of a new building (its box, floor at {@code box.minY()}) to the nearest street within
     * {@link #CONNECT_RANGE}: one bend, over natural ground within a block of the building's level, in the village's
     * pavement when its streets are paved, a dirt path otherwise. Returns the blocks laid (0: no street near, or
     * the way is blocked).
     */
    public static int connect(ServerLevel level, VillageState village, BoundingBox box) {
        int ground = box.minY();
        BlockPos target = nearestStreet(level, box, ground);
        if (target == null) {
            return 0;
        }
        RandomSource random = level.getRandom();
        boolean paved = paved(village);
        int x = Mth.clamp(target.getX(), box.minX(), box.maxX());
        int z = Mth.clamp(target.getZ(), box.minZ(), box.maxZ());
        int y = ground;
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (x != target.getX() || z != target.getZ()) {
            if (x != target.getX()) {
                x += Integer.signum(target.getX() - x);
            } else {
                z += Integer.signum(target.getZ() - z);
            }
            if (box.isInside(x, ground, z)) {
                continue;
            }
            if (x == target.getX() && z == target.getZ()) {
                break;
            }
            BlockPos top = naturalTop(level, pos.set(x, y, z));
            if (top == null) {
                break;
            }
            level.setBlock(top, paved ? pavement(village, random) : Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
            y = top.getY();
            placed++;
        }
        return placed;
    }

    /** The street block nearest to the box (under the sky, within a block of the ground level), or null. */
    static @Nullable BlockPos nearestStreet(ServerLevel level, BoundingBox box, int ground) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - CONNECT_RANGE; x <= box.maxX() + CONNECT_RANGE; x++) {
            for (int z = box.minZ() - CONNECT_RANGE; z <= box.maxZ() + CONNECT_RANGE; z++) {
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                    continue;
                }
                int distance = Math.max(0, Math.max(box.minX() - x, x - box.maxX())) + Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
                if (distance >= bestDistance) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    pos.set(x, ground + dy, z);
                    if (level.isLoaded(pos) && isStreet(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir() && underSky(level, pos)) {
                        best = pos.immutable();
                        bestDistance = distance;
                        break;
                    }
                }
            }
        }
        return best;
    }

    /** The natural ground block at the column within a block of {@code pos}, with only air (or a snow layer) above; null when none. */
    static @Nullable BlockPos naturalTop(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] {0, -1, 1}) {
            BlockPos at = pos.above(dy);
            if (!level.isLoaded(at)) {
                return null;
            }
            BlockState state = level.getBlockState(at);
            BlockState above = level.getBlockState(at.above());
            if (isStreet(state) && above.isAir()) {
                return null;
            }
            if (natural(state) && (above.isAir() || above.is(Blocks.SNOW)) && underSky(level, at)) {
                if (above.is(Blocks.SNOW)) {
                    level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                return at.immutable();
            }
        }
        return null;
    }

    /**
     * The blocks to look at for the village's streets: the whole village structure (all its pieces, a few blocks
     * around) when the bell belongs to a generated village, at least {@code radius} around the bell; only inside
     * {@code area} when given (tests).
     */
    static BoundingBox scanBox(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area) {
        BlockPos bell = village.bell();
        BoundingBox box = new BoundingBox(bell.getX() - radius, bell.getY() - 64, bell.getZ() - radius, bell.getX() + radius, bell.getY() + 64,
            bell.getZ() + radius);
        if (area == null) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE);
            if (start.isValid()) {
                box = BoundingBox.encapsulatingBoxes(java.util.List.of(box, start.getBoundingBox().inflatedBy(VILLAGE_MARGIN))).orElseThrow();
            }
            return box;
        }
        return new BoundingBox(Math.max(box.minX(), area.minX()), Math.max(box.minY(), area.minY()), Math.max(box.minZ(), area.minZ()),
            Math.min(box.maxX(), area.maxX()), Math.min(box.maxY(), area.maxY()), Math.min(box.maxZ(), area.maxZ()));
    }

    /** Dirt paths on top of their columns in the village (see {@link #scanBox}). */
    public static int countStreets(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area) {
        return streets(level, scanBox(level, village, radius, area)).size();
    }

    /** The top block of a loaded column inside the box, or null. Never loads a chunk. */
    private static @Nullable BlockPos top(ServerLevel level, BoundingBox box, int x, int z) {
        BlockPos column = new BlockPos(x, box.minY(), z);
        if (!level.hasChunkAt(column)) {
            return null;
        }
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        return box.isInside(top) ? top : null;
    }

    /** Every street block (path or pavement) on top of its column with air above, in the box. */
    static List<BlockPos> streetTops(ServerLevel level, BoundingBox box) {
        List<BlockPos> streets = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos top = top(level, box, x, z);
                if (top != null && openStreet(level, top)) {
                    streets.add(top);
                }
            }
        }
        return streets;
    }

    private static List<BlockPos> streets(ServerLevel level, BoundingBox box) {
        List<BlockPos> streets = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos top = top(level, box, x, z);
                if (top != null && level.getBlockState(top).is(Blocks.DIRT_PATH)) {
                    streets.add(top);
                }
            }
        }
        return streets;
    }

    /** The block's state, or air when its chunk isn't loaded (never loads one). */
    private static BlockState state(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    /** A street block with nothing on it. */
    private static boolean openStreet(ServerLevel level, BlockPos pos) {
        return isStreet(state(level, pos)) && state(level, pos.above()).isAir();
    }

    /**
     * Paves every dirt path of the village (see {@link #scanBox}), fills one-block dips in them (a path block with
     * street a block higher on both sides) and one-block gaps (grass or dirt between two streets in a line, the
     * holes vanilla's street processor leaves). Returns the blocks changed. Idempotent: run again, it paves what has
     * been loaded or laid since.
     */
    public static int pave(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area) {
        BoundingBox box = scanBox(level, village, radius, area);
        List<BlockPos> streets = streets(level, box);
        RandomSource random = level.getRandom();
        int changed = 0;
        for (BlockPos pos : streets) {
            level.setBlock(pos, pavement(village, random), Block.UPDATE_ALL);
            changed++;
        }
        for (BlockPos pos : streets) {
            BlockPos raised = pos.above();
            if ((dip(level, pos, Direction.Axis.X) || dip(level, pos, Direction.Axis.Z)) && state(level, raised).isAir() && box.isInside(raised)) {
                level.setBlock(raised, pavement(village, random), Block.UPDATE_ALL);
                changed++;
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos top = top(level, box, x, z);
                if (top == null || !natural(level.getBlockState(top)) || !(gap(level, top, Direction.Axis.X) || gap(level, top, Direction.Axis.Z))) {
                    continue;
                }
                BlockState above = state(level, top.above());
                if (!above.isAir() && !(above.canBeReplaced() && above.getFluidState().isEmpty())) {
                    continue;
                }
                if (!above.isAir()) {
                    level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                level.setBlock(top, pavement(village, random), Block.UPDATE_ALL);
                changed++;
            }
        }
        return changed;
    }

    private static boolean dip(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        Direction forward = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return isStreet(state(level, pos.relative(forward).above())) && isStreet(state(level, pos.relative(forward.getOpposite()).above()));
    }

    private static boolean gap(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        Direction forward = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return openStreet(level, pos.relative(forward)) && openStreet(level, pos.relative(forward.getOpposite()));
    }

    /**
     * End of a level tick: once a day, every paved village whose bell is loaded paves what is new (parts of the
     * village loaded since, paths players or the village laid).
     */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % REPAVE_CHECK_TICKS != 0) {
            return;
        }
        long today = VillagerPurchases.today(level.getServer());
        for (VillageState village : VillageEconomy.get(level.getServer()).all()) {
            if (village.dimension() != level.dimension() || !paved(village) || !level.isLoaded(village.bell())) {
                continue;
            }
            Long last = PAVED_DAY.get(village.key());
            if (last != null && last == today) {
                continue;
            }
            PAVED_DAY.put(village.key(), today);
            if (pave(level, village, Villages.config().boardRadius(), null) > 0) {
                TradePersistence.traded(level, village.bell());
            }
        }
    }

    public static void clear() {
        PAVED_DAY.clear();
    }
}
