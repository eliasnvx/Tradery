package dev.eliasnvx.tradery.neoforge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

    /** One DeferredRegister per registry, created on first use and attached to the mod bus by {@link #attach}. */
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private boolean attached;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<? extends T> factory) {
        if (attached) {
            throw new IllegalStateException("Too late to register " + name + ": registries are attached");
        }
        DeferredRegister<? super T> register = (DeferredRegister<? super T>) registers.computeIfAbsent(registry,
            key -> DeferredRegister.create((ResourceKey) key, Tradery.MOD_ID));
        return (Supplier<T>) (Supplier) register.register(name, factory);
    }

    /** Attaches every DeferredRegister to the mod bus; called once after common registration. */
    void attach(IEventBus modBus) {
        attached = true;
        registers.values().forEach(register -> register.register(modBus));
    }

    @Override
    public <M extends AbstractContainerMenu, D> MenuType<M> menuType(MenuFactory<M, D> factory,
                                                                     StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec) {
        return IMenuTypeExtension.create((containerId, inventory, buf) -> factory.create(containerId, inventory, dataCodec.decode(buf)));
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data) {
        if (player.connection.hasChannel(AdvancedOpenScreenPayload.TYPE)) {
            player.openMenu(provider, buf -> dataCodec.encode(buf, data));
        } else {
            // Connections without NeoForge's channel (GameTest mock players) can't take the opening data;
            // the server-side menu works the same without it
            player.openMenu(provider);
        }
    }

    /** Player-placed block marks per chunk; registered before the registers attach, saved with the chunk. */
    private Supplier<net.neoforged.neoforge.attachment.AttachmentType<it.unimi.dsi.fastutil.longs.LongSet>> placedType;

    void registerAttachments() {
        placedType = register(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "placed_blocks",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<it.unimi.dsi.fastutil.longs.LongSet>builder(
                    () -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet())
                .serialize(dev.eliasnvx.tradery.rewards.PlacedBlocks.CODEC.fieldOf("positions"), set -> !set.isEmpty())
                .build());
    }

    @Override
    public it.unimi.dsi.fastutil.longs.LongSet placedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk) {
        return chunk.hasData(placedType.get()) ? chunk.getData(placedType.get()) : it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;
    }

    @Override
    public void setPlacedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk, it.unimi.dsi.fastutil.longs.LongSet positions) {
        chunk.setData(placedType.get(), positions);
        chunk.markUnsaved();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
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
