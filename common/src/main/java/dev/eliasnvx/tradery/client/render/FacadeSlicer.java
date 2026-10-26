package dev.eliasnvx.tradery.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A facade's block model cut at the height of the base, the way a slab shows the lower part of a block: side faces
 * keep the bottom rows of their texture at full scale, the top face comes down to the cut. (Squeezing the whole
 * block into the base squashed every texture to a few pixels.) The model's parts are taken straight from the model
 * manager, not from a render state: on Fabric the renderer API fills render states with a mesh instead of parts.
 * One cut per block state, cached for the renderer's life (a resource reload makes new renderers).
 */
final class FacadeSlicer {
    private static final float EPSILON = 1.0e-4f;
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Vanilla's seed for block models drawn outside the world (BlockModelResolver). */
    private static final long MODEL_SEED = 42L;
    private static final int TRANSLUCENT = 1;

    /**
     * A facade ready to draw.
     *
     * @param parts      the model's parts, cut
     * @param renderType cutout or translucent, as vanilla picks for the block
     * @param tints      tint colours by tint index (grass, leaves)
     * @param blockLight the block's own light (glowstone)
     */
    record Facade(List<BlockStateModelPart> parts, RenderType renderType, int[] tints, int blockLight) {
    }

    private final float height;
    private final BlockModelResolver resolver;
    private final BlockDisplayContext displayContext = BlockDisplayContext.create();
    private final Map<BlockState, Facade> cache = new IdentityHashMap<>();

    /** @param height where to cut, in blocks */
    FacadeSlicer(float height, BlockModelResolver resolver) {
        this.height = height;
        this.resolver = resolver;
    }

    Facade get(BlockState state) {
        Facade facade = cache.get(state);
        if (facade == null) {
            facade = build(state);
            cache.put(state, facade);
        }
        return facade;
    }

    @SuppressWarnings("deprecation")
    private Facade build(BlockState state) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(MODEL_SEED), parts);
        parts.replaceAll(part -> new SlicedPart(part, height));
        RenderType renderType = model.hasMaterialFlag(TRANSLUCENT) ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet();
        // Tints and the block's own light, as vanilla works them out for a block drawn outside the world
        BlockModelRenderState scratch = new BlockModelRenderState();
        resolver.update(scratch, state, displayContext);
        return new Facade(List.copyOf(parts), renderType, scratch.tintLayers().toIntArray(), scratch.blockLightCoords);
    }

    /** A model part whose quads are cut at {@code height}. */
    private static final class SlicedPart implements BlockStateModelPart {
        private final BlockStateModelPart source;
        private final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculled;

        SlicedPart(BlockStateModelPart source, float height) {
            this.source = source;
            for (Direction direction : DIRECTIONS) {
                culled.put(direction, slice(source.getQuads(direction), height));
            }
            this.unculled = slice(source.getQuads(null), height);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? unculled : culled.get(direction);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return source.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return source.particleMaterial();
        }

        @Override
        public @BakedQuad.MaterialFlags int materialFlags() {
            return source.materialFlags();
        }
    }

    private static List<BakedQuad> slice(List<BakedQuad> quads, float height) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            BakedQuad cut = slice(quad, height);
            if (cut != null) {
                out.add(cut);
            }
        }
        return List.copyOf(out);
    }

    /** The part of the quad below {@code height}; null when nothing of it is left. */
    static @Nullable BakedQuad slice(BakedQuad quad, float height) {
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float y = quad.position(i).y();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (maxY <= height + EPSILON) {
            return quad;
        }
        if (maxY - minY < EPSILON) {
            // A flat face above the cut: the top comes down to it, anything else would be inside the base
            return quad.direction() == Direction.UP ? withPositions(quad, flat(quad, height), quad) : null;
        }
        if (minY >= height - EPSILON) {
            return null;
        }
        Vector3fc[] positions = new Vector3fc[4];
        long[] uvs = new long[4];
        for (int i = 0; i < 4; i++) {
            Vector3fc position = quad.position(i);
            if (position.y() <= height) {
                positions[i] = position;
                uvs[i] = quad.packedUV(i);
                continue;
            }
            // Down the edge to the neighbour below the cut, with the texture coordinates along it
            int below = lowerNeighbour(quad, i, height);
            if (below < 0) {
                return squeezed(quad, height, minY, maxY);
            }
            Vector3fc low = quad.position(below);
            float t = (height - low.y()) / (position.y() - low.y());
            positions[i] = new Vector3f(low).lerp(position, t);
            long lowUv = quad.packedUV(below);
            long highUv = quad.packedUV(i);
            uvs[i] = UVPair.pack(lerp(UVPair.unpackU(lowUv), UVPair.unpackU(highUv), t), lerp(UVPair.unpackV(lowUv), UVPair.unpackV(highUv), t));
        }
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(),
            quad.materialInfo());
    }

    /** The lower of the two adjacent corners (in the quad's order), if it is below the cut; otherwise -1. */
    private static int lowerNeighbour(BakedQuad quad, int vertex, float height) {
        int previous = (vertex + 3) % 4;
        int next = (vertex + 1) % 4;
        int lower = quad.position(previous).y() <= quad.position(next).y() ? previous : next;
        return quad.position(lower).y() <= height ? lower : -1;
    }

    /** A shape the cut can't follow (three corners above it): squeezed into the base, as before. */
    private static BakedQuad squeezed(BakedQuad quad, float height, float minY, float maxY) {
        Vector3fc[] positions = new Vector3fc[4];
        float scale = (height - minY) / (maxY - minY);
        for (int i = 0; i < 4; i++) {
            Vector3fc p = quad.position(i);
            positions[i] = new Vector3f(p.x(), minY + (p.y() - minY) * scale, p.z());
        }
        return withPositions(quad, positions, quad);
    }

    private static Vector3fc[] flat(BakedQuad quad, float height) {
        Vector3fc[] positions = new Vector3fc[4];
        for (int i = 0; i < 4; i++) {
            Vector3fc p = quad.position(i);
            positions[i] = new Vector3f(p.x(), height, p.z());
        }
        return positions;
    }

    private static BakedQuad withPositions(BakedQuad quad, Vector3fc[] positions, BakedQuad uvs) {
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs.packedUV0(), uvs.packedUV1(), uvs.packedUV2(),
            uvs.packedUV3(), quad.direction(), quad.materialInfo());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
