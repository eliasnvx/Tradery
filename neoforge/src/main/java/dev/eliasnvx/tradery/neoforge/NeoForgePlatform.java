package dev.eliasnvx.tradery.neoforge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class NeoForgePlatform implements Platform {
    /** Nodes tradery.balance.others etc.; the default resolver is the op level fallback. */
    private static final Map<TraderyPermission, PermissionNode<Boolean>> NODES = new EnumMap<>(TraderyPermission.class);

    static {
        for (TraderyPermission permission : TraderyPermission.values()) {
            NODES.put(permission, new PermissionNode<>(Tradery.MOD_ID, permission.path(), PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && hasLevel(player, permission)));
        }
    }

    static List<PermissionNode<?>> nodes() {
        return List.copyOf(NODES.values());
    }

    private static boolean hasLevel(ServerPlayer player, TraderyPermission permission) {
        return player.permissions() instanceof LevelBasedPermissionSet set && set.level().isEqualOrHigherThan(permission.fallback());
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, TraderyPermission permission) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return hasPermission(player, permission);
        }
        // Console, command blocks, functions: their own permission level decides
        return source.permissions() instanceof LevelBasedPermissionSet set && set.level().isEqualOrHigherThan(permission.fallback());
    }

    @Override
    public boolean hasPermission(ServerPlayer player, TraderyPermission permission) {
        return PermissionAPI.getPermission(player, NODES.get(permission));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return FMLLoader.getCurrent().getDist().isClient() && Client.canSend(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        Client.send(payload);
    }

    /** Client-only classes, loaded only when these are called on the client. */
    private static final class Client {
        static boolean canSend(CustomPacketPayload.Type<?> type) {
            var connection = Minecraft.getInstance().getConnection();
            return connection != null && connection.hasChannel(type);
        }

        static void send(CustomPacketPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }
}
