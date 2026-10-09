package dev.eliasnvx.tradery.forge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.network.TraderyPacket;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class ForgePlatform implements Platform {
    /** Nodes tradery.balance.others etc.; the default resolver is the op level fallback. */
    private static final Map<TraderyPermission, PermissionNode<Boolean>> NODES = new EnumMap<>(TraderyPermission.class);

    static {
        for (TraderyPermission permission : TraderyPermission.values()) {
            NODES.put(permission, new PermissionNode<>(Tradery.MOD_ID, permission.path(), PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && permission.fallbackAllows(player)));
        }
    }

    static List<PermissionNode<?>> nodes() {
        return List.copyOf(NODES.values());
    }

    @Override
    public String loaderName() {
        return "forge";
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
        return permission.fallbackAllows(source);
    }

    @Override
    public boolean hasPermission(ServerPlayer player, TraderyPermission permission) {
        return PermissionAPI.getPermission(player, NODES.get(permission));
    }

    /** Fake players (Create & co.) have no client; vanilla clients and GameTest players have no Tradery channel. */
    @Override
    public void sendToPlayer(ServerPlayer player, TraderyPacket packet) {
        if (!(player instanceof FakePlayer) && ForgeNetwork.canSend(player)) {
            ForgeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    @Override
    public boolean canSendToServer(ResourceLocation id) {
        return FMLEnvironment.dist.isClient() && isServerbound(id) && Client.canSend();
    }

    private static boolean isServerbound(ResourceLocation id) {
        for (TraderyPayloads.Entry<?> entry : TraderyPayloads.SERVERBOUND) {
            if (entry.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void sendToServer(TraderyPacket packet) {
        ForgeNetwork.CHANNEL.sendToServer(packet);
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
    public <M extends AbstractContainerMenu, D> MenuType<M> menuType(MenuFactory<M, D> factory, Function<FriendlyByteBuf, D> reader) {
        return IForgeMenuType.create((containerId, inventory, buf) -> factory.create(containerId, inventory, reader.apply(buf)));
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, BiConsumer<FriendlyByteBuf, D> writer, D data) {
        if (player instanceof FakePlayer) {
            // Forge 47's FakePlayer would keep a server-side menu open; fake players get none (as on Fabric)
            return;
        }
        if (ForgeNetwork.isModdedConnection(player.connection.connection)) {
            NetworkHooks.openScreen(player, provider, buf -> writer.accept(buf, data));
        } else {
            // Connections without Forge's handshake (GameTest players, vanilla clients) can't take the opening data;
            // the server-side menu works the same without it
            player.openMenu(provider);
        }
    }

    // ------------------------------------------------------------------ player-placed blocks

    /** Player-placed block marks of a server chunk, saved with the chunk (under {@code ForgeCaps}). */
    static final Capability<PlacedBlocksData> PLACED = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Registers the capability and attaches it to every server chunk. */
    void registerPlacedBlocks(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(PlacedBlocksData.class));
        MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class, (AttachCapabilitiesEvent<LevelChunk> event) -> {
            if (event.getObject().getLevel() instanceof ServerLevel) {
                event.addCapability(Tradery.id("placed_blocks"), new PlacedBlocksData());
            }
        });
    }

    @Override
    public LongSet placedBlocks(LevelChunk chunk) {
        return chunk.getCapability(PLACED).map(PlacedBlocksData::positions).orElse(LongSets.EMPTY_SET);
    }

    @Override
    public void setPlacedBlocks(LevelChunk chunk, LongSet positions) {
        chunk.getCapability(PLACED).ifPresent(data -> {
            data.positions = positions.isEmpty() ? LongSets.EMPTY_SET : positions;
            chunk.setUnsaved(true);
        });
    }

    /** The marks; replaced as a whole, never mutated (see {@code PlacedBlocks}). */
    static final class PlacedBlocksData implements ICapabilitySerializable<LongArrayTag> {
        private final LazyOptional<PlacedBlocksData> self = LazyOptional.of(() -> this);
        private LongSet positions = LongSets.EMPTY_SET;

        LongSet positions() {
            return positions;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return PLACED.orEmpty(capability, self);
        }

        @Override
        public LongArrayTag serializeNBT() {
            return new LongArrayTag(positions.toLongArray());
        }

        @Override
        public void deserializeNBT(LongArrayTag tag) {
            long[] values = tag.getAsLongArray();
            positions = values.length == 0 ? LongSets.EMPTY_SET : new LongOpenHashSet(values);
        }
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    /** Client-only classes, loaded only when these are called on the client. */
    private static final class Client {
        static boolean canSend() {
            var connection = Minecraft.getInstance().getConnection();
            return connection != null && ForgeNetwork.CHANNEL.isRemotePresent(connection.getConnection());
        }
    }
}
