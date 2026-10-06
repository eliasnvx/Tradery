package dev.eliasnvx.tradery.neoforge.clienttest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only crash test on a dedicated server (release checklist: "kill -9 the server"). Two launches of
 * {@code ./gradlew :neoforge:runCrashTest}, with a {@code kill -9} in between (see {@code tools/crash-test.sh}):
 * <ol>
 *     <li>Two fake players and a vending block; Bob buys 3 lots; the world is saved ("saved" state). Bob buys 5 more
 *     lots and gets 1000.00 ("traded" state). Then 13 s pass, so Minecraft writes the changed chunk on its own (it
 *     does so ~10 s after a change, long before the autosave); {@code crash-ready.txt} tells the script to kill it.</li>
 *     <li>The next launch must find one consistent state, all "saved" or all "traded": the stock was never written
 *     without the money it was traded for. The money supply must equal the sum of balances. Result in
 *     {@code crash-result.txt}.</li>
 * </ol>
 */
@Mod(value = TraderyClientTest.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class TraderyCrashTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");
    private static final Path EXPECTED = Path.of("crash-expected.txt");
    private static final Path READY = Path.of("crash-ready.txt");
    private static final Path RESULT = Path.of("crash-result.txt");

    public TraderyCrashTest() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            MinecraftServer server = event.getServer();
            try {
                if (Files.exists(EXPECTED)) {
                    verify(server);
                } else {
                    prepare(server);
                }
            } catch (Exception e) {
                LOGGER.error("Tradery crash test failed", e);
                write(RESULT, "FAIL: " + e);
                server.halt(false);
            }
        });
    }

    /** First launch: a saved state, then unsaved changes, then wait for kill -9. */
    private static void prepare(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos pos = new BlockPos(0, level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0), 0);
        level.setChunkForced(0, 0, true);
        FakePlayer alice = FakePlayerFactory.get(level, new GameProfile(ALICE, "Alice"));
        FakePlayer bob = FakePlayerFactory.get(level, new GameProfile(BOB, "Bob"));
        EconomyService.INSTANCE.onPlayerJoin(alice);
        EconomyService.INSTANCE.onPlayerJoin(bob);

        level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) level.getBlockEntity(pos);
        vendor.setOwner(AccountId.player(ALICE), "Alice");
        vendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.STATIC));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
        check(VendingTrades.execute(bob, vendor, 3).trades() == 3, "3 lots before the save");

        String saved = snapshot(server, pos);
        check(server.saveEverything(true, true, true), "saved");
        LOGGER.info("Tradery crash test: saved state {}", saved);

        check(VendingTrades.execute(bob, vendor, 5).trades() == 5, "5 lots after the save");
        EconomyService.INSTANCE.deposit(EconomyService.INSTANCE.account(BOB), 100_000, Reason.of(Reasons.ADMIN_GIVE, "crash test"));
        String traded = snapshot(server, pos);
        LOGGER.info("Tradery crash test: traded state {}", traded);
        write(EXPECTED, saved + "\n---\n" + traded);
        int[] ticks = {0};
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (++ticks[0] == 260) {
                write(READY, String.valueOf(ProcessHandle.current().pid()));
            }
        });
    }

    /** Second launch: one consistent state, all "saved" or all "traded". */
    private static void verify(MinecraftServer server) throws Exception {
        String[] states = Files.readString(EXPECTED).split("\n---\n");
        String saved = states[0].strip();
        String traded = states[1].strip();
        BlockPos pos = BlockPos.of(Long.parseLong(saved.lines().findFirst().orElseThrow().split("=")[1]));
        server.overworld().setChunkForced(0, 0, true);
        String actual = snapshot(server, pos);
        List<String> problems = new ArrayList<>();
        String which = actual.equals(traded) ? "all traded (the chunk and the money were written together)"
            : actual.equals(saved) ? "all saved (nothing written after the save)" : null;
        if (which == null) {
            problems.add("mixed state\nsaved:\n" + saved + "\ntraded:\n" + traded);
        }
        long supply = EconomyService.INSTANCE.ledger().total(EconomyService.INSTANCE.defaultCurrency().id());
        long sum = EconomyService.INSTANCE.ledger().all().stream()
            .filter(account -> !account.isInfinite())
            .mapToLong(account -> account.balance(EconomyService.INSTANCE.defaultCurrency()))
            .sum();
        if (supply != sum) {
            problems.add("money supply " + supply + " != sum of balances " + sum);
        }
        String report = (problems.isEmpty() ? "PASS" : "FAIL") + ": state after kill -9 and restart: " + which + "\n"
            + actual + "\n" + String.join("\n", problems) + "\n";
        LOGGER.info("Tradery crash test {}", report);
        write(RESULT, report);
        Files.delete(EXPECTED);
        server.halt(false);
    }

    private static String snapshot(MinecraftServer server, BlockPos pos) {
        var economy = EconomyService.INSTANCE;
        var currency = economy.defaultCurrency();
        int stock = 0;
        if (server.overworld().getBlockEntity(pos) instanceof VendingBlockEntity vendor) {
            for (ItemStack stack : vendor.stock().getItems()) {
                stock += stack.is(Items.BREAD) ? stack.getCount() : 0;
            }
        }
        return "pos=" + pos.asLong()
            + "\nalice=" + economy.account(ALICE).balance(currency)
            + "\nbob=" + economy.account(BOB).balance(currency)
            + "\nsupply=" + economy.ledger().total(currency.id())
            + "\nstock=" + stock;
    }

    private static void write(Path path, String text) {
        try {
            Files.writeString(path, text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new IllegalStateException(what);
        }
    }
}
