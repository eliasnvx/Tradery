package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageBoard;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillageOrders;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The village board window (SPEC "Доска деревни"): tabs Orders, Village and Market. No item slots, like the vending
 * buyer menu: deliveries take plain stacks from the inventory on the server. Every action is a menu button click
 * (vanilla checks it's this open menu and {@link #stillValid}): {@code action * 1000 + index}, the index into the
 * lists of the data the server sent last, so the client never names an order or project the server didn't show.
 */
public class VillageBoardMenu extends AbstractContainerMenu {
    public static final int MAX_ORDERS = 64;
    public static final int MAX_PROJECTS = 64;
    public static final int MAX_PROFESSIONS = 32;
    public static final int MAX_SHIFTS = 64;
    public static final int MAX_NEWS = 16;
    public static final int MAX_HISTORY = VillageState.HISTORY_DAYS;
    private static final int MAX_ID = 256;

    /** Deliver to the order at the index. */
    public static final int DELIVER = 0;
    /** Fund the project at the index. */
    public static final int FUND = 1;
    /** Rent a trading place (index 0). */
    public static final int RENT = 2;
    private static final int STRIDE = 1000;

    /** Adults of one profession in the village. */
    public record Profession(String id, int count) {
        static final StreamCodec<RegistryFriendlyByteBuf, Profession> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_ID), Profession::id,
            ByteBufCodecs.VAR_INT, Profession::count,
            Profession::new);
    }

    /** What the village saves for: a building's name (empty: nothing), the fund and the cost. */
    public record Saving(Component name, long have, long need) {
        public static final Saving NONE = new Saving(Component.empty(), 0, 0);
        static final StreamCodec<RegistryFriendlyByteBuf, Saving> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, Saving::name,
            ByteBufCodecs.VAR_LONG, Saving::have,
            ByteBufCodecs.VAR_LONG, Saving::need,
            Saving::new);

        public boolean pending() {
            return !name.getString().isEmpty();
        }
    }

    /**
     * The Village tab.
     *
     * @param name          the village's name
     * @param level         1..number of levels
     * @param prosperity    points
     * @param nextThreshold points for the next level, -1 at the top
     * @param reputation    the viewer's average reputation with the village's loaded villagers near the bell
     * @param history       the treasury's last days with money moving, oldest first ({@link VillageState#history()})
     * @param saving        the level building the village saves for
     */
    public record Info(Component name, int level, double prosperity, double nextThreshold, int population, int employed,
                       List<Profession> professions, long treasury, long dailyIncome, int reputation, List<VillageState.DayFlow> history,
                       Saving saving) {
        static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, Info::name,
            ByteBufCodecs.VAR_INT, Info::level,
            ByteBufCodecs.DOUBLE, Info::prosperity,
            ByteBufCodecs.DOUBLE, Info::nextThreshold,
            ByteBufCodecs.VAR_INT, Info::population,
            ByteBufCodecs.VAR_INT, Info::employed,
            Profession.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PROFESSIONS)), Info::professions,
            ByteBufCodecs.VAR_LONG, Info::treasury,
            ByteBufCodecs.VAR_LONG, Info::dailyIncome,
            ByteBufCodecs.VAR_INT, Info::reputation,
            VillageState.DayFlow.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_HISTORY)), Info::history,
            Saving.STREAM_CODEC, Info::saving,
            Info::new);
    }

    /** An order on the board; {@code item} is one of the wanted item. */
    public record OrderView(int id, ItemStack item, int count, int delivered, long rewardPerItem, long expiresDay, boolean restoration) {
        static final StreamCodec<RegistryFriendlyByteBuf, OrderView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrderView::id,
            ItemStack.OPTIONAL_STREAM_CODEC, OrderView::item,
            ByteBufCodecs.VAR_INT, OrderView::count,
            ByteBufCodecs.VAR_INT, OrderView::delivered,
            ByteBufCodecs.VAR_LONG, OrderView::rewardPerItem,
            ByteBufCodecs.VAR_LONG, OrderView::expiresDay,
            ByteBufCodecs.BOOL, OrderView::restoration,
            OrderView::new);

        public int remaining() {
            return Math.max(0, count - delivered);
        }
    }

    /** A project players can fund here; {@code cost} in minor units, {@code level} the village level it needs. */
    public record ProjectView(Identifier id, ItemStack icon, long cost, int level, boolean funded) {
        static final StreamCodec<RegistryFriendlyByteBuf, ProjectView> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ProjectView::id,
            ItemStack.OPTIONAL_STREAM_CODEC, ProjectView::icon,
            ByteBufCodecs.VAR_LONG, ProjectView::cost,
            ByteBufCodecs.VAR_INT, ProjectView::level,
            ByteBufCodecs.BOOL, ProjectView::funded,
            ProjectView::new);
    }

    /** Something this village sells cheap ({@code factor < 1}) or pays dear for. */
    public record ShiftView(ItemStack icon, double factor) {
        static final StreamCodec<RegistryFriendlyByteBuf, ShiftView> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, ShiftView::icon,
            ByteBufCodecs.DOUBLE, ShiftView::factor,
            ShiftView::new);
    }

    /**
     * The Market tab.
     *
     * @param nextMarketDay day number of the next market day, -1 when there are none
     * @param rentPerDay    price of a trading place per day (minor units), 0 = not available
     * @param rentDays      days one rent buys
     * @param licenceUntil  first day the viewer's licence no longer holds, -1 without one
     * @param caravans      news of caravans, one line each
     */
    public record Market(List<ShiftView> shifts, long nextMarketDay, boolean marketDay, long rentPerDay, int rentDays, long licenceUntil,
                         List<Component> caravans) {
        static final StreamCodec<RegistryFriendlyByteBuf, Market> STREAM_CODEC = StreamCodec.composite(
            ShiftView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SHIFTS)), Market::shifts,
            ByteBufCodecs.VAR_LONG, Market::nextMarketDay,
            ByteBufCodecs.BOOL, Market::marketDay,
            ByteBufCodecs.VAR_LONG, Market::rentPerDay,
            ByteBufCodecs.VAR_INT, Market::rentDays,
            ByteBufCodecs.VAR_LONG, Market::licenceUntil,
            ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NEWS)), Market::caravans,
            Market::new);
    }

    /**
     * What the window shows; sent when it opens and after every action ({@link TraderyPayloads.VillageBoardSyncPayload}).
     *
     * @param board the board block
     * @param today the in-game day number, for "expires in N days"
     */
    public record Data(BlockPos board, long today, Info info, List<OrderView> orders, List<ProjectView> projects, Market market) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Data::board,
            ByteBufCodecs.VAR_LONG, Data::today,
            Info.STREAM_CODEC, Data::info,
            OrderView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ORDERS)), Data::orders,
            ProjectView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PROJECTS)), Data::projects,
            Market.STREAM_CODEC, Data::market,
            Data::new);
    }

    private final @Nullable ServerLevel level;
    private final String villageKey;
    private final Player viewer;
    private Data data;

    /** Client side, from the opening data. */
    public VillageBoardMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null, "");
    }

    /** Server side, on the board at {@code data.board()} of the village {@code villageKey}. */
    public VillageBoardMenu(int containerId, Inventory inventory, Data data, @Nullable ServerLevel level, String villageKey) {
        super(TraderyMenus.VILLAGE_BOARD.get(), containerId);
        this.level = level;
        this.villageKey = villageKey;
        this.viewer = inventory.player;
        this.data = data;
    }

    public Data data() {
        return data;
    }

    /** Fresh data from the server (client side). */
    public void update(Data data) {
        this.data = data;
    }

    public static int buttonId(int action, int index) {
        return action * STRIDE + index;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || level == null || buttonId < 0 || !stillValid(player)) {
            return false;
        }
        int action = buttonId / STRIDE;
        int index = buttonId % STRIDE;
        boolean known = switch (action) {
            case DELIVER -> index < data.orders().size();
            case FUND -> index < data.projects().size();
            case RENT -> index == 0;
            default -> false;
        };
        if (!known) {
            return false;
        }
        if (!VendingTrades.allow(serverPlayer)) {
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        if (action == FUND && !VillageBoard.allowFund(serverPlayer)) {
            // Funding a stall searches the ground around the bell: not ten times a second
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        VillageState village = Villages.byKey(level.getServer(), villageKey);
        VillageBoard.Outcome outcome;
        if (village == null) {
            outcome = VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_village", "There's no village here"));
        } else {
            outcome = switch (action) {
                case DELIVER -> VillageOrders.deliver(serverPlayer, village, data.orders().get(index).id());
                case FUND -> VillageFunding.fund(serverPlayer, village, data.projects().get(index).id());
                default -> VillageBoard.rent(serverPlayer, village);
            };
            data = VillageBoard.snapshot(serverPlayer, village, data.board());
            Platform.get().sendToPlayer(serverPlayer, new TraderyPayloads.VillageBoardSyncPayload(containerId, data));
        }
        reply(serverPlayer, outcome.success(), outcome.message());
        return true;
    }

    /**
     * A donation to the treasury ({@link TraderyPayloads.VillageDonatePayload}): the same checks as a button click
     * (open and valid menu, the rate limit), then the money moves and the window gets fresh data.
     */
    public void donate(ServerPlayer player, long amount) {
        if (level == null || !stillValid(player)) {
            return;
        }
        if (!VendingTrades.allow(player)) {
            reply(player, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return;
        }
        VillageState village = Villages.byKey(level.getServer(), villageKey);
        VillageBoard.Outcome outcome;
        if (village == null) {
            outcome = VillageBoard.Outcome.fail(Messages.tr("tradery.board.no_village", "There's no village here"));
        } else {
            outcome = VillageFunding.donate(player, village, amount);
            data = VillageBoard.snapshot(player, village, data.board());
            Platform.get().sendToPlayer(player, new TraderyPayloads.VillageBoardSyncPayload(containerId, data));
        }
        reply(player, outcome.success(), outcome.message());
    }

    private void reply(ServerPlayer player, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(containerId, success, message));
    }

    /** No slots here: deliveries change the inventory, which vanilla syncs only through the inventory menu. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (level != null) {
            viewer.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    /** The board is still there and within reach. */
    @Override
    public boolean stillValid(Player player) {
        return level == null || stillValid(ContainerLevelAccess.create(level, data.board()), player, TraderyBlocks.VILLAGE_BOARD.get());
    }
}
