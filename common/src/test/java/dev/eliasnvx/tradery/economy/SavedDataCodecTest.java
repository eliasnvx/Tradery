package dev.eliasnvx.tradery.economy;

import dev.eliasnvx.tradery.api.AccountId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a server restart does to our data: encode to NBT, decode, compare. */
class SavedDataCodecTest {
    private static final ResourceLocation COIN = new ResourceLocation("tradery", "coin");

    @Test
    void accountsSurviveARestart() {
        AccountsData data = new AccountsData();
        data.setDecimals(2);
        Ledger ledger = data.ledger();
        UUID aliceId = UUID.randomUUID();
        LedgerAccount alice = ledger.create(AccountId.player(aliceId), "Alice", false);
        LedgerAccount bank = ledger.create(AccountId.system(new ResourceLocation("mymod", "bank")), "", false);
        ledger.create(AccountId.system(new ResourceLocation("tradery", "server")), "Server", true);
        ledger.move(null, alice, COIN, 123_456, 0, 0);
        ledger.move(alice, bank, COIN, 456, 0, 0);
        ledger.setLocked(bank, true);

        Tag tag = AccountsData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow(false, error -> { });
        AccountsData loaded = AccountsData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(false, error -> { });

        assertEquals(2, loaded.decimals());
        LedgerAccount loadedAlice = loaded.ledger().get(AccountId.player(aliceId)).orElseThrow();
        assertEquals(123_000, loadedAlice.balance(COIN));
        assertEquals("Alice", loadedAlice.name());
        assertEquals(aliceId, loaded.ledger().playerByName("alice").orElseThrow());
        LedgerAccount loadedBank = loaded.ledger().get(bank.id()).orElseThrow();
        assertEquals(456, loadedBank.balance(COIN));
        assertTrue(loadedBank.isLocked());
        assertTrue(loaded.ledger().get(AccountId.system(new ResourceLocation("tradery", "server"))).orElseThrow().isInfinite());
        assertEquals(123_456, loaded.ledger().total(COIN), "supply rebuilt from balances");
    }

    /** The path the world save takes: {@code save} into the file's "data" tag, then the factory's deserializer. */
    @Test
    void savedDataFilesRoundTrip() {
        AccountsData data = new AccountsData();
        data.setDecimals(3);
        UUID bobId = UUID.randomUUID();
        LedgerAccount bob = data.ledger().create(AccountId.player(bobId), "Bob", false);
        data.ledger().move(null, bob, COIN, 7_000, 0, 0);

        CompoundTag saved = data.save(new CompoundTag());
        AccountsData loaded = AccountsData.FACTORY.deserializer().apply(saved);
        assertEquals(3, loaded.decimals());
        assertEquals(7_000, loaded.ledger().get(AccountId.player(bobId)).orElseThrow().balance(COIN));

        HistoryData history = new HistoryData();
        history.add(bobId, new HistoryData.Entry(1, 5, 5, COIN, ""), 10);
        HistoryData loadedHistory = HistoryData.FACTORY.deserializer().apply(history.save(new CompoundTag()));
        assertEquals(1, loadedHistory.get(bobId).size());

        StatsData stats = new StatsData();
        stats.recordCashOut(250);
        StatsData loadedStats = StatsData.FACTORY.deserializer().apply(stats.save(new CompoundTag()));
        assertEquals(250, loadedStats.cashOutstanding());

        AccountsData fresh = AccountsData.FACTORY.deserializer().apply(new CompoundTag());
        assertEquals(AccountsData.UNSET, fresh.decimals(), "an empty tag is a fresh world");
    }

    @Test
    void historyAndStatsSurviveARestart() {
        HistoryData history = new HistoryData();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            history.add(player, new HistoryData.Entry(i, i * 10L, i * 100L, COIN, "x" + i), 3);
        }
        HistoryData loadedHistory = HistoryData.CODEC.parse(NbtOps.INSTANCE,
            HistoryData.CODEC.encodeStart(NbtOps.INSTANCE, history).getOrThrow(false, error -> { })).getOrThrow(false, error -> { });
        assertEquals(3, loadedHistory.get(player).size(), "capped at the limit");
        assertEquals(4, loadedHistory.get(player).get(0).time(), "newest first");

        StatsData stats = new StatsData();
        stats.recordEmitted(500);
        stats.recordBurned(20);
        stats.recordCashOut(300);
        stats.recordCashIn(100);
        stats.addEarned(player, StatsData.Earning.ORE, 42);
        StatsData loadedStats = StatsData.CODEC.parse(NbtOps.INSTANCE,
            StatsData.CODEC.encodeStart(NbtOps.INSTANCE, stats).getOrThrow(false, error -> { })).getOrThrow(false, error -> { });
        StatsData.Day today = loadedStats.day(StatsData.today());
        assertEquals(500, today.emitted());
        assertEquals(20, today.burned());
        assertEquals(200, loadedStats.cashOutstanding());
        assertEquals(42, loadedStats.earnedToday(player, StatsData.Earning.ORE));
    }
}
