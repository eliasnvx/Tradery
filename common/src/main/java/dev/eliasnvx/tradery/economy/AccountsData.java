package dev.eliasnvx.tradery.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Every account, saved with the world ({@code data/tradery_accounts.dat}). Also remembers the currency's
 * decimals the world was created with: amounts are stored in minor units, so they can't change afterwards.
 */
public final class AccountsData extends SavedData {
    /** No decimals recorded yet (fresh world). */
    static final int UNSET = -1;

    static final Codec<AccountsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("decimals", UNSET).forGetter(d -> d.decimals),
        LedgerAccount.CODEC.listOf().optionalFieldOf("accounts", List.of()).forGetter(d -> new ArrayList<>(d.ledger.all()))
    ).apply(instance, AccountsData::new));

    public static final String NAME = "tradery_accounts";
    public static final CodecSavedData.Factory<AccountsData> FACTORY = CodecSavedData.factory(NAME, CODEC, AccountsData::new);

    private final Ledger ledger = new Ledger();
    private int decimals;

    public AccountsData() {
        this(UNSET, List.of());
    }

    private AccountsData(int decimals, List<LedgerAccount> accounts) {
        this.decimals = decimals;
        for (LedgerAccount account : accounts) {
            ledger.load(account);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CodecSavedData.save(CODEC, this, tag);
    }

    public Ledger ledger() {
        return ledger;
    }

    int decimals() {
        return decimals;
    }

    void setDecimals(int decimals) {
        this.decimals = decimals;
        setDirty();
    }
}
