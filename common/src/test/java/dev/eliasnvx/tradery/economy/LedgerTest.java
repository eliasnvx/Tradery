package dev.eliasnvx.tradery.economy;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.FailReason;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {
    private static final ResourceLocation COIN = new ResourceLocation("tradery", "coin");
    private Ledger ledger;
    private LedgerAccount alice;
    private LedgerAccount bob;
    private LedgerAccount server;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        alice = ledger.create(AccountId.player(UUID.randomUUID()), "Alice", false);
        bob = ledger.create(AccountId.player(UUID.randomUUID()), "Bob", false);
        server = ledger.create(AccountId.system(new ResourceLocation("tradery", "server")), "", true);
        assertMoved(ledger.move(null, alice, COIN, 1_000, 0, 0));
    }

    private static Ledger.Moved assertMoved(Ledger.Outcome outcome) {
        return assertInstanceOf(Ledger.Moved.class, outcome);
    }

    private static void assertRefused(FailReason reason, Ledger.Outcome outcome) {
        assertEquals(new Ledger.Refused(reason), outcome);
    }

    @Test
    void transferMovesBothSidesAndKeepsTotal() {
        Ledger.Moved moved = assertMoved(ledger.move(alice, bob, COIN, 300, 0, 0));
        assertEquals(new Ledger.Moved(1_000, 700, 0, 300), moved);
        assertEquals(1_000, ledger.total(COIN));
    }

    @Test
    void feeIsDestroyed() {
        assertMoved(ledger.move(alice, bob, COIN, 100, 2, 0));
        assertEquals(900, alice.balance(COIN));
        assertEquals(98, bob.balance(COIN));
        assertEquals(998, ledger.total(COIN));
    }

    @Test
    void insufficientFundsChangesNothing() {
        assertRefused(FailReason.INSUFFICIENT_FUNDS, ledger.move(alice, bob, COIN, 1_001, 0, 0));
        assertEquals(1_000, alice.balance(COIN));
        assertEquals(0, bob.balance(COIN));
    }

    @Test
    void invalidAmounts() {
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(alice, bob, COIN, 0, 0, 0));
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(alice, bob, COIN, -5, 0, 0));
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(alice, bob, COIN, 10, 11, 0));
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(alice, bob, COIN, 10, -1, 0));
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(alice, alice, COIN, 10, 0, 0));
        assertRefused(FailReason.INVALID_AMOUNT, ledger.move(null, null, COIN, 10, 0, 0));
    }

    @Test
    void overflowAndCeilingAreRefused() {
        assertMoved(ledger.move(null, bob, COIN, Long.MAX_VALUE - 10, 0, 0));
        assertRefused(FailReason.LIMIT_EXCEEDED, ledger.move(alice, bob, COIN, 11, 0, 0));
        assertEquals(1_000, alice.balance(COIN));
        assertEquals(Long.MAX_VALUE - 10, bob.balance(COIN));

        LedgerAccount carol = ledger.create(AccountId.player(UUID.randomUUID()), "Carol", false);
        assertRefused(FailReason.LIMIT_EXCEEDED, ledger.move(alice, carol, COIN, 501, 0, 500));
        assertMoved(ledger.move(alice, carol, COIN, 500, 0, 500));
    }

    @Test
    void lockedAccountsCanNeitherPayNorBePaid() {
        ledger.setLocked(bob, true);
        assertRefused(FailReason.ACCOUNT_LOCKED, ledger.move(alice, bob, COIN, 1, 0, 0));
        assertRefused(FailReason.ACCOUNT_LOCKED, ledger.move(bob, alice, COIN, 1, 0, 0));
        ledger.setLocked(bob, false);
        assertMoved(ledger.move(alice, bob, COIN, 1, 0, 0));
    }

    @Test
    void infiniteAccountCreatesAndDestroysMoney() {
        assertMoved(ledger.move(server, bob, COIN, 250, 0, 0));
        assertEquals(250, bob.balance(COIN));
        assertEquals(1_250, ledger.total(COIN));
        assertMoved(ledger.move(alice, server, COIN, 1_000, 0, 0));
        assertEquals(0, alice.balance(COIN));
        assertEquals(250, ledger.total(COIN));
        assertEquals(Long.MAX_VALUE, server.balance(COIN));
        assertTrue(server.canAfford(new SimpleCurrency(COIN, "Coins", "", 2, " "), Long.MAX_VALUE));
    }

    @Test
    void namesAreIndexedCaseInsensitively() {
        assertEquals(alice.id(), AccountId.player(ledger.playerByName("aLiCe").orElseThrow()));
        UUID bobId = ((AccountId.Player) bob.id()).uuid();
        ledger.rename(bobId, "Robert");
        assertTrue(ledger.playerByName("bob").isEmpty());
        assertEquals(bobId, ledger.playerByName("robert").orElseThrow());
    }
}
