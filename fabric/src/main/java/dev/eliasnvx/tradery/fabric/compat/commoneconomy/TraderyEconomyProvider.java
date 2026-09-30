package dev.eliasnvx.tradery.fabric.compat.commoneconomy;

import com.mojang.authlib.GameProfile;
import dev.eliasnvx.tradery.api.Currency;
import dev.eliasnvx.tradery.economy.EconomyService;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Tradery as a Common Economy API provider: server mods written against that API (Universal Shops and others) read
 * and change Tradery balances. One currency, one account per player ({@code main}).
 */
public final class TraderyEconomyProvider implements EconomyProvider {
    public static final String ID = "tradery";
    static final String MAIN_ACCOUNT = "main";
    private static final TraderyEconomyProvider INSTANCE = new TraderyEconomyProvider();
    private final TraderyEconomyCurrency currency = new TraderyEconomyCurrency(this);

    private TraderyEconomyProvider() {
    }

    public static void register() {
        CommonEconomy.register(ID, INSTANCE);
    }

    @Override
    public Component name() {
        return Component.literal("Tradery");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(dev.eliasnvx.tradery.registry.TraderyItems.coin(dev.eliasnvx.tradery.ore.CoinTier.GOLD));
    }

    @Override
    public @Nullable EconomyAccount getAccount(MinecraftServer server, GameProfile profile, String accountId) {
        if (!MAIN_ACCOUNT.equals(accountId) || !EconomyService.INSTANCE.isReady()) {
            return null;
        }
        return new TraderyEconomyAccount(this, currency, profile.id(), profile.name());
    }

    @Override
    public Collection<EconomyAccount> getAccounts(MinecraftServer server, GameProfile profile) {
        EconomyAccount account = getAccount(server, profile, MAIN_ACCOUNT);
        return account == null ? List.of() : List.of(account);
    }

    @Override
    public @Nullable EconomyCurrency getCurrency(MinecraftServer server, String currencyId) {
        Currency current = EconomyService.INSTANCE.defaultCurrency();
        return current.id().toString().equals(currencyId) || current.id().getPath().equals(currencyId) ? currency : null;
    }

    @Override
    public Collection<EconomyCurrency> getCurrencies(MinecraftServer server) {
        return List.of(currency);
    }

    @Override
    public @Nullable String defaultAccount(MinecraftServer server, GameProfile profile, EconomyCurrency currency) {
        return currency == this.currency ? MAIN_ACCOUNT : null;
    }
}
