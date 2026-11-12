package dev.eliasnvx.tradery.compat.guardvillagers;

import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Guard Villagers (SPEC "Интеграции 1.2"): the watch post project calls two of its guards too. No compile-time
 * dependency: the guard is looked up by id, so nothing happens without the mod.
 */
public final class GuardVillagers {
    public static final String MOD_ID = "guardvillagers";
    static final Identifier GUARD = Identifier.fromNamespaceAndPath(MOD_ID, "guard");
    /** Guards that come with a watch post. */
    public static final int PER_WATCH_POST = 2;

    private GuardVillagers() {
    }

    /** Guard Villagers is installed and has its guard. */
    public static boolean available() {
        return Platform.get().isModLoaded(MOD_ID) && BuiltInRegistries.ENTITY_TYPE.containsKey(GUARD);
    }

    /**
     * A guard at {@code x, y, z}, set up as the mod sets up its own (equipment, look by biome), not in the world yet;
     * null without the mod.
     */
    public static @Nullable Entity prepareGuard(ServerLevel level, double x, double y, double z) {
        if (!available()) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(GUARD);
        Entity guard = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (guard == null) {
            return null;
        }
        guard.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0f, 0.0f);
        if (guard instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        }
        return guard;
    }
}
