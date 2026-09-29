package dev.eliasnvx.tradery.api.internal;

import dev.eliasnvx.tradery.api.TraderyEconomy;
import org.jetbrains.annotations.ApiStatus;

/** Holds the implementation. Tradery installs it once at startup; addons never touch this class. */
@ApiStatus.Internal
public final class TraderyEconomyHolder {
    private static volatile TraderyEconomy instance;

    private TraderyEconomyHolder() {
    }

    public static TraderyEconomy get() {
        TraderyEconomy economy = instance;
        if (economy == null) {
            throw new IllegalStateException("Tradery is not loaded yet");
        }
        return economy;
    }

    public static boolean isInstalled() {
        return instance != null;
    }

    public static void install(TraderyEconomy economy) {
        if (instance != null) {
            throw new IllegalStateException("Tradery economy already installed");
        }
        instance = economy;
    }
}
