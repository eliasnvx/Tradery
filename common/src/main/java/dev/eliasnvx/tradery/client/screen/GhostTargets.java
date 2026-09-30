package dev.eliasnvx.tradery.client.screen;

import dev.eliasnvx.tradery.menu.GhostSlot;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Sample slots of Tradery screens as drop targets for recipe viewers (JEI, REI). Client side. */
public final class GhostTargets {
    /** A sample slot on screen: absolute GUI coordinates, 16x16. */
    public record Target(int slotIndex, int x, int y, GhostSlot slot) {
    }

    private GhostTargets() {
    }

    /** Active sample slots of a Tradery screen, or nothing for other screens. */
    public static List<Target> of(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof TraderyGhostScreen ghostScreen)) {
            return List.of();
        }
        List<Target> targets = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof GhostSlot ghost && ghost.isActive()) {
                targets.add(new Target(slot.index, ghostScreen.left() + slot.x, ghostScreen.top() + slot.y, ghost));
            }
        }
        return targets;
    }

    /** Whether this item may go into the slot (so viewers only highlight real targets). */
    public static boolean accepts(Target target, ItemStack stack) {
        return target.slot().accepts(stack);
    }

    /** Sets the sample on both sides: locally for instant feedback, then on the server. */
    public static void drop(AbstractContainerScreen<?> screen, Target target, ItemStack stack) {
        ItemStack sample = stack.copyWithCount(Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize())));
        target.slot().set(sample);
        Platform.get().sendToServer(new TraderyPayloads.GhostSamplePayload(screen.getMenu().containerId, target.slotIndex(), sample));
    }

    /** Implemented by Tradery screens with sample slots. */
    public interface TraderyGhostScreen {
        int left();

        int top();
    }
}
