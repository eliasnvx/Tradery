package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.ContainerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A vending block: owner, what it trades ({@link VendingSettings}), 27 slots of stock, 9 slots of item revenue,
 * an optional facade and admin flags. Never ticks. The client copy knows everything except the two inventories.
 */
public class VendingBlockEntity extends BlockEntity implements OwnedBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("Tradery");
    public static final int STOCK_SIZE = 27;
    public static final int REVENUE_SIZE = 9;
    /** How far a player may be to use the block (spec: 8 blocks). */
    public static final double MAX_DISTANCE = 8.0;

    private final VendingContainer stock = new VendingContainer(STOCK_SIZE, this::onStockChanged);
    private final VendingContainer revenue = new VendingContainer(REVENUE_SIZE, this::onRevenueChanged);
    private @Nullable AccountId owner;
    private String ownerName = "";
    private VendingSettings settings = VendingSettings.EMPTY;
    private AdminFlags admin = AdminFlags.NONE;
    private @Nullable BlockState facade;
    /** The owner was told the revenue is full; reset when revenue space frees up. Not saved. */
    private boolean fullNotified;

    public VendingBlockEntity(BlockPos pos, BlockState state) {
        super(TraderyBlocks.VENDING_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    public VendingContainer stock() {
        return stock;
    }

    public VendingContainer revenue() {
        return revenue;
    }

    @Override
    public @Nullable AccountId owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(@Nullable AccountId owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        changedAndSync();
    }

    public VendingSettings settings() {
        return settings;
    }

    public void setSettings(VendingSettings settings) {
        this.settings = settings;
        changedAndSync();
        updateStockedState();
    }

    public AdminFlags admin() {
        return admin;
    }

    public void setAdmin(AdminFlags admin) {
        this.admin = admin;
        changedAndSync();
        updateStockedState();
    }

    public @Nullable BlockState facade() {
        return facade;
    }

    public void setFacade(@Nullable BlockState facade) {
        this.facade = facade;
        changedAndSync();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            boolean hasFacade = facade != null;
            if (state.hasProperty(VendingBlock.FACADE) && state.getValue(VendingBlock.FACADE) != hasFacade) {
                level.setBlock(worldPosition, state.setValue(VendingBlock.FACADE, hasFacade), Block.UPDATE_ALL);
            }
        }
    }

    boolean fullNotified() {
        return fullNotified;
    }

    void setFullNotified(boolean fullNotified) {
        this.fullNotified = fullNotified;
    }

    /** Trades the stock can cover right now (infinite stock: a large number). */
    public int tradesInStock() {
        if (!settings.isConfigured()) {
            return 0;
        }
        if (admin.infiniteStock()) {
            return VendingTrades.UNLIMITED;
        }
        return (int) Math.min(VendingTrades.UNLIMITED, StackMath.count(stock.getItems(), settings.goods()) / settings.perTrade());
    }

    /** The owner or an admin with the vendor key may configure it. */
    public boolean canConfigure(ServerPlayer player) {
        return isOwner(player) || VendingProtection.isAdmin(player);
    }

    public boolean isWithinReach(Player player) {
        return !isRemoved() && level == player.level() && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private void onStockChanged() {
        setChanged();
        updateStockedState();
    }

    private void onRevenueChanged() {
        setChanged();
        fullNotified = false;
    }

    /** Keeps the block's STOCKED property (the green light) in line with the stock. */
    public void updateStockedState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(VendingBlock.STOCKED)) {
            return;
        }
        boolean stocked = settings.isConfigured() && (settings.isBuyback() || tradesInStock() > 0);
        if (state.getValue(VendingBlock.STOCKED) != stocked) {
            level.setBlock(worldPosition, state.setValue(VendingBlock.STOCKED, stocked), Block.UPDATE_ALL);
        }
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel && owner != null) {
            // Self-heal the index: a block loaded from disk that the index doesn't know yet
            VendorsData.get(serverLevel.getServer()).add(serverLevel, worldPosition, owner, false);
        }
    }

    /** The block is being removed for good (not unloaded): drop the contents once and close menus. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            VendingMenus.closeAllFor(serverLevel, pos);
            Containers.dropContents(serverLevel, pos, stock);
            Containers.dropContents(serverLevel, pos, revenue);
            stock.clearContent();
            revenue.clearContent();
            VendorsData.get(serverLevel.getServer()).remove(serverLevel, pos);
        }
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", AccountId.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        settings = input.read("settings", VendingSettings.CODEC).orElse(VendingSettings.EMPTY);
        admin = input.read("admin", AdminFlags.CODEC).orElse(AdminFlags.NONE);
        facade = input.read("facade", BlockState.CODEC).orElse(null);
        stock.getItems().replaceAll(stack -> ItemStack.EMPTY);
        revenue.getItems().replaceAll(stack -> ItemStack.EMPTY);
        input.child("stock").ifPresent(child -> ContainerHelper.loadAllItems(child, stock.getItems()));
        input.child("revenue").ifPresent(child -> ContainerHelper.loadAllItems(child, revenue.getItems()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeShared(output);
        ContainerHelper.saveAllItems(output.child("stock"), stock.getItems(), true);
        ContainerHelper.saveAllItems(output.child("revenue"), revenue.getItems(), true);
    }

    /** What both the save file and the client get. */
    private void writeShared(ValueOutput output) {
        output.storeNullable("owner", AccountId.CODEC, owner);
        output.putString("owner_name", ownerName);
        output.store("settings", VendingSettings.CODEC, settings);
        output.store("admin", AdminFlags.CODEC, admin);
        output.storeNullable("facade", BlockState.CODEC, facade);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            writeShared(output);
            return output.buildResult();
        }
    }

    /** Client side: the stock and revenue aren't sent; this reads only what the update tag has. */
    public boolean isOwnedBy(UUID player) {
        return owner instanceof AccountId.Player p && p.uuid().equals(player);
    }
}
