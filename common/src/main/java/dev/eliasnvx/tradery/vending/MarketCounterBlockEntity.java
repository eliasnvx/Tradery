package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

/**
 * A market counter: owner, up to {@link CounterSettings#MAX_OFFERS} offers ({@link CounterSettings}) and one shared
 * stock of 27 slots. Never ticks. Like the vending block it isn't a {@link net.minecraft.world.Container}: hoppers and
 * pipes can't reach the stock. The client copy knows the owner, the offers and which offers are in stock, not the
 * stock itself.
 */
public class MarketCounterBlockEntity extends BlockEntity implements OwnedBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("Tradery");
    public static final int STOCK_SIZE = 27;

    private final VendingContainer stock = new VendingContainer(STOCK_SIZE, this::onStockChanged);
    private @Nullable AccountId owner;
    private String ownerName = "";
    private CounterSettings settings = CounterSettings.EMPTY;
    /** Bit i: offer i has at least one trade in stock. Computed on the server, synced for the hint and the renderer. */
    private int stockedMask;
    /** Client: each offer's goods with a count of one, built when the offers change, so rendering allocates nothing. */
    private final ItemStack[] shown = new ItemStack[CounterSettings.MAX_OFFERS];

    public MarketCounterBlockEntity(BlockPos pos, BlockState state) {
        super(TraderyBlocks.MARKET_COUNTER_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(shown, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ state

    public VendingContainer stock() {
        return stock;
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

    public CounterSettings settings() {
        return settings;
    }

    public void setSettings(CounterSettings settings) {
        this.settings = settings;
        updateShown();
        stockedMask = computeStockedMask();
        changedAndSync();
        refreshIndex();
    }

    /** Trades of offer {@code index} the stock covers right now (0 for an empty offer). */
    public int tradesInStock(int index) {
        CounterOffer offer = settings.offer(index);
        if (!offer.isConfigured()) {
            return 0;
        }
        return (int) Math.min(VendingTrades.UNLIMITED, StackMath.count(stock.getItems(), offer.goods()) / offer.perTrade());
    }

    /** Offer {@code index} has at least one trade in stock; on the client as last synced. */
    public boolean isStocked(int index) {
        return index >= 0 && index < CounterSettings.MAX_OFFERS && (stockedMask & (1 << index)) != 0;
    }

    public int stockedMask() {
        return stockedMask;
    }

    /** Client: the goods of offer {@code index} with a count of one (empty for an empty offer), for the renderer. */
    public ItemStack shown(int index) {
        return shown[index];
    }

    /** The owner or an admin may configure it. */
    public boolean canConfigure(ServerPlayer player) {
        return isOwner(player) || VendingProtection.isAdmin(player);
    }

    public boolean isWithinReach(Player player) {
        return !isRemoved() && level == player.level() && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= VendingBlockEntity.MAX_DISTANCE * VendingBlockEntity.MAX_DISTANCE;
    }

    /** Client side: the stock isn't sent; this reads only what the update tag has. */
    public boolean isOwnedBy(UUID player) {
        return owner instanceof AccountId.Player p && p.uuid().equals(player);
    }

    private void onStockChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            int mask = computeStockedMask();
            if (mask != stockedMask) {
                stockedMask = mask;
                changedAndSync();
            }
        }
        refreshIndex();
    }

    private int computeStockedMask() {
        int mask = 0;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            if (tradesInStock(i) > 0) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private void updateShown() {
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            CounterOffer offer = settings.offer(i);
            shown[i] = offer.isConfigured() ? offer.goods().copyWithCount(1) : ItemStack.EMPTY;
        }
    }

    /** Keeps this counter's snapshot in the vending index current: the shop ledger shows it while the chunk is unloaded. */
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

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            stockedMask = computeStockedMask();
            if (owner != null) {
                // Self-heal the index: a counter loaded from disk that the index doesn't know yet
                VendorsData.get(serverLevel.getServer()).add(serverLevel, worldPosition, owner, false);
                refreshIndex();
            }
        }
    }

    /**
     * The counter is being removed for good (not unloaded): drop the stock once and close menus. Called by
     * {@link MarketCounterBlock#onRemove} on the server when the block changes to another block.
     */
    void removedForGood() {
        BlockPos pos = worldPosition;
        if (level instanceof ServerLevel serverLevel) {
            VendingMenus.closeAllFor(serverLevel, pos);
            Containers.dropContents(serverLevel, pos, stock);
            stock.clearContent();
            VendorsData.get(serverLevel.getServer()).remove(serverLevel, pos);
        }
    }

    // ------------------------------------------------------------------ save / sync

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = TagCodecs.read(tag, "owner", AccountId.CODEC).orElse(null);
        ownerName = tag.getString("owner_name");
        settings = TagCodecs.read(tag, "settings", CounterSettings.CODEC).orElse(CounterSettings.EMPTY);
        stockedMask = tag.getInt("stocked");
        updateShown();
        stock.getItems().replaceAll(stack -> ItemStack.EMPTY);
        if (tag.contains("stock")) {
            ContainerHelper.loadAllItems(tag.getCompound("stock"), stock.getItems());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeShared(tag);
        tag.put("stock", ContainerHelper.saveAllItems(new CompoundTag(), stock.getItems(), true));
    }

    /** What both the save file and the client get. */
    private void writeShared(CompoundTag tag) {
        TagCodecs.putNullable(tag, "owner", AccountId.CODEC, owner);
        tag.putString("owner_name", ownerName);
        TagCodecs.put(tag, "settings", CounterSettings.CODEC, settings);
        tag.putInt("stocked", stockedMask);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
        return tag;
    }
}
