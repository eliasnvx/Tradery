package dev.eliasnvx.tradery.gametest;

import com.mojang.authlib.GameProfile;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.RewardsConfig;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.ore.CoinWithdraw;
import dev.eliasnvx.tradery.registry.TraderyItems;
import dev.eliasnvx.tradery.rewards.Rewards;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coin ore, coins, withdraw/deposit and rewards on a real server. */
public final class SourcesGameTests {
    private static final BlockPos ORE = new BlockPos(2, 1, 2);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("ore_pays_the_miner", SourcesGameTests::orePaysTheMiner),
        new TraderyGameTests.Entry("ore_drops_coins_without_a_player", SourcesGameTests::oreDropsCoinsWithoutAPlayer),
        new TraderyGameTests.Entry("ore_daily_cap", SourcesGameTests::oreDailyCap),
        new TraderyGameTests.Entry("coin_pickup_with_full_inventory", SourcesGameTests::coinPickupWithFullInventory),
        new TraderyGameTests.Entry("coins_withdraw_and_use", SourcesGameTests::coinsWithdrawAndUse),
        new TraderyGameTests.Entry("reward_kill_skips_spawner_mobs", SourcesGameTests::rewardKillSkipsSpawnerMobs),
        new TraderyGameTests.Entry("reward_mine_skips_placed_blocks", SourcesGameTests::rewardMineSkipsPlacedBlocks),
        new TraderyGameTests.Entry("ore_feature_places_ore", SourcesGameTests::oreFeaturePlacesOre),
        new TraderyGameTests.Entry("mixins_apply", SourcesGameTests::mixinsApply),
        new TraderyGameTests.Entry("coin_ore_in_overworld_biomes", SourcesGameTests::coinOreInOverworldBiomes),
        new TraderyGameTests.Entry("coin_ore_not_processable", SourcesGameTests::coinOreNotProcessable));

    private SourcesGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** Like {@code makeMockServerPlayerInLevel}, but in survival (coin ore pays only survival players). */
    static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos near) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tradery-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(near));
        player.setPos(at.x, at.y, at.z);
        player.getInventory().clearContent();
        return player;
    }

    private static long balance(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID()).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static void setBalance(ServerPlayer player, long amount) {
        EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), amount, Reason.of(Reasons.ADMIN_SET, "test"));
    }

    private static int itemsAround(GameTestHelper helper, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM, ORE, 4)) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static ServerConfig withOre(ServerConfig config, boolean direct, BigDecimal dailyCap) {
        ServerConfig.OreSection ore = config.ore();
        return new ServerConfig(config.currency(), config.economy(), config.pay(), config.vending(),
            new ServerConfig.OreSection(ore.enabled(), direct, ore.coinValues(), dailyCap, ore.inflationDamping()), config.history(), config.log());
    }

    // ------------------------------------------------------------------ tests

    public static void orePaysTheMiner(GameTestHelper helper) {
        ServerPlayer miner = survivalPlayer(helper, ORE.north());
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        setBalance(miner, 0);
        helper.setBlock(ORE, TraderyItems.ore(CoinTier.COPPER, false));
        helper.assertTrue(miner.gameMode.destroyBlock(helper.absolutePos(ORE)), "mined");
        long paid = balance(miner);
        long coin = CoinTier.COPPER.value();
        helper.assertTrue(paid >= coin && paid <= 3 * coin, "1-3 copper coins straight to the balance, got " + paid);
        helper.assertValueEqual(itemsAround(helper, TraderyItems.coin(CoinTier.COPPER)), 0, "no coin items dropped");
        helper.succeed();
    }

    public static void oreDropsCoinsWithoutAPlayer(GameTestHelper helper) {
        long cashBefore = EconomyService.INSTANCE.stats().cashOutstanding();
        helper.setBlock(ORE, TraderyItems.ore(CoinTier.SILVER, true));
        helper.getLevel().destroyBlock(helper.absolutePos(ORE), true);
        int coins = itemsAround(helper, TraderyItems.coin(CoinTier.SILVER));
        helper.assertTrue(coins >= 1 && coins <= 2, "1-2 silver coins dropped, got " + coins);
        helper.assertValueEqual(EconomyService.INSTANCE.stats().cashOutstanding() - cashBefore, coins * CoinTier.SILVER.value(),
            "minted coins counted as cash in the world");
        helper.succeed();
    }

    public static void oreDailyCap(GameTestHelper helper) {
        ServerConfig original = TraderyConfig.server();
        try {
            TraderyConfig.setServerForTests(withOre(original, true, new BigDecimal("150")));
            ServerPlayer miner = survivalPlayer(helper, ORE.north());
            miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            setBalance(miner, 0);
            for (int i = 0; i < 3; i++) {
                helper.setBlock(ORE, TraderyItems.ore(CoinTier.GOLD, false));
                miner.gameMode.destroyBlock(helper.absolutePos(ORE));
            }
            helper.assertValueEqual(balance(miner), EconomyService.INSTANCE.toMinorOrMax(new BigDecimal("150"), "test"),
                "three gold coins mined, capped at 150");
            helper.assertValueEqual(itemsAround(helper, TraderyItems.coin(CoinTier.GOLD)), 0, "the cap drops nothing either");
        } finally {
            TraderyConfig.setServerForTests(original);
        }
        helper.succeed();
    }

    public static void coinPickupWithFullInventory(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, ORE);
        setBalance(player, 0);
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            player.getInventory().getNonEquipmentItems().set(i, new ItemStack(Items.DIRT, 64));
        }
        ItemEntity coins = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(),
            new ItemStack(TraderyItems.coin(CoinTier.SILVER), 3));
        coins.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(coins);
        coins.playerTouch(player);
        helper.assertTrue(coins.isRemoved(), "coins picked up");
        helper.assertValueEqual(balance(player), 3 * CoinTier.SILVER.value(), "credited even with a full inventory");
        helper.succeed();
    }

    public static void coinsWithdrawAndUse(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, ORE);
        long gold = CoinTier.GOLD.value();
        long silver = CoinTier.SILVER.value();
        long copper = CoinTier.COPPER.value();
        setBalance(player, 2 * gold);

        helper.assertValueEqual(CoinWithdraw.withdraw(player, copper / 2 + gold), 0, "half a copper coin can't be paid out");
        helper.assertValueEqual(balance(player), 2 * gold, "nothing withdrawn");
        helper.assertValueEqual(CoinWithdraw.withdraw(player, gold + silver + copper), 1, "withdraw one of each");
        helper.assertValueEqual(balance(player), gold - silver - copper, "balance down by 111");
        helper.assertValueEqual(player.getInventory().countItem(TraderyItems.coin(CoinTier.GOLD)), 1, "one gold coin");
        helper.assertValueEqual(player.getInventory().countItem(TraderyItems.coin(CoinTier.SILVER)), 1, "one silver coin");
        helper.assertValueEqual(player.getInventory().countItem(TraderyItems.coin(CoinTier.COPPER)), 1, "one copper coin");

        // Sneak + use puts every coin back
        player.setShiftKeyDown(true);
        ItemStack held = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(TraderyItems.coin(CoinTier.GOLD))).findFirst()
            .orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
        held.setCount(0);
        TraderyItems.coin(CoinTier.GOLD).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(balance(player), 2 * gold, "all coins deposited");
        helper.assertValueEqual(player.getInventory().countItem(TraderyItems.coin(CoinTier.SILVER)), 0, "coins gone after deposit");

        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            player.getInventory().getNonEquipmentItems().set(i, new ItemStack(Items.DIRT, 64));
        }
        helper.assertValueEqual(CoinWithdraw.withdraw(player, gold), 0, "no room: refused");
        helper.assertValueEqual(balance(player), 2 * gold, "no money taken without room");
        helper.succeed();
    }

    public static void rewardKillSkipsSpawnerMobs(GameTestHelper helper) {
        ServerPlayer hunter = survivalPlayer(helper, ORE);
        setBalance(hunter, 0);
        Zombie farmed = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ORE.east());
        farmed.addTag(Rewards.SPAWNER_TAG);
        farmed.hurtServer(helper.getLevel(), hunter.damageSources().playerAttack(hunter), 1000);
        helper.assertValueEqual(balance(hunter), 0L, "a spawner zombie pays nothing");

        Zombie wild = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ORE.west());
        wild.hurtServer(helper.getLevel(), hunter.damageSources().playerAttack(hunter), 1000);
        long paid = balance(hunter);
        long unit = EconomyService.INSTANCE.toMinorOrMax(BigDecimal.ONE, "test");
        helper.assertTrue(paid >= unit && paid <= 3 * unit, "a wild zombie pays 1-3, got " + paid);
        helper.succeed();
    }

    public static void rewardMineSkipsPlacedBlocks(GameTestHelper helper) {
        RewardsConfig original = Rewards.config();
        try {
            RewardsConfig.Section mine = new RewardsConfig.Section(true,
                Map.of("minecraft:diamond_ore", new RewardsConfig.Reward(BigDecimal.valueOf(5), BigDecimal.valueOf(5), 1.0)));
            Rewards.setConfigForTests(new RewardsConfig(original.kill(), mine, original.craft(), original.fish(), original.advancement(),
                original.rules()));
            ServerPlayer miner = survivalPlayer(helper, ORE.north());
            miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            setBalance(miner, 0);

            helper.setBlock(ORE, Blocks.DIAMOND_ORE);
            Rewards.onBlockPlaced(helper.getLevel(), helper.absolutePos(ORE), Blocks.DIAMOND_ORE.defaultBlockState());
            miner.gameMode.destroyBlock(helper.absolutePos(ORE));
            helper.assertValueEqual(balance(miner), 0L, "a placed ore pays nothing");

            helper.setBlock(ORE, Blocks.DIAMOND_ORE);
            miner.gameMode.destroyBlock(helper.absolutePos(ORE));
            helper.assertValueEqual(balance(miner), EconomyService.INSTANCE.toMinorOrMax(BigDecimal.valueOf(5), "test"), "a natural ore pays 5");
        } finally {
            Rewards.setConfigForTests(original);
        }
        helper.succeed();
    }

    /**
     * Loads every class Tradery mixes into: a wrong target signature fails here, not in a player's game when they
     * first fish or craft.
     */
    public static void mixinsApply(GameTestHelper helper) {
        String[] targets = {
            "net.minecraft.world.entity.item.ItemEntity",
            "net.minecraft.world.inventory.ResultSlot",
            "net.minecraft.world.entity.projectile.FishingHook",
            "net.minecraft.server.PlayerAdvancements",
            "net.minecraft.world.entity.EntityType",
            "net.minecraft.world.item.BlockItem"
        };
        for (String target : targets) {
            try {
                Class.forName(target, true, SourcesGameTests.class.getClassLoader());
            } catch (Throwable t) {
                helper.fail("mixin target " + target + " failed to load: " + t);
                return;
            }
        }
        helper.succeed();
    }

    /** The loader's biome modification (Fabric code / NeoForge biome modifier) added the coin ore features. */
    public static void coinOreInOverworldBiomes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var biomes = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var features = registries.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (var biomeKey : List.of(net.minecraft.world.level.biome.Biomes.PLAINS, net.minecraft.world.level.biome.Biomes.DEEP_DARK)) {
            var settings = biomes.getOrThrow(biomeKey).value().getGenerationSettings();
            for (var featureKey : dev.eliasnvx.tradery.ore.CoinOreGeneration.ALL) {
                helper.assertTrue(settings.hasFeature(features.getOrThrow(featureKey).value()), featureKey.identifier() + " in " + biomeKey.identifier());
            }
        }
        var nether = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES).value().getGenerationSettings();
        helper.assertFalse(nether.hasFeature(features.getOrThrow(dev.eliasnvx.tradery.ore.CoinOreGeneration.GOLD).value()), "no coin ore in the Nether");
        helper.succeed();
    }

    /**
     * Ore doublers (Create crushing, Mekanism enriching, mods that smelt "#c:ores") must not multiply money: coin ore
     * is in no conventional ore/raw/dust tag, and no furnace recipe takes it or a coin.
     */
    public static void coinOreNotProcessable(GameTestHelper helper) {
        List<String> processingTags = List.of("ores", "raw_materials", "dusts", "ingots", "nuggets");
        var level = helper.getLevel();
        for (CoinTier tier : CoinTier.values()) {
            for (boolean deepslate : new boolean[] {false, true}) {
                var block = TraderyItems.ore(tier, deepslate);
                var stack = new ItemStack(block.asItem());
                for (String tag : processingTags) {
                    var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("c", tag);
                    helper.assertFalse(block.defaultBlockState().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id)),
                        block + " is not in #c:" + tag);
                    helper.assertFalse(stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id)),
                        stack.getItem() + " is not in #c:" + tag);
                }
                assertNoFurnaceRecipe(helper, level, stack);
            }
            assertNoFurnaceRecipe(helper, level, new ItemStack(TraderyItems.coin(tier)));
        }
        helper.succeed();
    }

    private static void assertNoFurnaceRecipe(GameTestHelper helper, ServerLevel level, ItemStack stack) {
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        var recipes = level.getServer().getRecipeManager();
        helper.assertTrue(recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level).isEmpty(),
            "no smelting recipe for " + stack.getItem());
        helper.assertTrue(recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING, input, level).isEmpty(),
            "no blasting recipe for " + stack.getItem());
    }

    public static void oreFeaturePlacesOre(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        var source = helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        // A vein is a random blob: in a 5x5x5 cube it can fall outside or next to air, so try a few times
        int found = 0;
        for (int attempt = 0; attempt < 10 && found == 0; attempt++) {
            helper.getLevel().getServer().getCommands().performPrefixedCommand(source,
                "place feature tradery:copper_coin_ore " + center.getX() + " " + center.getY() + " " + center.getZ());
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    for (int z = 0; z < 5; z++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(TraderyItems.ore(CoinTier.COPPER, false))) {
                            found++;
                        }
                    }
                }
            }
        }
        helper.assertTrue(found > 0, "the configured feature places copper coin ore in stone");
        helper.succeed();
    }
}
