package dev.eliasnvx.tradery.neoforge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.server.TraderyServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(Tradery.MOD_ID)
public final class TraderyNeoForge {
    public TraderyNeoForge(IEventBus modBus) {
        NeoForgePlatform platform = new NeoForgePlatform();
        Platform.install(platform);
        Tradery.init();
        dev.eliasnvx.tradery.registry.TraderyBlocks.init();
        platform.registerAttachments();
        platform.attach(modBus);

        modBus.addListener(TraderyNeoForge::registerPayloads);
        TraderyGameTestsNeoForge.register(modBus);
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(NeoForgePlatform.nodes()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> TraderyServer.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> TraderyServer.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> TraderyServer.onServerStopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerLeave(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            if (!TraderyServer.mayBreak(event.getPlayer(), (net.minecraft.world.level.Level) event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        });
        // Last, so protections of other mods had their say
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (BreakBlockEvent event) -> {
            if (!event.isCanceled()) {
                TraderyServer.onBlockBroken(event.getPlayer(), (net.minecraft.world.level.Level) event.getLevel(), event.getPos(), event.getState());
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
            (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
                if (!event.isCanceled()) {
                    TraderyServer.onLivingDeath(event.getEntity(), event.getSource());
                }
            });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerRespawnOrTravel(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerRespawnOrTravel(player);
            }
        });
    }

    /** Optional channels: a server without Tradery on the client side just doesn't get the HUD packets. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Tradery.MOD_ID).versioned("1").optional().executesOn(HandlerThread.MAIN);
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.CLIENTBOUND) {
            registerClientbound(registrar, entry);
        }
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            registerServerbound(registrar, entry);
        }
    }

    private static <T extends CustomPacketPayload> void registerServerbound(PayloadRegistrar registrar, TraderyPayloads.Entry<T> entry) {
        registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                TraderyServer.handle(player, payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerClientbound(PayloadRegistrar registrar, TraderyPayloads.Entry<T> entry) {
        registrar.playToClient(entry.type(), entry.codec(), TraderyNeoForge::handleOnClient);
    }

    /** Dedicated servers never receive clientbound payloads; the client class is only touched on the client. */
    private static <T extends CustomPacketPayload> void handleOnClient(T payload, IPayloadContext context) {
        dev.eliasnvx.tradery.client.TraderyClient.handle(payload);
    }
}
