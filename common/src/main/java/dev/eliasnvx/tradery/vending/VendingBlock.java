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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The vending block: a base (or a facade), a glass case with the goods, a lid, and a light that is green while it
 * can trade. Only the owner (or an admin holding the vendor key) can break it; explosions and pistons can't.
 */
public class VendingBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Green light: configured and in stock (or buying). */
    public static final BooleanProperty STOCKED = BooleanProperty.create("stocked");
    /** A facade replaces the default base; the renderer draws it. */
    public static final BooleanProperty FACADE = BooleanProperty.create("facade");

    /** Height of the base under the glass case, in pixels; the models in tools/assets.py use the same value. */
    public static final int BASE_HEIGHT = 3;

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, BASE_HEIGHT, 16),
        Block.box(1, BASE_HEIGHT, 1, 15, 15, 15),
        Block.box(0, 15, 0, 16, 16, 16));

    public VendingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STOCKED, false).setValue(FACADE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STOCKED, FACADE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
            && !mayPlace(level, context.getClickedPos(), player)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Per-player limit and the placement event; tells the player why not. */
    private static boolean mayPlace(ServerLevel level, BlockPos pos, ServerPlayer player) {
        int limit = TraderyConfig.server().vending().maxPerPlayer();
        if (limit > 0 && !VendingProtection.isAdmin(player)
            && VendorsData.get(level.getServer()).countOwnedBy(AccountId.player(player.getUUID())) >= limit) {
            player.sendOverlayMessage(Messages.tr("tradery.vending.limit", "You already own %s vending blocks", limit));
            return false;
        }
        VendingPlacedEvent event = VendingPlacedEvent.EVENT.post(new VendingPlacedEvent(player, level, pos));
        if (event.isCancelled()) {
            player.sendOverlayMessage(event.cancelMessage() != null ? event.cancelMessage()
                : Messages.tr("tradery.vending.place_denied", "You can't place a vending block here"));
            return false;
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
            AccountId owner = AccountId.player(player.getUUID());
            vendor.setOwner(owner, player.nameAndId().name());
            vendor.setSettings(VendingSettings.EMPTY.withAnimation(TraderyConfig.server().vending().defaultAnimation()));
            VendorsData.get(serverLevel.getServer()).add(serverLevel, pos, owner, true);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.getItem() instanceof VendorKeyItem) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
                if (VendingProtection.isAdmin(serverPlayer)) {
                    VendingMenus.openOwner(serverPlayer, vendor, true);
                } else {
                    serverPlayer.sendOverlayMessage(Messages.tr("tradery.vending.not_admin", "Only admins can use the vendor key"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
            // The owner configures; sneaking shows what buyers see
            if (vendor.isOwner(serverPlayer) && !serverPlayer.isShiftKeyDown()) {
                VendingMenus.openOwner(serverPlayer, vendor, false);
            } else {
                VendingMenus.openBuyer(serverPlayer, vendor);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VendingBlockEntity vendor && !VendingProtection.canBreak(player, vendor)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VendingBlockEntity(pos, state);
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
