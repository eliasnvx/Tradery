package dev.eliasnvx.tradery.client.render;

import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.DisplayBlock;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Display block: the shown item in the glass case. */
public class DisplayRenderer extends ShowcaseRenderer<DisplayBlockEntity> {
    public DisplayRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected ItemStack item(DisplayBlockEntity display) {
        return display.shown();
    }

    @Override
    protected DisplayAnimation animation(DisplayBlockEntity display) {
        return display.animation();
    }

    @Override
    protected Direction facing(DisplayBlockEntity display) {
        return display.getBlockState().getValue(DisplayBlock.FACING);
    }

    @Override
    protected float itemY() {
        return 9.5f / 16f;
    }
}
