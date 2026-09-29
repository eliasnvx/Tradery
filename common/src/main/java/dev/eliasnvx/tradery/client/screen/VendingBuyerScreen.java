package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The buyer's window: the offer, how much is left, and ×1 / ×8 / ×max. */
public class VendingBuyerScreen extends AbstractContainerScreen<VendingBuyerMenu> implements VendingResultView {
    private static final int OFFER_Y = 18;
    private static final int GOODS_X = 14;
    private static final int PRICE_X = 104;
    private static final int GOLD = 0xFFE0A800;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;

    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public VendingBuyerScreen(VendingBuyerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 128);
    }

    @Override
    protected void init() {
        super.init();
        VendingSettings settings = menu.data().settings();
        String verb = settings.isBuyback() ? "sell" : "buy";
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_one"), VendingBuyerMenu.BUY_ONE, 8));
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_eight"), VendingBuyerMenu.BUY_EIGHT, 63));
        addRenderableWidget(button(Component.translatable("tradery.screen." + verb + "_max"), VendingBuyerMenu.BUY_MAX, 118));
    }

    private Button button(Component label, int id, int x) {
        return Button.builder(label, b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
            .bounds(leftPos + x, topPos + 74, 50, 20).build();
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        Panels.inset(graphics, leftPos + 7, topPos + OFFER_Y, 162, 28);
        Panels.slot(graphics, leftPos + GOODS_X, topPos + OFFER_Y + 6);
        VendingSettings settings = menu.data().settings();
        if (settings.priceMode() == PriceMode.ITEM && !settings.isBuyback()) {
            Panels.slot(graphics, leftPos + PRICE_X, topPos + OFFER_Y + 6);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, Panels.LABEL, false);
        VendingSettings settings = menu.data().settings();
        ItemStack goods = settings.goods();
        int rowY = OFFER_Y + 6;

        graphics.item(goods, GOODS_X, rowY);
        graphics.itemDecorations(font, goods, GOODS_X, rowY);
        Component arrow = Component.translatable(settings.isBuyback() ? "tradery.screen.you_get" : "tradery.screen.for");
        graphics.text(font, arrow, GOODS_X + 22, rowY + 4, Panels.LABEL, false);
        if (settings.priceMode() == PriceMode.ITEM && !settings.isBuyback()) {
            ItemStack price = settings.priceItem();
            graphics.item(price, PRICE_X, rowY);
            graphics.itemDecorations(font, price, PRICE_X, rowY);
        } else {
            ClientEconomy.CurrencyView currency = ClientEconomy.currency();
            String text = settings.price() == 0 ? Component.translatable("tradery.vending.free").getString()
                : currency != null ? currency.format(settings.price()) : String.valueOf(settings.price());
            graphics.text(font, text, PRICE_X - 20 + 16, rowY + 4, GOLD, true);
        }

        int available = menu.available();
        Component stock = settings.isBuyback()
            ? Component.translatable("tradery.screen.room", count(available))
            : Component.translatable("tradery.screen.in_stock", count(available));
        graphics.text(font, stock, 8, 52, Panels.LABEL, false);
        Component can = Component.translatable(settings.isBuyback() ? "tradery.screen.you_have" : "tradery.screen.you_afford",
            count(menu.affordable()));
        graphics.text(font, can, imageWidth - 8 - font.width(can), 52, Panels.LABEL, false);
        graphics.text(font, Component.translatable("tradery.screen.per_trade", settings.perTrade()), 8, 62, 0xFF707070, false);

        if (settings.priceMode() == PriceMode.CURRENCY && ClientEconomy.currency() != null) {
            Component balance = Component.translatable("tradery.screen.balance", ClientEconomy.currency().format(ClientEconomy.balance()));
            graphics.text(font, balance, 8, 100, Panels.LABEL, false);
        }
        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            List<FormattedCharSequence> lines = font.split(resultMessage, imageWidth - 16);
            int y = 111;
            for (FormattedCharSequence line : lines.subList(0, Math.min(1, lines.size()))) {
                graphics.text(font, line, 8, y, resultSuccess ? OK : ERROR, false);
                y += 10;
            }
        }
    }

    private static Component count(int value) {
        return value >= VendingTrades.UNLIMITED ? Component.literal("∞") : Component.literal(String.valueOf(value)).withStyle(ChatFormatting.BOLD);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        VendingSettings settings = menu.data().settings();
        int rowY = topPos + OFFER_Y + 6;
        if (isHovering(GOODS_X, OFFER_Y + 6, 16, 16, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, settings.goods(), mouseX, mouseY);
        } else if (settings.priceMode() == PriceMode.ITEM && isHovering(PRICE_X, OFFER_Y + 6, 16, 16, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, settings.priceItem(), mouseX, mouseY);
        }
    }
}
