package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Saved data {@code tradery_villages}: every known village's {@link VillageState}, by village key (the bell). Apart
 * from {@link VillagerLedger} (wallets, demand), which existed first and is per villager. Server thread only.
 */
public final class VillageEconomy extends SavedData {
    /** Villages not seen for this many in-game days are forgotten (their bell is gone or nobody went there). */
    public static final long FORGET_AFTER_DAYS = 60;

    static final Codec<VillageEconomy> CODEC = Codec.unboundedMap(Codec.STRING, VillageState.CODEC)
        .xmap(VillageEconomy::new, data -> data.villages);

    static final String NAME = "tradery_villages";
    public static final CodecSavedData.Factory<VillageEconomy> FACTORY = CodecSavedData.factory(NAME, CODEC, VillageEconomy::new);

    private final Map<String, VillageState> villages = new HashMap<>();

    public VillageEconomy() {
    }

    private VillageEconomy(Map<String, VillageState> villages) {
        this.villages.putAll(villages);
    }

    public static VillageEconomy get(MinecraftServer server) {
        return CodecSavedData.get(server, FACTORY, NAME);
    }

    public @Nullable VillageState get(String key) {
        return villages.get(key);
    }

    VillageState getOrCreate(String key, java.util.function.Supplier<VillageState> create) {
        VillageState state = villages.get(key);
        if (state == null) {
            state = create.get();
            villages.put(key, state);
            setDirty();
        }
        return state;
    }

    public Collection<VillageState> all() {
        return java.util.List.copyOf(villages.values());
    }

    void remove(String key) {
        if (villages.remove(key) != null) {
            setDirty();
        }
    }

    /**
     * Drops villages not seen for {@link #FORGET_AFTER_DAYS} days, unless they hold something players paid for or are
     * owed: funded projects, a treasury, open orders or licences stay through any clock jump.
     */
    public void prune(long today) {
        if (villages.values().removeIf(v -> v.seenDay < today - FORGET_AFTER_DAYS && v.projects.isEmpty() && v.treasury == 0
            && v.orders.isEmpty() && v.licences.isEmpty())) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CodecSavedData.save(CODEC, this, tag);
    }
}
