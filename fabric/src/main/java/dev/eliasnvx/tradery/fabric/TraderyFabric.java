package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.network.TraderyPacket;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.server.TraderyServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

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

        // Clientbound channels need no server registration on 1.20.1: the client announces what it receives
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            registerServerReceiver(entry);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> TraderyServer.registerCommands(dispatcher));
        // The economy is up before other mods' SERVER_STARTED listeners and still up in their SERVER_STOPPED ones
        ResourceLocation economyPhase = Tradery.id("economy");
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
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            TraderyServer.onPlayerRespawnOrTravel(player));
    }

    /**
     * The body is decoded on the network thread (the buffer is released after the handler returns; the reader bounds
     * every size and throws on garbage, which disconnects the sender), then handled on the server thread.
     */
    private static <T extends TraderyPacket> void registerServerReceiver(TraderyPayloads.Entry<T> entry) {
        ServerPlayNetworking.registerGlobalReceiver(entry.id(), (server, player, handler, buf, sender) -> {
            T packet = entry.reader().apply(buf);
            server.execute(() -> TraderyServer.handle(player, packet));
        });
    }
}
