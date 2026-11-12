package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.menu.TradeCrateMenu;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The trade crate owner's window: three wanted-item samples on top, the payment store, the received store and the
 * inventory, like a chest. Hovering the labels explains what goes where.
 */
public class TradeCrateScreen extends AbstractContainerScreen<TradeCrateMenu> implements GhostTargets.TraderyGhostScreen {
    private static final int WIDTH = 176;
    private static final int GRAY = 0xFF707070;

    public TradeCrateScreen(TradeCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, TradeCrateMenu.HEIGHT);
        inventoryLabelY = TradeCrateMenu.INVENTORY_TOP - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        Panels.panel(graphics, x, y, WIDTH, imageHeight);
        for (int i = 0; i < TradeCrateBlockEntity.WANTS; i++) {
            Panels.ghostSlot(graphics, x + TradeCrateMenu.WANT_X + i * 18, y + TradeCrateMenu.WANT_Y);
        }
        for (int col = 0; col < TradeCrateBlockEntity.STORE_SIZE; col++) {
            Panels.slot(graphics, x + TradeCrateMenu.GRID_LEFT + col * 18, y + TradeCrateMenu.PAYMENT_Y);
            Panels.slot(graphics, x + TradeCrateMenu.GRID_LEFT + col * 18, y + TradeCrateMenu.RECEIVED_Y);
        }
        Panels.inventory(graphics, x + TradeCrateMenu.GRID_LEFT, y + TradeCrateMenu.INVENTORY_TOP);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Panels.fit(font, title, WIDTH - 16), titleLabelX, titleLabelY, Panels.LABEL, false);
        graphics.text(font, Panels.fit(font, Component.translatable("tradery.crate.wants"), TradeCrateMenu.WANT_X - 12),
            TradeCrateMenu.GRID_LEFT, TradeCrateMenu.WANT_Y + 4, Panels.LABEL, false);
        graphics.text(font, Component.translatable("tradery.crate.payment"), TradeCrateMenu.GRID_LEFT, TradeCrateMenu.PAYMENT_Y - 10,
            Panels.LABEL, false);
        graphics.text(font, Component.translatable("tradery.crate.received"), TradeCrateMenu.GRID_LEFT, TradeCrateMenu.RECEIVED_Y - 10,
            Panels.LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panels.LABEL, false);
        if (allWantsEmpty()) {
            int right = TradeCrateMenu.WANT_X + TradeCrateBlockEntity.WANTS * 18 + 4;
            graphics.text(font, Panels.fit(font, Component.translatable("tradery.crate.no_wants"), WIDTH - right - 6), right,
                TradeCrateMenu.WANT_Y + 4, GRAY, false);
        }
    }

    private boolean allWantsEmpty() {
        for (int i = 0; i < TradeCrateBlockEntity.WANTS; i++) {
            if (!menu.want(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.isFake() && !hoveredSlot.hasItem()) {
            Panels.tooltip(graphics, font, List.of(Component.translatable("tradery.crate.want_tip")), mouseX, mouseY);
            return;
        }
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (x >= TradeCrateMenu.GRID_LEFT && x < WIDTH - 8) {
            if (y >= TradeCrateMenu.PAYMENT_Y - 11 && y < TradeCrateMenu.PAYMENT_Y - 1) {
                Panels.tooltip(graphics, font, List.of(Component.translatable(menu.data().emeraldsAsMoney()
                    ? "tradery.crate.payment_tip_money" : "tradery.crate.payment_tip_items")), mouseX, mouseY);
            } else if (y >= TradeCrateMenu.RECEIVED_Y - 11 && y < TradeCrateMenu.RECEIVED_Y - 1) {
                Panels.tooltip(graphics, font, List.of(Component.translatable("tradery.crate.received_tip")), mouseX, mouseY);
            }
        }
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
