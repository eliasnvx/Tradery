package dev.eliasnvx.tradery.neoforge;

import com.mojang.authlib.GameProfile;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.gametest.TraderyGameTests;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Registers the shared {@link TraderyGameTests} on NeoForge, only when GameTests are enabled. The tests use the
 * {@code tradery:empty} structure, so {@code neoforge.enabledGameTestNamespaces=tradery} keeps them. Names are
 * {@code tradery.<entry name>}.
 */
public final class TraderyGameTestsNeoForge {
    private static final int MAX_TICKS = 200;

    /** The vanilla registry instantiates the class of a generator method. */
    public TraderyGameTestsNeoForge() {
    }

    static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        modBus.addListener((RegisterGameTestsEvent event) -> event.register(TraderyGameTestsNeoForge.class));
    }

    @GameTestGenerator
    public Collection<TestFunction> sharedTests() {
        String structure = Tradery.id("empty").toString();
        List<TestFunction> tests = new ArrayList<>();
        for (TraderyGameTests.Entry entry : TraderyGameTests.all()) {
            tests.add(new TestFunction(Tradery.MOD_ID, Tradery.MOD_ID + "." + entry.name(), structure, MAX_TICKS, 0, true, entry.body()));
        }
        tests.add(new TestFunction(Tradery.MOD_ID, Tradery.MOD_ID + ".fake_player_trades", structure, MAX_TICKS, 0, true,
            TraderyGameTestsNeoForge::fakePlayerTrades));
        return tests;
    }

    /**
     * A NeoForge fake player (Create's deployer and the like) joins the economy and buys: on 21.1 its connection has no
     * channel, so sending it a payload or opening a menu with data threw. Tradery sends fake players nothing.
     */
    private static void fakePlayerTrades(GameTestHelper helper) {
        FakePlayer buyer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TraderyFake"));
        EconomyService.INSTANCE.onPlayerJoin(buyer);
        EconomyService.INSTANCE.deposit(EconomyService.INSTANCE.account(buyer.getUUID()), 1_000, Reason.of(Reasons.ADMIN_GIVE, "test"));

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) helper.getBlockEntity(pos);
        vendor.setOwner(AccountId.player(UUID.randomUUID()), "Owner");
        vendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.STATIC));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();

        long before = EconomyService.INSTANCE.account(buyer.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
        VendingTrades.Outcome outcome = VendingTrades.execute(buyer, vendor, 2);
        helper.assertValueEqual(outcome.trades(), 2, "two trades: " + outcome.message().getString());
        helper.assertValueEqual(buyer.getInventory().countItem(Items.BREAD), 8, "the fake player got 8 bread");
        helper.assertValueEqual(EconomyService.INSTANCE.account(buyer.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency()),
            before - 500, "the fake player paid 5.00");
        // A plain click opens the buyer screen for real players; a fake player gets no menu and no crash
        BlockPos absolute = helper.absolutePos(pos);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), buyer,
            new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "no menu for the fake player");
        buyer.getInventory().clearContent();
        helper.succeed();
    }
}
