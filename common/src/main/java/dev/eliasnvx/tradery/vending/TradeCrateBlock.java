package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.event.VendingPlacedEvent;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.menu.TradeCrateMenu;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The trade crate (SPEC "Торговый ящик"): a wooden crate villagers walk up to and do their own trades with. Owned and
 * protected like the vending block: only the owner (or an admin holding the vendor key) opens and breaks it;
 * explosions and pistons can't. FACING is where the villager stands. A comparator reads how full the received store
 * is.
 */
public class TradeCrateBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final com.mojang.serialization.MapCodec<TradeCrateBlock> CODEC = simpleCodec(TradeCrateBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 1.21.1's BaseEntityBlock renders nothing by default. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    public TradeCrateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate) {
            crate.removedForGood();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
            && !mayPlace(level, context.getClickedPos(), player)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The per-player shop limit (crates count with vending blocks and counters) and the placement event. */
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
                : Messages.tr("tradery.crate.place_denied", "You can't place a trade crate here"), true);
            return false;
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate) {
            AccountId owner = AccountId.player(player.getUUID());
            crate.setOwner(owner, player.getGameProfile().getName());
            VendorsData.get(serverLevel.getServer()).add(serverLevel, pos, owner, true);
            crate.refreshIndex();
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                                                  InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.getItem() instanceof VendorKeyItem) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate) {
                if (VendingProtection.isAdmin(serverPlayer)) {
                    TradeCrateMenu.open(serverPlayer, crate, true);
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
            && level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate) {
            if (crate.isOwner(serverPlayer)) {
                TradeCrateMenu.open(serverPlayer, crate, false);
            } else {
                serverPlayer.displayClientMessage(crate.ownerName().isEmpty()
                    ? Messages.tr("tradery.crate.not_yours", "This trade crate isn't yours")
                    : Messages.tr("tradery.crate.owned_by", "%s's trade crate", crate.ownerName()), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate && !VendingProtection.canBreak(player, crate)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** How full the received store is, like a chest (the payment store doesn't count). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TradeCrateBlockEntity crate)) {
            return 0;
        }
        float fill = 0;
        for (ItemStack stack : crate.received()) {
            if (!stack.isEmpty()) {
                fill += stack.getCount() / (float) crate.getMaxStackSize(stack);
            }
        }
        fill /= TradeCrateBlockEntity.STORE_SIZE;
        return fill <= 0 ? 0 : 1 + Math.round(fill * 14);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradeCrateBlockEntity(pos, state);
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
