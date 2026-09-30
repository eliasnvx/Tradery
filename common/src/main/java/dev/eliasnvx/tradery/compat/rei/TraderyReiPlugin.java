package dev.eliasnvx.tradery.compat.rei;

import dev.eliasnvx.tradery.client.screen.GhostTargets;
import dev.eliasnvx.tradery.compat.CompatInfo;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

/**
 * REI: drag items onto sample slots and information pages. Fabric loads it through the {@code rei_client} entrypoint,
 * NeoForge through the annotated subclass in the NeoForge module.
 */
public class TraderyReiPlugin implements REIClientPlugin {
    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDraggableStackVisitor(new SampleVisitor());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (CompatInfo.Page page : CompatInfo.pages()) {
            for (ItemStack stack : page.items()) {
                BuiltinClientPlugin.getInstance().registerInformation(EntryIngredients.of(stack), page.title(), lines -> {
                    lines.add(page.text());
                    return lines;
                });
            }
        }
    }

    private static final class SampleVisitor implements DraggableStackVisitor<Screen> {
        @Override
        public <R extends Screen> boolean isHandingScreen(R screen) {
            return screen instanceof GhostTargets.TraderyGhostScreen;
        }

        private static ItemStack stackOf(DraggableStack dragged) {
            return dragged.getStack().getValue() instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }

        @Override
        public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack dragged) {
            ItemStack stack = stackOf(dragged);
            if (stack.isEmpty() || !(context.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                return Stream.empty();
            }
            List<Rectangle> areas = GhostTargets.of(screen).stream()
                .filter(target -> GhostTargets.accepts(target, stack))
                .map(target -> new Rectangle(target.x(), target.y(), 16, 16))
                .toList();
            return Stream.of(BoundsProvider.ofRectangles(areas));
        }

        @Override
        public DraggedAcceptorResult acceptDraggedStack(DraggingContext<Screen> context, DraggableStack dragged) {
            ItemStack stack = stackOf(dragged);
            if (stack.isEmpty() || !(context.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                return DraggedAcceptorResult.PASS;
            }
            var point = context.getCurrentPosition();
            for (GhostTargets.Target target : GhostTargets.of(screen)) {
                if (point.x >= target.x() && point.x < target.x() + 16 && point.y >= target.y() && point.y < target.y() + 16
                    && GhostTargets.accepts(target, stack)) {
                    GhostTargets.drop(screen, target, stack);
                    return DraggedAcceptorResult.CONSUMED;
                }
            }
            return DraggedAcceptorResult.PASS;
        }
    }
}
