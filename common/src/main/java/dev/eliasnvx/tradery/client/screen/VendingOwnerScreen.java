package dev.eliasnvx.tradery.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.util.MoneyText;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * The owner's window: stock (3 rows) and item revenue (1 row) on the left like a chest, the offer settings on the
 * right. Sample slots and toggles change a draft; "Save" applies it on the server.
 */
public class VendingOwnerScreen extends AbstractContainerScreen<VendingOwnerMenu> implements VendingResultView, GhostTargets.TraderyGhostScreen {
    private static final int CHEST_WIDTH = 176;
    private static final int CHEST_HEIGHT = 186;
    private static final int PANEL_X = 176;
    private static final int PANEL_WIDTH = 124;
    private static final int PANEL_INNER = 184;
    /** From the -/+ buttons to the panel's edge. */
    private static final int PER_TRADE_WIDTH = 54;
    private static final int GRAY = 0xFF707070;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int GOLD_LINE = 0xFFB88A1B;

    private EditBox priceBox;
    private Button modeButton;
    private Button buybackButton;
    private Button animationButton;
    private Button[] priceCountButtons = new Button[2];
    private Button[] adminButtons = new Button[4];
    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public VendingOwnerScreen(VendingOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CHEST_WIDTH + PANEL_WIDTH;
        imageHeight = menu.adminMode() ? 232 : 206;
        inventoryLabelY = CHEST_HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + PANEL_INNER;
        addRenderableWidget(small("-", VendingOwnerMenu.GOODS_MINUS, VendingOwnerMenu.GOODS_MINUS_8, leftPos + VendingOwnerMenu.GOODS_X + 22,
            topPos + VendingOwnerMenu.GOODS_Y + 1));
        addRenderableWidget(small("+", VendingOwnerMenu.GOODS_PLUS, VendingOwnerMenu.GOODS_PLUS_8, leftPos + VendingOwnerMenu.GOODS_X + 38,
            topPos + VendingOwnerMenu.GOODS_Y + 1));

        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(VendingOwnerMenu.TOGGLE_MODE))
            .bounds(x, topPos + 44, 112, 16).build());

        priceBox = new EditBox(font, leftPos + VendingOwnerMenu.PRICE_X, topPos + VendingOwnerMenu.PRICE_Y, 78, 16,
            Component.translatable("tradery.screen.price"));
        priceBox.setMaxLength(20);
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        int decimals = currency != null ? currency.decimals() : 2;
        long price = menu.data().settings().price();
        priceBox.setValue(Money.toMajor(price, decimals).stripTrailingZeros().toPlainString());
        priceBox.setHint(Component.literal("0"));
        addRenderableWidget(priceBox);
        priceCountButtons[0] = addRenderableWidget(small("-", VendingOwnerMenu.PRICE_MINUS, VendingOwnerMenu.PRICE_MINUS_8,
            leftPos + VendingOwnerMenu.PRICE_X + 22, topPos + VendingOwnerMenu.PRICE_Y + 1));
        priceCountButtons[1] = addRenderableWidget(small("+", VendingOwnerMenu.PRICE_PLUS, VendingOwnerMenu.PRICE_PLUS_8,
            leftPos + VendingOwnerMenu.PRICE_X + 38, topPos + VendingOwnerMenu.PRICE_Y + 1));

        buybackButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(VendingOwnerMenu.TOGGLE_BUYBACK))
            .bounds(x, topPos + 96, 112, 16).tooltip(Tooltip.create(Component.translatable("tradery.screen.buyback_tip"))).build());
        animationButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(VendingOwnerMenu.CYCLE_ANIMATION))
            .bounds(x, topPos + 148, 112, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("tradery.screen.save"), b -> save())
            .bounds(x, topPos + 166, 112, 18).build());

        if (menu.adminMode()) {
            String[] keys = {"infinite", "burn", "no_fee", "server"};
            int[] ids = {VendingOwnerMenu.ADMIN_INFINITE, VendingOwnerMenu.ADMIN_BURN, VendingOwnerMenu.ADMIN_NO_FEE, VendingOwnerMenu.ADMIN_SERVER_OWNER};
            for (int i = 0; i < 4; i++) {
                int id = ids[i];
                adminButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> click(id))
                    .bounds(x + i * 28, topPos + 188, 26, 16)
                    .tooltip(Tooltip.create(Component.translatable("tradery.screen.admin." + keys[i] + "_tip"))).build());
            }
        }
        refreshWidgets();
    }

    /** A 14x14 button: normal click = one step, shift-click = eight. */
    private Button small(String label, int id, int shiftId, int x, int y) {
        return Button.builder(Component.literal(label), b -> click(hasShiftDown() ? shiftId : id)).bounds(x, y, 14, 14).build();
    }

    private void click(int buttonId) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
    }

    private void save() {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        long price = 0;
        if (menu.priceMode() == PriceMode.CURRENCY) {
            OptionalLong parsed = Money.parse(priceBox.getValue().trim().isEmpty() ? "0" : priceBox.getValue().trim(),
                currency != null ? currency.decimals() : 2);
            if (parsed.isEmpty()) {
                showResult(false, Component.translatable("tradery.error.bad_amount", priceBox.getValue()));
                return;
            }
            price = parsed.getAsLong();
        }
        Platform.get().sendToServer(new TraderyPayloads.VendingSavePayload(menu.containerId, price));
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshWidgets();
    }

    /** Labels and visibility follow the draft, which the server syncs through data slots. */
    private void refreshWidgets() {
        boolean currency = menu.priceMode() == PriceMode.CURRENCY;
        modeButton.setMessage(Component.translatable(currency ? "tradery.screen.price_money" : "tradery.screen.price_item"));
        priceBox.visible = currency;
        priceBox.active = currency;
        for (Button button : priceCountButtons) {
            button.visible = !currency;
        }
        buybackButton.visible = currency;
        buybackButton.setMessage(Component.translatable(menu.buyback() ? "tradery.screen.buyback_on" : "tradery.screen.buyback_off"));
        // "Show: spin & bob"; just "spin & bob" (full text as the tooltip) where the language makes it too long
        Component animation = Component.translatable("tradery.animation." + menu.animation().name().toLowerCase(Locale.ROOT));
        Component full = Component.translatable("tradery.screen.animation", animation);
        boolean fits = font.width(full) <= animationButton.getWidth() - 8;
        animationButton.setMessage(fits ? full : Panels.fit(font, animation, animationButton.getWidth() - 8));
        animationButton.setTooltip(fits ? null : Tooltip.create(full));
        if (menu.adminMode()) {
            AdminFlags flags = menu.adminFlags();
            boolean[] on = {flags.infiniteStock(), flags.burnPayment(), flags.noFee(), menu.serverOwned()};
            String[] keys = {"infinite", "burn", "no_fee", "server"};
            for (int i = 0; i < 4; i++) {
                // 26 px: a label too long for it is cut to what fits (the tooltip explains the button)
                Component label = Component.translatable("tradery.screen.admin." + keys[i]);
                String text = font.width(label) <= adminButtons[i].getWidth() - 4 ? label.getString()
                    : font.plainSubstrByWidth(label.getString(), adminButtons[i].getWidth() - 4);
                adminButtons[i].setMessage(Component.literal(text).withStyle(on[i] ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panels.panel(graphics, x, y, CHEST_WIDTH, CHEST_HEIGHT);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                Panels.slot(graphics, x + VendingOwnerMenu.GRID_LEFT + col * 18, y + VendingOwnerMenu.GRID_TOP + row * 18);
            }
        }
        // Gold line between the stock and the revenue row
        graphics.fill(x + 7, y + VendingOwnerMenu.GRID_TOP + 3 * 18 - 1, x + 169, y + VendingOwnerMenu.GRID_TOP + 3 * 18, GOLD_LINE);
        Panels.inventory(graphics, x + VendingOwnerMenu.GRID_LEFT, y + VendingOwnerMenu.INVENTORY_TOP);

        Panels.panel(graphics, x + PANEL_X, y, PANEL_WIDTH, imageHeight);
        Panels.ghostSlot(graphics, x + VendingOwnerMenu.GOODS_X, y + VendingOwnerMenu.GOODS_Y);
        if (menu.priceMode() == PriceMode.ITEM) {
            Panels.ghostSlot(graphics, x + VendingOwnerMenu.PRICE_X, y + VendingOwnerMenu.PRICE_Y);
        }
        Panels.ghostSlot(graphics, x + VendingOwnerMenu.FACADE_X, y + VendingOwnerMenu.FACADE_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panels.LABEL, false);
        Component revenue = Component.translatable("tradery.screen.revenue");
        graphics.drawString(font, revenue, CHEST_WIDTH - 8 - font.width(revenue), VendingOwnerMenu.GRID_TOP + 4 * 18 + 2, GRAY, false);

        graphics.drawString(font, Component.translatable("tradery.screen.goods"), PANEL_INNER, 6, Panels.LABEL, false);
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.per_trade_short"), PER_TRADE_WIDTH), VendingOwnerMenu.GOODS_X + 56,
            VendingOwnerMenu.GOODS_Y + 4, GRAY, false);
        boolean currency = menu.priceMode() == PriceMode.CURRENCY;
        if (currency) {
            ClientEconomy.CurrencyView view = ClientEconomy.currency();
            if (view != null) {
                graphics.drawString(font, MoneyText.coin(view.symbol()), VendingOwnerMenu.PRICE_X + 82, VendingOwnerMenu.PRICE_Y + 4, Panels.LABEL, false);
            }
            graphics.drawString(font, Component.translatable("tradery.screen.fee", menu.data().feePercent()), PANEL_INNER + 2,
                VendingOwnerMenu.PRICE_Y + 19, GRAY, false);
        } else {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.per_trade_short"), PER_TRADE_WIDTH), VendingOwnerMenu.PRICE_X + 56,
                VendingOwnerMenu.PRICE_Y + 4, GRAY, false);
        }
        graphics.drawString(font, Component.translatable("tradery.screen.facade"), PANEL_INNER, VendingOwnerMenu.FACADE_Y - 11, Panels.LABEL, false);
        List<FormattedCharSequence> hint = font.split(Component.translatable("tradery.screen.facade_hint"), 90);
        for (int i = 0; i < Math.min(2, hint.size()); i++) {
            graphics.drawString(font, hint.get(i), VendingOwnerMenu.FACADE_X + 20, VendingOwnerMenu.FACADE_Y + i * 9, GRAY, false);
        }

        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 8000) {
            int y = menu.adminMode() ? 208 : 188;
            List<FormattedCharSequence> lines = font.split(resultMessage, PANEL_WIDTH - 12);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), PANEL_INNER - 2, y + i * 10, resultSuccess ? OK : ERROR, false);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.isFake()) {
            String key = hoveredSlot.index == VendingOwnerMenu.GOODS_SLOT ? "tradery.screen.goods_tip"
                : hoveredSlot.index == VendingOwnerMenu.PRICE_SLOT ? "tradery.screen.price_tip" : "tradery.screen.facade_tip";
            graphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing the price, keys go to the text box (so "E" doesn't close and digits don't swap slots)
        if (priceBox.isVisible() && priceBox.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
            return priceBox.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (priceBox.isVisible() && priceBox.isFocused()) {
            return priceBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo, int button) {
        return mx < xo || my < yo || mx >= xo + imageWidth || my >= yo + imageHeight
            // the corner under the chest part and left of the panel is outside too
            || (mx < xo + CHEST_WIDTH && my >= yo + CHEST_HEIGHT);
    }

    /** Animation names for tooltips elsewhere. */
    static Component animationName(DisplayAnimation animation) {
        return Component.translatable("tradery.animation." + animation.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public int left() {
        return leftPos;
    }

    @Override
    public int top() {
        return topPos;
    }
}
