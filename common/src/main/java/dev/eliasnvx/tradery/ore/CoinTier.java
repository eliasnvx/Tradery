package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.Function;

/** Coin denominations; values come from {@code ore.coinValues} in the server config. */
public enum CoinTier {
    COPPER(ServerConfig.CoinValues::copper),
    SILVER(ServerConfig.CoinValues::silver),
    GOLD(ServerConfig.CoinValues::gold);

    /** Largest first, for splitting an amount into coins. */
    static final CoinTier[] DESCENDING = {GOLD, SILVER, COPPER};

    private final Function<ServerConfig.CoinValues, BigDecimal> value;

    CoinTier(Function<ServerConfig.CoinValues, BigDecimal> value) {
        this.value = value;
    }

    /** Value of one coin in minor units of the current currency. */
    public long value() {
        return EconomyService.INSTANCE.toMinorOrMax(value.apply(TraderyConfig.server().ore().coinValues()), "ore.coinValues");
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
