package dev.eliasnvx.tradery.fabric.gametest;

import com.mojang.authlib.GameProfile;
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
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** A Fabric API fake player (machines of other mods) joins the economy, buys and clicks a vending block without a crash. */
public final class FakePlayerGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void fakePlayerTrades(GameTestHelper helper) {
        FakePlayer buyer = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TraderyFake"));
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
        assertValueEqual(helper, outcome.trades(), 2, "two trades: " + outcome.message().getString());
        assertValueEqual(helper, buyer.getInventory().countItem(Items.BREAD), 8, "the fake player got 8 bread");
        assertValueEqual(helper, EconomyService.INSTANCE.account(buyer.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency()),
            before - 500, "the fake player paid 5.00");

        // A plain click opens the buyer screen for real players; a fake player gets no menu and no crash
        BlockPos absolute = helper.absolutePos(pos);
        helper.getBlockState(pos).use(helper.getLevel(), buyer, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
        helper.assertTrue(buyer.containerMenu == buyer.inventoryMenu, "no menu for the fake player");
        buyer.getInventory().clearContent();
        helper.succeed();
    }

    /** {@code GameTestHelper#assertValueEqual} of later versions (1.20.1 has none); same message. */
    private static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String name) {
        helper.assertTrue(Objects.equals(actual, expected), "Expected " + name + " to be " + expected + ", but was " + actual);
    }
}
