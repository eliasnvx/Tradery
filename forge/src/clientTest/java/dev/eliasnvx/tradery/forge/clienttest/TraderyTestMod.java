package dev.eliasnvx.tradery.forge.clienttest;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The dev-only test mod (Forge 47 has one mod class per mod id and no per-side mod classes): the client check on the
 * physical client ({@code runClientTest}), the crash test on a dedicated server ({@code runCrashTest}).
 */
@Mod(TraderyTestMod.MOD_ID)
public final class TraderyTestMod {
    static final String MOD_ID = "tradery_client_test";

    public TraderyTestMod() {
        if (FMLEnvironment.dist.isClient()) {
            new TraderyClientTest();
        } else {
            new TraderyCrashTest();
        }
    }
}
