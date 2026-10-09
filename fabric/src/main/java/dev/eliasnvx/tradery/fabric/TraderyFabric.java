package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.server.TraderyServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class TraderyFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatform());
        Tradery.init();
        dev.eliasnvx.tradery.registry.TraderyBlocks.init();
        // Common Economy API is inside our jar: always register; Placeholder API only when installed
        dev.eliasnvx.tradery.fabric.compat.commoneconomy.TraderyEconomyProvider.register();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            dev.eliasnvx.tradery.fabric.compat.placeholders.TraderyPlaceholders.register();
        }

        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerClientbound(entry);
        }
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            registerServerbound(entry);
            registerServerReceiver(entry.type());
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> TraderyServer.registerCommands(dispatcher));
        // The economy is up before other mods' SERVER_STARTED listeners and still up in their SERVER_STOPPED ones
        Identifier economyPhase = Tradery.id("economy");
        ServerLifecycleEvents.SERVER_STARTED.addPhaseOrdering(economyPhase, Event.DEFAULT_PHASE);
        ServerLifecycleEvents.SERVER_STARTED.register(economyPhase, TraderyServer::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.addPhaseOrdering(Event.DEFAULT_PHASE, economyPhase);
        ServerLifecycleEvents.SERVER_STOPPED.register(economyPhase, TraderyServer::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TraderyServer.onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TraderyServer.onPlayerLeave(handler.getPlayer()));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> TraderyServer.mayBreak(player, level, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> TraderyServer.onBlockBroken(player, level, pos, state));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(TraderyServer::onLivingDeath);
        for (var feature : dev.eliasnvx.tradery.ore.CoinOreGeneration.ALL) {
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(dev.eliasnvx.tradery.ore.CoinOreGeneration.BIOMES),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES, feature);
        }
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
