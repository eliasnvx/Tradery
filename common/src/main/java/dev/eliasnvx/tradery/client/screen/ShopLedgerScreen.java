package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.client.ShopBeams;
import dev.eliasnvx.tradery.menu.ShopLedgerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.vending.ShopSnapshot;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop ledger: the player's vending blocks and market counters, alerts first. Each row: the goods, where it is
 * (distance here, dimension elsewhere), the price and stock or what's wrong, item revenue waiting, the villagers
 * toggle and "Show". Rows of shops in unloaded chunks show their last known state (darker, said in the tooltip).
 * The server does everything; this only shows what it sent and asks.
 */
public class ShopLedgerScreen extends AbstractContainerScreen<ShopLedgerMenu> implements VendingResultView {
    private static final int PAD = 8;
    private static final int LIST_X = PAD;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 276;
    private static final int ROW_H = 24;
    private static final int ROWS = 7;
    private static final int SCROLL_X = LIST_X + LIST_W + 2;
    private static final int BOTTOM_Y = LIST_Y + ROWS * ROW_H + 6;

    private static final int SHOW_W = 54;
    private static final int SHOW_X = LIST_X + LIST_W - SHOW_W - 2;
    private static final int TOGGLE_X = SHOW_X - 18;
    private static final int REVENUE_X = TOGGLE_X - 19;
    private static final int TEXT_X = LIST_X + 23;
    private static final int TEXT_W = REVENUE_X - TEXT_X - 3;

    private static final int GOLD = 0xFFE0A800;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int MUTED = 0xFF5A5A5A;
    private static final int ROW = 0xFF9A9A9A;
    private static final int ROW_HOVER = 0xFFAAAAAA;
    private static final int ROW_STALE = 0xFF8A8A8A;
    private static final int ROW_ALERT = 0xFFB59A9A;
    private static final int THUMB = 0xFFC6C6C6;
    private static final int THUMB_IDLE = 0xFF8B8B8B;
    private static final int ICON_OFF_SHADE = 0xB0505050;
    private static final ItemStack VILLAGER_ICON = new ItemStack(Items.VILLAGER_SPAWN_EGG);
    private static final ItemStack UNKNOWN_ICON = new ItemStack(Items.BOOK);

    private final Button[] showButtons = new Button[ROWS];
    private final Button[] villagerButtons = new Button[ROWS];
    private Button previousPage;
    private Button nextPage;
    private int scroll;
    private ShopLedgerMenu.Data shownData;
    /** The scroll the row buttons were set up for (-1: not yet). */
    private int shownScroll = -1;

    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public ShopLedgerScreen(ShopLedgerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = BOTTOM_Y + 22;
    }

    /** A new page (or a fresh copy) from the server for the open window. */
    public static void onSync(TraderyPayloads.ShopLedgerSyncPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ShopLedgerMenu menu && menu.containerId == payload.containerId()) {
            menu.update(payload.data());
        }
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < ROWS; i++) {
            int slot = i;
            int y = topPos + LIST_Y + i * ROW_H + 4;
            showButtons[i] = addRenderableWidget(Button.builder(Panels.fit(font, Component.translatable("tradery.ledger.show"), SHOW_W - 6),
                    b -> click(ShopLedgerMenu.SHOW, scroll + slot))
                .bounds(leftPos + SHOW_X, y, SHOW_W, 16)
                .tooltip(Tooltip.create(Component.translatable("tradery.ledger.show_tip"))).build());
            villagerButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> click(ShopLedgerMenu.VILLAGERS, scroll + slot))
                .bounds(leftPos + TOGGLE_X, y, 16, 16).build());
        }
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), b -> click(ShopLedgerMenu.PAGE, menu.data().page() - 1))
            .bounds(leftPos + PAD, topPos + BOTTOM_Y, 14, 14).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> click(ShopLedgerMenu.PAGE, menu.data().page() + 1))
            .bounds(leftPos + PAD + 16, topPos + BOTTOM_Y, 14, 14).build());
        shownData = null;
        shownScroll = -1;
        refresh();
    }

    private void click(int action, int index) {
        if (index >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ShopLedgerMenu.buttonId(action, index));
        }
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    /** New data or a new scroll: keep the scroll in range, set the row buttons up (not every frame). */
    private void refresh() {
        ShopLedgerMenu.Data data = menu.data();
        if (data == shownData && scroll == shownScroll) {
            return;
        }
        if (shownData != null && data != shownData && shownData.page() != data.page()) {
            scroll = 0;
        }
        shownData = data;
        scroll = Mth.clamp(scroll, 0, Math.max(0, data.rows().size() - ROWS));
        shownScroll = scroll;
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            ShopLedgerMenu.Row row = index < data.rows().size() ? data.rows().get(index) : null;
            showButtons[i].visible = row != null;
            boolean toggle = row != null && row.kind() != ShopLedgerMenu.KIND_UNKNOWN && row.villagersUsed();
            villagerButtons[i].visible = toggle;
            villagerButtons[i].active = toggle && row.live();
            if (toggle) {
                villagerButtons[i].setTooltip(villagersTooltip(row));
            }
        }
        boolean paged = data.pages() > 1;
        previousPage.visible = paged;
        nextPage.visible = paged;
        previousPage.active = data.page() > 0;
        nextPage.active = data.page() < data.pages() - 1;
    }

    private Tooltip villagersTooltip(ShopLedgerMenu.Row row) {
        String state = row.buyback()
            ? (row.villagersFlag() ? "tradery.screen.villagers_sell_on" : "tradery.screen.villagers_sell_off")
            : (row.villagersFlag() ? "tradery.screen.villagers_on" : "tradery.screen.villagers_off");
        MutableComponent text = Component.translatable(state).append("\n")
            .append(Component.translatable(row.live() ? "tradery.ledger.villagers_tip" : "tradery.ledger.villagers_unloaded")
                .withStyle(ChatFormatting.GRAY));
        return Tooltip.create(text);
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
        Panels.inset(graphics, leftPos + LIST_X - 1, topPos + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 1);
        Panels.inset(graphics, leftPos + SCROLL_X - 1, topPos + LIST_Y - 1, 8, ROWS * ROW_H + 1);
        // Row backgrounds go under the widgets ("Show", the villagers toggle); text and icons are drawn over them
        List<ShopLedgerMenu.Row> rows = menu.data().rows();
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ShopLedgerMenu.Row row = rows.get(scroll + i);
            int color = scroll + i == hovered ? ROW_HOVER : row.alerts() != 0 ? ROW_ALERT : row.live() ? ROW : ROW_STALE;
            int y = topPos + LIST_Y + i * ROW_H;
            graphics.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H - 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ShopLedgerMenu.Data data = menu.data();
        Component count = Component.translatable("tradery.ledger.count", data.total());
        int countWidth = font.width(count);
        graphics.drawString(font, Panels.fit(font, title, LIST_W - countWidth - 8), PAD, 6, Panels.LABEL, false);
        graphics.drawString(font, count, PAD + LIST_W - countWidth, 6, MUTED, false);

        List<ShopLedgerMenu.Row> rows = data.rows();
        if (rows.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("tradery.ledger.empty"), LIST_W - 12);
            int y = LIST_Y + 6;
            for (FormattedCharSequence line : lines.subList(0, Math.min(5, lines.size()))) {
                graphics.drawString(font, line, LIST_X + 6, y, Panels.LABEL, false);
                y += 10;
            }
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            drawRow(graphics, rows.get(scroll + i), LIST_Y + i * ROW_H);
        }
        scrollbar(graphics, rows.size());

        int messageX = PAD;
        if (data.pages() > 1) {
            Component page = Component.translatable("tradery.ledger.page", data.page() + 1, data.pages());
            graphics.drawString(font, page, PAD + 34, BOTTOM_Y + 3, Panels.LABEL, false);
            messageX = PAD + 40 + font.width(page);
        }
        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            graphics.drawString(font, Panels.fit(font, resultMessage, PAD + LIST_W - messageX), messageX, BOTTOM_Y + 3, resultSuccess ? OK : ERROR, false);
        }
    }

    private void drawRow(GuiGraphics graphics, ShopLedgerMenu.Row row, int y) {
        ItemStack icon = row.offers().isEmpty() ? UNKNOWN_ICON : row.offers().get(0).goods();
        graphics.renderItem(icon, LIST_X + 3, y + 4);
        if (!row.offers().isEmpty()) {
            graphics.renderItemDecorations(font, icon, LIST_X + 3, y + 4);
        }
        graphics.drawString(font, Panels.fit(font, where(row), TEXT_W), TEXT_X, y + 3, Panels.LABEL, false);
        graphics.drawString(font, Panels.fit(font, status(row), TEXT_W), TEXT_X, y + 13, row.alerts() != 0 ? ERROR : MUTED, false);

        if (!row.revenue().isEmpty()) {
            Panels.slot(graphics, REVENUE_X, y + 4);
            graphics.renderItem(row.revenue(), REVENUE_X, y + 4);
            graphics.renderItemDecorations(font, row.revenue(), REVENUE_X, y + 4, shortCount(row.revenueCount()));
        }
        if (row.kind() != ShopLedgerMenu.KIND_UNKNOWN && row.villagersUsed()) {
            graphics.renderItem(VILLAGER_ICON, TOGGLE_X, y + 4);
            if (!row.villagersFlag()) {
                graphics.fill(TOGGLE_X, y + 4, TOGGLE_X + 16, y + 20, ICON_OFF_SHADE);
            }
        }
    }

    /** "12 -40 305 · 23 m" here, "12 -40 305 · The Nether" elsewhere. */
    private Component where(ShopLedgerMenu.Row row) {
        BlockPos pos = row.pos().pos();
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        var player = minecraft.player;
        if (player != null && player.level().dimension() == row.pos().dimension()) {
            long distance = Math.round(Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
            return Component.translatable("tradery.ledger.where_near", at, distance);
        }
        return Component.translatable("tradery.ledger.where_far", at, ShopBeams.dimensionName(row.pos()));
    }

    /** What's wrong (red), or the offer at a glance. */
    private Component status(ShopLedgerMenu.Row row) {
        if (row.kind() == ShopLedgerMenu.KIND_UNKNOWN) {
            return Component.translatable("tradery.ledger.unknown");
        }
        List<Component> alerts = alerts(row);
        if (!alerts.isEmpty()) {
            MutableComponent text = Component.empty();
            for (int i = 0; i < alerts.size(); i++) {
                text.append(i == 0 ? Component.empty() : Component.literal(" · ")).append(alerts.get(i));
            }
            return text;
        }
        if (row.kind() == ShopLedgerMenu.KIND_COUNTER) {
            return Component.translatable("tradery.ledger.counter_offers", row.offers().size());
        }
        ShopLedgerMenu.OfferView offer = row.offers().get(0);
        Component price = price(offer);
        return row.buyback()
            ? Component.translatable("tradery.ledger.buys", price, stock(offer.stock()))
            : Component.translatable("tradery.ledger.sells", price, stock(offer.stock()));
    }

    private static List<Component> alerts(ShopLedgerMenu.Row row) {
        List<Component> alerts = new ArrayList<>();
        if (row.has(ShopSnapshot.NOT_CONFIGURED)) {
            alerts.add(Component.translatable("tradery.hint.not_set_up"));
        }
        if (row.has(ShopSnapshot.EMPTY)) {
            int empty = 0;
            for (ShopLedgerMenu.OfferView offer : row.offers()) {
                if (offer.stock() <= 0) {
                    empty++;
                }
            }
            alerts.add(row.kind() == ShopLedgerMenu.KIND_COUNTER && empty < row.offers().size()
                ? Component.translatable("tradery.ledger.offers_out", empty, row.offers().size())
                : Component.translatable("tradery.vending.empty"));
        }
        if (row.has(ShopSnapshot.NO_ROOM)) {
            alerts.add(Component.translatable(row.buyback() ? "tradery.ledger.stock_full" : "tradery.ledger.no_room"));
        }
        return alerts;
    }

    private Component price(ShopLedgerMenu.OfferView offer) {
        if (!offer.priceItem().isEmpty()) {
            return Component.literal(offer.priceItem().getCount() + " × ").append(offer.priceItem().getHoverName());
        }
        if (offer.price() == 0) {
            return Component.translatable("tradery.vending.free");
        }
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.money(offer.price()) : Component.literal(String.valueOf(offer.price()));
    }

    private static Component stock(int value) {
        return Component.literal(value >= VendingTrades.UNLIMITED ? "∞" : String.valueOf(value));
    }

    private static String shortCount(int count) {
        return count >= 10_000 ? count / 1000 + "k" : String.valueOf(count);
    }

    private void scrollbar(GuiGraphics graphics, int count) {
        int height = ROWS * ROW_H;
        int max = Math.max(0, count - ROWS);
        int thumb = max == 0 ? height : Math.max(10, height * ROWS / count);
        int thumbY = max == 0 ? LIST_Y : LIST_Y + (height - thumb) * scroll / max;
        graphics.fill(SCROLL_X, thumbY, SCROLL_X + 6, thumbY + thumb, max == 0 ? THUMB_IDLE : THUMB);
        if (max > 0) {
            graphics.fill(SCROLL_X + 5, thumbY, SCROLL_X + 6, thumbY + thumb, 0xFF555555);
            graphics.fill(SCROLL_X, thumbY + thumb - 1, SCROLL_X + 6, thumbY + thumb, 0xFF555555);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        ShopLedgerMenu.Row row = menu.data().rows().get(index);
        double x = mouseX - leftPos;
        int rowY = topPos + LIST_Y + (index - scroll) * ROW_H;
        if (!row.offers().isEmpty() && isHovering(LIST_X + 3, rowY - topPos + 4, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, row.offers().get(0).goods(), mouseX, mouseY);
        } else if (x < REVENUE_X - 2 || (!row.revenue().isEmpty() && x < TOGGLE_X)) {
            graphics.renderComponentTooltip(font, details(row), mouseX, mouseY);
        }
    }

    /** Everything about the shop: each offer, the revenue waiting, the alerts, whether this is the last known state. */
    private List<Component> details(ShopLedgerMenu.Row row) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(row.kind() == ShopLedgerMenu.KIND_COUNTER ? "block.tradery.market_counter" : "block.tradery.vending_block"));
        for (ShopLedgerMenu.OfferView offer : row.offers()) {
            Component goods = Component.literal(offer.goods().getCount() + " × ").append(offer.goods().getHoverName());
            Component stock = row.buyback()
                ? Component.translatable("tradery.screen.room", stock(offer.stock()))
                : Component.translatable("tradery.screen.in_stock", stock(offer.stock()));
            lines.add(Component.translatable(row.buyback() ? "tradery.ledger.offer_buys" : "tradery.ledger.offer_sells", goods, price(offer), stock)
                .withStyle(offer.stock() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        if (!row.revenue().isEmpty()) {
            lines.add(Component.translatable("tradery.ledger.revenue", row.revenueCount(), row.revenue().getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        for (Component alert : alerts(row)) {
            lines.add(alert.copy().withStyle(ChatFormatting.RED));
        }
        if (row.kind() == ShopLedgerMenu.KIND_UNKNOWN) {
            lines.add(Component.translatable("tradery.ledger.unknown_tip").withStyle(ChatFormatting.GRAY));
        } else if (!row.live()) {
            lines.add(Component.translatable("tradery.ledger.last_known").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    // ------------------------------------------------------------------ input

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x < 0 || x >= LIST_W || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = scroll + (int) (y / ROW_H);
        return index < menu.data().rows().size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        if (super.mouseScrolled(x, y, scrollY)) {
            return true;
        }
        scroll = Mth.clamp((int) (scroll - scrollY), 0, Math.max(0, menu.data().rows().size() - ROWS));
        refresh();
        return true;
    }
}
