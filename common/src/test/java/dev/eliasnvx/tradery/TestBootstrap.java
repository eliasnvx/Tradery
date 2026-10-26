package dev.eliasnvx.tradery;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

/**
 * Vanilla in a JUnit test: the registries, and the items' default components, which 26.3 binds only when a server
 * loads its data ({@code ReloadableServerResources}); without them no {@code ItemStack} can be made.
 */
public final class TestBootstrap {
    private static boolean done;

    private TestBootstrap() {
    }

    public static synchronized void bootstrap() {
        if (done) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
            .forEach(DataComponentInitializers.PendingComponents::apply);
        done = true;
    }
}
