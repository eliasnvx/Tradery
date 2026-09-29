package dev.eliasnvx.tradery.client.render;

import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/** What the vending and display renderers draw: the goods (animated) and, for vending blocks, a facade. */
public class ShowcaseRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public boolean hasItem;
    public final BlockModelRenderState facade = new BlockModelRenderState();
    public boolean hasFacade;
    public Direction facing = Direction.NORTH;
    public DisplayAnimation animation = DisplayAnimation.SPIN_BOB;
    /** Ticks with partial tick, for the animation. */
    public float time;
    /** Height of the item's centre in the block (blocks). */
    public float itemY = 0.62f;
    /** Height of the base the facade replaces (blocks); 0 = no facade slot. */
    public float baseHeight;
}
