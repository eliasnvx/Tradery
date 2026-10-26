package dev.eliasnvx.tradery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlock;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The market counter's goods, standing on its shelves facing the buyers: the first three offers on the low front
 * shelf, the rest on the raised back shelf, left to right as the buyer sees them. The stacks come from the block
 * entity (built when the offers change), so a frame allocates nothing beyond vanilla's own render and item states.
 */
public class CounterRenderer implements BlockEntityRenderer<MarketCounterBlockEntity, CounterRenderState> {
    private static final float SCALE = 0.3f;
    private static final int PER_SHELF = 3;
    /** Across the shelf, from the buyer's left (block facing north: +x is the buyer's left). */
    private static final float[] PLACE_X = {0.28f, 0f, -0.28f};
    private static final float FRONT_Z = MarketCounterBlock.FRONT_DEPTH / 2f / 16f - 0.5f;
    private static final float BACK_Z = (16 + MarketCounterBlock.FRONT_DEPTH) / 2f / 16f - 0.5f;
    private static final float FRONT_Y = MarketCounterBlock.FRONT_HEIGHT / 16f;
    private static final float BACK_Y = MarketCounterBlock.BACK_HEIGHT / 16f;

    private final ItemModelResolver itemModelResolver;

    public CounterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public CounterRenderState createRenderState() {
        return new CounterRenderState();
    }

    @Override
    public void extractRenderState(MarketCounterBlockEntity counter, CounterRenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(counter, state, partialTicks, cameraPosition, breakProgress);
        state.facing = counter.getBlockState().getValue(MarketCounterBlock.FACING);
        int place = 0;
        int seed = (int) counter.getBlockPos().asLong();
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            ItemStack stack = counter.shown(i);
            if (stack.isEmpty()) {
                continue;
            }
            itemModelResolver.updateForTopItem(state.item(place), stack, ItemDisplayContext.FIXED, counter.getLevel(), null, seed + i);
            state.shown[place] = true;
            place++;
        }
        for (; place < CounterSettings.MAX_OFFERS; place++) {
            state.shown[place] = false;
        }
    }

    @Override
    public void submit(CounterRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int place = 0; place < CounterSettings.MAX_OFFERS; place++) {
            if (!state.shown[place]) {
                continue;
            }
            ItemStackRenderState item = state.item(place);
            boolean front = place < PER_SHELF;
            // Flat items stand on their lower edge; block models (a cube half the size in FIXED) on their base
            float half = item.usesBlockLight() ? SCALE * 0.25f : SCALE * 0.5f;
            poseStack.pushPose();
            poseStack.translate(0.5f, 0f, 0.5f);
            poseStack.rotateDegrees(Axis.YP, 180f - state.facing.toYRot());
            poseStack.translate(PLACE_X[place % PER_SHELF], (front ? FRONT_Y : BACK_Y) + half + 0.005f, front ? FRONT_Z : BACK_Z);
            poseStack.rotateDegrees(Axis.YP, 180f);
            poseStack.scale(SCALE, SCALE, SCALE);
            item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
