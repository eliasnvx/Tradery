package dev.eliasnvx.tradery.village;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Optional;

/**
 * What a village of Town level and up specializes in (SPEC "Специализация"), picked by its patron. Each gives
 * {@code villages.council.specializationBonus} more of one thing: farming its suppliers' produce, crafts its wages and
 * treasury income, trade the visits of its customers and suppliers.
 */
public enum VillageSpecialization {
    FARMING(Items.WHEAT, "Farming"),
    CRAFTS(Items.ANVIL, "Crafts"),
    TRADE(Items.EMERALD, "Trade");

    private final Item icon;
    private final String englishName;

    VillageSpecialization(Item icon, String englishName) {
        this.icon = icon;
        this.englishName = englishName;
    }

    /** "farming", "crafts", "trade": what the save file and the network carry. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Item icon() {
        return icon;
    }

    /** "Farming" (lang {@code tradery.specialization.<id>}). */
    public Component displayName() {
        return Component.translatableWithFallback("tradery.specialization." + id(), englishName);
    }

    /** What it gives (lang {@code tradery.specialization.<id>.desc}, with the bonus in percent). */
    public Component description(int percent) {
        return Component.translatable("tradery.specialization." + id() + ".desc", percent);
    }

    public static Optional<VillageSpecialization> byId(String id) {
        for (VillageSpecialization specialization : values()) {
            if (specialization.id().equals(id)) {
                return Optional.of(specialization);
            }
        }
        return Optional.empty();
    }
}
