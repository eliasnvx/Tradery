package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.event.VendingPlacedEvent;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.TraderyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The market counter (SPEC "Удобство для серверов"): a wooden counter with a low front shelf and a raised back shelf,
 * up to six offers shown on them, one shared stock. Owned and protected like the vending block: only the owner (or an
 * admin holding the vendor key) can break it; explosions and pistons can't. FACING is the buyers' side.
 */
public class MarketCounterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Height of the front shelf and of the raised back shelf, in pixels; tools/assets.py and the renderer use the same. */
    public static final int FRONT_HEIGHT = 10;
    public static final int BACK_HEIGHT = 14;
    /** Depth of the front shelf (from the front face), in pixels. */
    public static final int FRONT_DEPTH = 8;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Facing north (front at z = 0): the body, and the back shelf on the south half
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, Shapes.or(Block.box(0, 0, 0, 16, FRONT_HEIGHT, 16), backShelf(facing)));
        }
    }

    private static VoxelShape backShelf(Direction facing) {
        return switch (facing) {
            case SOUTH -> Block.box(0, FRONT_HEIGHT, 0, 16, BACK_HEIGHT, 16 - FRONT_DEPTH);
            case WEST -> Block.box(FRONT_DEPTH, FRONT_HEIGHT, 0, 16, BACK_HEIGHT, 16);
            case EAST -> Block.box(0, FRONT_HEIGHT, 0, 16 - FRONT_DEPTH, BACK_HEIGHT, 16);
            default -> Block.box(0, FRONT_HEIGHT, FRONT_DEPTH, 16, BACK_HEIGHT, 16);
        };
    }

    public static final com.mojang.serialization.MapCodec<MarketCounterBlock> CODEC = simpleCodec(MarketCounterBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 1.21.1's block entity blocks are invisible unless they say otherwise. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter) {
            counter.removedForGood();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public MarketCounterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
            && !mayPlace(level, context.getClickedPos(), player)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The per-player limit (vending blocks and counters count together) and the placement event; tells the player why not. */
    private static boolean mayPlace(ServerLevel level, BlockPos pos, ServerPlayer player) {
        int limit = TraderyConfig.server().vending().maxPerPlayer();
        if (limit > 0 && !VendingProtection.isAdmin(player)
            && VendorsData.get(level.getServer()).countOwnedBy(AccountId.player(player.getUUID())) >= limit) {
            player.displayClientMessage(Messages.tr("tradery.counter.limit", "You already own %s shops", limit), true);
            return false;
        }
        VendingPlacedEvent event = VendingPlacedEvent.EVENT.post(new VendingPlacedEvent(player, level, pos));
        if (event.isCancelled()) {
            player.displayClientMessage(event.cancelMessage() != null ? event.cancelMessage()
                : Messages.tr("tradery.counter.place_denied", "You can't place a market counter here"), true);
            return false;
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter) {
            AccountId owner = AccountId.player(player.getUUID());
            counter.setOwner(owner, player.getGameProfile().getName());
            VendorsData.get(serverLevel.getServer()).add(serverLevel, pos, owner, true);
            counter.refreshIndex();
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.getItem() instanceof VendorKeyItem) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter) {
                if (VendingProtection.isAdmin(serverPlayer)) {
                    CounterMenus.openOwner(serverPlayer, counter, true);
                } else {
                    serverPlayer.displayClientMessage(Messages.tr("tradery.vending.not_admin", "Only admins can use the vendor key"), true);
                }
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter) {
            // The owner configures; sneaking shows what buyers see
            if (counter.isOwner(serverPlayer) && !serverPlayer.isShiftKeyDown()) {
                CounterMenus.openOwner(serverPlayer, counter, false);
            } else {
                CounterMenus.openBuyer(serverPlayer, counter);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter && !VendingProtection.canBreak(player, counter)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketCounterBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
