package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.gametest.MockPlayers;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyProvider;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Acceptance: mods using the Common Economy API see and change Tradery balances (Fabric only). */
public final class CommonEconomyGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void commonEconomySeesTraderyBalances(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.create(helper);
        EconomyService eco = EconomyService.INSTANCE;
        eco.setBalance(eco.account(player.getUUID()), 1_000, Reason.of(Reasons.ADMIN_SET, "test"));

        EconomyProvider provider = CommonEconomy.getProvider("tradery");
        helper.assertTrue(provider != null, "Tradery is registered as a Common Economy provider");
        EconomyAccount account = provider.getDefaultAccount(player, provider.getCurrencies(player.level().getServer()).iterator().next());
        helper.assertTrue(account != null, "default account");
        assertValueEqual(helper, account.balance(), 1_000L, "same balance through the other API");
        helper.assertTrue(CommonEconomy.getAccounts(player).contains(account) || !CommonEconomy.getAccounts(player).isEmpty(), "listed");

        helper.assertTrue(account.increaseBalance(500).isSuccessful(), "increase");
        assertValueEqual(helper, eco.account(player.getUUID()).balance(eco.defaultCurrency()), 1_500L, "Tradery sees the increase");
        helper.assertFalse(account.decreaseBalance(10_000).isSuccessful(), "can't go negative");
        helper.assertTrue(account.canDecreaseBalance(1_500).isSuccessful(), "dry run allowed");
        assertValueEqual(helper, eco.account(player.getUUID()).balance(eco.defaultCurrency()), 1_500L, "dry run changed nothing");
        helper.assertTrue(account.decreaseBalance(1_500).isSuccessful(), "decrease");
        assertValueEqual(helper, eco.account(player.getUUID()).balance(eco.defaultCurrency()), 0L, "Tradery sees the decrease");
        assertValueEqual(helper, account.currency().formatValue(12_345, true), eco.defaultCurrency().formatPlain(12_345), "same formatting");
        helper.succeed();
    }

    /** {@code GameTestHelper#assertValueEqual} of later versions (1.20.1 has none); same message. */
    private static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String name) {
        helper.assertTrue(Objects.equals(actual, expected), "Expected " + name + " to be " + expected + ", but was " + actual);
    }
}
