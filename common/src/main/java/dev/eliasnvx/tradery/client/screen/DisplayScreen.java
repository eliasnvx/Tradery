package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.menu.DisplayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Display block settings: the shown item (a sample) and the animation. */
public class DisplayScreen extends AbstractContainerScreen<DisplayMenu> implements GhostTargets.TraderyGhostScreen {
    private Button animationButton;

    public DisplayScreen(DisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 152;
        inventoryLabelY = DisplayMenu.INVENTORY_TOP - 11;
    }

    @Override
    protected void init() {
        super.init();
        animationButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DisplayMenu.CYCLE_ANIMATION))
            .bounds(leftPos + 38, topPos + 46, 100, 16).build());
        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        animationButton.setMessage(Component.translatable("tradery.screen.animation", VendingOwnerScreen.animationName(menu.animation())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        Panels.ghostSlot(graphics, leftPos + DisplayMenu.SAMPLE_X, topPos + DisplayMenu.SAMPLE_Y);
        Panels.inventory(graphics, leftPos + 8, topPos + DisplayMenu.INVENTORY_TOP);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.isFake() && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font, Component.translatable("tradery.screen.display_tip"), mouseX, mouseY);
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
