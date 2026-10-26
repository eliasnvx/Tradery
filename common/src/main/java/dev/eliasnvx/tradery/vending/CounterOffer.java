package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.network.codec.ByteBufCodecs;
import dev.eliasnvx.tradery.network.codec.StreamCodec;
import dev.eliasnvx.tradery.network.codec.TraderyStreamCodecs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One offer of a market counter: {@code goods} (its count is the amount per trade) for {@code price} minor units.
 * An offer without goods is an empty place on the counter.
 */
public record CounterOffer(ItemStack goods, long price) {
    public static final CounterOffer EMPTY = new CounterOffer(ItemStack.EMPTY, 0);

    public static final Codec<CounterOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
        // Goods of a removed mod read as empty: the sample is lost, not the price with it
        TagCodecs.ITEM.optionalFieldOf("goods", ItemStack.EMPTY).forGetter(CounterOffer::goods),
        Codec.LONG.optionalFieldOf("price", 0L).forGetter(CounterOffer::price)
    ).apply(i, CounterOffer::new));

    public static final StreamCodec<FriendlyByteBuf, CounterOffer> STREAM_CODEC = StreamCodec.composite(
        TraderyStreamCodecs.ITEM_STACK, CounterOffer::goods,
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
