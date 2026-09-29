package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.server.TraderyServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TraderyFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatform());
        Tradery.init();
        dev.eliasnvx.tradery.registry.TraderyBlocks.init();

        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerClientbound(entry);
        }
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            registerServerbound(entry);
            registerServerReceiver(entry.type());
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> TraderyServer.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(TraderyServer::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(TraderyServer::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TraderyServer.onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TraderyServer.onPlayerLeave(handler.getPlayer()));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> TraderyServer.mayBreak(player, level, pos));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> TraderyServer.onPlayerRespawnOrTravel(newPlayer));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
            TraderyServer.onPlayerRespawnOrTravel(player));
    }

    private static <T extends CustomPacketPayload> void registerClientbound(TraderyPayloads.Entry<T> entry) {
        PayloadTypeRegistry.clientboundPlay().register(entry.type(), entry.codec());
    }

    private static <T extends CustomPacketPayload> void registerServerbound(TraderyPayloads.Entry<T> entry) {
        PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
    }

    /** Fabric runs play payload handlers on the server thread. */
    private static <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> TraderyServer.handle(context.player(), payload));
    }
}
