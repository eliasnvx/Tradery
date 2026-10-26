package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Street lights (SPEC "Фонари"): along every street of the village where it's dark, a post in the village type's
 * style (a wall for a base, a fence or wall pole) with an arm over the street and a lantern hanging from it, at
 * least {@link #SPACING} blocks apart. Streets already lit (vanilla lamp posts, torches, other lamps) are left alone.
 * A village without streets gets plain posts around its bell. Planned before any money moves; then once a day the
 * lit villages light what was loaded or laid since. Never loads a chunk. Server thread.
 */
public final class VillageLamps {
    /** Two lamps are at least this far apart (along either axis). */
    public static final int SPACING = 10;
    /** Lamps per village at most. */
    public static final int MAX = 48;
    /** A street with this much block light needs no lamp. */
    static final int LIT = 10;
    /** Posts around the bell when the village has no streets. */
    static final int BELL_POSTS = 8;
    static final int BELL_SPACING = 5;
    /** The arm is this high above the ground: three free blocks under the lantern, enough for an iron golem. */
    public static final int HEIGHT = 5;
    static final int CHECK_TICKS = 1200;
    private static final Map<String, Long> LIT_DAY = new HashMap<>();

    private VillageLamps() {
    }

    /** A lamp: the ground under its post, and the side its arm reaches out over the street (null: a plain post). */
    public record Lamp(BlockPos ground, @Nullable Direction arm) {
    }

    record Style(BlockState base, BlockState pole) {
    }

    static Style style(VillageState village) {
        return switch (VillageFunding.pool(village)) {
            case "desert" -> new Style(Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.SANDSTONE_WALL.defaultBlockState());
            case "savanna" -> new Style(Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.ACACIA_FENCE.defaultBlockState());
            case "taiga" -> new Style(Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState());
            case "snowy" -> new Style(Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState());
            default -> new Style(Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState());
        };
    }

    /** Whether the village has a funded project that lights its streets. */
    public static boolean lit(VillageState village) {
        for (String id : village.projects) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && VillageProjects.get(parsed).map(p -> p.action() == VillageProjects.Action.STREET_LAMPS).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where lamps go: by the village's streets (see {@link VillageStreets#scanBox}), nearest the bell first, where the
     * street is dark, {@link #SPACING} apart, at most {@link #MAX}; with {@code aroundBell} and no street spot at all,
     * plain posts around the bell instead.
     */
    public static List<Lamp> plan(ServerLevel level, VillageState village, int radius, @Nullable BoundingBox area, boolean aroundBell) {
        BoundingBox box = VillageStreets.scanBox(level, village, radius, area);
        BlockPos bell = village.bell();
        List<BlockPos> streets = VillageStreets.streetTops(level, box);
        streets.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(bell)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<Lamp> lamps = new ArrayList<>();
        for (BlockPos street : streets) {
            if (lamps.size() >= MAX) {
                break;
            }
            if (level.getBrightness(LightLayer.BLOCK, street.above()) >= LIT || tooNear(lamps, street, SPACING)) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos ground = street.relative(side);
                Direction arm = side.getOpposite();
                if (fitsPost(level, box, ground, HEIGHT) && clearOver(level, box, street)) {
                    lamps.add(new Lamp(ground, arm));
                    break;
                }
            }
        }
        if (lamps.isEmpty() && aroundBell) {
            for (BlockPos offset : VillageFunding.ringOffsets(radius)) {
                if (lamps.size() >= BELL_POSTS) {
                    break;
                }
                if (Math.abs(offset.getX()) < 2 && Math.abs(offset.getZ()) < 2) {
                    continue;
                }
                BlockPos ground = groundNear(level, bell.offset(offset.getX(), -1, offset.getZ()));
                if (ground != null && !tooNear(lamps, ground, BELL_SPACING) && fitsPost(level, box, ground, HEIGHT - 1)
                    && level.getBrightness(LightLayer.BLOCK, ground.above()) < LIT) {
                    lamps.add(new Lamp(ground, null));
                }
            }
        }
        return lamps;
    }

    private static boolean tooNear(List<Lamp> lamps, BlockPos pos, int spacing) {
        for (Lamp lamp : lamps) {
            if (Math.abs(lamp.ground().getX() - pos.getX()) < spacing && Math.abs(lamp.ground().getZ() - pos.getZ()) < spacing) {
                return true;
            }
        }
        return false;
    }

    /** The ground block within the height range around {@code pos} with air above it, or null. */
    private static @Nullable BlockPos groundNear(ServerLevel level, BlockPos pos) {
        for (int step = 0; step <= 2 * VillageFunding.GOLEM_HEIGHT_RANGE; step++) {
            int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
            BlockPos at = pos.above(dy);
            if (!level.isLoaded(at)) {
                return null;
            }
            if (!level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                return at;
            }
        }
        return null;
    }

    /**
     * A post fits on {@code ground}: real ground (sturdy, dry, three blocks deep, not a street), under the open sky,
     * outside village buildings, {@code height} blocks of air or plants above it, no door beside it, nobody there.
     */
    static boolean fitsPost(ServerLevel level, BoundingBox box, BlockPos ground, int height) {
        if (!level.isLoaded(ground) || !box.isInside(ground.above(height))) {
            return false;
        }
        BlockState state = level.getBlockState(ground);
        if (VillageStreets.isStreet(state) || !state.isFaceSturdy(level, ground, Direction.UP) || !state.getFluidState().isEmpty()
            || !VillageFunding.solidGround(level, ground)) {
            return false;
        }
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, ground.getX(), ground.getZ()) > ground.getY() + 1
            || VillageFunding.inVillageBuilding(level, ground.above())) {
            return false;
        }
        for (int dy = 1; dy <= height; dy++) {
            BlockState above = level.getBlockState(ground.above(dy));
            if (!above.isAir() && !(above.canBeReplaced() && above.getFluidState().isEmpty())) {
                return false;
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos beside = ground.above(dy).relative(side);
                if (level.isLoaded(beside) && level.getBlockState(beside).getBlock() instanceof DoorBlock) {
                    return false;
                }
            }
        }
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(ground.above()).expandTowards(0, height - 1, 0)).isEmpty();
    }

    /** The street keeps its headroom: nothing above it up to the arm. */
    private static boolean clearOver(ServerLevel level, BoundingBox box, BlockPos street) {
        for (int dy = 1; dy <= HEIGHT; dy++) {
            BlockPos pos = street.above(dy);
            if (!box.isInside(pos) || !level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** Builds the lamps; returns how many. */
    public static int place(ServerLevel level, VillageState village, List<Lamp> lamps) {
        Style style = style(village);
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState standing = Blocks.LANTERN.defaultBlockState();
        for (Lamp lamp : lamps) {
            BlockPos ground = lamp.ground();
            List<BlockPos> placed = new ArrayList<>();
            if (lamp.arm() != null) {
                set(level, ground.above(1), style.base(), placed);
                for (int dy = 2; dy <= HEIGHT; dy++) {
                    set(level, ground.above(dy), style.pole(), placed);
                }
                BlockPos arm = ground.above(HEIGHT).relative(lamp.arm());
                set(level, arm, style.pole(), placed);
                set(level, arm.below(), hanging, placed);
            } else {
                set(level, ground.above(1), style.base(), placed);
                set(level, ground.above(2), style.pole(), placed);
                set(level, ground.above(3), style.pole(), placed);
                set(level, ground.above(4), standing, placed);
            }
            // Fences and walls join up only when their neighbours tell them: settle the shapes once all is in place
            for (BlockPos pos : placed) {
                BlockState state = level.getBlockState(pos);
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                if (shaped != state) {
                    level.setBlock(pos, shaped, Block.UPDATE_ALL);
                }
            }
        }
        if (!lamps.isEmpty()) {
            TradePersistence.traded(level, lamps.get(0).ground());
        }
        return lamps.size();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        placed.add(pos.immutable());
    }

    /** End of a level tick: once a day, every lit village whose bell is loaded lights its new dark streets. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        long today = VillagerPurchases.today(level.getServer());
        for (VillageState village : VillageEconomy.get(level.getServer()).all()) {
            if (village.dimension() != level.dimension() || !lit(village) || !level.isLoaded(village.bell())) {
                continue;
            }
            Long last = LIT_DAY.get(village.key());
            if (last != null && last == today) {
                continue;
            }
            LIT_DAY.put(village.key(), today);
            place(level, village, plan(level, village, Villages.config().boardRadius(), null, false));
        }
    }

    public static void clear() {
        LIT_DAY.clear();
    }
}
