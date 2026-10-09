package dev.eliasnvx.tradery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Shared renderer of vending and display blocks: the item in the glass case (static, spinning, bobbing) and the
 * facade squeezed into the base. No allocations per frame beyond vanilla's own (pose stack entries).
 */
public abstract class ShowcaseRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final float ITEM_SCALE = 0.45f;
    private final ItemRenderer itemRenderer;
    private final BlockRenderDispatcher blockRenderer;
    /** The item's yaw, reused every frame (block entities render on the render thread only). */
    private final Quaternionf rotation = new Quaternionf();

    protected ShowcaseRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    protected abstract ItemStack item(T blockEntity);

    protected abstract DisplayAnimation animation(T blockEntity);

    protected abstract Direction facing(T blockEntity);

    protected @Nullable BlockState facade(T blockEntity) {
        return null;
    }

    /** Height of the item's centre in the block (blocks). */
    protected abstract float itemY();

    /** Middle of the glass case between a base of {@code baseHeight} pixels and the frame at 15 px. */
    protected static float caseCenter(int baseHeight) {
        return (baseHeight + 15) / 2f / 16f;
    }

    /** Height of the base the facade replaces (blocks); 0 = no facade slot. */
    protected float baseHeight() {
        return 0;
    }

    /** The player's own choice wins over the block's (client config). */
    private static DisplayAnimation effectiveAnimation(DisplayAnimation fromBlock) {
        ClientConfig.AnimationOverride override = TraderyConfig.client().vending().animationOverride();
        return override == ClientConfig.AnimationOverride.SERVER ? fromBlock : DisplayAnimation.valueOf(override.name());
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState facade = facade(blockEntity);
        float baseHeight = baseHeight();
        if (facade != null && baseHeight > 0) {
            poseStack.pushPose();
            // Slightly larger than the base so it covers it; squeezed to the base height
            poseStack.translate(-0.001f, -0.001f, -0.001f);
            poseStack.scale(1.002f, baseHeight + 0.002f, 1.002f);
            blockRenderer.renderSingleBlock(facade, poseStack, buffers, light, overlay);
            poseStack.popPose();
        }

        ItemStack item = item(blockEntity);
        DisplayAnimation animation = effectiveAnimation(animation(blockEntity));
        if (item.isEmpty() || animation == DisplayAnimation.NONE) {
            return;
        }
        Level level = blockEntity.getLevel();
        float time = level != null ? (level.getGameTime() % 72000L) + partialTick : 0;
        poseStack.pushPose();
        float bob = switch (animation) {
            case BOB, SPIN_BOB -> (float) Math.sin(time / 12.0) * 0.04f;
            default -> 0f;
        };
        poseStack.translate(0.5f, itemY() + bob, 0.5f);
        float yaw = switch (animation) {
            case SPIN, SPIN_BOB -> (time * 2.5f) % 360f;
            default -> -facing(blockEntity).toYRot();
        };
        poseStack.mulPose(rotation.rotationY(yaw * Mth.DEG_TO_RAD));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, poseStack, buffers, level,
            (int) blockEntity.getBlockPos().asLong());
        poseStack.popPose();
    }
}
