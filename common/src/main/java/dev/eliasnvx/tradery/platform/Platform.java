package dev.eliasnvx.tradery.platform;

import dev.eliasnvx.tradery.command.TraderyPermission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * What common code needs from the loader. Each loader installs its implementation first thing in its mod
 * initializer ({@link #install}); nothing in common runs before that.
 */
public interface Platform {

    /** "fabric" or "neoforge". */
    String loaderName();

    boolean isModLoaded(String modId);

    /** The game's config directory ({@code config/}). */
    Path configDir();

    /** A mod's machine acting as a player (Fabric / NeoForge {@code FakePlayer}). */
    boolean isFakePlayer(ServerPlayer player);

    /** Permission check through the loader's permission API (LuckPerms), falling back to the node's op level. */
    boolean hasPermission(CommandSourceStack source, TraderyPermission permission);

    /** Same for a player outside of commands (GUI actions). */
    boolean hasPermission(ServerPlayer player, TraderyPermission permission);

    /** Sends a payload if the player's client has Tradery; vanilla clients never get it. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client side: whether the connected server accepts this payload (it has Tradery too). */
    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    /** Client side: sends a payload to the server; check {@link #canSendToServer} first. */
    void sendToServer(CustomPacketPayload payload);

    /**
     * Registers an object into a vanilla registry. Fabric registers at once; NeoForge defers to its registry
     * events. The returned supplier works once registries are populated (after mod construction).
     */
    <T> Supplier<T> register(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<? extends T> factory);

    /** A menu type whose client-side menu is built from data sent when it opens. */
    <M extends AbstractContainerMenu, D> MenuType<M> menuType(MenuFactory<M, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec);

    /** Opens a menu of a {@link #menuType} type, sending its opening data. */
    <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> dataCodec, D data);

    /** Positions ({@code BlockPos#asLong}) of player-placed blocks in this chunk that matter for rewards. Don't mutate. */
    it.unimi.dsi.fastutil.longs.LongSet placedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk);

    /** Replaces the marks of a chunk (and marks it for saving). */
    void setPlacedBlocks(net.minecraft.world.level.chunk.LevelChunk chunk, it.unimi.dsi.fastutil.longs.LongSet positions);

    /** A creative tab builder the loader accepts (Fabric needs its own). */
    CreativeModeTab.Builder creativeTabBuilder();

    /** Builds a menu from its opening data (on the client) or from the server-side state. */
    @FunctionalInterface
    interface MenuFactory<M extends AbstractContainerMenu, D> {
        M create(int containerId, Inventory inventory, D data);
    }

    static Platform get() {
        Platform platform = Holder.instance;
        if (platform == null) {
            throw new IllegalStateException("Tradery platform not installed yet");
        }
        return platform;
    }

    static void install(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static volatile Platform instance;

        private Holder() {
        }
    }
}
