package dev.eliasnvx.tradery.rewards;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.RewardsConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One reward section compiled for lookups: exact ids first, then tags in file order, then {@code "*"}.
 *
 * @param <T> what the keys name (entity types, blocks, items); advancements use ids only
 */
final class RewardTable<T> {
    private final boolean enabled;
    private final Map<Identifier, RewardsConfig.Reward> exact = new HashMap<>();
    private final List<Map.Entry<TagKey<T>, RewardsConfig.Reward>> tags = new ArrayList<>();
    private @Nullable RewardsConfig.Reward wildcard;

    RewardTable(RewardsConfig.Section section, @Nullable ResourceKey<? extends Registry<T>> registry, String name) {
        this.enabled = section.enabled();
        section.rewards().forEach((key, reward) -> {
            if (key.equals("*")) {
                wildcard = reward;
            } else if (key.startsWith("#")) {
                Identifier id = Identifier.tryParse(key.substring(1));
                if (id == null || registry == null) {
                    Tradery.LOGGER.warn("rewards.json5: bad tag '{}' in {}", key, name);
                } else {
                    tags.add(Map.entry(TagKey.create(registry, id), reward));
                }
            } else {
                Identifier id = Identifier.tryParse(key);
                if (id == null) {
                    Tradery.LOGGER.warn("rewards.json5: bad id '{}' in {}", key, name);
                } else {
                    exact.put(id, reward);
                }
            }
        });
    }

    boolean enabled() {
        return enabled;
    }

    /** Whether anything of this kind can ever pay (cheap check before building event data). */
    boolean isEmpty() {
        return !enabled || (exact.isEmpty() && tags.isEmpty() && wildcard == null);
    }

    @Nullable RewardsConfig.Reward find(Identifier id, @Nullable Holder<T> holder) {
        if (!enabled) {
            return null;
        }
        RewardsConfig.Reward reward = exact.get(id);
        if (reward != null) {
            return reward;
        }
        if (holder != null) {
            for (Map.Entry<TagKey<T>, RewardsConfig.Reward> tag : tags) {
                if (holder.is(tag.getKey())) {
                    return tag.getValue();
                }
            }
        }
        return wildcard;
    }
}
