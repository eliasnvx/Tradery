package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A showcase: shows one item (a copy, not a real stack) with an animation. Nothing is sold. */
public class DisplayBlockEntity extends BlockEntity implements OwnedBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("Tradery");

    private @Nullable AccountId owner;
    private ItemStack shown = ItemStack.EMPTY;
    private DisplayAnimation animation = DisplayAnimation.SPIN_BOB;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(TraderyBlocks.DISPLAY_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public @Nullable AccountId owner() {
        return owner;
    }

    public void setOwner(AccountId owner) {
        this.owner = owner;
        changedAndSync();
    }

    public ItemStack shown() {
        return shown;
    }

    public void setShown(ItemStack shown) {
        this.shown = shown.copyWithCount(Math.max(1, Math.min(shown.getCount(), shown.getMaxStackSize())));
        if (shown.isEmpty()) {
            this.shown = ItemStack.EMPTY;
        }
        changedAndSync();
    }

    public DisplayAnimation animation() {
        return animation;
    }

    public void setAnimation(DisplayAnimation animation) {
        this.animation = animation;
        changedAndSync();
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", AccountId.CODEC).orElse(null);
        shown = input.read("shown", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        animation = input.read("animation", dev.eliasnvx.tradery.config.ConfigCodecs.enumCodec(DisplayAnimation.class))
            .orElse(DisplayAnimation.SPIN_BOB);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        write(output);
    }

    private void write(ValueOutput output) {
        output.storeNullable("owner", AccountId.CODEC, owner);
        output.store("shown", ItemStack.OPTIONAL_CODEC, shown);
        output.store("animation", dev.eliasnvx.tradery.config.ConfigCodecs.enumCodec(DisplayAnimation.class), animation);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            write(output);
            return output.buildResult();
        }
    }
}
