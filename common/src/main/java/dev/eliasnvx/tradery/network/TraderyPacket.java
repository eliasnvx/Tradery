package dev.eliasnvx.tradery.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * A Tradery packet (1.20.1 has no {@code CustomPacketPayload}): its channel id and how to write its body. Every
 * packet record also has a static {@code read(FriendlyByteBuf)} that bounds every size it reads.
 */
public interface TraderyPacket {
    /** The channel: {@code tradery:<name>}. */
    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
