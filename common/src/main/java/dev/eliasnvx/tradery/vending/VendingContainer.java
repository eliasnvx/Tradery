package dev.eliasnvx.tradery.vending;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

/**
 * Storage of a vending block. Deliberately not exposed as the block's own {@link net.minecraft.world.Container}:
 * hoppers and pipes can't reach it, only the owner's menu and the trade code do.
 */
public final class VendingContainer extends SimpleContainer {
    private final Runnable onChanged;

    public VendingContainer(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChanged.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu checks the block entity itself
    }
}
