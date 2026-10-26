package dev.eliasnvx.tradery.village;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

/**
 * 1.21.1: villager professions and types are plain registry values here (26.x made them holders and keys). The rest of
 * the village code works with holders, as on 26.x; these turn the 1.21.1 values into them.
 */
public final class VillagerCompat {
    private VillagerCompat() {
    }

    public static Holder<VillagerProfession> profession(Villager villager) {
        return BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(villager.getVillagerData().getProfession());
    }

    public static Holder<VillagerProfession> holder(VillagerProfession profession) {
        return BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession);
    }

    /** Type id of the villager, like {@code minecraft:plains}. */
    public static String typeId(Villager villager) {
        return typeId(villager.getVillagerData().getType());
    }

    public static String typeId(VillagerType type) {
        return BuiltInRegistries.VILLAGER_TYPE.getKey(type).toString();
    }

    /** The profession's display name, as vanilla shows it over a villager's trade window. */
    public static Component name(Holder<VillagerProfession> profession) {
        return Component.translatable(EntityType.VILLAGER.getDescriptionId() + "." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession.value()).getPath());
    }
}
