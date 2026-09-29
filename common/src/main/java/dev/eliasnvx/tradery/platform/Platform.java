package dev.eliasnvx.tradery.platform;

import dev.eliasnvx.tradery.command.TraderyPermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * What common code needs from the loader. Each loader installs its implementation first thing in its mod
 * initializer ({@link #install}); nothing in common runs before that.
 */
public interface Platform {

    /** "fabric" or "neoforge". */
    String loaderName();

    boolean isModLoaded(String modId);

    /** The game's config directory ({@code config/}). */
    Path configDir();

    /** A mod's machine acting as a player (Fabric / NeoForge {@code FakePlayer}). */
    boolean isFakePlayer(ServerPlayer player);

    /** Permission check through the loader's permission API (LuckPerms), falling back to the node's op level. */
    boolean hasPermission(CommandSourceStack source, TraderyPermission permission);

    /** Same for a player outside of commands (GUI actions). */
    boolean hasPermission(ServerPlayer player, TraderyPermission permission);

    /** Sends a payload if the player's client has Tradery; vanilla clients never get it. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client side: whether the connected server accepts this payload (it has Tradery too). */
    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    /** Client side: sends a payload to the server; check {@link #canSendToServer} first. */
    void sendToServer(CustomPacketPayload payload);

    static Platform get() {
        Platform platform = Holder.instance;
        if (platform == null) {
            throw new IllegalStateException("Tradery platform not installed yet");
        }
        return platform;
    }

    static void install(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static volatile Platform instance;

        private Holder() {
        }
    }
}
