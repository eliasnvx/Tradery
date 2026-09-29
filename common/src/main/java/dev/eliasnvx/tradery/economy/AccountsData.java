package dev.eliasnvx.tradery.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.TraderyApi;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Every account, saved with the world ({@code data/tradery/accounts.dat}). Also remembers the currency's
 * decimals the world was created with: amounts are stored in minor units, so they can't change afterwards.
 */
public final class AccountsData extends SavedData {
    /** No decimals recorded yet (fresh world). */
    static final int UNSET = -1;

    static final Codec<AccountsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("decimals", UNSET).forGetter(d -> d.decimals),
        LedgerAccount.CODEC.listOf().optionalFieldOf("accounts", List.of()).forGetter(d -> new ArrayList<>(d.ledger.all()))
    ).apply(instance, AccountsData::new));

    public static final SavedDataType<AccountsData> TYPE = new SavedDataType<>(
        TraderyApi.id("accounts"),
        AccountsData::new,
        CODEC,
        // Plain mod data: the command-storage fixer leaves it alone
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

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
