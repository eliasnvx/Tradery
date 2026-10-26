package dev.eliasnvx.tradery;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Vanilla in a JUnit test: the registries (1.20.1 items need nothing else). */
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
        done = true;
    }
}
