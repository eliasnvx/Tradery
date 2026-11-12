package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A trade crate (SPEC "Торговый ящик"): owner, up to {@link #WANTS} wanted items and two stores of
 * {@link #STORE_SIZE} slots, the payment the crate trades with and what villagers left in it. Unlike vending blocks it
 * is a real {@link WorldlyContainer}, because automation is the point: hoppers and pipes put items into the payment
 * from the top and sides and take the received items from below ({@code tradeCrates.hoppers}). Never ticks; villagers
 * come through {@code VillagerCustomers}. The client copy knows the owner and the wanted items, not the stores.
 */
public class TradeCrateBlockEntity extends BlockEntity implements OwnedBlockEntity, WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Tradery");
    /** Wanted items (ghost samples). */
    public static final int WANTS = 3;
    /** Slots of the payment store and of the received store each. */
    public static final int STORE_SIZE = 9;
    /** Container slots: payment {@code 0..8}, received {@code 9..17}. */
    public static final int RECEIVED_START = STORE_SIZE;
    public static final int SIZE = 2 * STORE_SIZE;
    private static final int[] PAYMENT_SLOTS = range(0, STORE_SIZE);
    private static final int[] RECEIVED_SLOTS = range(RECEIVED_START, SIZE);

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final VendingContainer wants = new VendingContainer(WANTS, this::onWantsChanged);
    private @Nullable AccountId owner;
    private String ownerName = "";

    public TradeCrateBlockEntity(BlockPos pos, BlockState state) {
        super(TraderyBlocks.TRADE_CRATE_BLOCK_ENTITY.get(), pos, state);
    }

    private static int[] range(int from, int to) {
        int[] slots = new int[to - from];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = from + i;
        }
        return slots;
    }

    // ------------------------------------------------------------------ state

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

    /** The wanted-item samples; changing one (the owner's menu) saves, syncs and updates the index. */
    public VendingContainer wants() {
        return wants;
    }

    /** The wanted items that are set, in slot order. */
    public List<ItemStack> wanted() {
        List<ItemStack> wanted = new ArrayList<>(WANTS);
        for (int i = 0; i < WANTS; i++) {
            ItemStack want = wants.getItem(i);
            if (!want.isEmpty()) {
                wanted.add(want);
            }
        }
        return wanted;
    }

    /** The payment store (live list: slots {@code 0..8} of the container). */
    public List<ItemStack> payment() {
        return items.subList(0, STORE_SIZE);
    }

    /** The received store (live list: slots {@code 9..17} of the container). */
    public List<ItemStack> received() {
        return items.subList(RECEIVED_START, SIZE);
    }

    /** The owner or an admin may configure it. */
    public boolean canConfigure(ServerPlayer player) {
        return isOwner(player) || VendingProtection.isAdmin(player);
    }

    public boolean isWithinReach(Player player) {
        return !isRemoved() && level == player.level() && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= VendingBlockEntity.MAX_DISTANCE * VendingBlockEntity.MAX_DISTANCE;
    }

    /** Client side: the stores aren't sent; this reads only what the update tag has. */
    public boolean isOwnedBy(UUID player) {
        return owner instanceof AccountId.Player p && p.uuid().equals(player);
    }

    private void onWantsChanged() {
        changedAndSync();
        refreshIndex();
    }

    /** Keeps this crate's snapshot in the vending index current: the shop ledger shows it while the chunk is unloaded. */
    public void refreshIndex() {
        if (level instanceof ServerLevel serverLevel && owner != null && !isRemoved()) {
            VendorsData.get(serverLevel.getServer()).updateSnapshot(serverLevel, worldPosition, ShopSnapshot.of(this));
        }
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ container: payment in from the top and sides, received out from below

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        refreshIndex();
    }

    @Override
    public boolean stillValid(Player player) {
        return isWithinReach(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < RECEIVED_START;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? RECEIVED_SLOTS : PAYMENT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && slot < RECEIVED_START && TraderyConfig.server().tradeCrates().hoppers();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot >= RECEIVED_START && TraderyConfig.server().tradeCrates().hoppers();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel && owner != null) {
            // Self-heal the index: a crate loaded from disk that the index doesn't know yet
            VendorsData.get(serverLevel.getServer()).add(serverLevel, worldPosition, owner, false);
            refreshIndex();
        }
    }

    /** The crate is being removed for good (not unloaded): drop both stores once and close menus. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            VendingMenus.closeAllFor(serverLevel, pos);
            Containers.dropContents(serverLevel, pos, this);
            items.clear();
            VendorsData.get(serverLevel.getServer()).remove(serverLevel, pos);
        }
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", AccountId.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        List<ItemStack> loaded = input.read("wants", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < WANTS; i++) {
            // Not setItem: loading must not save, sync or touch the index
            wants.getItems().set(i, i < loaded.size() ? loaded.get(i) : ItemStack.EMPTY);
        }
        items.replaceAll(stack -> ItemStack.EMPTY);
        input.child("stores").ifPresent(child -> ContainerHelper.loadAllItems(child, items));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeShared(output);
        ContainerHelper.saveAllItems(output.child("stores"), items, true);
    }

    /** What both the save file and the client get. */
    private void writeShared(ValueOutput output) {
        output.storeNullable("owner", AccountId.CODEC, owner);
        output.putString("owner_name", ownerName);
        output.store("wants", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(wants.getItems()));
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
}
