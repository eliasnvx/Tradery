package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.GhostSlots;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingMenus;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageEconomy;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.VillagerTrading;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * What a hostile or unlucky client can't do: park kilobytes in a sample, hammer the save button, lose a village's
 * money to a clock jump, or make a villager pay more than it has.
 */
public final class RobustnessGameTests {
    private static final BlockPos VENDOR = new BlockPos(2, 1, 2);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("robust_sample_size_capped", RobustnessGameTests::sampleSizeCapped),
        new TraderyGameTests.Entry("robust_save_rate_limited", RobustnessGameTests::saveRateLimited),
        new TraderyGameTests.Entry("robust_villages_with_money_survive_pruning", RobustnessGameTests::villagesWithMoneySurvivePruning),
        new TraderyGameTests.Entry("robust_villager_wallet_covers_tax", RobustnessGameTests::villagerWalletCoversTax));

    private RobustnessGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** A plain sample, an enchanted one and a sample with a tag go through; kilobytes of custom data don't. */
    public static void sampleSizeCapped(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(GhostSlots.acceptableSample(new ItemStack(Items.BREAD), registries), "plain");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(sword).size() == 1, "enchanted");
        helper.assertTrue(GhostSlots.acceptableSample(sword, registries), "an enchanted sample is fine (registry data encodes)");
        ItemStack small = new ItemStack(Items.BREAD);
        CompoundTag tag = new CompoundTag();
        tag.putString("note", "x".repeat(200));
        small.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        helper.assertTrue(GhostSlots.acceptableSample(small, registries), "a little custom data is fine");
        ItemStack big = new ItemStack(Items.BREAD);
        CompoundTag bigTag = new CompoundTag();
        bigTag.putString("payload", "x".repeat(20_000));
        big.set(DataComponents.CUSTOM_DATA, CustomData.of(bigTag));
        helper.assertFalse(GhostSlots.acceptableSample(big, registries), "20 KB of custom data is refused");
        helper.succeed();
    }

    /** The owner's Save shares the trade rate limit: ten in a second, the rest refused and nothing re-sent. */
    public static void saveRateLimited(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(VENDOR, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = helper.getBlockEntity(VENDOR);
        @SuppressWarnings("removal")
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(VENDOR.north()));
        owner.setPos(at.x, at.y, at.z);
        vendor.setOwner(AccountId.player(owner.getUUID()), "owner");
        vendor.setSettings(new VendingSettings(new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 100, ItemStack.EMPTY, false,
            DisplayAnimation.STATIC));
        VendingTrades.forget(owner);
        VendingMenus.openOwner(owner, vendor, false);
        VendingOwnerMenu menu = (VendingOwnerMenu) owner.containerMenu;
        for (int i = 1; i <= VendingTrades.RATE_LIMIT + 3; i++) {
            VendingConfigurator.save(owner, menu, 100 + i);
        }
        helper.assertValueEqual(vendor.settings().price(), 100L + VendingTrades.RATE_LIMIT, "saves beyond the limit were refused");
        owner.closeContainer();
        VendingTrades.forget(owner);
        helper.succeed();
    }

    /** Pruning forgets empty villages only: a treasury, a project, an order or a licence keeps a village through any clock jump. */
    public static void villagesWithMoneySurvivePruning(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        long today = VillagerPurchases.today(server);
        VillageState rich = Villages.at(server, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
        VillageState empty = Villages.at(server, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 1, 4)));
        Villages.set(server, rich, 0, 1234);
        Villages.set(server, empty, 0, 0);
        VillageEconomy economy = VillageEconomy.get(server);
        economy.prune(today + VillageEconomy.FORGET_AFTER_DAYS + 1000);
        helper.assertTrue(economy.get(rich.key()) != null, "a village with money in its treasury stays");
        helper.assertTrue(economy.get(empty.key()) == null, "an empty village long unseen is forgotten");
        helper.succeed();
    }

    /** A villager paying money (an emerald result) out of a payment it also receives: the tax counts, the wallet never goes below zero. */
    public static void villagerWalletCoversTax(GameTestHelper helper) {
        floor(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0))));
        // One emerald in, two out: the villager keeps the payment minus the village's tax and must still cover the payout
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 1), Optional.empty(), new ItemStack(Items.EMERALD, 2), 0, 16, 1, 0.05f);
        villager.getOffers().clear();
        villager.getOffers().add(offer);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 2)));
        player.setPos(at.x, at.y, at.z);
        villager.setTradingPlayer(player);
        long emerald = VillagerTrading.emeraldValue();
        long tax = Money.percentOf(emerald, TraderyConfig.server().villages().tradeTaxPercent());
        helper.assertTrue(tax > 0, "the default trade tax is not zero");
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), 10 * emerald, Reason.of(Reasons.ADMIN_SET, "test"));
        long today = VillagerPurchases.today(server);
        VillagerLedger ledger = VillagerLedger.get(server);

        ledger.setWallet(villager.getUUID(), emerald, today);
        VillagerTrading.Outcome refused = VillagerTrading.trade(player, villager, 0, false);
        helper.assertFalse(refused.success(), "payout minus the kept payment exceeds the wallet: refused");
        helper.assertValueEqual(VillagerPurchases.wallet(villager, today), emerald, "wallet untouched");

        ledger.setWallet(villager.getUUID(), emerald + tax, today);
        VillagerTrading.Outcome done = VillagerTrading.trade(player, villager, 0, false);
        helper.assertTrue(done.success(), "with the tax covered it trades: " + done.message().getString());
        helper.assertValueEqual(VillagerPurchases.wallet(villager, today), 0L, "the wallet ends at exactly zero, not clamped");
        villager.discard();
        helper.succeed();
    }
}
