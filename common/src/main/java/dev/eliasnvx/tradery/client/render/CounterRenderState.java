package dev.eliasnvx.tradery.client.render;

import dev.eliasnvx.tradery.vending.CounterSettings;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/**
 * What the market counter renderer draws: up to six goods on its two shelves. Item states are made only for places
 * with goods ({@link #item}).
 */
public class CounterRenderState extends BlockEntityRenderState {
    private final ItemStackRenderState[] items = new ItemStackRenderState[CounterSettings.MAX_OFFERS];
    /** Which places have goods to draw. */
    public final boolean[] shown = new boolean[CounterSettings.MAX_OFFERS];
    public Direction facing = Direction.NORTH;

    /** The item state of place {@code index}, made on first use. */
    public ItemStackRenderState item(int index) {
        ItemStackRenderState item = items[index];
        if (item == null) {
            item = new ItemStackRenderState();
            items[index] = item;
        }
        return item;
    }
}
