package dev.eliasnvx.tradery.api;

import net.minecraft.resources.ResourceLocation;

/** Constants shared by the API and its implementation. */
public final class TraderyApi {
    /** Mod id and namespace of every Tradery identifier. */
    public static final String MOD_ID = "tradery";

    /**
     * Version of this API (SemVer, independent of the mod version). Breaking changes only in a major release.
     */
    public static final String API_VERSION = "1.0.0";

    private TraderyApi() {
    }

    /**
     * Builds an identifier in the {@code tradery} namespace.
     *
     * @param path the path
     * @return {@code tradery:<path>}
     */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
