package dev.eliasnvx.tradery;

import dev.eliasnvx.tradery.api.TraderyApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The constant addons read at runtime must match the published artifact's version. */
class ApiVersionTest {
    @Test
    void constantMatchesGradle() {
        assertEquals(System.getProperty("tradery.apiVersion"), TraderyApi.API_VERSION, "TraderyApi.API_VERSION vs api_version in gradle.properties");
    }
}
