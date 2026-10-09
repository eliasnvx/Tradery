package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

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

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(infiniteStock);
        buf.writeBoolean(burnPayment);
        buf.writeBoolean(noFee);
    }

    public static AdminFlags read(FriendlyByteBuf buf) {
        return new AdminFlags(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public boolean any() {
        return infiniteStock || burnPayment || noFee;
    }
}
