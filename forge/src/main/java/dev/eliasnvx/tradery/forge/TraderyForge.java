package dev.eliasnvx.tradery.forge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.server.TraderyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

@Mod(Tradery.MOD_ID)
public final class TraderyForge {
    /** The static context accessor works on every Forge 47 build (early ones can't pass the context to the constructor). */
    @SuppressWarnings("removal")
    public TraderyForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgePlatform platform = new ForgePlatform();
        Platform.install(platform);
        Tradery.init();
        dev.eliasnvx.tradery.registry.TraderyBlocks.init();
        platform.attach(modBus);
        platform.registerPlacedBlocks(modBus);
        ForgeNetwork.register();
        TraderyGameTestsForge.register(modBus);
        if (FMLEnvironment.dist.isClient()) {
            dev.eliasnvx.tradery.forge.client.TraderyForgeClient.init(modBus);
        }

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(ForgePlatform.nodes()));
        bus.addListener((RegisterCommandsEvent event) -> TraderyServer.registerCommands(event.getDispatcher()));
        // The economy is up before other mods' ServerStartedEvent listeners and still up in their ServerStoppedEvent ones
        bus.addListener(EventPriority.HIGHEST, (ServerStartedEvent event) -> TraderyServer.onServerStarted(event.getServer()));
        bus.addListener(EventPriority.LOWEST, (ServerStoppedEvent event) -> TraderyServer.onServerStopped(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerJoin(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerLeave(player);
            }
        });
        bus.addListener((BlockEvent.BreakEvent event) -> {
            if (!TraderyServer.mayBreak(event.getPlayer(), (net.minecraft.world.level.Level) event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        });
        // Last, so protections of other mods had their say
        bus.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent event) -> {
            if (!event.isCanceled()) {
                TraderyServer.onBlockBroken(event.getPlayer(), (net.minecraft.world.level.Level) event.getLevel(), event.getPos(), event.getState());
            }
        });
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (!event.isCanceled()) {
                TraderyServer.onLivingDeath(event.getEntity(), event.getSource());
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerRespawnOrTravel(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TraderyServer.onPlayerRespawnOrTravel(player);
            }
        });
    }
}
