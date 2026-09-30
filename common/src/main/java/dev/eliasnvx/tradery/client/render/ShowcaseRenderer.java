package dev.eliasnvx.tradery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared renderer of vending and display blocks: the item in the glass case (static, spinning, bobbing) and the
 * facade squeezed into the base. No allocations per frame beyond vanilla's own item state.
 */
public abstract class ShowcaseRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, ShowcaseRenderState> {
    private static final BlockDisplayContext FACADE_CONTEXT = BlockDisplayContext.create();
    private static final float ITEM_SCALE = 0.45f;
    private final ItemModelResolver itemModelResolver;
    private final BlockModelResolver blockModelResolver;

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
            itemModelResolver.updateForTopItem(state.item, item.copyWithCount(1), ItemDisplayContext.FIXED, blockEntity.getLevel(), null,
                (int) blockEntity.getBlockPos().asLong());
        }
        BlockState facade = facade(blockEntity);
        state.hasFacade = facade != null;
        if (facade != null) {
            blockModelResolver.update(state.facade, facade, FACADE_CONTEXT);
        }
    }

    /** The player's own choice wins over the block's (client config). */
    private static DisplayAnimation effectiveAnimation(DisplayAnimation fromBlock) {
        ClientConfig.AnimationOverride override = TraderyConfig.client().vending().animationOverride();
        return override == ClientConfig.AnimationOverride.SERVER ? fromBlock : DisplayAnimation.valueOf(override.name());
    }

    @Override
    public void submit(ShowcaseRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.hasFacade && state.baseHeight > 0) {
            poseStack.pushPose();
            // Slightly larger than the base so it covers it; squeezed to the base height
            poseStack.translate(-0.001f, -0.001f, -0.001f);
            poseStack.scale(1.002f, state.baseHeight + 0.002f, 1.002f);
            state.facade.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
