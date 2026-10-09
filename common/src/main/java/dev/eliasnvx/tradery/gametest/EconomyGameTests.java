package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.TraderyEconomy;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.api.event.BalanceChangedEvent;
import dev.eliasnvx.tradery.api.event.TransactionEvent;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The economy on a real server: transactions, events, the thread contract and /pay. */
public final class EconomyGameTests {
    static final ResourceLocation TAXED = new ResourceLocation("tradery_test", "taxed");
    static final ResourceLocation BLOCKED = new ResourceLocation("tradery_test", "blocked");
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static final AtomicInteger BALANCE_EVENTS = new AtomicInteger();

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("economy_transfer_is_atomic", EconomyGameTests::transferIsAtomic),
        new TraderyGameTests.Entry("economy_pre_event_taxes_and_cancels", EconomyGameTests::preEventTaxesAndCancels),
        new TraderyGameTests.Entry("economy_requires_server_thread", EconomyGameTests::requiresServerThread),
        new TraderyGameTests.Entry("economy_pay_command", EconomyGameTests::payCommand));

    private EconomyGameTests() {
    }

    /** Test listeners act only on the test reason types, so they never touch other tests or real play. */
    private static void listenOnce() {
        if (!LISTENING.compareAndSet(false, true)) {
            return;
        }
        TransactionEvent.Pre.EVENT.register(event -> {
            if (event.reason().type().equals(TAXED)) {
                event.setFee(event.amount() / 10);
            } else if (event.reason().type().equals(BLOCKED)) {
                event.cancel(Component.literal("blocked by test"));
            }
        });
        BalanceChangedEvent.EVENT.register(event -> {
            if (event.reason().type().equals(TAXED)) {
                BALANCE_EVENTS.incrementAndGet();
            }
        });
    }

    /**
     * {@code GameTestHelper#assertValueEqual} of later versions (1.20.1 has none); same message. Both values must be
     * of the same boxed type ({@code 5L} vs {@code 5} differ).
     */
    static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String name) {
        helper.assertTrue(Objects.equals(actual, expected), "Expected " + name + " to be " + expected + ", but was " + actual);
    }

    private static LedgerAccount freshAccount(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.create(helper);
        return EconomyService.INSTANCE.account(player.getUUID());
    }

    public static void transferIsAtomic(GameTestHelper helper) {
        EconomyService eco = EconomyService.INSTANCE;
        LedgerAccount alice = freshAccount(helper);
        LedgerAccount bob = freshAccount(helper);
        long aliceStart = alice.balance(eco.defaultCurrency());
        long bobStart = bob.balance(eco.defaultCurrency());
        long supplyStart = eco.ledger().total(eco.defaultCurrency().id());

        helper.assertTrue(eco.deposit(alice, 5_000, Reason.of(TAXED.withPath("deposit"))).isSuccess(), "deposit");
        TransactionResult result = eco.transfer(alice, bob, 1_234, 34, Reason.of(TAXED.withPath("plain")));
        helper.assertTrue(result instanceof TransactionResult.Success, "transfer succeeds: " + result);
        assertValueEqual(helper, alice.balance(eco.defaultCurrency()), aliceStart + 5_000 - 1_234, "payer balance");
        assertValueEqual(helper, bob.balance(eco.defaultCurrency()), bobStart + 1_200, "payee gets amount minus fee");
        assertValueEqual(helper, eco.ledger().total(eco.defaultCurrency().id()), supplyStart + 5_000 - 34, "fee destroyed");

        long bobNow = bob.balance(eco.defaultCurrency());
        long aliceNow = alice.balance(eco.defaultCurrency());
        TransactionResult tooMuch = eco.transfer(bob, alice, bobNow + 1, Reason.of(TAXED.withPath("plain")));
        assertValueEqual(helper, tooMuch, TransactionResult.Failure.of(FailReason.INSUFFICIENT_FUNDS), "overdraft refused");
        assertValueEqual(helper, bob.balance(eco.defaultCurrency()), bobNow, "payer untouched after refusal");
        assertValueEqual(helper, alice.balance(eco.defaultCurrency()), aliceNow, "payee untouched after refusal");

        assertValueEqual(helper, eco.transfer(alice, alice, 1, Reason.of(TAXED)), TransactionResult.Failure.of(FailReason.INVALID_AMOUNT), "self transfer");
        assertValueEqual(helper, eco.withdraw(alice, 0, Reason.of(TAXED)), TransactionResult.Failure.of(FailReason.INVALID_AMOUNT), "zero amount");

        eco.setLocked(bob, true);
        assertValueEqual(helper, eco.transfer(alice, bob, 1, Reason.of(TAXED.withPath("plain"))),
            TransactionResult.Failure.of(FailReason.ACCOUNT_LOCKED), "locked payee");
        eco.setLocked(bob, false);
        helper.succeed();
    }

    public static void preEventTaxesAndCancels(GameTestHelper helper) {
        listenOnce();
        EconomyService eco = EconomyService.INSTANCE;
        LedgerAccount payer = freshAccount(helper);
        LedgerAccount payee = freshAccount(helper);
        eco.deposit(payer, 10_000, Reason.of(TAXED.withPath("deposit")));
        long payerStart = payer.balance(eco.defaultCurrency());
        long payeeStart = payee.balance(eco.defaultCurrency());
        int eventsBefore = BALANCE_EVENTS.get();

        TransactionResult taxed = eco.transfer(payer, payee, 1_000, Reason.of(TAXED));
        helper.assertTrue(taxed instanceof TransactionResult.Success success && success.fee() == 100, "listener set a 10% fee: " + taxed);
        assertValueEqual(helper, payee.balance(eco.defaultCurrency()), payeeStart + 900, "payee got 90%");
        assertValueEqual(helper, BALANCE_EVENTS.get() - eventsBefore, 2, "one BalanceChangedEvent per side");

        TransactionResult blocked = eco.transfer(payer, payee, 1_000, Reason.of(BLOCKED));
        helper.assertTrue(blocked instanceof TransactionResult.Failure failure && failure.reason() == FailReason.CANCELLED
            && failure.message() != null, "listener cancelled with a message: " + blocked);
        assertValueEqual(helper, payer.balance(eco.defaultCurrency()), payerStart - 1_000, "cancelled transfer took nothing");
        helper.succeed();
    }

    public static void requiresServerThread(GameTestHelper helper) {
        UUID someone = UUID.randomUUID();
        TraderyEconomy eco = TraderyEconomy.get();
        helper.assertTrue(eco.isReady(), "ready on the server thread");
        try {
            CompletableFuture.supplyAsync(() -> eco.account(someone)).join();
            helper.fail("account() from another thread must throw");
        } catch (CompletionException e) {
            helper.assertTrue(e.getCause() instanceof IllegalStateException, "IllegalStateException, got " + e.getCause());
        }
        helper.assertFalse(CompletableFuture.supplyAsync(eco::isReady).join(), "not ready off-thread");
        helper.succeed();
    }

    public static void payCommand(GameTestHelper helper) {
        EconomyService eco = EconomyService.INSTANCE;
        ServerPlayer sender = MockPlayers.create(helper);
        LedgerAccount from = eco.account(sender.getUUID());
        UUID receiverId = UUID.randomUUID();
        LedgerAccount to = eco.account(receiverId);
        String receiverName = "pay_" + receiverId.toString().substring(0, 8);
        eco.ledger().rename(receiverId, receiverName);
        eco.deposit(from, 5_000, Reason.of(TAXED.withPath("deposit")));
        long fromStart = from.balance(eco.defaultCurrency());
        long toStart = to.balance(eco.defaultCurrency());

        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(sender.createCommandSourceStack(), "pay " + receiverName + " 12.50");
        assertValueEqual(helper, from.balance(eco.defaultCurrency()), fromStart - 1_250, "sender paid 12.50");
        assertValueEqual(helper, to.balance(eco.defaultCurrency()), toStart + 1_250, "receiver got 12.50 (no tax by default)");

        commands.performPrefixedCommand(sender.createCommandSourceStack(), "pay " + receiverName + " 12.505");
        commands.performPrefixedCommand(sender.createCommandSourceStack(), "pay " + receiverName + " -5");
        commands.performPrefixedCommand(sender.createCommandSourceStack(), "pay " + receiverName + " 999999999");
        assertValueEqual(helper, from.balance(eco.defaultCurrency()), fromStart - 1_250, "bad amounts and overdraft change nothing");
        helper.succeed();
    }
}
