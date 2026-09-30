package dev.eliasnvx.tradery.client.render;

import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Vending block: goods in the glass case, facade in the base. */
public class VendingRenderer extends ShowcaseRenderer<VendingBlockEntity> {
    public VendingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected ItemStack item(VendingBlockEntity vendor) {
        return vendor.settings().goods();
    }

    @Override
    protected DisplayAnimation animation(VendingBlockEntity vendor) {
        return vendor.settings().animation();
    }

    @Override
    protected Direction facing(VendingBlockEntity vendor) {
        return vendor.getBlockState().getValue(VendingBlock.FACING);
    }

    @Override
    protected @Nullable BlockState facade(VendingBlockEntity vendor) {
        return vendor.facade();
    }

    @Override
    protected float itemY() {
        return caseCenter(VendingBlock.BASE_HEIGHT);
    }

    @Override
    protected float baseHeight() {
        return VendingBlock.BASE_HEIGHT / 16f;
    }
}
