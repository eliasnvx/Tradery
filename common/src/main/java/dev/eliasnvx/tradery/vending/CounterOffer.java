package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One offer of a market counter: {@code goods} (its count is the amount per trade) for {@code price} minor units.
 * An offer without goods is an empty place on the counter.
 */
public record CounterOffer(ItemStack goods, long price) {
    public static final CounterOffer EMPTY = new CounterOffer(ItemStack.EMPTY, 0);

    public static final Codec<CounterOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
        // Lenient: goods of a removed mod lose the sample, not the price with it
        ItemStack.OPTIONAL_CODEC.lenientOptionalFieldOf("goods", ItemStack.EMPTY).forGetter(CounterOffer::goods),
        Codec.LONG.optionalFieldOf("price", 0L).forGetter(CounterOffer::price)
    ).apply(i, CounterOffer::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CounterOffer> STREAM_CODEC = StreamCodec.composite(
        ItemStack.OPTIONAL_STREAM_CODEC, CounterOffer::goods,
        ByteBufCodecs.VAR_LONG, CounterOffer::price,
        CounterOffer::new);

    public CounterOffer {
        goods = goods.copy();
        price = Math.max(0, price);
    }

    /** Goods are set: the offer can trade. */
    public boolean isConfigured() {
        return !goods.isEmpty();
    }

    /** Items of goods per trade, 1..64. */
    public int perTrade() {
        return Math.max(1, goods.getCount());
    }

    /** Same content (records compare ItemStacks by identity). */
    public boolean sameAs(CounterOffer other) {
        return ItemStack.matches(goods, other.goods) && price == other.price;
    }
}
