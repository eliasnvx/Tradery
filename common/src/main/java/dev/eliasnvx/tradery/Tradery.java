package dev.eliasnvx.tradery;

import dev.eliasnvx.tradery.api.TraderyApi;
import dev.eliasnvx.tradery.api.internal.TraderyEconomyHolder;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Tradery {
    public static final String MOD_ID = TraderyApi.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger("Tradery");

    private Tradery() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Called once by each loader, right after the platform is installed. */
    public static void init() {
        TraderyEconomyHolder.install(EconomyService.INSTANCE);
        // Written now so server owners find the file before the first world starts
        TraderyConfig.loadServer();
        LOGGER.info("Tradery initialized on {} (API {})", dev.eliasnvx.tradery.platform.Platform.get().loaderName(), TraderyApi.API_VERSION);
    }
}
