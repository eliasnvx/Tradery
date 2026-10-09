package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fabric glue for the shared {@link TraderyGameTests}: one test per entry, generated, so a test added to the shared
 * list can't be forgotten here. Names are {@code tradery.<entry name>}.
 */
public final class TraderyGameTestsFabric {
    private static final int MAX_TICKS = 200;

    /** The vanilla registry instantiates the class of a generator method. */
    public TraderyGameTestsFabric() {
    }

    @GameTestGenerator
    public Collection<TestFunction> sharedTests() {
        List<TestFunction> tests = new ArrayList<>();
        for (TraderyGameTests.Entry entry : TraderyGameTests.all()) {
            tests.add(new TestFunction(Tradery.MOD_ID, Tradery.MOD_ID + "." + entry.name(), FabricGameTest.EMPTY_STRUCTURE, MAX_TICKS, 0,
                true, entry.body()));
        }
        return tests;
    }
}
