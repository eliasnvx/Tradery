package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where every vending block is and who owns it: for {@code /tradery vendors} and the per-player limit. Blocks
 * add themselves when placed and when loaded (self-healing), and remove themselves when broken.
 */
public final class VendorsData extends SavedData {
    public record Entry(GlobalPos pos, AccountId owner) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
            AccountId.CODEC.fieldOf("owner").forGetter(Entry::owner)
        ).apply(i, Entry::new));
    }

    static final Codec<VendorsData> CODEC = Entry.CODEC.listOf().optionalFieldOf("vendors", List.of())
        .xmap(VendorsData::new, d -> new ArrayList<>(d.vendors.values())).codec();

    /** File name in the overworld's data storage ({@code data/tradery_vendors.dat}). */
    public static final String NAME = "tradery_vendors";
    public static final CodecSavedData.Factory<VendorsData> FACTORY = CodecSavedData.factory(NAME, CODEC, VendorsData::new);

    private final Map<GlobalPos, Entry> vendors = new LinkedHashMap<>();

    public VendorsData() {
    }

    private VendorsData(List<Entry> entries) {
        entries.forEach(e -> vendors.put(e.pos(), e));
    }

    public static VendorsData get(MinecraftServer server) {
        return CodecSavedData.get(server, FACTORY, NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CodecSavedData.save(CODEC, this, tag);
    }

    /** @param overwrite replace a different owner (placement); a load never does */
    public void add(ServerLevel level, BlockPos pos, AccountId owner, boolean overwrite) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        Entry current = vendors.get(key);
        if (current == null || (overwrite && !current.owner().equals(owner))) {
            vendors.put(key, new Entry(key, owner));
            setDirty();
        }
    }

    public void setOwner(ServerLevel level, BlockPos pos, AccountId owner) {
        add(level, pos, owner, true);
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (vendors.remove(GlobalPos.of(level.dimension(), pos)) != null) {
            setDirty();
        }
    }

    public int countOwnedBy(AccountId owner) {
        return (int) vendors.values().stream().filter(e -> e.owner().equals(owner)).count();
    }

    public List<Entry> ownedBy(AccountId owner) {
        return vendors.values().stream().filter(e -> e.owner().equals(owner)).toList();
    }

    public List<Entry> all() {
        return List.copyOf(vendors.values());
    }
}
