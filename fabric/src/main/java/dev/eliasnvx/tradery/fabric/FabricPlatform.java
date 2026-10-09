package dev.eliasnvx.tradery.fabric;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.network.TraderyPacket;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.rewards.PlacedBlocks;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class FabricPlatform implements Platform {
    /** Player-placed block marks per chunk; saved with the chunk. */
    private static final AttachmentType<LongSet> PLACED = AttachmentRegistry.createPersistent(Tradery.id("placed_blocks"),
        PlacedBlocks.CODEC);

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

    /** lucko's fabric-permissions-api (shipped in the jar): LuckPerms & co. answer for tradery.balance.others etc. */
    @Override
    public boolean hasPermission(CommandSourceStack source, TraderyPermission permission) {
        return Permissions.check(source, permission.node(), permission.fallback());
    }

    @Override
    public boolean hasPermission(ServerPlayer player, TraderyPermission permission) {
        return Permissions.check(player, permission.node(), permission.fallback());
    }

    /** Clients without Tradery never registered the channel; fake players have no client at all. */
    @Override
    public void sendToPlayer(ServerPlayer player, TraderyPacket packet) {
        if (!(player instanceof FakePlayer) && ServerPlayNetworking.canSend(player, packet.id())) {
            ServerPlayNetworking.send(player, packet.id(), encode(packet));
        }
    }

    @Override
    public boolean canSendToServer(ResourceLocation id) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && Client.canSend(id);
    }

    @Override
    public void sendToServer(TraderyPacket packet) {
        Client.send(packet);
    }

    static FriendlyByteBuf encode(TraderyPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        return buf;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<? extends T> factory) {
        Registry<? super T> target = (Registry<? super T>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) registry);
        T value = Registry.register(target, Tradery.id(name), factory.get());
        return () -> value;
    }

    @Override
    public <M extends AbstractContainerMenu, D> MenuType<M> menuType(MenuFactory<M, D> factory, Function<FriendlyByteBuf, D> reader) {
        return new ExtendedScreenHandlerType<>((containerId, inventory, buf) -> factory.create(containerId, inventory, reader.apply(buf)));
    }

    /** Fabric's FakePlayer#openMenu opens nothing, so fake players get no menu here either. */
    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, BiConsumer<FriendlyByteBuf, D> writer, D data) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer opener, FriendlyByteBuf buf) {
                writer.accept(buf, data);
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

    @Override
    public LongSet placedBlocks(LevelChunk chunk) {
        return chunk.getAttachedOrElse(PLACED, LongSets.EMPTY_SET);
    }

    /** Setting or removing an attachment marks the chunk unsaved (Fabric API does it). */
    @Override
    public void setPlacedBlocks(LevelChunk chunk, LongSet positions) {
        if (positions.isEmpty()) {
            chunk.removeAttached(PLACED);
        } else {
            chunk.setAttached(PLACED, positions);
        }
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricItemGroup.builder();
    }

    /** Client-only classes, loaded only when these are called on the client. */
    private static final class Client {
        static boolean canSend(ResourceLocation id) {
            return ClientPlayNetworking.canSend(id);
        }

        static void send(TraderyPacket packet) {
            ClientPlayNetworking.send(packet.id(), encode(packet));
        }
    }
}
