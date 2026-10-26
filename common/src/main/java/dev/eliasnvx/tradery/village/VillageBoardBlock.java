package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.vending.VendingProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The village board (SPEC "Доска деревни"): a notice board on two posts. No block entity: it belongs to the nearest
 * bell ({@link Villages#near}), and using it opens the board window for that village. Boards on generated market
 * stalls have {@code market=true}: like the market's vending blocks, only an admin holding the vendor key breaks them.
 * One a player placed is just a way into the village, anybody may break it.
 */
public class VillageBoardBlock extends HorizontalDirectionalBlock {
    /** Generated with a market stall (protected), not placed by a player. */
    public static final BooleanProperty MARKET = BooleanProperty.create("market");

    /** Facing north: two posts, the board between them, a small roof; tools/assets.py draws the same boxes. */
    private static final VoxelShape NORTH_SOUTH = Shapes.or(
        Block.box(0, 0, 7, 2, 14, 9),
        Block.box(14, 0, 7, 16, 14, 9),
        Block.box(2, 5, 7, 14, 14, 9),
        Block.box(0, 14, 5, 16, 16, 11));
    /** The same boxes turned a quarter: the board is symmetric, so east and west share one shape. */
    private static final VoxelShape EAST_WEST = Shapes.or(
        Block.box(7, 0, 0, 9, 14, 2),
        Block.box(7, 0, 14, 9, 14, 16),
        Block.box(7, 5, 2, 9, 14, 14),
        Block.box(5, 14, 0, 11, 16, 16));
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(Direction.NORTH, NORTH_SOUTH, Direction.SOUTH, NORTH_SOUTH,
        Direction.EAST, EAST_WEST, Direction.WEST, EAST_WEST);
    public static final com.mojang.serialization.MapCodec<VillageBoardBlock> CODEC = simpleCodec(VillageBoardBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public VillageBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MARKET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MARKET);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            VillageBoard.open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!mayBreak(player, state)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** A market board needs an admin with the vendor key (on the client, holding the key is enough to predict). */
    public static boolean mayBreak(Player player, BlockState state) {
        if (!(state.getBlock() instanceof VillageBoardBlock) || !state.getValue(MARKET)) {
            return true;
        }
        if (!VendingProtection.holdsKey(player)) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer) || VendingProtection.isAdmin(serverPlayer);
    }

    /** For the loaders' break events (creative mode breaks instantly, skipping destroy progress). */
    public static boolean mayBreak(Player player, BlockGetter level, BlockPos pos) {
        return mayBreak(player, level.getBlockState(pos));
    }
}
