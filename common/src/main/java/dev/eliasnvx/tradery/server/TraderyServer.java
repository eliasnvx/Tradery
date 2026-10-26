package dev.eliasnvx.tradery.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.eliasnvx.tradery.command.EconomyCommands;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPacket;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingNotifier;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.vending.VendingQuickTrade;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
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
        dev.eliasnvx.tradery.rewards.Rewards.load();
    }

    public static void onServerStopped(MinecraftServer server) {
        EconomyService.INSTANCE.stop();
        dev.eliasnvx.tradery.vending.TradePersistence.clear();
        dev.eliasnvx.tradery.village.VillagerCustomers.onServerStopped();
        dev.eliasnvx.tradery.village.VillageCommerce.onServerStopped();
        dev.eliasnvx.tradery.vending.VendingTrades.clear();
        dev.eliasnvx.tradery.vending.VendingQuickTrade.clear();
        dev.eliasnvx.tradery.rewards.Rewards.clear();
        dev.eliasnvx.tradery.village.VillageBoard.clear();
        dev.eliasnvx.tradery.village.VillagePresence.clear();
        dev.eliasnvx.tradery.village.VillageStreets.clear();
        dev.eliasnvx.tradery.village.VillageLamps.clear();
    }

    public static void onPlayerJoin(ServerPlayer player) {
        EconomyService.INSTANCE.onPlayerJoin(player);
        VendingNotifier.onJoin(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        VendingTrades.forget(player);
        VendingQuickTrade.forget(player);
        dev.eliasnvx.tradery.rewards.Rewards.forget(player);
        dev.eliasnvx.tradery.vending.TradePersistence.onPlayerLeave(player);
        dev.eliasnvx.tradery.village.VillageBoard.forget(player);
        dev.eliasnvx.tradery.village.VillagePresence.forget(player);
    }

    /** A living entity died (not cancelled): kill rewards; a villager's wallet goes with it. */
    public static void onLivingDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        dev.eliasnvx.tradery.rewards.Rewards.onDeath(entity, source);
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager) {
            dev.eliasnvx.tradery.village.VillagerCustomers.onVillagerDeath(villager);
            dev.eliasnvx.tradery.village.Villages.onVillagerDeath(villager, source.getEntity());
        }
    }

    /** End of a server level's tick: villagers walking to vending blocks (Living Market). */
    public static void onLevelTick(net.minecraft.server.level.ServerLevel level) {
        dev.eliasnvx.tradery.village.VillagerCustomers.onLevelTick(level);
        dev.eliasnvx.tradery.village.Villages.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillageCommerce.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillagePresence.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillageBuildings.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillageFestivals.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillageStreets.onLevelTick(level);
        dev.eliasnvx.tradery.village.VillageLamps.onLevelTick(level);
        dev.eliasnvx.tradery.vending.TradePersistence.flush(level.getServer());
    }

    /** A player broke a block (not cancelled): mine rewards. */
    public static void onBlockBroken(Player player, Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.eliasnvx.tradery.rewards.Rewards.onBlockBroken(player, serverLevel, pos, state);
        }
    }

    /**
     * Serverbound packets, on the server thread. Each one is checked against the menu the player really has open
     * (and its container id); reach, ownership and rate limits are checked by the handlers.
     */
    public static void handle(ServerPlayer player, TraderyPacket payload) {
        if (payload instanceof TraderyPayloads.VendingSavePayload save
            && player.containerMenu instanceof VendingOwnerMenu menu && menu.containerId == save.containerId()) {
            VendingConfigurator.save(player, menu, save.price());
        } else if (payload instanceof TraderyPayloads.GhostSamplePayload ghost && player.containerMenu.containerId == ghost.containerId()) {
            dev.eliasnvx.tradery.menu.GhostSlots.setFromViewer(player, player.containerMenu, ghost.slot(), ghost.stack());
        } else if (payload instanceof TraderyPayloads.VendingQuickTradePayload quick) {
            VendingQuickTrade.handle(player, quick.pos(), quick.sell());
        } else if (payload instanceof TraderyPayloads.BellInfoRequestPayload bell) {
            dev.eliasnvx.tradery.village.VillageBoard.onBellRequest(player, bell.pos());
        } else if (payload instanceof TraderyPayloads.CounterSavePayload save
            && player.containerMenu instanceof dev.eliasnvx.tradery.menu.CounterOwnerMenu menu && menu.containerId == save.containerId()) {
            dev.eliasnvx.tradery.vending.CounterConfigurator.save(player, menu, save.prices());
        } else if (payload instanceof TraderyPayloads.VillageDonatePayload donate
            && player.containerMenu instanceof dev.eliasnvx.tradery.menu.VillageBoardMenu menu && menu.containerId == donate.containerId()) {
            menu.donate(player, donate.amount());
        }
    }

    /** Break events of the loaders: false cancels (non-owners, including creative mode's instant break). */
    public static boolean mayBreak(Player player, Level level, BlockPos pos) {
        if (!dev.eliasnvx.tradery.village.VillageBoardBlock.mayBreak(player, level, pos)) {
            return false;
        }
        return VendingProtection.mayBreak(player, level, pos);
    }

    /** Respawn and dimension change recreate the client player: resend the balance. */
    public static void onPlayerRespawnOrTravel(ServerPlayer player) {
        EconomyService.INSTANCE.syncBalance(player);
    }
}
