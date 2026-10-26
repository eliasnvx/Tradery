package dev.eliasnvx.tradery;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Vanilla in a JUnit test: the registries; on 1.21.1 items carry their default components from the start. */
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
