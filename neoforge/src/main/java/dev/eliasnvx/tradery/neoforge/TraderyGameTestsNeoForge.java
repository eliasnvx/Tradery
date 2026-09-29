package dev.eliasnvx.tradery.neoforge;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Registers the shared {@link TraderyGameTests} on NeoForge, only when GameTests are enabled. */
final class TraderyGameTestsNeoForge {
    private static final int MAX_TICKS = 200;

    private TraderyGameTestsNeoForge() {
    }

    static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, Tradery.MOD_ID);
        List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> holders = new ArrayList<>();
        for (TraderyGameTests.Entry entry : TraderyGameTests.all()) {
            holders.add(functions.register(entry.name(), entry::body));
        }
        functions.register(modBus);

        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Tradery.id("default"));
            for (var holder : holders) {
                event.registerTest(holder.getId(), new FunctionGameTestInstance(holder.getKey(),
                    new TestData<>(environment, Tradery.id("empty"), MAX_TICKS, 0, true)));
            }
        });
    }
}
