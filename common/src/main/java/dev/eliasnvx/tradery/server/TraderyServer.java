package dev.eliasnvx.tradery.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.eliasnvx.tradery.command.EconomyCommands;
import dev.eliasnvx.tradery.economy.EconomyService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side entry points, called by the loaders' event hooks on the server thread. */
public final class TraderyServer {
    private TraderyServer() {
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        EconomyCommands.register(dispatcher);
    }

    public static void onServerStarted(MinecraftServer server) {
        EconomyService.INSTANCE.start(server);
    }

    public static void onServerStopped(MinecraftServer server) {
        EconomyService.INSTANCE.stop();
    }

    public static void onPlayerJoin(ServerPlayer player) {
        EconomyService.INSTANCE.onPlayerJoin(player);
    }

    /** Respawn and dimension change recreate the client player: resend the balance. */
    public static void onPlayerRespawnOrTravel(ServerPlayer player) {
        EconomyService.INSTANCE.syncBalance(player);
    }
}
