package dev.eliasnvx.tradery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlock;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The market counter's goods, standing on its shelves facing the buyers: the first three offers on the low front
 * shelf, the rest on the raised back shelf, left to right as the buyer sees them. The stacks come from the block
 * entity (built when the offers change), so a frame allocates nothing beyond vanilla's own item rendering.
 */
public class CounterRenderer implements BlockEntityRenderer<MarketCounterBlockEntity> {
    private static final float SCALE = 0.3f;
    private static final int PER_SHELF = 3;
    /** Across the shelf, from the buyer's left (block facing north: +x is the buyer's left). */
    private static final float[] PLACE_X = {0.28f, 0f, -0.28f};
    private static final float FRONT_Z = MarketCounterBlock.FRONT_DEPTH / 2f / 16f - 0.5f;
    private static final float BACK_Z = (16 + MarketCounterBlock.FRONT_DEPTH) / 2f / 16f - 0.5f;
    private static final float FRONT_Y = MarketCounterBlock.FRONT_HEIGHT / 16f;
    private static final float BACK_Y = MarketCounterBlock.BACK_HEIGHT / 16f;

    private final ItemRenderer itemRenderer;

    public CounterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MarketCounterBlockEntity counter, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = counter.getBlockState().getValue(MarketCounterBlock.FACING);
        int seed = (int) counter.getBlockPos().asLong();
        int place = 0;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            ItemStack stack = counter.shown(i);
            if (stack.isEmpty()) {
                continue;
            }
            BakedModel model = itemRenderer.getModel(stack, counter.getLevel(), null, seed + i);
            boolean front = place < PER_SHELF;
            // Flat items stand on their lower edge; block models (a cube half the size in FIXED) on their base
            float half = model.usesBlockLight() ? SCALE * 0.25f : SCALE * 0.5f;
            poseStack.pushPose();
            poseStack.translate(0.5f, 0f, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
            poseStack.translate(PLACE_X[place % PER_SHELF], (front ? FRONT_Y : BACK_Y) + half + 0.005f, front ? FRONT_Z : BACK_Z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            poseStack.scale(SCALE, SCALE, SCALE);
            itemRenderer.render(stack, ItemDisplayContext.FIXED, false, poseStack, buffers, light, overlay, model);
            poseStack.popPose();
            place++;
        }
    }
}
