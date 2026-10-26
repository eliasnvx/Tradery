package dev.eliasnvx.tradery.network;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Every Tradery payload. Decoders never trust sizes from the wire: strings and lists are bounded, so a malformed
 * or hostile packet fails fast instead of allocating.
 */
public final class TraderyPayloads {
    public static final int MAX_ID = 256;
    public static final int MAX_TEXT = 256;
    public static final int MAX_ARGS = 8;
    /** A notification argument starting with this is a translation key, translated on the client. */
    public static final String TRANSLATABLE_PREFIX = "\u0001";

    /** A payload type with its codec, for the loaders to register. */
    public record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Tradery.id(path));
    }

    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID);
    private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(MAX_TEXT);

    /** Cuts text that would make the encoder throw (which disconnects the player). */
    public static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

    /**
     * How the client displays money, and what one coin of each tier is worth (for tooltips). Sent on join (before
     * the balance) and after /tradery reload.
     */
    public record CurrencyInfoPayload(String currencyId, String name, String symbol, int decimals, String thousandsSeparator,
                                      long copperValue, long silverValue, long goldValue) implements CustomPacketPayload {
        public static final Type<CurrencyInfoPayload> TYPE = payloadType("currency_info");
        public static final StreamCodec<ByteBuf, CurrencyInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ID.encode(buf, clip(p.currencyId, MAX_ID));
                TEXT.encode(buf, clip(p.name, MAX_TEXT));
                TEXT.encode(buf, clip(p.symbol, MAX_TEXT));
                ByteBufCodecs.VAR_INT.encode(buf, p.decimals);
                TEXT.encode(buf, clip(p.thousandsSeparator, MAX_TEXT));
                ByteBufCodecs.VAR_LONG.encode(buf, p.copperValue);
                ByteBufCodecs.VAR_LONG.encode(buf, p.silverValue);
                ByteBufCodecs.VAR_LONG.encode(buf, p.goldValue);
            },
            buf -> {
                String id = ID.decode(buf);
                String name = TEXT.decode(buf);
                String symbol = TEXT.decode(buf);
                int decimals = ByteBufCodecs.VAR_INT.decode(buf);
                if (decimals < 0 || decimals > 6) {
                    throw new IllegalArgumentException("Bad currency decimals: " + decimals);
                }
                return new CurrencyInfoPayload(id, name, symbol, decimals, TEXT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
            });

        @Override
        public Type<CurrencyInfoPayload> type() {
            return TYPE;
        }
    }

    /** The player's full balance: on join, respawn and dimension change. */
    public record BalanceSyncPayload(String currencyId, long balance) implements CustomPacketPayload {
        public static final Type<BalanceSyncPayload> TYPE = payloadType("balance_sync");
        public static final StreamCodec<ByteBuf, BalanceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ID, BalanceSyncPayload::currencyId,
            ByteBufCodecs.VAR_LONG, BalanceSyncPayload::balance,
            BalanceSyncPayload::new);

        @Override
        public Type<BalanceSyncPayload> type() {
            return TYPE;
        }
    }

    /** One change of the player's balance: drives the HUD popup. */
    public record BalanceDeltaPayload(long delta, long balance, String reasonType) implements CustomPacketPayload {
        public static final Type<BalanceDeltaPayload> TYPE = payloadType("balance_delta");
        public static final StreamCodec<ByteBuf, BalanceDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BalanceDeltaPayload::delta,
            ByteBufCodecs.VAR_LONG, BalanceDeltaPayload::balance,
            ID, BalanceDeltaPayload::reasonType,
            BalanceDeltaPayload::new);

        @Override
        public Type<BalanceDeltaPayload> type() {
            return TYPE;
        }
    }

    /**
     * A message for the player that the client may filter by its settings (sales, empty vendor...). The text is
     * a translation key with plain string arguments, translated on the client.
     */
    public record NotificationPayload(NotificationKind kind, String key, List<String> args) implements CustomPacketPayload {
        public static final Type<NotificationPayload> TYPE = payloadType("notification");
        public static final StreamCodec<ByteBuf, NotificationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.kind.ordinal());
                ID.encode(buf, clip(p.key, MAX_ID));
                int count = Math.min(p.args.size(), MAX_ARGS);
                ByteBufCodecs.VAR_INT.encode(buf, count);
                for (int i = 0; i < count; i++) {
                    TEXT.encode(buf, clip(p.args.get(i), MAX_TEXT));
                }
            },
            buf -> {
                int kind = ByteBufCodecs.VAR_INT.decode(buf);
                if (kind < 0 || kind >= NotificationKind.VALUES.length) {
                    throw new IllegalArgumentException("Bad notification kind: " + kind);
                }
                String key = ID.decode(buf);
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX_ARGS) {
                    throw new IllegalArgumentException("Too many notification arguments: " + count);
                }
                List<String> args = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    args.add(TEXT.decode(buf));
                }
                return new NotificationPayload(NotificationKind.VALUES[kind], key, List.copyOf(args));
            });

        @Override
        public Type<NotificationPayload> type() {
            return TYPE;
        }
    }

    /** Kinds of {@link NotificationPayload}; each can be switched off in the client config. */
    public enum NotificationKind {
        INFO, SALE, EMPTY, NO_SPACE, OFFLINE_SUMMARY;

        static final NotificationKind[] VALUES = values();
    }

    /** {@code /tradery hud}: flip the HUD on the client. */
    public record HudTogglePayload() implements CustomPacketPayload {
        public static final HudTogglePayload INSTANCE = new HudTogglePayload();
        public static final Type<HudTogglePayload> TYPE = payloadType("hud_toggle");
        public static final StreamCodec<ByteBuf, HudTogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<HudTogglePayload> type() {
            return TYPE;
        }
    }

    /** The outcome of a trade or of saving vendor settings, shown inside the open vending screen. */
    public record VendingResultPayload(int containerId, boolean success, Component message) implements CustomPacketPayload {
        public static final Type<VendingResultPayload> TYPE = payloadType("vending_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, VendingResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VendingResultPayload::containerId,
            ByteBufCodecs.BOOL, VendingResultPayload::success,
            ComponentSerialization.TRUSTED_STREAM_CODEC, VendingResultPayload::message,
            VendingResultPayload::new);

        @Override
        public Type<VendingResultPayload> type() {
            return TYPE;
        }
    }

    /**
     * The owner saves the draft settings of the open vending menu. Only the money price travels here; goods,
     * price item and facade are the server's own copies of the menu's sample slots.
     */
    public record VendingSavePayload(int containerId, long price) implements CustomPacketPayload {
        public static final Type<VendingSavePayload> TYPE = payloadType("vending_save");
        public static final StreamCodec<ByteBuf, VendingSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VendingSavePayload::containerId,
            ByteBufCodecs.VAR_LONG, VendingSavePayload::price,
            VendingSavePayload::new);

        @Override
        public Type<VendingSavePayload> type() {
            return TYPE;
        }
    }

    /**
     * A recipe viewer (JEI/REI) dropped an item on a sample slot of the open menu. Samples are copies, never real
     * items, so the server only checks the slot is a sample that accepts this item.
     */
    public record GhostSamplePayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
        public static final Type<GhostSamplePayload> TYPE = payloadType("ghost_sample");
        public static final StreamCodec<RegistryFriendlyByteBuf, GhostSamplePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSamplePayload::containerId,
            ByteBufCodecs.VAR_INT, GhostSamplePayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, GhostSamplePayload::stack,
            GhostSamplePayload::new);

        @Override
        public Type<GhostSamplePayload> type() {
            return TYPE;
        }
    }

    /**
     * Sneak + use (buy) or sneak + attack (sell) on a vending block: one trade, no window. Sent again while the
     * button is held, at most every 4 ticks; the server checks reach, the block and the trade rate limit.
     */
    public record VendingQuickTradePayload(BlockPos pos, boolean sell) implements CustomPacketPayload {
        public static final Type<VendingQuickTradePayload> TYPE = payloadType("vending_quick_trade");
        public static final StreamCodec<ByteBuf, VendingQuickTradePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VendingQuickTradePayload::pos,
            ByteBufCodecs.BOOL, VendingQuickTradePayload::sell,
            VendingQuickTradePayload::new);

        @Override
        public Type<VendingQuickTradePayload> type() {
            return TYPE;
        }
    }

    /** Fresh offers, experience and wallet for the open villager trade window, after a trade. */
    public record VillagerTradeSyncPayload(int containerId, VillagerTradeMenu.Data data) implements CustomPacketPayload {
        public static final Type<VillagerTradeSyncPayload> TYPE = payloadType("villager_trade_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillagerTradeSyncPayload::containerId,
            VillagerTradeMenu.Data.STREAM_CODEC, VillagerTradeSyncPayload::data,
            VillagerTradeSyncPayload::new);

        @Override
        public Type<VillagerTradeSyncPayload> type() {
            return TYPE;
        }
    }

    /** Fresh data for the open village board window, after an action. */
    public record VillageBoardSyncPayload(int containerId, dev.eliasnvx.tradery.menu.VillageBoardMenu.Data data) implements CustomPacketPayload {
        public static final Type<VillageBoardSyncPayload> TYPE = payloadType("village_board_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, VillageBoardSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillageBoardSyncPayload::containerId,
            dev.eliasnvx.tradery.menu.VillageBoardMenu.Data.STREAM_CODEC, VillageBoardSyncPayload::data,
            VillageBoardSyncPayload::new);

        @Override
        public Type<VillageBoardSyncPayload> type() {
            return TYPE;
        }
    }

    /**
     * The client looks at a bell and asks about its village, at most every two seconds per bell. The server checks
     * the rate, reach and that a bell is there.
     */
    public record BellInfoRequestPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<BellInfoRequestPayload> TYPE = payloadType("bell_info_request");
        public static final StreamCodec<ByteBuf, BellInfoRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BellInfoRequestPayload::pos,
            BellInfoRequestPayload::new);

        @Override
        public Type<BellInfoRequestPayload> type() {
            return TYPE;
        }
    }

    /**
     * The village of a bell, for the look-at hint; {@code known} false for a bell Tradery knows no village of.
     * {@code nextThreshold} -1 at the top level, {@code marketDayIn} 0 today and -1 without market days.
     */
    public record BellInfoPayload(BlockPos pos, boolean known, Component name, int level, int prosperity, int nextThreshold,
                                  int marketDayIn) implements CustomPacketPayload {
        public static final Type<BellInfoPayload> TYPE = payloadType("bell_info");
        public static final StreamCodec<RegistryFriendlyByteBuf, BellInfoPayload> STREAM_CODEC = dev.eliasnvx.tradery.network.PortCodecs.composite(
            BlockPos.STREAM_CODEC, BellInfoPayload::pos,
            ByteBufCodecs.BOOL, BellInfoPayload::known,
            ComponentSerialization.TRUSTED_STREAM_CODEC, BellInfoPayload::name,
            ByteBufCodecs.VAR_INT, BellInfoPayload::level,
            ByteBufCodecs.VAR_INT, BellInfoPayload::prosperity,
            ByteBufCodecs.VAR_INT, BellInfoPayload::nextThreshold,
            ByteBufCodecs.VAR_INT, BellInfoPayload::marketDayIn,
            BellInfoPayload::new);

        @Override
        public Type<BellInfoPayload> type() {
            return TYPE;
        }
    }

    /**
     * The owner saves the draft of the open market counter menu: one money price per offer (exactly
     * {@code CounterSettings.MAX_OFFERS}). The goods are the server's own copies of the menu's sample slots.
     */
    public record CounterSavePayload(int containerId, List<Long> prices) implements CustomPacketPayload {
        public static final Type<CounterSavePayload> TYPE = payloadType("counter_save");
        public static final StreamCodec<ByteBuf, CounterSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CounterSavePayload::containerId,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(dev.eliasnvx.tradery.vending.CounterSettings.MAX_OFFERS)), CounterSavePayload::prices,
            CounterSavePayload::new);

        @Override
        public Type<CounterSavePayload> type() {
            return TYPE;
        }
    }

    /**
     * The player entered a village (or its level changed while there): its name and level for the title on screen;
     * {@code known} false when the player left villages behind; {@code festival} when a festival just started there.
     */
    public record VillageTitlePayload(Component name, int level, boolean known, boolean festival) implements CustomPacketPayload {
        public static final Type<VillageTitlePayload> TYPE = payloadType("village_title");
        public static final StreamCodec<RegistryFriendlyByteBuf, VillageTitlePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, VillageTitlePayload::name,
            ByteBufCodecs.VAR_INT, VillageTitlePayload::level,
            ByteBufCodecs.BOOL, VillageTitlePayload::known,
            ByteBufCodecs.BOOL, VillageTitlePayload::festival,
            VillageTitlePayload::new);

        @Override
        public Type<VillageTitlePayload> type() {
            return TYPE;
        }
    }

    /** "Donate" on the village board: {@code amount} (minor units, positive) from the viewer to the village's treasury. */
    public record VillageDonatePayload(int containerId, long amount) implements CustomPacketPayload {
        public static final Type<VillageDonatePayload> TYPE = payloadType("village_donate");
        public static final StreamCodec<ByteBuf, VillageDonatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillageDonatePayload::containerId,
            ByteBufCodecs.VAR_LONG, VillageDonatePayload::amount,
            VillageDonatePayload::new);

        @Override
        public Type<VillageDonatePayload> type() {
            return TYPE;
        }
    }

    /** Another page (or a fresh copy after an action) for the open shop ledger window. */
    public record ShopLedgerSyncPayload(int containerId, dev.eliasnvx.tradery.menu.ShopLedgerMenu.Data data) implements CustomPacketPayload {
        public static final Type<ShopLedgerSyncPayload> TYPE = payloadType("shop_ledger_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopLedgerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopLedgerSyncPayload::containerId,
            dev.eliasnvx.tradery.menu.ShopLedgerMenu.Data.STREAM_CODEC, ShopLedgerSyncPayload::data,
            ShopLedgerSyncPayload::new);

        @Override
        public Type<ShopLedgerSyncPayload> type() {
            return TYPE;
        }
    }

    /** "Show" in the shop ledger: the client draws a particle beam over this shop for a while (client-side only). */
    public record ShopBeamPayload(net.minecraft.core.GlobalPos pos) implements CustomPacketPayload {
        public static final Type<ShopBeamPayload> TYPE = payloadType("shop_beam");
        public static final StreamCodec<ByteBuf, ShopBeamPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.GlobalPos.STREAM_CODEC, ShopBeamPayload::pos,
            ShopBeamPayload::new);

        @Override
        public Type<ShopBeamPayload> type() {
            return TYPE;
        }
    }

    /** Server → client payloads. */
    public static final List<Entry<?>> CLIENTBOUND = List.of(
        new Entry<>(CurrencyInfoPayload.TYPE, CurrencyInfoPayload.STREAM_CODEC),
        new Entry<>(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC),
        new Entry<>(BalanceDeltaPayload.TYPE, BalanceDeltaPayload.STREAM_CODEC),
        new Entry<>(NotificationPayload.TYPE, NotificationPayload.STREAM_CODEC),
        new Entry<>(HudTogglePayload.TYPE, HudTogglePayload.STREAM_CODEC),
        new Entry<>(VendingResultPayload.TYPE, VendingResultPayload.STREAM_CODEC),
        new Entry<>(VillagerTradeSyncPayload.TYPE, VillagerTradeSyncPayload.STREAM_CODEC),
        new Entry<>(VillageBoardSyncPayload.TYPE, VillageBoardSyncPayload.STREAM_CODEC),
        new Entry<>(BellInfoPayload.TYPE, BellInfoPayload.STREAM_CODEC),
        new Entry<>(ShopLedgerSyncPayload.TYPE, ShopLedgerSyncPayload.STREAM_CODEC),
        new Entry<>(ShopBeamPayload.TYPE, ShopBeamPayload.STREAM_CODEC),
        new Entry<>(VillageTitlePayload.TYPE, VillageTitlePayload.STREAM_CODEC)
    );

    /** Client → server payloads. */
    public static final List<Entry<?>> SERVERBOUND = List.of(
        new Entry<>(VendingSavePayload.TYPE, VendingSavePayload.STREAM_CODEC),
        new Entry<>(GhostSamplePayload.TYPE, GhostSamplePayload.STREAM_CODEC),
        new Entry<>(VendingQuickTradePayload.TYPE, VendingQuickTradePayload.STREAM_CODEC),
        new Entry<>(BellInfoRequestPayload.TYPE, BellInfoRequestPayload.STREAM_CODEC),
        new Entry<>(CounterSavePayload.TYPE, CounterSavePayload.STREAM_CODEC),
        new Entry<>(VillageDonatePayload.TYPE, VillageDonatePayload.STREAM_CODEC)
    );

    private TraderyPayloads() {
    }
}
