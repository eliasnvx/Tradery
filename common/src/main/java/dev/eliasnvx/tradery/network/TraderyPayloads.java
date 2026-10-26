package dev.eliasnvx.tradery.network;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.network.codec.ByteBufCodecs;
import dev.eliasnvx.tradery.network.codec.StreamCodec;
import dev.eliasnvx.tradery.network.codec.TraderyStreamCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    /** A packet channel with its type and codec, for the loaders to register. */
    public record Entry<T extends TraderyPacket>(ResourceLocation id, Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        public java.util.function.Function<FriendlyByteBuf, T> reader() {
            return codec::decode;
        }
    }

    private static final StreamCodec<FriendlyByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID);
    private static final StreamCodec<FriendlyByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(MAX_TEXT);

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
                                      long copperValue, long silverValue, long goldValue) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("currency_info");
        public static final StreamCodec<FriendlyByteBuf, CurrencyInfoPayload> STREAM_CODEC = StreamCodec.of(
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

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** The player's full balance: on join, respawn and dimension change. */
    public record BalanceSyncPayload(String currencyId, long balance) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("balance_sync");
        public static final StreamCodec<FriendlyByteBuf, BalanceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ID, BalanceSyncPayload::currencyId,
            ByteBufCodecs.VAR_LONG, BalanceSyncPayload::balance,
            BalanceSyncPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** One change of the player's balance: drives the HUD popup. */
    public record BalanceDeltaPayload(long delta, long balance, String reasonType) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("balance_delta");
        public static final StreamCodec<FriendlyByteBuf, BalanceDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BalanceDeltaPayload::delta,
            ByteBufCodecs.VAR_LONG, BalanceDeltaPayload::balance,
            ID, BalanceDeltaPayload::reasonType,
            BalanceDeltaPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * A message for the player that the client may filter by its settings (sales, empty vendor...). The text is
     * a translation key with plain string arguments, translated on the client.
     */
    public record NotificationPayload(NotificationKind kind, String key, List<String> args) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("notification");
        public static final StreamCodec<FriendlyByteBuf, NotificationPayload> STREAM_CODEC = StreamCodec.of(
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

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

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
        public static final ResourceLocation TYPE = Tradery.id("hud_toggle");
        public static final StreamCodec<FriendlyByteBuf, HudTogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** The outcome of a trade or of saving vendor settings, shown inside the open vending screen. */
    public record VendingResultPayload(int containerId, boolean success, Component message) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("vending_result");
        public static final StreamCodec<FriendlyByteBuf, VendingResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VendingResultPayload::containerId,
            ByteBufCodecs.BOOL, VendingResultPayload::success,
            TraderyStreamCodecs.COMPONENT, VendingResultPayload::message,
            VendingResultPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * The owner saves the draft settings of the open vending menu. Only the money price travels here; goods,
     * price item and facade are the server's own copies of the menu's sample slots.
     */
    public record VendingSavePayload(int containerId, long price) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("vending_save");
        public static final StreamCodec<FriendlyByteBuf, VendingSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VendingSavePayload::containerId,
            ByteBufCodecs.VAR_LONG, VendingSavePayload::price,
            VendingSavePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * A recipe viewer (JEI/REI) dropped an item on a sample slot of the open menu. Samples are copies, never real
     * items, so the server only checks the slot is a sample that accepts this item.
     */
    public record GhostSamplePayload(int containerId, int slot, ItemStack stack) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("ghost_sample");
        public static final StreamCodec<FriendlyByteBuf, GhostSamplePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSamplePayload::containerId,
            ByteBufCodecs.VAR_INT, GhostSamplePayload::slot,
            TraderyStreamCodecs.ITEM_STACK, GhostSamplePayload::stack,
            GhostSamplePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * Sneak + use (buy) or sneak + attack (sell) on a vending block: one trade, no window. Sent again while the
     * button is held, at most every 4 ticks; the server checks reach, the block and the trade rate limit.
     */
    public record VendingQuickTradePayload(BlockPos pos, boolean sell) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("vending_quick_trade");
        public static final StreamCodec<FriendlyByteBuf, VendingQuickTradePayload> STREAM_CODEC = StreamCodec.composite(
            TraderyStreamCodecs.BLOCK_POS, VendingQuickTradePayload::pos,
            ByteBufCodecs.BOOL, VendingQuickTradePayload::sell,
            VendingQuickTradePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** Fresh offers, experience and wallet for the open villager trade window, after a trade. */
    public record VillagerTradeSyncPayload(int containerId, VillagerTradeMenu.Data data) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("villager_trade_sync");
        public static final StreamCodec<FriendlyByteBuf, VillagerTradeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillagerTradeSyncPayload::containerId,
            VillagerTradeMenu.Data.STREAM_CODEC, VillagerTradeSyncPayload::data,
            VillagerTradeSyncPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** Fresh data for the open village board window, after an action. */
    public record VillageBoardSyncPayload(int containerId, dev.eliasnvx.tradery.menu.VillageBoardMenu.Data data) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("village_board_sync");
        public static final StreamCodec<FriendlyByteBuf, VillageBoardSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillageBoardSyncPayload::containerId,
            dev.eliasnvx.tradery.menu.VillageBoardMenu.Data.STREAM_CODEC, VillageBoardSyncPayload::data,
            VillageBoardSyncPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * The client looks at a bell and asks about its village, at most every two seconds per bell. The server checks
     * the rate, reach and that a bell is there.
     */
    public record BellInfoRequestPayload(BlockPos pos) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("bell_info_request");
        public static final StreamCodec<FriendlyByteBuf, BellInfoRequestPayload> STREAM_CODEC = StreamCodec.composite(
            TraderyStreamCodecs.BLOCK_POS, BellInfoRequestPayload::pos,
            BellInfoRequestPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * The village of a bell, for the look-at hint; {@code known} false for a bell Tradery knows no village of.
     * {@code nextThreshold} -1 at the top level, {@code marketDayIn} 0 today and -1 without market days.
     */
    public record BellInfoPayload(BlockPos pos, boolean known, Component name, int level, int prosperity, int nextThreshold,
                                  int marketDayIn) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("bell_info");
        public static final StreamCodec<FriendlyByteBuf, BellInfoPayload> STREAM_CODEC = dev.eliasnvx.tradery.network.PortCodecs.composite(
            TraderyStreamCodecs.BLOCK_POS, BellInfoPayload::pos,
            ByteBufCodecs.BOOL, BellInfoPayload::known,
            TraderyStreamCodecs.COMPONENT, BellInfoPayload::name,
            ByteBufCodecs.VAR_INT, BellInfoPayload::level,
            ByteBufCodecs.VAR_INT, BellInfoPayload::prosperity,
            ByteBufCodecs.VAR_INT, BellInfoPayload::nextThreshold,
            ByteBufCodecs.VAR_INT, BellInfoPayload::marketDayIn,
            BellInfoPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * The owner saves the draft of the open market counter menu: one money price per offer (exactly
     * {@code CounterSettings.MAX_OFFERS}). The goods are the server's own copies of the menu's sample slots.
     */
    public record CounterSavePayload(int containerId, List<Long> prices) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("counter_save");
        public static final StreamCodec<FriendlyByteBuf, CounterSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CounterSavePayload::containerId,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(dev.eliasnvx.tradery.vending.CounterSettings.MAX_OFFERS)), CounterSavePayload::prices,
            CounterSavePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /**
     * The player entered a village (or its level changed while there): its name and level for the title on screen;
     * {@code known} false when the player left villages behind; {@code festival} when a festival just started there.
     */
    public record VillageTitlePayload(Component name, int level, boolean known, boolean festival) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("village_title");
        public static final StreamCodec<FriendlyByteBuf, VillageTitlePayload> STREAM_CODEC = StreamCodec.composite(
            TraderyStreamCodecs.COMPONENT, VillageTitlePayload::name,
            ByteBufCodecs.VAR_INT, VillageTitlePayload::level,
            ByteBufCodecs.BOOL, VillageTitlePayload::known,
            ByteBufCodecs.BOOL, VillageTitlePayload::festival,
            VillageTitlePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** "Donate" on the village board: {@code amount} (minor units, positive) from the viewer to the village's treasury. */
    public record VillageDonatePayload(int containerId, long amount) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("village_donate");
        public static final StreamCodec<FriendlyByteBuf, VillageDonatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VillageDonatePayload::containerId,
            ByteBufCodecs.VAR_LONG, VillageDonatePayload::amount,
            VillageDonatePayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** Another page (or a fresh copy after an action) for the open shop ledger window. */
    public record ShopLedgerSyncPayload(int containerId, dev.eliasnvx.tradery.menu.ShopLedgerMenu.Data data) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("shop_ledger_sync");
        public static final StreamCodec<FriendlyByteBuf, ShopLedgerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopLedgerSyncPayload::containerId,
            dev.eliasnvx.tradery.menu.ShopLedgerMenu.Data.STREAM_CODEC, ShopLedgerSyncPayload::data,
            ShopLedgerSyncPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** "Show" in the shop ledger: the client draws a particle beam over this shop for a while (client-side only). */
    public record ShopBeamPayload(net.minecraft.core.GlobalPos pos) implements TraderyPacket {
        public static final ResourceLocation TYPE = Tradery.id("shop_beam");
        public static final StreamCodec<FriendlyByteBuf, ShopBeamPayload> STREAM_CODEC = StreamCodec.composite(
            TraderyStreamCodecs.GLOBAL_POS, ShopBeamPayload::pos,
            ShopBeamPayload::new);

        @Override

        public ResourceLocation id() {

            return TYPE;

        }


        @Override

        public void write(FriendlyByteBuf buf) {

            STREAM_CODEC.encode(buf, this);

        }
    }

    /** Server → client payloads. */
    public static final List<Entry<?>> CLIENTBOUND = List.of(
        new Entry<>(CurrencyInfoPayload.TYPE, CurrencyInfoPayload.class, CurrencyInfoPayload.STREAM_CODEC),
        new Entry<>(BalanceSyncPayload.TYPE, BalanceSyncPayload.class, BalanceSyncPayload.STREAM_CODEC),
        new Entry<>(BalanceDeltaPayload.TYPE, BalanceDeltaPayload.class, BalanceDeltaPayload.STREAM_CODEC),
        new Entry<>(NotificationPayload.TYPE, NotificationPayload.class, NotificationPayload.STREAM_CODEC),
        new Entry<>(HudTogglePayload.TYPE, HudTogglePayload.class, HudTogglePayload.STREAM_CODEC),
        new Entry<>(VendingResultPayload.TYPE, VendingResultPayload.class, VendingResultPayload.STREAM_CODEC),
        new Entry<>(VillagerTradeSyncPayload.TYPE, VillagerTradeSyncPayload.class, VillagerTradeSyncPayload.STREAM_CODEC),
        new Entry<>(VillageBoardSyncPayload.TYPE, VillageBoardSyncPayload.class, VillageBoardSyncPayload.STREAM_CODEC),
        new Entry<>(BellInfoPayload.TYPE, BellInfoPayload.class, BellInfoPayload.STREAM_CODEC),
        new Entry<>(ShopLedgerSyncPayload.TYPE, ShopLedgerSyncPayload.class, ShopLedgerSyncPayload.STREAM_CODEC),
        new Entry<>(ShopBeamPayload.TYPE, ShopBeamPayload.class, ShopBeamPayload.STREAM_CODEC),
        new Entry<>(VillageTitlePayload.TYPE, VillageTitlePayload.class, VillageTitlePayload.STREAM_CODEC)
    );

    /** Client → server payloads. */
    public static final List<Entry<?>> SERVERBOUND = List.of(
        new Entry<>(VendingSavePayload.TYPE, VendingSavePayload.class, VendingSavePayload.STREAM_CODEC),
        new Entry<>(GhostSamplePayload.TYPE, GhostSamplePayload.class, GhostSamplePayload.STREAM_CODEC),
        new Entry<>(VendingQuickTradePayload.TYPE, VendingQuickTradePayload.class, VendingQuickTradePayload.STREAM_CODEC),
        new Entry<>(BellInfoRequestPayload.TYPE, BellInfoRequestPayload.class, BellInfoRequestPayload.STREAM_CODEC),
        new Entry<>(CounterSavePayload.TYPE, CounterSavePayload.class, CounterSavePayload.STREAM_CODEC),
        new Entry<>(VillageDonatePayload.TYPE, VillageDonatePayload.class, VillageDonatePayload.STREAM_CODEC)
    );

    private TraderyPayloads() {
    }
}
