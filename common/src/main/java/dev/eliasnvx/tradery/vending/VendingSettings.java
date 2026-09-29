package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a vending block trades. One trade = {@code goods} (its count is the amount per trade) for either
 * {@code price} minor units ({@link PriceMode#CURRENCY}) or {@code priceItem} (its count per trade, {@link PriceMode#ITEM}).
 * With {@code buyback} the block buys the goods from players instead (money only).
 */
public record VendingSettings(ItemStack goods, PriceMode priceMode, long price, ItemStack priceItem, boolean buyback,
                              DisplayAnimation animation) {
    public static final int MAX_PER_TRADE = 64;

    public static final VendingSettings EMPTY = new VendingSettings(ItemStack.EMPTY, PriceMode.CURRENCY, 0, ItemStack.EMPTY, false,
        DisplayAnimation.SPIN_BOB);

    public static final Codec<VendingSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("goods", ItemStack.EMPTY).forGetter(VendingSettings::goods),
        ConfigCodecs.enumCodec(PriceMode.class).optionalFieldOf("price_mode", PriceMode.CURRENCY).forGetter(VendingSettings::priceMode),
        Codec.LONG.optionalFieldOf("price", 0L).forGetter(VendingSettings::price),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("price_item", ItemStack.EMPTY).forGetter(VendingSettings::priceItem),
        Codec.BOOL.optionalFieldOf("buyback", false).forGetter(VendingSettings::buyback),
        ConfigCodecs.enumCodec(DisplayAnimation.class).optionalFieldOf("animation", DisplayAnimation.SPIN_BOB).forGetter(VendingSettings::animation)
    ).apply(i, VendingSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingSettings> STREAM_CODEC = StreamCodec.of(
        (buf, s) -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.goods);
            buf.writeVarInt(s.priceMode.ordinal());
            buf.writeVarLong(s.price);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.priceItem);
            buf.writeBoolean(s.buyback);
            buf.writeVarInt(s.animation.ordinal());
        },
        buf -> new VendingSettings(
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            enumAt(PriceMode.values(), buf.readVarInt()),
            buf.readVarLong(),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            buf.readBoolean(),
            enumAt(DisplayAnimation.values(), buf.readVarInt())));

    public static <E extends Enum<E>> E enumAt(E[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Bad enum ordinal " + ordinal);
        }
        return values[ordinal];
    }

    public VendingSettings {
        goods = goods.copy();
        priceItem = priceItem.copy();
        price = Math.max(0, price);
    }

    /** Goods and a price are set: the block can trade. */
    public boolean isConfigured() {
        if (goods.isEmpty()) {
            return false;
        }
        return priceMode == PriceMode.CURRENCY || !priceItem.isEmpty();
    }

    /** Buyback works only for money. */
    public boolean isBuyback() {
        return buyback && priceMode == PriceMode.CURRENCY;
    }

    /** Items of goods per trade, 1..64. */
    public int perTrade() {
        return Math.max(1, goods.getCount());
    }

    /** Price items per trade ({@link PriceMode#ITEM}). */
    public int pricePerTrade() {
        return Math.max(1, priceItem.getCount());
    }

    public VendingSettings withGoods(ItemStack goods) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    public VendingSettings withPriceItem(ItemStack priceItem) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    public VendingSettings withPriceMode(PriceMode priceMode) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    public VendingSettings withPrice(long price) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    public VendingSettings withBuyback(boolean buyback) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    public VendingSettings withAnimation(DisplayAnimation animation) {
        return new VendingSettings(goods, priceMode, price, priceItem, buyback, animation);
    }

    /** Same content (records compare ItemStacks by identity). */
    public boolean sameAs(VendingSettings other) {
        return ItemStack.matches(goods, other.goods) && priceMode == other.priceMode && price == other.price
            && ItemStack.matches(priceItem, other.priceItem) && buyback == other.buyback && animation == other.animation;
    }
}
