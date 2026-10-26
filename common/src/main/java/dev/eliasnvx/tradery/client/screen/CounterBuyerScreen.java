package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.CounterBuyerMenu;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** The buyer's window at a market counter: one row per offer (goods, price, stock) with ×1 / ×8 / ×max. */
public class CounterBuyerScreen extends AbstractContainerScreen<CounterBuyerMenu> implements VendingResultView {
    private static final int PAD = 8;
    private static final int WIDTH = 236;
    private static final int TOP = 18;
    private static final int ROW_H = 26;
    private static final int ROW_W = WIDTH - 2 * PAD;
    private static final int ICON_X = PAD + 4;
    private static final int TEXT_X = ICON_X + 20;
    private static final int[] BUTTON_X = {148, 174, 200};
    private static final int[] BUTTON_W = {24, 24, 28};
    private static final int TEXT_W = BUTTON_X[0] - TEXT_X - 4;
    private static final int GOLD = 0xFFE0A800;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int MUTED = 0xFF5A5A5A;

    /** Offer index of each shown row (configured offers only, in order). */
    private final int[] rowOffer;
    private final int rows;
    private final Button[][] buttons;
    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public CounterBuyerScreen(CounterBuyerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = height(menu);
        CounterSettings settings = menu.data().settings();
        int[] offers = new int[CounterSettings.MAX_OFFERS];
        int count = 0;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            if (settings.offer(i).isConfigured()) {
                offers[count++] = i;
            }
        }
        rowOffer = offers;
        rows = count;
        buttons = new Button[count][3];
    }

    private static int height(CounterBuyerMenu menu) {
        return TOP + Math.max(1, menu.data().settings().configuredCount()) * ROW_H + 30;
    }

    @Override
    protected void init() {
        super.init();
        String[] keys = {"tradery.screen.buy_one", "tradery.screen.buy_eight", "tradery.screen.buy_max"};
        Component[] labels = {Component.literal("×1"), Component.literal("×8"), Component.translatable("tradery.screen.max_short")};
        for (int row = 0; row < rows; row++) {
            int offer = rowOffer[row];
            for (int amount = 0; amount < 3; amount++) {
                int id = CounterBuyerMenu.buttonId(offer, amount);
                Component label = labels[amount];
                buttons[row][amount] = addRenderableWidget(Button.builder(Panels.fit(font, label, BUTTON_W[amount] - 4),
                        b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + BUTTON_X[amount], topPos + TOP + row * ROW_H + 5, BUTTON_W[amount], 16)
                    .tooltip(Tooltip.create(Component.translatable(keys[amount]))).build());
            }
        }
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    /** Buttons work only while the offer has stock and the buyer can pay for a trade. */
    private void refreshButtons() {
        for (int row = 0; row < rows; row++) {
            int offer = rowOffer[row];
            boolean can = menu.available(offer) > 0 && menu.affordable(offer) > 0;
            for (Button button : buttons[row]) {
                button.active = can;
            }
        }
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (int row = 0; row < rows; row++) {
            int y = topPos + TOP + row * ROW_H;
            Panels.inset(graphics, leftPos + PAD, y, ROW_W, ROW_H - 2);
            Panels.slot(graphics, leftPos + ICON_X, y + 4);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Panels.fit(font, title, imageWidth - 16), titleLabelX, titleLabelY, Panels.LABEL, false);
        CounterSettings settings = menu.data().settings();
        if (rows == 0) {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.hint.not_set_up"), ROW_W), PAD, TOP + 8, ERROR, false);
        }
        for (int row = 0; row < rows; row++) {
            int offerIndex = rowOffer[row];
            CounterOffer offer = settings.offer(offerIndex);
            int y = TOP + row * ROW_H;
            graphics.renderItem(offer.goods(), ICON_X, y + 4);
            graphics.renderItemDecorations(font, offer.goods(), ICON_X, y + 4);
            graphics.drawString(font, Panels.fit(font, offer.goods().getHoverName(), TEXT_W), TEXT_X, y + 3, Panels.LABEL, false);
            int available = menu.available(offerIndex);
            Component price = price(offer);
            int priceWidth = Math.min(font.width(price), TEXT_W);
            graphics.drawString(font, Panels.fit(font, price, TEXT_W), TEXT_X, y + 13, GOLD, true);
            Component stock = available > 0
                ? Component.translatable("tradery.counter.left", count(available))
                : Component.translatable("tradery.hint.sold_out");
            graphics.drawString(font, Panels.fit(font, stock, Math.max(0, TEXT_W - priceWidth - 6)), TEXT_X + priceWidth + 6, y + 13,
                available > 0 ? MUTED : ERROR, false);
        }

        int bottom = TOP + Math.max(1, rows) * ROW_H;
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (currency != null) {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.balance", currency.money(ClientEconomy.balance())), ROW_W),
                PAD, bottom + 2, Panels.LABEL, false);
        }
        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            List<FormattedCharSequence> lines = font.split(resultMessage, ROW_W);
            if (!lines.isEmpty()) {
                graphics.drawString(font, lines.getFirst(), PAD, bottom + 14, resultSuccess ? OK : ERROR, false);
            }
        }
    }

    private static Component price(CounterOffer offer) {
        if (offer.price() == 0) {
            return Component.translatable("tradery.vending.free");
        }
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.money(offer.price()) : Component.literal(String.valueOf(offer.price()));
    }

    private static Component count(int value) {
        return value >= VendingTrades.UNLIMITED ? Component.literal("∞") : Component.literal(String.valueOf(value));
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        for (int row = 0; row < rows; row++) {
            int offerIndex = rowOffer[row];
            CounterOffer offer = menu.data().settings().offer(offerIndex);
            int y = TOP + row * ROW_H;
            if (isHovering(ICON_X, y + 4, 16, 16, mouseX, mouseY)) {
                graphics.renderTooltip(font, offer.goods(), mouseX, mouseY);
            } else if (isHovering(TEXT_X, y + 2, TEXT_W, ROW_H - 4, mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                MutableComponent goods = Component.literal(offer.perTrade() + " × ").append(offer.goods().getHoverName());
                lines.add(goods);
                lines.add(Component.translatable("tradery.screen.per_trade", offer.perTrade()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tradery.screen.in_stock", count(menu.available(offerIndex))).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tradery.screen.you_afford", count(menu.affordable(offerIndex))).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }
}
