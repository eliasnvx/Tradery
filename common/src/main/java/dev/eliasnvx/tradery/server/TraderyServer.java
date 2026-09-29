package dev.eliasnvx.tradery.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.eliasnvx.tradery.command.EconomyCommands;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingNotifier;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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
        VendingNotifier.onJoin(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        VendingTrades.forget(player);
    }

    /** Serverbound payloads, on the server thread. */
    public static void handle(ServerPlayer player, CustomPacketPayload payload) {
        if (payload instanceof TraderyPayloads.VendingSavePayload save
            && player.containerMenu instanceof VendingOwnerMenu menu && menu.containerId == save.containerId()) {
            VendingConfigurator.save(player, menu, save.price());
        }
    }

    /** Break events of the loaders: false cancels (non-owners, including creative mode's instant break). */
    public static boolean mayBreak(Player player, Level level, BlockPos pos) {
        return VendingProtection.mayBreak(player, level, pos);
    }

    /** Respawn and dimension change recreate the client player: resend the balance. */
    public static void onPlayerRespawnOrTravel(ServerPlayer player) {
        EconomyService.INSTANCE.syncBalance(player);
    }
}
