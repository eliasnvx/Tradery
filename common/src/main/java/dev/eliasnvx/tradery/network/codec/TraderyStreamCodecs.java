package dev.eliasnvx.tradery.network.codec;

import dev.eliasnvx.tradery.network.codec.TraderyStreamCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** Codecs of vanilla types that have a {@code STREAM_CODEC} from 1.20.5 on, written with 1.20.1's buffer methods. */
public final class TraderyStreamCodecs {
    /** {@code TraderyStreamCodecs.ITEM_STACK}: empty stacks too; {@link FriendlyByteBuf#readItem()} bounds the NBT. */
    public static final StreamCodec<FriendlyByteBuf, ItemStack> ITEM_STACK = StreamCodec.of(FriendlyByteBuf::writeItem, FriendlyByteBuf::readItem);
    public static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS = StreamCodec.of(FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::readBlockPos);
    public static final StreamCodec<FriendlyByteBuf, GlobalPos> GLOBAL_POS = StreamCodec.of(FriendlyByteBuf::writeGlobalPos, FriendlyByteBuf::readGlobalPos);
    public static final StreamCodec<FriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION =
        StreamCodec.of(FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::readResourceLocation);
    /** {@code TraderyStreamCodecs.COMPONENT}: server → client text. */
    public static final StreamCodec<FriendlyByteBuf, Component> COMPONENT = StreamCodec.of(FriendlyByteBuf::writeComponent, FriendlyByteBuf::readComponent);
    /** One offer as {@code MerchantOffers#writeToStream} writes each. */
    public static final StreamCodec<FriendlyByteBuf, MerchantOffer> MERCHANT_OFFER = StreamCodec.of((buf, offer) -> {
        buf.writeItem(offer.getBaseCostA());
        buf.writeItem(offer.getResult());
        buf.writeItem(offer.getCostB());
        buf.writeBoolean(offer.isOutOfStock());
        buf.writeInt(offer.getUses());
        buf.writeInt(offer.getMaxUses());
        buf.writeInt(offer.getXp());
        buf.writeInt(offer.getSpecialPriceDiff());
        buf.writeFloat(offer.getPriceMultiplier());
        buf.writeInt(offer.getDemand());
    }, buf -> {
        ItemStack costA = buf.readItem();
        ItemStack result = buf.readItem();
        ItemStack costB = buf.readItem();
        boolean outOfStock = buf.readBoolean();
        int uses = buf.readInt();
        int maxUses = buf.readInt();
        int xp = buf.readInt();
        int specialPrice = buf.readInt();
        float multiplier = buf.readFloat();
        int demand = buf.readInt();
        MerchantOffer offer = new MerchantOffer(costA, costB, result, uses, maxUses, xp, multiplier, demand);
        offer.setSpecialPriceDiff(specialPrice);
        if (outOfStock) {
            offer.setToOutOfStock();
        }
        return offer;
    });

    private TraderyStreamCodecs() {
    }
}
