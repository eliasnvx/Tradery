package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A showcase: shows one item (a copy, not a real stack) with an animation. Nothing is sold. */
public class DisplayBlockEntity extends BlockEntity implements OwnedBlockEntity {
    private static final Codec<DisplayAnimation> ANIMATION_CODEC = ConfigCodecs.enumCodec(DisplayAnimation.class);

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
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = TagCodecs.read(tag, "owner", AccountId.CODEC).orElse(null);
        shown = tag.contains("shown", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("shown")) : ItemStack.EMPTY;
        animation = TagCodecs.read(tag, "animation", ANIMATION_CODEC).orElse(DisplayAnimation.SPIN_BOB);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    private void write(CompoundTag tag) {
        TagCodecs.putNullable(tag, "owner", AccountId.CODEC, owner);
        if (!shown.isEmpty()) {
            tag.put("shown", shown.save(new CompoundTag()));
        }
        TagCodecs.put(tag, "animation", ANIMATION_CODEC, animation);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }
}
