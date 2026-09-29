package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Admin-only vendor settings (vendor key).
 *
 * @param infiniteStock sells without using stock; buyback items vanish
 * @param burnPayment   money and price items are destroyed instead of paid to the owner (a sink)
 * @param noFee         no vending fee
 */
public record AdminFlags(boolean infiniteStock, boolean burnPayment, boolean noFee) {
    public static final AdminFlags NONE = new AdminFlags(false, false, false);

    public static final Codec<AdminFlags> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("infinite_stock", false).forGetter(AdminFlags::infiniteStock),
        Codec.BOOL.optionalFieldOf("burn_payment", false).forGetter(AdminFlags::burnPayment),
        Codec.BOOL.optionalFieldOf("no_fee", false).forGetter(AdminFlags::noFee)
    ).apply(i, AdminFlags::new));

    public static final StreamCodec<ByteBuf, AdminFlags> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, AdminFlags::infiniteStock,
        ByteBufCodecs.BOOL, AdminFlags::burnPayment,
        ByteBufCodecs.BOOL, AdminFlags::noFee,
        AdminFlags::new);

    public boolean any() {
        return infiniteStock || burnPayment || noFee;
    }
}
