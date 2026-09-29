package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.menu.DisplayMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Display block settings: the shown item (a sample) and the animation. */
public class DisplayScreen extends AbstractContainerScreen<DisplayMenu> {
    private Button animationButton;

    public DisplayScreen(DisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 152);
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        Panels.ghostSlot(graphics, leftPos + DisplayMenu.SAMPLE_X, topPos + DisplayMenu.SAMPLE_Y);
        Panels.inventory(graphics, leftPos + 8, topPos + DisplayMenu.INVENTORY_TOP);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.isFake() && !hoveredSlot.hasItem()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("tradery.screen.display_tip"), mouseX, mouseY);
        }
    }
}
