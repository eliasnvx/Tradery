package dev.eliasnvx.tradery.network;

import dev.eliasnvx.tradery.Tradery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Every Tradery packet. Decoders never trust sizes from the wire: strings and lists are bounded, so a malformed
 * or hostile packet fails fast instead of allocating.
 */
public final class TraderyPayloads {
    public static final int MAX_ID = 256;
    public static final int MAX_TEXT = 256;
    public static final int MAX_ARGS = 8;
    /** A notification argument starting with this is a translation key, translated on the client. */
    public static final String TRANSLATABLE_PREFIX = "\u0001";

    /** A packet channel with its type and reader, for the loaders to register. */
    public record Entry<T extends TraderyPacket>(ResourceLocation id, Class<T> type, Function<FriendlyByteBuf, T> reader) {
    }

    /** Cuts text that would make the encoder throw (which disconnects the player). */
    public static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

    private static void writeId(FriendlyByteBuf buf, String value) {
        buf.writeUtf(clip(value, MAX_ID), MAX_ID);
    }

    private static void writeText(FriendlyByteBuf buf, String value) {
        buf.writeUtf(clip(value, MAX_TEXT), MAX_TEXT);
    }

    private static String readId(FriendlyByteBuf buf) {
        return buf.readUtf(MAX_ID);
    }

    private static String readText(FriendlyByteBuf buf) {
        return buf.readUtf(MAX_TEXT);
    }

    /**
     * How the client displays money, and what one coin of each tier is worth (for tooltips). Sent on join (before
     * the balance) and after /tradery reload.
     */
    public record CurrencyInfoPayload(String currencyId, String name, String symbol, int decimals, String thousandsSeparator,
                                      long copperValue, long silverValue, long goldValue) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("currency_info");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            writeId(buf, currencyId);
            writeText(buf, name);
            writeText(buf, symbol);
            buf.writeVarInt(decimals);
            writeText(buf, thousandsSeparator);
            buf.writeVarLong(copperValue);
            buf.writeVarLong(silverValue);
            buf.writeVarLong(goldValue);
        }

        public static CurrencyInfoPayload read(FriendlyByteBuf buf) {
            String id = readId(buf);
            String name = readText(buf);
            String symbol = readText(buf);
            int decimals = buf.readVarInt();
            if (decimals < 0 || decimals > 6) {
                throw new IllegalArgumentException("Bad currency decimals: " + decimals);
            }
            return new CurrencyInfoPayload(id, name, symbol, decimals, readText(buf),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
        }
    }

    /** The player's full balance: on join, respawn and dimension change. */
    public record BalanceSyncPayload(String currencyId, long balance) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("balance_sync");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            writeId(buf, currencyId);
            buf.writeVarLong(balance);
        }

        public static BalanceSyncPayload read(FriendlyByteBuf buf) {
            return new BalanceSyncPayload(readId(buf), buf.readVarLong());
        }
    }

    /** One change of the player's balance: drives the HUD popup. */
    public record BalanceDeltaPayload(long delta, long balance, String reasonType) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("balance_delta");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(delta);
            buf.writeVarLong(balance);
            writeId(buf, reasonType);
        }

        public static BalanceDeltaPayload read(FriendlyByteBuf buf) {
            long delta = buf.readVarLong();
            long balance = buf.readVarLong();
            return new BalanceDeltaPayload(delta, balance, readId(buf));
        }
    }

    /**
     * A message for the player that the client may filter by its settings (sales, empty vendor...). The text is
     * a translation key with plain string arguments, translated on the client.
     */
    public record NotificationPayload(NotificationKind kind, String key, List<String> args) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("notification");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind.ordinal());
            writeId(buf, key);
            int count = Math.min(args.size(), MAX_ARGS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writeText(buf, args.get(i));
            }
        }

        public static NotificationPayload read(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            if (kind < 0 || kind >= NotificationKind.VALUES.length) {
                throw new IllegalArgumentException("Bad notification kind: " + kind);
            }
            String key = readId(buf);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ARGS) {
                throw new IllegalArgumentException("Too many notification arguments: " + count);
            }
            List<String> args = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                args.add(readText(buf));
            }
            return new NotificationPayload(NotificationKind.VALUES[kind], key, List.copyOf(args));
        }
    }

    /** Kinds of {@link NotificationPayload}; each can be switched off in the client config. */
    public enum NotificationKind {
        INFO, SALE, EMPTY, NO_SPACE, OFFLINE_SUMMARY;

        static final NotificationKind[] VALUES = values();
    }

    /** {@code /tradery hud}: flip the HUD on the client. */
    public record HudTogglePayload() implements TraderyPacket {
        public static final HudTogglePayload INSTANCE = new HudTogglePayload();
        public static final ResourceLocation ID = Tradery.id("hud_toggle");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
        }

        public static HudTogglePayload read(FriendlyByteBuf buf) {
            return INSTANCE;
        }
    }

    /** The outcome of a trade or of saving vendor settings, shown inside the open vending screen. */
    public record VendingResultPayload(int containerId, boolean success, Component message) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("vending_result");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeBoolean(success);
            buf.writeComponent(message);
        }

        /** Clientbound only: the component comes from the server, which the client trusts (bounded by the reader). */
        public static VendingResultPayload read(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            boolean success = buf.readBoolean();
            return new VendingResultPayload(containerId, success, buf.readComponent());
        }
    }

    /**
     * The owner saves the draft settings of the open vending menu. Only the money price travels here; goods,
     * price item and facade are the server's own copies of the menu's sample slots.
     */
    public record VendingSavePayload(int containerId, long price) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("vending_save");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarLong(price);
        }

        public static VendingSavePayload read(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            return new VendingSavePayload(containerId, buf.readVarLong());
        }
    }

    /**
     * A recipe viewer (JEI/REI) dropped an item on a sample slot of the open menu. Samples are copies, never real
     * items, so the server only checks the slot is a sample that accepts this item.
     */
    public record GhostSamplePayload(int containerId, int slot, ItemStack stack) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("ghost_sample");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarInt(slot);
            buf.writeItem(stack);
        }

        /** The item's tag is read with the vanilla NBT size quota. */
        public static GhostSamplePayload read(FriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            int slot = buf.readVarInt();
            return new GhostSamplePayload(containerId, slot, buf.readItem());
        }
    }

    /**
     * Sneak + use (buy) or sneak + attack (sell) on a vending block: one trade, no window. Sent again while the
     * button is held, at most every 4 ticks; the server checks reach, the block and the trade rate limit.
     */
    public record VendingQuickTradePayload(BlockPos pos, boolean sell) implements TraderyPacket {
        public static final ResourceLocation ID = Tradery.id("vending_quick_trade");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(sell);
        }

        public static VendingQuickTradePayload read(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            return new VendingQuickTradePayload(pos, buf.readBoolean());
        }
    }

    /** Server → client packets. */
    public static final List<Entry<?>> CLIENTBOUND = List.of(
        new Entry<>(CurrencyInfoPayload.ID, CurrencyInfoPayload.class, CurrencyInfoPayload::read),
        new Entry<>(BalanceSyncPayload.ID, BalanceSyncPayload.class, BalanceSyncPayload::read),
        new Entry<>(BalanceDeltaPayload.ID, BalanceDeltaPayload.class, BalanceDeltaPayload::read),
        new Entry<>(NotificationPayload.ID, NotificationPayload.class, NotificationPayload::read),
        new Entry<>(HudTogglePayload.ID, HudTogglePayload.class, HudTogglePayload::read),
        new Entry<>(VendingResultPayload.ID, VendingResultPayload.class, VendingResultPayload::read)
    );

    /** Client → server packets. */
    public static final List<Entry<?>> SERVERBOUND = List.of(
        new Entry<>(VendingSavePayload.ID, VendingSavePayload.class, VendingSavePayload::read),
        new Entry<>(GhostSamplePayload.ID, GhostSamplePayload.class, GhostSamplePayload::read),
        new Entry<>(VendingQuickTradePayload.ID, VendingQuickTradePayload.class, VendingQuickTradePayload::read)
    );

    private TraderyPayloads() {
    }
}
