package dev.eliasnvx.tradery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared renderer of vending and display blocks: the item in the glass case (static, spinning, bobbing) and the
 * facade cut to the height of the base ({@link FacadeSlicer}). No allocations per frame beyond vanilla's own item and
 * block model states.
 */
public abstract class ShowcaseRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, ShowcaseRenderState> {
    private static final float ITEM_SCALE = 0.45f;
    private static final float FACADE_MARGIN = 0.001f;
    private final ItemModelResolver itemModelResolver;
    private final BlockModelResolver blockModelResolver;
    private @Nullable FacadeSlicer facadeSlicer;

    protected ShowcaseRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.blockModelResolver = context.blockModelResolver();
    }

    protected abstract ItemStack item(T blockEntity);

    protected abstract DisplayAnimation animation(T blockEntity);

    protected abstract Direction facing(T blockEntity);

    protected @Nullable BlockState facade(T blockEntity) {
        return null;
    }

    protected abstract float itemY();

    /** Middle of the glass case between a base of {@code baseHeight} pixels and the frame at 15 px. */
    protected static float caseCenter(int baseHeight) {
        return (baseHeight + 15) / 2f / 16f;
    }

    protected float baseHeight() {
        return 0;
    }

    @Override
    public ShowcaseRenderState createRenderState() {
        return new ShowcaseRenderState();
    }

    @Override
    public void extractRenderState(T blockEntity, ShowcaseRenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facing = facing(blockEntity);
        state.itemY = itemY();
        state.baseHeight = baseHeight();
        state.animation = effectiveAnimation(animation(blockEntity));
        state.time = blockEntity.getLevel() != null ? (blockEntity.getLevel().getGameTime() % 72000L) + partialTicks : 0;

        ItemStack item = item(blockEntity);
        state.hasItem = !item.isEmpty() && state.animation != DisplayAnimation.NONE;
        if (state.hasItem) {
            // The count doesn't change the model, so the live stack goes in as it is: no copy per frame
            itemModelResolver.updateForTopItem(state.item, item, ItemDisplayContext.FIXED, blockEntity.getLevel(), null,
                (int) blockEntity.getBlockPos().asLong());
        }
        BlockState facade = facade(blockEntity);
        state.facade = facade == null || baseHeight() <= 0 ? null : facadeSlicer().get(facade);
    }

    private FacadeSlicer facadeSlicer() {
        if (facadeSlicer == null) {
            // A hair above the base, so the facade covers it (the base model isn't drawn under a facade anyway)
            facadeSlicer = new FacadeSlicer(baseHeight() + FACADE_MARGIN * 2, blockModelResolver);
        }
        return facadeSlicer;
    }

    /** The player's own choice wins over the block's (client config). */
    private static DisplayAnimation effectiveAnimation(DisplayAnimation fromBlock) {
        ClientConfig.AnimationOverride override = TraderyConfig.client().vending().animationOverride();
        DisplayAnimation forced = override.animation();
        return forced == null ? fromBlock : forced;
    }

    @Override
    public void submit(ShowcaseRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        FacadeSlicer.Facade facade = state.facade;
        if (facade != null) {
            poseStack.pushPose();
            // Slightly wider than the base so it covers it; already cut to the base height
            poseStack.translate(-FACADE_MARGIN, -FACADE_MARGIN, -FACADE_MARGIN);
            poseStack.scale(1 + FACADE_MARGIN * 2, 1, 1 + FACADE_MARGIN * 2);
            collector.submitBlockModel(poseStack, facade.renderType(), facade.parts(), facade.tints(),
                LightCoordsUtil.max(state.lightCoords, facade.blockLight()), OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (!state.hasItem) {
            return;
        }
        poseStack.pushPose();
        float bob = switch (state.animation) {
            case BOB, SPIN_BOB -> (float) Math.sin(state.time / 12.0) * 0.04f;
            default -> 0f;
        };
        poseStack.translate(0.5f, state.itemY + bob, 0.5f);
        float yaw = switch (state.animation) {
            case SPIN, SPIN_BOB -> (state.time * 2.5f) % 360f;
            default -> -state.facing.toYRot();
        };
        poseStack.rotateDegrees(Axis.YP, yaw);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
