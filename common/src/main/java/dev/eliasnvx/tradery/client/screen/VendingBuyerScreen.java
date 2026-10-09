package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The buyer's window: the offer, how much is left, and ×1 / ×8 / ×max. */
public class VendingBuyerScreen extends AbstractContainerScreen<VendingBuyerMenu> implements VendingResultView {
    private static final int OFFER_Y = 18;
    private static final int BUTTON_WIDTH = 50;
    private static final int GOODS_X = 14;
    private static final int PRICE_X = 104;
    private static final int GOLD = 0xFFE0A800;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;

    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public VendingBuyerScreen(VendingBuyerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 134;
    }

    @Override
    protected void init() {
        super.init();
        VendingSettings settings = menu.data().settings();
        String verb = settings.isBuyback() ? "sell" : "buy";
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_one"), Component.literal("×1"), VendingBuyerMenu.BUY_ONE, 8));
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_eight"), Component.literal("×8"), VendingBuyerMenu.BUY_EIGHT, 63));
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_max"), Component.translatable("tradery.screen.max_short"),
            VendingBuyerMenu.BUY_MAX, 118));
    }

    /** "Buy ×1" if it fits the button; otherwise "×1" with the full label as the tooltip (long words in some languages). */
    private Button button(Component label, Component shortLabel, int id, int x) {
        boolean fits = font.width(label) <= BUTTON_WIDTH - 6;
        Button.Builder builder = Button.builder(fits ? label : shortLabel, b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
            .bounds(leftPos + x, topPos + 81, BUTTON_WIDTH, 20);
        if (!fits) {
            builder.tooltip(Tooltip.create(label));
        }
        return builder.build();
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 container screens don't dim the world themselves
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        Panels.inset(graphics, leftPos + 7, topPos + OFFER_Y, 162, 28);
        Panels.slot(graphics, leftPos + GOODS_X, topPos + OFFER_Y + 6);
        VendingSettings settings = menu.data().settings();
        if (settings.priceMode() == PriceMode.ITEM && !settings.isBuyback()) {
            Panels.slot(graphics, leftPos + PRICE_X, topPos + OFFER_Y + 6);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.LABEL, false);
        VendingSettings settings = menu.data().settings();
        ItemStack goods = settings.goods();
        int rowY = OFFER_Y + 6;

        graphics.renderItem(goods, GOODS_X, rowY);
        graphics.renderItemDecorations(font, goods, GOODS_X, rowY);
        Component arrow = Component.translatable(settings.isBuyback() ? "tradery.screen.you_get" : "tradery.screen.for");
        graphics.drawString(font, arrow, GOODS_X + 22, rowY + 4, Panels.LABEL, false);
        if (settings.priceMode() == PriceMode.ITEM && !settings.isBuyback()) {
            ItemStack price = settings.priceItem();
            graphics.renderItem(price, PRICE_X, rowY);
            graphics.renderItemDecorations(font, price, PRICE_X, rowY);
        } else {
            ClientEconomy.CurrencyView currency = ClientEconomy.currency();
            Component text = settings.price() == 0 ? Component.translatable("tradery.vending.free")
                : currency != null ? currency.money(settings.price()) : Component.literal(String.valueOf(settings.price()));
            graphics.drawString(font, text, PRICE_X - 20 + 16, rowY + 4, GOLD, true);
        }

        int available = menu.available();
        Component stock = settings.isBuyback()
            ? Component.translatable("tradery.screen.room", count(available))
            : Component.translatable("tradery.screen.in_stock", count(available));
        // One fact per line: side by side they collide in languages with long words
        graphics.drawString(font, stock, 8, 50, Panels.LABEL, false);
        Component can = Component.translatable(settings.isBuyback() ? "tradery.screen.you_have" : "tradery.screen.you_afford",
            count(menu.affordable()));
        graphics.drawString(font, can, 8, 60, Panels.LABEL, false);
        graphics.drawString(font, Component.translatable("tradery.screen.per_trade", settings.perTrade()), 8, 70, 0xFF707070, false);

        if (settings.priceMode() == PriceMode.CURRENCY && ClientEconomy.currency() != null) {
            Component balance = Component.translatable("tradery.screen.balance", ClientEconomy.currency().money(ClientEconomy.balance()));
            graphics.drawString(font, balance, 8, 106, Panels.LABEL, false);
        }
        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            List<FormattedCharSequence> lines = font.split(resultMessage, imageWidth - 16);
            int y = 117;
            for (FormattedCharSequence line : lines.subList(0, Math.min(1, lines.size()))) {
                graphics.drawString(font, line, 8, y, resultSuccess ? OK : ERROR, false);
                y += 10;
            }
        }
    }

    private static Component count(int value) {
        return value >= VendingTrades.UNLIMITED ? Component.literal("∞") : Component.literal(String.valueOf(value)).withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        VendingSettings settings = menu.data().settings();
        int rowY = topPos + OFFER_Y + 6;
        if (isHovering(GOODS_X, OFFER_Y + 6, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, settings.goods(), mouseX, mouseY);
        } else if (settings.priceMode() == PriceMode.ITEM && isHovering(PRICE_X, OFFER_Y + 6, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, settings.priceItem(), mouseX, mouseY);
        }
    }
}
