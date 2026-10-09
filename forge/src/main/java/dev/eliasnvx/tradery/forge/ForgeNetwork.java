package dev.eliasnvx.tradery.forge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.network.TraderyPacket;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.server.TraderyServer;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

/**
 * Tradery's packets on Forge: one optional channel ({@code tradery:main}), one message index per
 * {@link TraderyPayloads} entry. A side without Tradery (a vanilla client, a server without the mod) is accepted and
 * simply gets no packets.
 */
final class ForgeNetwork {
    private static final String VERSION = "1";
    /** Forge's package-private {@code NetworkConstants.FML_NETVERSION} ({@code valueOf} returns the same key). */
    private static final AttributeKey<String> FML_NETVERSION = AttributeKey.valueOf("fml:netversion");

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Tradery.id("main"), () -> VERSION,
        NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private ForgeNetwork() {
    }

    /** Called once from the mod constructor. */
    static void register() {
        int index = 0;
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerClientbound(index++, entry);
        }
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            registerServerbound(index++, entry);
        }
    }

    /**
     * Whether this connection went through Forge's handshake with a Forge client. GameTest players (a netty
     * {@code EmbeddedChannel} without the handshake), fake players (no channel) and vanilla clients are not; Forge's
     * {@code NetworkHooks.isVanillaConnection} throws without the FML version attribute, so that is checked first.
     */
    static boolean isModdedConnection(@Nullable Connection connection) {
        Channel channel = connection == null ? null : connection.channel();
        return channel != null && channel.attr(FML_NETVERSION).get() != null && !NetworkHooks.isVanillaConnection(connection);
    }

    /** The player's client has Tradery's channel. */
    static boolean canSend(ServerPlayer player) {
        Connection connection = player.connection == null ? null : player.connection.connection;
        return isModdedConnection(connection) && CHANNEL.isRemotePresent(connection);
    }

    /** Decoded on the network thread (the reader bounds every size; garbage throws and disconnects the sender). */
    private static <T extends TraderyPacket> void registerServerbound(int index, TraderyPayloads.Entry<T> entry) {
        CHANNEL.messageBuilder(entry.type(), index, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TraderyPacket::write)
            .decoder(entry.reader())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.get().getSender();
                if (sender != null) {
                    TraderyServer.handle(sender, packet);
                }
            })
            .add();
    }

    private static <T extends TraderyPacket> void registerClientbound(int index, TraderyPayloads.Entry<T> entry) {
        CHANNEL.messageBuilder(entry.type(), index, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TraderyPacket::write)
            .decoder(entry.reader())
            .consumerMainThread((packet, context) -> handleOnClient(packet))
            .add();
    }

    /** Dedicated servers never receive clientbound packets; the client class is only touched on the client. */
    private static void handleOnClient(TraderyPacket packet) {
        dev.eliasnvx.tradery.client.TraderyClient.handle(packet);
    }
}
