package dev.eliasnvx.tradery.fabric.compat.commoneconomy;

import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.SimpleCurrency;
import dev.eliasnvx.tradery.util.Money;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalLong;

/** Tradery's currency for the Common Economy API. Values are minor units, as in Tradery. */
final class TraderyEconomyCurrency implements EconomyCurrency {
    private final EconomyProvider provider;

    TraderyEconomyCurrency(EconomyProvider provider) {
        this.provider = provider;
    }

    private static SimpleCurrency currency() {
        return EconomyService.INSTANCE.defaultCurrency();
    }

    @Override
    public Component name() {
        return currency().name();
    }

    @Override
    public ResourceLocation id() {
        return currency().id();
    }

    @Override
    public String formatValue(long value, boolean precise) {
        long amount = TraderyEconomyAccount.clamp(value);
        return precise ? currency().formatPlain(amount) : currency().formatShort(amount);
    }

    @Override
    public long parseValue(String text) throws NumberFormatException {
        OptionalLong parsed = Money.parse(text.trim(), currency().decimals());
        if (parsed.isEmpty()) {
            throw new NumberFormatException("Not a " + currency().id() + " amount: " + text);
        }
        return parsed.getAsLong();
    }

    @Override
    public EconomyProvider provider() {
        return provider;
    }

    @Override
    public ItemStack icon() {
        return provider.icon();
    }
}
