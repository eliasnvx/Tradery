package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

final class FabricPlatform implements Platform {
    /** Fabric permission API ids: tradery:balance/others (LuckPerms shows tradery.balance.others). */
    private static final Map<TraderyPermission, Identifier> PERMISSION_IDS = new EnumMap<>(TraderyPermission.class);

    static {
        for (TraderyPermission permission : TraderyPermission.values()) {
            PERMISSION_IDS.put(permission, Tradery.id(permission.identifierPath()));
        }
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, TraderyPermission permission) {
        return source.checkPermission(PERMISSION_IDS.get(permission), permission.fallback());
    }

    @Override
    public boolean hasPermission(ServerPlayer player, TraderyPermission permission) {
        return player.checkPermission(PERMISSION_IDS.get(permission), permission.fallback());
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && Client.canSend(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        Client.send(payload);
    }

    /** Client-only classes, loaded only when these are called on the client. */
    private static final class Client {
        static boolean canSend(CustomPacketPayload.Type<?> type) {
            return ClientPlayNetworking.canSend(type);
        }

        static void send(CustomPacketPayload payload) {
            ClientPlayNetworking.send(payload);
        }
    }
}
