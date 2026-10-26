package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.CounterOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.util.MoneyText;
import dev.eliasnvx.tradery.vending.CounterSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * The market counter owner's window: the shared stock (3 rows) on the left like a chest, the six offers on the right
 * (goods sample with the amount per trade, -/+ and the money price), the villagers toggle and Save. Samples and the
 * toggle change a draft; "Save" sends the prices and the server applies the whole draft.
 */
public class CounterOwnerScreen extends AbstractContainerScreen<CounterOwnerMenu> implements VendingResultView, GhostTargets.TraderyGhostScreen {
    private static final int CHEST_WIDTH = 176;
    private static final int PANEL_WIDTH = 146;
    private static final int PANEL_INNER = CounterOwnerMenu.PANEL_LEFT + 8;
    private static final int MINUS_X = CounterOwnerMenu.SAMPLE_X + 20;
    private static final int PLUS_X = MINUS_X + 16;
    private static final int PRICE_X = PLUS_X + 18;
    private static final int PRICE_W = 64;
    private static final int BOTTOM_Y = CounterOwnerMenu.OFFER_TOP + CounterSettings.MAX_OFFERS * CounterOwnerMenu.OFFER_ROW + 2;
    private static final int ICON_BUTTON = 16;
    private static final int GRAY = 0xFF707070;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int ICON_OFF_SHADE = 0xB0505050;
    private static final ItemStack VILLAGER_ICON = new ItemStack(Items.VILLAGER_SPAWN_EGG);

    private final EditBox[] priceBoxes = new EditBox[CounterSettings.MAX_OFFERS];
    private final Button[] minusButtons = new Button[CounterSettings.MAX_OFFERS];
    private final Button[] plusButtons = new Button[CounterSettings.MAX_OFFERS];
    private Button villagersButton;
    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public CounterOwnerScreen(CounterOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CHEST_WIDTH + PANEL_WIDTH;
        imageHeight = BOTTOM_Y + 56;
        inventoryLabelY = CounterOwnerMenu.INVENTORY_TOP - 11;
    }

    @Override
    protected void init() {
        super.init();
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        int decimals = currency != null ? currency.decimals() : 2;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            int offer = i;
            int y = topPos + CounterOwnerMenu.sampleY(i);
            minusButtons[i] = addRenderableWidget(Button.builder(Component.literal("-"),
                b -> click(CounterOwnerMenu.buttonId(offer, net.minecraft.client.gui.screens.Screen.hasShiftDown() ? CounterOwnerMenu.MINUS_8 : CounterOwnerMenu.MINUS)))
                .bounds(leftPos + MINUS_X, y + 1, 14, 14).build());
            plusButtons[i] = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> click(CounterOwnerMenu.buttonId(offer, net.minecraft.client.gui.screens.Screen.hasShiftDown() ? CounterOwnerMenu.PLUS_8 : CounterOwnerMenu.PLUS)))
                .bounds(leftPos + PLUS_X, y + 1, 14, 14).build());
            EditBox box = new EditBox(font, leftPos + PRICE_X, y, PRICE_W, 16, Component.translatable("tradery.screen.price"));
            box.setMaxLength(20);
            long price = menu.data().settings().offer(i).price();
            box.setValue(price == 0 ? "" : Money.toMajor(price, decimals).stripTrailingZeros().toPlainString());
            box.setHint(Component.literal("0"));
            priceBoxes[i] = addRenderableWidget(box);
        }
        villagersButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(CounterOwnerMenu.TOGGLE_VILLAGERS))
            .bounds(leftPos + PANEL_INNER, topPos + BOTTOM_Y, ICON_BUTTON, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("tradery.screen.save"), b -> save())
            .bounds(leftPos + PANEL_INNER + ICON_BUTTON + 2, topPos + BOTTOM_Y, PANEL_WIDTH - 16 - ICON_BUTTON - 2, 18).build());
        refreshWidgets();
    }

    private void click(int buttonId) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
    }

    private void save() {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        int decimals = currency != null ? currency.decimals() : 2;
        List<Long> prices = new ArrayList<>(CounterSettings.MAX_OFFERS);
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            String text = priceBoxes[i].getValue().trim();
            if (menu.sample(i).isEmpty() || text.isEmpty()) {
                prices.add(0L);
                continue;
            }
            OptionalLong parsed = Money.parse(text, decimals);
            if (parsed.isEmpty()) {
                showResult(false, Component.translatable("tradery.error.bad_amount", text));
                return;
            }
            prices.add(parsed.getAsLong());
        }
        Platform.get().sendToServer(new TraderyPayloads.CounterSavePayload(menu.containerId, prices));
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

    /** Price boxes and -/+ only for offers with goods; the toggle's tooltip follows the draft. */
    private void refreshWidgets() {
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            boolean set = !menu.sample(i).isEmpty();
            priceBoxes[i].visible = set;
            priceBoxes[i].active = set;
            minusButtons[i].visible = set;
            plusButtons[i].visible = set;
        }
        villagersButton.setTooltip(Tooltip.create(Component.translatable(menu.villagers() ? "tradery.screen.villagers_on" : "tradery.screen.villagers_off")
            .append("\n").append(Component.translatable("tradery.counter.villagers_tip").withStyle(ChatFormatting.GRAY))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panels.panel(graphics, x, y, CHEST_WIDTH, CounterOwnerMenu.CHEST_HEIGHT);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panels.slot(graphics, x + CounterOwnerMenu.GRID_LEFT + col * 18, y + CounterOwnerMenu.GRID_TOP + row * 18);
            }
        }
        Panels.inventory(graphics, x + CounterOwnerMenu.GRID_LEFT, y + CounterOwnerMenu.INVENTORY_TOP);
        Panels.panel(graphics, x + CounterOwnerMenu.PANEL_LEFT, y, PANEL_WIDTH, imageHeight);
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            Panels.ghostSlot(graphics, x + CounterOwnerMenu.SAMPLE_X, y + CounterOwnerMenu.sampleY(i));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Panels.fit(font, title, CHEST_WIDTH - 16), titleLabelX, titleLabelY, Panels.LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panels.LABEL, false);
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.counter.offers"), PANEL_WIDTH - 16), PANEL_INNER, 6, Panels.LABEL, false);

        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            int y = CounterOwnerMenu.sampleY(i);
            if (menu.sample(i).isEmpty()) {
                graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.counter.empty_offer"), PANEL_WIDTH - 40),
                    CounterOwnerMenu.SAMPLE_X + 22, y + 4, GRAY, false);
            } else if (currency != null) {
                graphics.drawString(font, MoneyText.coin(currency.symbol()), PRICE_X + PRICE_W + 2, y + 4, Panels.LABEL, false);
            }
        }

        int iconX = villagersButton.getX() - leftPos;
        int iconY = villagersButton.getY() - topPos + 1;
        graphics.renderItem(VILLAGER_ICON, iconX, iconY);
        if (!menu.villagers()) {
            graphics.fill(iconX, iconY, iconX + ICON_BUTTON, iconY + ICON_BUTTON, ICON_OFF_SHADE);
        }
        graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.fee", menu.data().feePercent()), PANEL_WIDTH - 16),
            PANEL_INNER, BOTTOM_Y + 22, GRAY, false);

        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 8000) {
            List<FormattedCharSequence> lines = font.split(resultMessage, PANEL_WIDTH - 14);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), PANEL_INNER - 1, BOTTOM_Y + 33 + i * 10, resultSuccess ? OK : ERROR, false);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.isFake()) {
            if (!hoveredSlot.hasItem()) {
                graphics.renderTooltip(font, Component.translatable("tradery.counter.goods_tip"), mouseX, mouseY);
            }
            return;
        }
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            if (priceBoxes[i].visible && priceBoxes[i].isHovered() && !priceBoxes[i].isFocused()) {
                graphics.renderTooltip(font, Component.translatable("tradery.counter.price_tip", menu.sample(i).getCount(),
                    menu.sample(i).getHoverName()), mouseX, mouseY);
            }
        }
    }

    private EditBox focusedPriceBox() {
        for (EditBox box : priceBoxes) {
            if (box.isVisible() && box.isFocused()) {
                return box;
            }
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing a price, keys go to the text box (so "E" doesn't close and digits don't swap slots)
        EditBox box = focusedPriceBox();
        if (box != null && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            return box.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        EditBox box = focusedPriceBox();
        if (box != null) {
            return box.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo, int button) {
        return mx < xo || my < yo || mx >= xo + imageWidth || my >= yo + imageHeight
            // the corner under the chest part and left of the panel is outside too
            || (mx < xo + CHEST_WIDTH && my >= yo + CounterOwnerMenu.CHEST_HEIGHT);
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
