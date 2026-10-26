package dev.eliasnvx.tradery.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.VillageBoardMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyItems;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import dev.eliasnvx.tradery.village.VillagerCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * The village board (SPEC "Доска деревни"): tabs Orders (deliver plain stacks for money from the treasury), Village
 * (level, prosperity, people, treasury with its last week, the viewer's reputation, projects to fund and a donation
 * to the treasury) and Market (cheap and dear goods, market days, trading places, caravans). The server does
 * everything; this only shows what it sent and asks.
 */
public class VillageBoardScreen extends AbstractContainerScreen<VillageBoardMenu> implements VendingResultView {
    private static final int TAB_ORDERS = 0;
    private static final int TAB_VILLAGE = 1;
    private static final int TAB_MARKET = 2;
    private static final String[] TAB_KEYS = {"tradery.board.tab.orders", "tradery.board.tab.village", "tradery.board.tab.market"};

    private static final int PAD = 8;
    private static final int INNER_W = 284;
    private static final int TAB_Y = 18;
    private static final int TAB_H = 14;
    private static final int TOP = 37;
    private static final int LINE = 10;

    // Orders tab: list on the left, the chosen order on the right
    private static final int LIST_X = PAD;
    private static final int LIST_W = 174;
    private static final int ROW_H = 22;
    private static final int ROWS = 6;
    private static final int LIST_SCROLL_X = LIST_X + LIST_W + 2;
    private static final int SIDE_X = 198;
    private static final int SIDE_W = 92;

    // Village tab: facts on the left, projects on the right
    private static final int LEFT_W = 142;
    private static final int RIGHT_X = 156;
    private static final int RIGHT_W = 136;
    private static final int PROJECT_Y = TOP + 12;
    private static final int PROJECT_ROW_H = 20;
    private static final int PROJECT_ROWS = 4;
    private static final int PROJECT_W = RIGHT_W - 9;
    private static final int PROJECT_SCROLL_X = RIGHT_X + PROJECT_W + 2;
    private static final int PROFESSION_LINES = 3;
    /** The first row of the projects list is the donation to the treasury; project {@code i} is row {@code i + 1}. */
    private static final int DONATION_ROW = 0;
    private static final int DONATE_Y = 147;
    private static final int DONATE_H = 14;
    /** The treasury's last week: a pair of bars (in, out) per day under the income line. */
    private static final int CHART_H = 14;
    private static final int CHART_SLOT = 20;
    private static final int CHART_BAR = 9;
    private static final int CHART_DAYS = VillageState.HISTORY_DAYS;

    // Market tab
    private static final int ICONS_PER_ROW = 7;
    private static final int ICON_ROWS = 2;

    private static final int BUTTON_Y = 164;
    private static final int MESSAGE_Y = 194;

    private static final int GOLD = 0xFFE0A800;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int MUTED = 0xFF707070;
    private static final int HEADING = 0xFF5A3E12;
    private static final int RESTORATION = 0xFF9A5A00;
    private static final int ROW = 0xFF9A9A9A;
    private static final int ROW_HOVER = 0xFFAAAAAA;
    private static final int ROW_SELECTED = 0xFFC2C2C2;
    private static final int ROW_RESTORATION = 0xFFB09A7A;
    private static final int TAB_IDLE = 0xFF8B8B8B;
    private static final int BAR_BACK = 0xFF555555;
    private static final int BAR_FILL = 0xFF5BA829;
    private static final int THUMB = 0xFFC6C6C6;
    private static final int FLOW_IN = 0xFF5BA829;
    private static final int FLOW_OUT = 0xFFB04040;

    private int tab = TAB_ORDERS;
    private int selectedOrder;
    private int orderScroll;
    private int selectedProject;
    private int projectScroll;
    private VillageBoardMenu.Data shownData;
    private List<Component> professionNames = List.of();
    /** Where the Village tab drew the professions, the treasury chart and the reputation (for their tooltips). */
    private int professionsY;
    private int chartY;
    private int reputationY;
    private final ItemStack donationIcon = new ItemStack(TraderyItems.coin(CoinTier.GOLD));
    /** The amount typed for a donation, in minor units; -1 when the box holds nothing usable. */
    private long donateAmount = -1;
    private String donateText = "";
    /** What the buttons' tooltips were made for, so they aren't rebuilt every frame. */
    private Object deliverTipFor;
    private long deliverTipCount = -1;

    private Button deliverButton;
    private Button fundButton;
    private Button rentButton;
    private EditBox donateBox;

    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public VillageBoardScreen(VillageBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 208;
    }

    /** A fresh snapshot from the server for the open window. */
    public static void onSync(TraderyPayloads.VillageBoardSyncPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof VillageBoardMenu menu && menu.containerId == payload.containerId()) {
            menu.update(payload.data());
        }
    }

    @Override
    protected void init() {
        super.init();
        deliverButton = addRenderableWidget(Button.builder(Component.translatable("tradery.board.deliver"),
                b -> click(VillageBoardMenu.DELIVER, selectedOrder))
            .bounds(leftPos + SIDE_X - 1, topPos + 148, SIDE_W + 2, 20).build());
        fundButton = addRenderableWidget(Button.builder(Component.translatable("tradery.board.fund_button"), b -> fund())
            .bounds(leftPos + RIGHT_X, topPos + BUTTON_Y, RIGHT_W, 20).build());
        donateBox = new EditBox(font, leftPos + RIGHT_X, topPos + DONATE_Y, RIGHT_W, DONATE_H, Component.translatable("tradery.board.donate_amount"));
        donateBox.setMaxLength(20);
        donateBox.setHint(Component.translatable("tradery.board.donate_amount"));
        donateBox.setValue(donateText);
        donateBox.setResponder(this::onDonateTyped);
        addRenderableWidget(donateBox);
        onDonateTyped(donateText);
        rentButton = addRenderableWidget(Button.builder(Component.translatable("tradery.board.rent_button"),
                b -> click(VillageBoardMenu.RENT, 0))
            .bounds(leftPos + RIGHT_X, topPos + TOP + 66, RIGHT_W, 20).build());
        refresh();
        updateButtons();
    }

    private void click(int action, int index) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, VillageBoardMenu.buttonId(action, index));
    }

    /** The button under the projects: funds the chosen project, or sends the typed donation. */
    private void fund() {
        if (selectedProject == DONATION_ROW) {
            if (donateAmount > 0) {
                Platform.get().sendToServer(new TraderyPayloads.VillageDonatePayload(menu.containerId, donateAmount));
            }
        } else {
            click(VillageBoardMenu.FUND, selectedProject - 1);
        }
    }

    private void onDonateTyped(String text) {
        donateText = text;
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        OptionalLong parsed = Money.parse(text.trim(), currency != null ? currency.decimals() : 2);
        donateAmount = parsed.isPresent() && parsed.getAsLong() > 0 ? parsed.getAsLong() : -1;
    }

    /** Rows of the projects list: the donation, then the projects. */
    private static int projectRows(VillageBoardMenu.Data data) {
        return data.projects().size() + 1;
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    private VillageBoardMenu.Data data() {
        return menu.data();
    }

    /** Re-reads the data when the server sent new data: selections stay in range, buttons get their labels. */
    private void refresh() {
        VillageBoardMenu.Data data = data();
        if (data == shownData) {
            return;
        }
        shownData = data;
        selectedOrder = Mth.clamp(selectedOrder, 0, Math.max(0, data.orders().size() - 1));
        orderScroll = Mth.clamp(orderScroll, 0, Math.max(0, data.orders().size() - ROWS));
        selectedProject = Mth.clamp(selectedProject, 0, projectRows(data) - 1);
        projectScroll = Mth.clamp(projectScroll, 0, Math.max(0, projectRows(data) - PROJECT_ROWS));
        List<Component> names = new ArrayList<>();
        for (VillageBoardMenu.Profession profession : data.info().professions()) {
            names.add(Component.translatable("tradery.board.profession", professionName(profession.id()), profession.count()));
        }
        professionNames = names;

        VillageBoardMenu.Market market = data.market();
        rentButton.setMessage(market.rentPerDay() > 0
            ? Component.translatable("tradery.board.rent_for", money(rentTotal(market)), market.rentDays())
            : Component.translatable("tradery.board.rent_button"));
        rentButton.setTooltip(Tooltip.create(Component.translatable("tradery.board.rent_tip")));
    }

    private static long rentTotal(VillageBoardMenu.Market market) {
        try {
            return Math.multiplyExact(market.rentPerDay(), (long) market.rentDays());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static Component professionName(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null) {
            var holder = BuiltInRegistries.VILLAGER_PROFESSION.getHolder(parsed);
            if (holder.isPresent()) {
                return VillagerCompat.name(holder.get());
            }
        }
        return Component.literal(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        refresh();
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        int tabW = (INNER_W - 4) / 3;
        for (int i = 0; i < 3; i++) {
            int x = leftPos + PAD + i * (tabW + 2);
            if (i == tab) {
                graphics.fill(x, topPos + TAB_Y, x + tabW, topPos + TAB_Y + TAB_H, 0xFFFFFFFF);
                graphics.fill(x + 1, topPos + TAB_Y + 1, x + tabW, topPos + TAB_Y + TAB_H, 0xFF555555);
                graphics.fill(x + 1, topPos + TAB_Y + 1, x + tabW - 1, topPos + TAB_Y + TAB_H - 1, Panels.BODY);
            } else {
                Panels.inset(graphics, x, topPos + TAB_Y, tabW, TAB_H);
            }
        }
        switch (tab) {
            case TAB_ORDERS -> {
                Panels.inset(graphics, leftPos + LIST_X - 1, topPos + TOP - 1, LIST_W + 2, ROWS * ROW_H + 1);
                Panels.inset(graphics, leftPos + LIST_SCROLL_X - 1, topPos + TOP - 1, 8, ROWS * ROW_H + 1);
                Panels.inset(graphics, leftPos + SIDE_X - 2, topPos + TOP - 1, SIDE_W + 4, ROWS * ROW_H + 1);
            }
            case TAB_VILLAGE -> {
                Panels.inset(graphics, leftPos + RIGHT_X - 1, topPos + PROJECT_Y - 1, PROJECT_W + 2, PROJECT_ROWS * PROJECT_ROW_H + 1);
                Panels.inset(graphics, leftPos + PROJECT_SCROLL_X - 1, topPos + PROJECT_Y - 1, 8, PROJECT_ROWS * PROJECT_ROW_H + 1);
            }
            default -> {
                List<VillageBoardMenu.ShiftView> shifts = data().market().shifts();
                int cheap = 0;
                int dear = 0;
                for (VillageBoardMenu.ShiftView shift : shifts) {
                    int index = shift.factor() < 1 ? cheap++ : dear++;
                    int[] at = shiftSlot(shift.factor() < 1, index);
                    if (at != null) {
                        Panels.slot(graphics, leftPos + at[0], topPos + at[1]);
                    }
                }
            }
        }
    }

    /** The buttons follow the inventory (what can be delivered) on the tick, not on every frame. */
    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    /** Plain items of the chosen order's kind in the inventory, counted by {@link #updateButtons} on the tick. */
    private long selectedOwned;

    private void updateButtons() {
        VillageBoardMenu.Data data = data();
        deliverButton.visible = tab == TAB_ORDERS;
        fundButton.visible = tab == TAB_VILLAGE;
        rentButton.visible = tab == TAB_MARKET;

        VillageBoardMenu.OrderView order = selectedOrder < data.orders().size() ? data.orders().get(selectedOrder) : null;
        selectedOwned = order == null ? 0 : owned(order.item().getItem());
        long deliverable = order == null ? 0 : Math.min(order.remaining(), selectedOwned);
        deliverButton.active = deliverable > 0;
        if (order != deliverTipFor || deliverable != deliverTipCount) {
            deliverTipFor = order;
            deliverTipCount = deliverable;
            deliverButton.setTooltip(order == null ? null : Tooltip.create(deliverable > 0
                ? Component.translatable("tradery.board.deliver_tip", deliverable, order.item().getHoverName(),
                    money(deliverable * order.rewardPerItem()))
                : Component.translatable("tradery.board.deliver_none", order.item().getHoverName())));
        }

        boolean donation = tab == TAB_VILLAGE && selectedProject == DONATION_ROW;
        donateBox.visible = donation;
        if (!donation && donateBox.isFocused()) {
            donateBox.setFocused(false);
        }
        VillageBoardMenu.ProjectView project = selectedProject > DONATION_ROW && selectedProject - 1 < data.projects().size()
            ? data.projects().get(selectedProject - 1) : null;
        if (donation) {
            fundButton.active = donateAmount > 0 && ClientEconomy.balance() >= donateAmount;
            fundButton.setMessage(donateAmount > 0
                ? Panels.fit(font, Component.translatable("tradery.board.donate_for", money(donateAmount)), RIGHT_W - 8)
                : Component.translatable("tradery.board.donate_button"));
        } else if (project == null) {
            fundButton.active = false;
            fundButton.setMessage(Component.translatable("tradery.board.fund_button"));
        } else if (project.funded()) {
            fundButton.active = false;
            fundButton.setMessage(Component.translatable("tradery.board.project_built"));
        } else if (data.info().level() < project.level()) {
            fundButton.active = false;
            fundButton.setMessage(Panels.fit(font, Component.translatable("tradery.board.project_needs", Villages.levelName(project.level())),
                RIGHT_W - 8));
        } else {
            fundButton.active = ClientEconomy.balance() >= project.cost();
            fundButton.setMessage(Panels.fit(font, Component.translatable("tradery.board.fund_for", money(project.cost())), RIGHT_W - 8));
        }
        rentButton.active = data.market().rentPerDay() > 0;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        VillageBoardMenu.Data data = data();
        VillageBoardMenu.Info info = data.info();
        Component level = Villages.levelName(info.level());
        int levelWidth = font.width(level);
        graphics.drawString(font, Panels.fit(font, info.name(), INNER_W - levelWidth - 8), PAD, 6, Panels.LABEL, false);
        graphics.drawString(font, level, PAD + INNER_W - levelWidth, 6, HEADING, false);

        int tabW = (INNER_W - 4) / 3;
        for (int i = 0; i < 3; i++) {
            Component label = Panels.fit(font, Component.translatable(TAB_KEYS[i]), tabW - 6);
            int x = PAD + i * (tabW + 2) + (tabW - font.width(label)) / 2;
            graphics.drawString(font, label, x, TAB_Y + 3, i == tab ? Panels.LABEL : 0xFF3A3A3A, false);
        }

        switch (tab) {
            case TAB_ORDERS -> orders(graphics, data, mouseX, mouseY);
            case TAB_VILLAGE -> village(graphics, data, mouseX, mouseY);
            default -> market(graphics, data);
        }

        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            graphics.drawString(font, Panels.fit(font, resultMessage, INNER_W), PAD, MESSAGE_Y, resultSuccess ? OK : ERROR, false);
        }
    }

    // ------------------------------------------------------------------ Orders tab

    private void orders(GuiGraphics graphics, VillageBoardMenu.Data data, int mouseX, int mouseY) {
        List<VillageBoardMenu.OrderView> orders = data.orders();
        if (orders.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("tradery.board.no_orders"), LIST_W - 8);
            int y = TOP + 4;
            for (FormattedCharSequence line : lines.subList(0, Math.min(6, lines.size()))) {
                graphics.drawString(font, line, LIST_X + 4, y, Panels.LABEL, false);
                y += LINE;
            }
        }
        int hovered = orderAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && orderScroll + i < orders.size(); i++) {
            int index = orderScroll + i;
            VillageBoardMenu.OrderView order = orders.get(index);
            int y = TOP + i * ROW_H;
            int color = index == selectedOrder ? ROW_SELECTED : index == hovered ? ROW_HOVER : order.restoration() ? ROW_RESTORATION : ROW;
            graphics.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H - 1, color);
            graphics.renderItem(order.item(), LIST_X + 3, y + 3);
            graphics.drawString(font, Panels.fit(font, order.item().getHoverName(), LIST_W - 26), LIST_X + 23, y + 2, Panels.LABEL, false);
            Component line = Component.translatable("tradery.board.order_row", order.delivered(), order.count(), money(order.rewardPerItem()));
            graphics.drawString(font, Panels.fit(font, line, LIST_W - 26), LIST_X + 23, y + 12, order.restoration() ? RESTORATION : MUTED, false);
        }
        scrollbar(graphics, LIST_SCROLL_X, TOP, ROWS * ROW_H, orders.size(), ROWS, orderScroll);

        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (currency != null) {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.balance", currency.money(ClientEconomy.balance())), LIST_W),
                PAD, TOP + ROWS * ROW_H + 5, Panels.LABEL, false);
        }

        if (selectedOrder >= orders.size()) {
            return;
        }
        VillageBoardMenu.OrderView order = orders.get(selectedOrder);
        int x = SIDE_X;
        int y = TOP + 2;
        graphics.renderItem(order.item(), x, y);
        List<FormattedCharSequence> name = font.split(order.item().getHoverName(), SIDE_W - 20);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            graphics.drawString(font, name.get(i), x + 19, y + (name.size() == 1 ? 4 : i * 9), Panels.LABEL, false);
        }
        y += 22;
        if (order.restoration()) {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.restoration"), SIDE_W), x, y, RESTORATION, false);
            y += LINE;
        }
        y = wrapped(graphics, Component.translatable("tradery.board.progress", order.delivered(), order.count()), x, y, SIDE_W, Panels.LABEL, 2);
        y = wrapped(graphics, Component.translatable("tradery.board.each", money(order.rewardPerItem())), x, y, SIDE_W, Panels.LABEL, 2);
        y = wrapped(graphics, Component.translatable("tradery.board.you_have", selectedOwned), x, y, SIDE_W, Panels.LABEL, 1);
        long daysLeft = order.expiresDay() - data.today();
        Component expiry = daysLeft <= 1 ? Component.translatable("tradery.board.last_day")
            : Component.translatable("tradery.board.days_left", daysLeft);
        wrapped(graphics, expiry, x, y, SIDE_W, daysLeft <= 1 ? ERROR : MUTED, 1);
    }

    /**
     * Text wrapped to {@code width}, at most {@code max} lines; when it doesn't fit, the last line ends with "…" (the
     * full text is in a tooltip where it matters). Returns the next line's y.
     */
    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int max) {
        List<FormattedCharSequence> lines = font.split(text, width);
        if (lines.size() <= max) {
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, x, y, color, false);
                y += LINE;
            }
            return y;
        }
        List<FormattedText> parts = font.getSplitter().splitLines(text, width, Style.EMPTY);
        for (int i = 0; i < max - 1; i++) {
            graphics.drawString(font, lines.get(i), x, y, color, false);
            y += LINE;
        }
        StringBuilder rest = new StringBuilder();
        for (int i = max - 1; i < parts.size(); i++) {
            rest.append(i > max - 1 ? " " : "").append(parts.get(i).getString().strip());
        }
        graphics.drawString(font, Panels.fit(font, Component.literal(rest.toString() + " …"), width), x, y, color, false);
        return y + LINE;
    }

    // ------------------------------------------------------------------ Village tab

    private void village(GuiGraphics graphics, VillageBoardMenu.Data data, int mouseX, int mouseY) {
        VillageBoardMenu.Info info = data.info();
        int x = PAD;
        int y = TOP + 1;
        graphics.drawString(font, Panels.fit(font, Villages.levelName(info.level()).copy().withStyle(ChatFormatting.BOLD), LEFT_W), x, y, HEADING, false);
        y += 11;
        graphics.fill(x, y, x + LEFT_W, y + 5, BAR_BACK);
        if (info.nextThreshold() > 0) {
            double progress = Mth.clamp(info.prosperity() / info.nextThreshold(), 0, 1);
            graphics.fill(x + 1, y + 1, x + 1 + (int) Math.round((LEFT_W - 2) * progress), y + 4, BAR_FILL);
        } else {
            graphics.fill(x + 1, y + 1, x + LEFT_W - 1, y + 4, BAR_FILL);
        }
        y += 8;
        Component prosperity = info.nextThreshold() > 0
            ? Component.translatable("tradery.bell.prosperity_next", (long) Math.floor(info.prosperity()), (long) Math.floor(info.nextThreshold()))
            : Component.translatable("tradery.bell.prosperity", (long) Math.floor(info.prosperity()));
        graphics.drawString(font, Panels.fit(font, prosperity, LEFT_W), x, y, MUTED, false);
        y += LINE + 4;

        y = wrapped(graphics, Component.translatable("tradery.board.population", info.population(), info.employed()), x, y, LEFT_W, Panels.LABEL, 2);
        professionsY = y;
        int shown = Math.min(PROFESSION_LINES, professionNames.size());
        for (int i = 0; i < shown; i++) {
            Component line = i == shown - 1 && professionNames.size() > PROFESSION_LINES
                ? Component.translatable("tradery.board.more_professions", professionNames.size() - PROFESSION_LINES + 1)
                : professionNames.get(i);
            graphics.drawString(font, Panels.fit(font, line, LEFT_W - 6), x + 6, y, MUTED, false);
            y += LINE;
        }
        y += 4;
        y = wrapped(graphics, Component.translatable("tradery.board.treasury", money(info.treasury())), x, y, LEFT_W, Panels.LABEL, 1);
        y = wrapped(graphics, Component.translatable("tradery.board.income", money(info.dailyIncome())), x, y, LEFT_W, MUTED, 1);
        y += 2;
        chartY = y;
        chart(graphics, info.history(), data.today(), x, y);
        y += CHART_H + 4;
        if (info.saving().pending()) {
            y = wrapped(graphics, Component.translatable("tradery.board.saving_for", info.saving().name()), x, y, LEFT_W, GOLD, 1);
            y = wrapped(graphics, Component.literal(number(info.saving().have()) + " / " + number(info.saving().need())), x + 6, y, LEFT_W - 6, MUTED, 1);
        }
        reputationY = y;
        int reputation = info.reputation();
        MutableComponent value = Component.literal(reputation > 0 ? "+" + reputation : String.valueOf(reputation))
            .withStyle(reputation > 0 ? ChatFormatting.DARK_GREEN : reputation < 0 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY);
        wrapped(graphics, Component.translatable("tradery.board.reputation", value), x, y, LEFT_W, Panels.LABEL, 1);

        // Projects
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.projects"), RIGHT_W), RIGHT_X, TOP + 1, HEADING, false);
        List<VillageBoardMenu.ProjectView> projects = data.projects();
        int rows = projectRows(data);
        int hovered = projectAt(mouseX, mouseY);
        for (int i = 0; i < PROJECT_ROWS && projectScroll + i < rows; i++) {
            int index = projectScroll + i;
            int rowY = PROJECT_Y + i * PROJECT_ROW_H;
            graphics.fill(RIGHT_X, rowY, RIGHT_X + PROJECT_W, rowY + PROJECT_ROW_H - 1,
                index == selectedProject ? ROW_SELECTED : index == hovered ? ROW_HOVER : ROW);
            if (index == DONATION_ROW) {
                graphics.renderItem(donationIcon, RIGHT_X + 2, rowY + 2);
                graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.donation"), PROJECT_W - 22), RIGHT_X + 21, rowY + 1,
                    Panels.LABEL, false);
                graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.donation_hint"), PROJECT_W - 22), RIGHT_X + 21, rowY + 10,
                    GOLD, true);
                continue;
            }
            VillageBoardMenu.ProjectView project = projects.get(index - 1);
            graphics.renderItem(project.icon(), RIGHT_X + 2, rowY + 2);
            graphics.drawString(font, Panels.fit(font, VillageFunding.name(project.id()), PROJECT_W - 22), RIGHT_X + 21, rowY + 1, Panels.LABEL, false);
            Component status;
            int color;
            if (project.funded()) {
                status = Component.translatable("tradery.board.project_built");
                color = OK;
            } else if (info.level() < project.level()) {
                status = Component.translatable("tradery.board.project_needs", Villages.levelName(project.level()));
                color = ERROR;
            } else {
                status = money(project.cost());
                color = GOLD;
            }
            graphics.drawString(font, Panels.fit(font, status, PROJECT_W - 22), RIGHT_X + 21, rowY + 10, color, color == GOLD);
        }
        scrollbar(graphics, PROJECT_SCROLL_X, PROJECT_Y, PROJECT_ROWS * PROJECT_ROW_H, rows, PROJECT_ROWS, projectScroll);
        int descriptionY = PROJECT_Y + PROJECT_ROWS * PROJECT_ROW_H + 3;
        if (selectedProject == DONATION_ROW) {
            wrapped(graphics, Component.translatable("tradery.board.donation.desc"), RIGHT_X, descriptionY, RIGHT_W, MUTED, 1);
        } else if (selectedProject - 1 < projects.size()) {
            ResourceLocation id = projects.get(selectedProject - 1).id();
            Component description = Component.translatableWithFallback("tradery.project." + id.getNamespace() + "." + id.getPath() + ".desc", "");
            wrapped(graphics, description, RIGHT_X, descriptionY, RIGHT_W, MUTED, 3);
        }
    }

    // ------------------------------------------------------------------ treasury chart

    /** The flow of day {@code day} in the history the server sent, or null. */
    @Nullable
    private static VillageState.DayFlow flowOf(List<VillageState.DayFlow> history, long day) {
        for (int i = 0; i < history.size(); i++) {
            VillageState.DayFlow flow = history.get(i);
            if (flow.day() == day) {
                return flow;
            }
        }
        return null;
    }

    /**
     * Seven days ending today, a green bar (money in) and a red bar (money out) each, scaled to the week's largest
     * movement, on a baseline. Nothing allocated per frame.
     */
    private void chart(GuiGraphics graphics, List<VillageState.DayFlow> history, long today, int x, int y) {
        long max = 0;
        for (int i = 0; i < CHART_DAYS; i++) {
            VillageState.DayFlow flow = flowOf(history, today - (CHART_DAYS - 1 - i));
            if (flow != null) {
                max = Math.max(max, Math.max(flow.in(), flow.out()));
            }
        }
        int baseline = y + CHART_H;
        graphics.fill(x, baseline, x + CHART_DAYS * CHART_SLOT - 1, baseline + 1, BAR_BACK);
        for (int i = 0; i < CHART_DAYS; i++) {
            VillageState.DayFlow flow = flowOf(history, today - (CHART_DAYS - 1 - i));
            int slotX = x + i * CHART_SLOT;
            if (flow == null || max == 0) {
                continue;
            }
            int in = barHeight(flow.in(), max);
            int out = barHeight(flow.out(), max);
            if (in > 0) {
                graphics.fill(slotX, baseline - in, slotX + CHART_BAR, baseline, FLOW_IN);
            }
            if (out > 0) {
                graphics.fill(slotX + CHART_BAR + 1, baseline - out, slotX + 2 * CHART_BAR + 1, baseline, FLOW_OUT);
            }
        }
    }

    /** Pixels for {@code value} against the week's {@code max}: at least one for anything above zero. */
    private static int barHeight(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(CHART_H * (value / (double) max)));
    }

    /** The chart's tooltip: each day with money moving, and the week's totals. */
    private List<Component> chartTooltip(List<VillageState.DayFlow> history, long today) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tradery.board.history"));
        long totalIn = 0;
        long totalOut = 0;
        int shown = 0;
        for (int i = 0; i < CHART_DAYS; i++) {
            int ago = CHART_DAYS - 1 - i;
            VillageState.DayFlow flow = flowOf(history, today - ago);
            if (flow == null) {
                continue;
            }
            totalIn += flow.in();
            totalOut += flow.out();
            shown++;
            Component day = ago == 0 ? Component.translatable("tradery.board.day.today")
                : ago == 1 ? Component.translatable("tradery.board.day.yesterday")
                : Component.translatable("tradery.board.day.ago", ago);
            lines.add(Component.translatable("tradery.board.history_day", day, flowText(flow.in(), true), flowText(flow.out(), false))
                .withStyle(ChatFormatting.GRAY));
        }
        if (shown == 0) {
            lines.add(Component.translatable("tradery.board.history_none").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tradery.board.history_week", flowText(totalIn, true), flowText(totalOut, false)));
        }
        return lines;
    }

    private Component flowText(long amount, boolean in) {
        return Component.literal(in ? "+" : "-").append(money(amount)).withStyle(in ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ Market tab

    /** Where the {@code index}-th cheap or dear icon goes (relative), or null when there's no room for it. */
    private static int[] shiftSlot(boolean cheap, int index) {
        if (index >= ICONS_PER_ROW * ICON_ROWS) {
            return null;
        }
        int y = (cheap ? TOP + 11 : TOP + 59) + (index / ICONS_PER_ROW) * 18;
        return new int[] {PAD + 1 + (index % ICONS_PER_ROW) * 18, y};
    }

    private void market(GuiGraphics graphics, VillageBoardMenu.Data data) {
        VillageBoardMenu.Market market = data.market();
        int cheap = 0;
        int dear = 0;
        for (VillageBoardMenu.ShiftView shift : market.shifts()) {
            int index = shift.factor() < 1 ? cheap++ : dear++;
            int[] at = shiftSlot(shift.factor() < 1, index);
            if (at != null) {
                graphics.renderItem(shift.icon(), at[0], at[1]);
            }
        }
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.cheap_here"), LEFT_W), PAD, TOP + 1, HEADING, false);
        if (cheap == 0) {
            wrapped(graphics, Component.translatable("tradery.board.none_cheap"), PAD, TOP + 13, LEFT_W, MUTED, 2);
        }
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.dear_here"), LEFT_W), PAD, TOP + 49, HEADING, false);
        if (dear == 0) {
            wrapped(graphics, Component.translatable("tradery.board.none_dear"), PAD, TOP + 61, LEFT_W, MUTED, 2);
        }
        wrapped(graphics, Component.translatable("tradery.board.prices_note"), PAD, TOP + 100, LEFT_W, MUTED, 5);

        int x = RIGHT_X;
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.market_day"), RIGHT_W), x, TOP + 1, HEADING, false);
        long next = market.nextMarketDay();
        Component day = market.marketDay() ? Component.translatable("tradery.board.market_day_today")
            : next < 0 ? Component.translatable("tradery.board.no_market_days")
            : next - data.today() <= 1 ? Component.translatable("tradery.board.market_day_tomorrow")
            : Component.translatable("tradery.board.market_day_in", next - data.today());
        wrapped(graphics, day, x, TOP + 12, RIGHT_W, market.marketDay() ? OK : Panels.LABEL, 2);

        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.trading_place"), RIGHT_W), x, TOP + 35, HEADING, false);
        Component licence;
        if (market.licenceUntil() > data.today()) {
            licence = Component.translatable("tradery.board.licence_left", market.licenceUntil() - data.today());
        } else if (market.rentPerDay() > 0) {
            licence = Component.translatable("tradery.board.licence_price", money(market.rentPerDay()));
        } else {
            licence = Component.translatable("tradery.board.no_trading_places");
        }
        wrapped(graphics, licence, x, TOP + 46, RIGHT_W, Panels.LABEL, 2);

        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.board.caravans"), RIGHT_W), x, TOP + 92, HEADING, false);
        int y = TOP + 103;
        if (market.caravans().isEmpty()) {
            wrapped(graphics, Component.translatable("tradery.board.no_caravans"), x, y, RIGHT_W, MUTED, 2);
        } else {
            for (Component news : market.caravans()) {
                if (y > MESSAGE_Y - 2 * LINE) {
                    break;
                }
                y = wrapped(graphics, news, x, y, RIGHT_W, Panels.LABEL, 3);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void scrollbar(GuiGraphics graphics, int x, int y, int height, int count, int visible, int offset) {
        int max = Math.max(0, count - visible);
        int thumb = max == 0 ? height : Math.max(10, height * visible / count);
        int thumbY = max == 0 ? y : y + (height - thumb) * offset / max;
        graphics.fill(x, thumbY, x + 6, thumbY + thumb, max == 0 ? TAB_IDLE : THUMB);
        if (max > 0) {
            graphics.fill(x + 5, thumbY, x + 6, thumbY + thumb, 0xFF555555);
            graphics.fill(x, thumbY + thumb - 1, x + 6, thumbY + thumb, 0xFF555555);
        }
    }

    /** The number alone, where the coin glyph would only get in the way. */
    private static String number(long amount) {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.formatNumber(amount) : String.valueOf(amount);
    }

    private MutableComponent money(long amount) {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.money(amount) : Component.literal(String.valueOf(amount));
    }

    /** Plain items of this kind in the player's inventory, as the server counts them for a delivery. */
    private long owned(Item item) {
        return StackMath.countMatching(minecraft.player.getInventory().items,
            stack -> stack.is(item) && stack.getComponentsPatch().isEmpty());
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        VillageBoardMenu.Data data = data();
        switch (tab) {
            case TAB_ORDERS -> {
                int row = orderAt(mouseX, mouseY);
                if (row >= 0 && isHovering(LIST_X + 3, TOP + (row - orderScroll) * ROW_H + 3, 16, 16, mouseX, mouseY)) {
                    graphics.renderTooltip(font, data.orders().get(row).item(), mouseX, mouseY);
                } else if (selectedOrder < data.orders().size() && isHovering(SIDE_X, TOP + 2, 16, 16, mouseX, mouseY)) {
                    graphics.renderTooltip(font, data.orders().get(selectedOrder).item(), mouseX, mouseY);
                }
            }
            case TAB_VILLAGE -> {
                int row = projectAt(mouseX, mouseY);
                if (row == DONATION_ROW) {
                    List<Component> lines = List.of(Component.translatable("tradery.board.donation"),
                        Component.translatable("tradery.board.donation.desc").withStyle(ChatFormatting.GRAY));
                    Panels.tooltip(graphics, font, lines, mouseX, mouseY);
                } else if (row > DONATION_ROW) {
                    ResourceLocation id = data.projects().get(row - 1).id();
                    List<Component> lines = new ArrayList<>();
                    lines.add(VillageFunding.name(id));
                    lines.add(Component.translatableWithFallback("tradery.project." + id.getNamespace() + "." + id.getPath() + ".desc", "")
                        .withStyle(ChatFormatting.GRAY));
                    Panels.tooltip(graphics, font, lines, mouseX, mouseY);
                } else if (professionNames.size() > PROFESSION_LINES && isHovering(PAD, professionsY, LEFT_W, PROFESSION_LINES * LINE, mouseX, mouseY)) {
                    Panels.tooltip(graphics, font, professionNames, mouseX, mouseY);
                } else if (isHovering(PAD, chartY, CHART_DAYS * CHART_SLOT - 1, CHART_H + 1, mouseX, mouseY)) {
                    Panels.tooltip(graphics, font, chartTooltip(data.info().history(), data.today()), mouseX, mouseY);
                } else if (isHovering(PAD, reputationY, LEFT_W, LINE, mouseX, mouseY)) {
                    Panels.tooltip(graphics, font, List.of(Component.translatable("tradery.board.reputation_tip")), mouseX, mouseY);
                }
            }
            default -> {
                int cheap = 0;
                int dear = 0;
                for (VillageBoardMenu.ShiftView shift : data.market().shifts()) {
                    int index = shift.factor() < 1 ? cheap++ : dear++;
                    int[] at = shiftSlot(shift.factor() < 1, index);
                    if (at != null && isHovering(at[0], at[1], 16, 16, mouseX, mouseY)) {
                        int percent = (int) Math.round((shift.factor() - 1) * 100);
                        List<Component> lines = List.of(shift.icon().getHoverName(),
                            Component.translatable("tradery.board.price_shift", (percent > 0 ? "+" : "") + percent + "%")
                                .withStyle(percent > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
                        Panels.tooltip(graphics, font, lines, mouseX, mouseY);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    private int orderAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - TOP;
        if (tab != TAB_ORDERS || x < 0 || x >= LIST_W || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = orderScroll + (int) (y / ROW_H);
        return index < data().orders().size() ? index : -1;
    }

    private int projectAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - RIGHT_X;
        double y = mouseY - topPos - PROJECT_Y;
        if (tab != TAB_VILLAGE || x < 0 || x >= PROJECT_W || y < 0 || y >= PROJECT_ROWS * PROJECT_ROW_H) {
            return -1;
        }
        int index = projectScroll + (int) (y / PROJECT_ROW_H);
        return index < projectRows(data()) ? index : -1;
    }

    private int tabAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - PAD;
        double y = mouseY - topPos - TAB_Y;
        int tabW = (INNER_W - 4) / 3;
        if (x < 0 || y < 0 || y >= TAB_H || x >= 3 * (tabW + 2)) {
            return -1;
        }
        int index = (int) (x / (tabW + 2));
        return x - index * (tabW + 2) < tabW ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            int clickedTab = tabAt(mouseX, mouseY);
            if (clickedTab >= 0) {
                tab = clickedTab;
                return true;
            }
            int order = orderAt(mouseX, mouseY);
            if (order >= 0) {
                selectedOrder = order;
                return true;
            }
            int project = projectAt(mouseX, mouseY);
            if (project >= 0) {
                selectedProject = project;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        if (tab == TAB_ORDERS) {
            orderScroll = Mth.clamp((int) (orderScroll - scrollY), 0, Math.max(0, data().orders().size() - ROWS));
        } else if (tab == TAB_VILLAGE) {
            projectScroll = Mth.clamp((int) (projectScroll - scrollY), 0, Math.max(0, projectRows(data()) - PROJECT_ROWS));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing a donation, keys go to the text box (so "E" doesn't close the window)
        if (donateBox.isVisible() && donateBox.isFocused() && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            return donateBox.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (donateBox.isVisible() && donateBox.isFocused()) {
            return donateBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }
}
