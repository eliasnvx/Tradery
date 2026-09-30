package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<? extends T> factory) {
        Registry<? super T> target = (Registry<? super T>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registry);
        T value = Registry.register(target, Tradery.id(name), factory.get());
        return () -> value;
    }

    @Override
    public <M extends AbstractContainerMenu, D> MenuType<M> menuType(MenuFactory<M, D> factory,
                                                                     StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec) {
        return new ExtendedMenuType<>(factory::create, dataCodec);
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data) {
        player.openMenu(new ExtendedMenuProvider<D>() {
            @Override
            public D getScreenOpeningData(ServerPlayer opener) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return provider.createMenu(containerId, inventory, opener);
            }
        });
    }

    /** Player-placed block marks per chunk; saved with the chunk. */
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<it.unimi.dsi.fastutil.longs.LongSet> PLACED =
        net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(Tradery.id("placed_blocks"),
            builder -> builder.persistent(dev.eliasnvx.tradery.rewards.PlacedBlocks.CODEC));

    @Override
    public it.unimi.dsi.fastutil.longs.LongSet placedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk) {
        return chunk.getAttachedOrElse(PLACED, it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET);
    }

    @Override
    public void setPlacedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk, it.unimi.dsi.fastutil.longs.LongSet positions) {
        if (positions.isEmpty()) {
            chunk.removeAttached(PLACED);
        } else {
            chunk.setAttached(PLACED, positions);
        }
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricCreativeModeTab.builder();
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
