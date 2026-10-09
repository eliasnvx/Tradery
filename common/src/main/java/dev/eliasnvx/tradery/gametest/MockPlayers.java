package dev.eliasnvx.tradery.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * GameTest players. 1.20.1's {@code GameTestHelper#makeMockServerPlayerInLevel} gives the player a connection
 * without a netty channel; Forge's login hooks then crash sending their packets. This copy attaches the connection
 * to an {@link EmbeddedChannel} (what vanilla itself does from 1.20.5 on), so the player works on every loader.
 * Like the vanilla mock player it is in creative mode and never a spectator.
 */
public final class MockPlayers {
    private static final String NAME = "test-mock-player";

    private MockPlayers() {
    }

    /** A new player with a random UUID, joined to the server and placed in the test's level. */
    public static ServerPlayer create(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), NAME)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // registers the connection as the channel's handler: it now has a channel
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }
}
