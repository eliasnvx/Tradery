package dev.eliasnvx.tradery.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The element lists of a template pool, to add the market stalls to the village house pools. {@code templates} holds
 * each element {@code weight} times (what jigsaw generation picks from); {@code rawTemplates} the (element, weight)
 * pairs, often an immutable list from the codec, so it is replaced rather than changed.
 */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> tradery$templates();

    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> tradery$rawTemplates();

    @Mutable
    @Accessor("rawTemplates")
    void tradery$setRawTemplates(List<Pair<StructurePoolElement, Integer>> rawTemplates);
}
