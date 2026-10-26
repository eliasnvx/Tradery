package dev.eliasnvx.tradery.neoforge.clienttest.shots;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.screen.VendingBuyerScreen;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyItems;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.DisplayBlock;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Dev-only: real in-game shots for the release post. A normal world, a small market next to a plains village,
 * Complementary Reimagined through Iris (mods/ and shaderpacks/ of the run dir), 1920x1080, GUI scale 3. Writes
 * screenshots/reddit_*.png and clip.raw (RGBA, 30 fps) into the run dir, then quits.
 *
 * <p>Run: {@code ./gradlew :neoforge:runShots}
 */
public final class RedditShots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID MIRA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID KAI = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID LENA = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final int TIMEOUT_TICKS = 20 * 120;
    private static final int CLIP_FPS = 30;
    private static final int WIDTH = 17;
    private static final int DEPTH = 9;

    private final AtomicInteger ticks;
    private Minecraft minecraft;

    public RedditShots(AtomicInteger ticks) {
        this.ticks = ticks;
    }

    public void runAndExit() {
        minecraft = Minecraft.getInstance();
        try {
            run();
            LOGGER.info("Tradery shots done");
            minecraft.execute(minecraft::stop);
        } catch (Throwable t) {
            LOGGER.error("Tradery shots failed", t);
            Runtime.getRuntime().halt(1);
        }
    }

    private void run() throws Exception {
        createWorld();
        command("gamerule sendCommandFeedback false");
        command("gamerule announceAdvancements false");
        command("gamerule doDaylightCycle false");
        command("gamerule doWeatherCycle false");
        command("gamerule doMobSpawning false");
        command("weather clear 1000000");
        command("time set 6000");
        command("gamemode creative @p");

        // Find a plains village, fly there, let the chunks load and the shaders compile
        BlockPos village = server(this::findVillage);
        LOGGER.info("Tradery shots: village at {}", village);
        command(String.format(Locale.ROOT, "tp @p %d %d %d", village.getX(), 200, village.getZ()));
        client(() -> {
            minecraft.player.getAbilities().flying = true;
        });
        waitTicks(200);
        BlockPos o = server(server -> findPlaza(server.overworld(), village));
        LOGGER.info("Tradery shots: market at {}", o);
        server(server -> {
            buildMarket(server, o);
            return null;
        });
        waitTicks(100);

        // 01 the market street, no GUI, late afternoon; invisible, so the shaders don't draw the player's shadow
        setGui(false, false);
        command("effect give @p minecraft:invisibility infinite 0 true");
        command("time set 11200");
        look(o, 0.5, 0.0, -0.5, 0.5, 0.9, 5.5);
        waitTicks(60);
        shot("reddit_market_front");
        look(o, -6.5, 0.6, 2.0, 1.0, 0.6, 5.5);
        waitTicks(40);
        shot("reddit_market_diag");
        look(o, 7.0, 0.6, 2.0, -1.0, 0.6, 5.5);
        waitTicks(40);
        shot("reddit_market_diag_east");
        look(o, -4.0, 3.0, -1.5, 1.0, 0.3, 6.0);
        waitTicks(40);
        shot("reddit_market_high");
        look(o, -2.5, 2.2, 0.5, 1.5, 0.3, 6.0);
        waitTicks(40);
        shot("reddit_market_high_close");
        command("effect clear @p");

        // 02 the hint at the bread stall, vanilla HUD hidden
        command("gamemode survival @p");
        client(() -> minecraft.player.getAbilities().flying = false);
        command("time set 8000");
        BlockPos bread = o.offset(-4, 0, 5);
        server(server -> {
            setBalance(server, 1_284_50L);
            restock(server, bread);
            return null;
        });
        setGui(true, true);
        look(o, -3.5, 0.0, 3.4, -3.5, 0.55, 5.5);
        waitTicks(40);
        shot("reddit_hint");

        // 03 a held sneak + right-click: bread in hand, the action bar, the balance popups
        hold(18);
        waitTicks(3);
        shot("reddit_quick_buy");
        waitTicks(60);

        // 04/05 the screens
        look(o, 0.5, 0.0, 3.4, 0.5, 0.55, 5.5);
        waitTicks(20);
        openScreen("reddit_buyer_screen", VendingBuyerScreen.class);
        server(server -> {
            ServerPlayer p = player(server);
            vendor(server, bread).setOwner(AccountId.player(p.getUUID()), "Alex");
            return null;
        });
        look(o, -3.5, 0.0, 3.4, -3.5, 0.55, 5.5);
        waitTicks(20);
        openScreen("reddit_owner_screen", VendingOwnerScreen.class);
        server(server -> {
            vendor(server, bread).setOwner(AccountId.player(MIRA), "Mira");
            return null;
        });

        // 06/07 a mine under the market: coin ore by torchlight, then mining it straight into the balance
        BlockPos mine = o.offset(0, -24, 0);
        Map<String, BlockPos> ores = server(server -> buildMine(server.overworld(), mine));
        setGui(false, false);
        command("gamemode creative @p");
        look(mine, 0.5, 0.0, 0.3, 1.0, 1.2, 10.0);
        command(String.format(Locale.ROOT, "summon minecraft:chest_minecart %d %d %d", mine.getX(), mine.getY(), mine.getZ() + 9));
        // The mine replaced a lot of blocks at once: rebuild every chunk section before the shot
        waitTicks(40);
        client(() -> minecraft.levelRenderer.allChanged());
        waitTicks(120);
        shot("reddit_coin_ore");
        command("gamemode survival @p");
        command("item replace entity @p weapon.mainhand with minecraft:iron_pickaxe");
        BlockPos target = ores.get("target");
        look(mine, 0.5, 0.0, 1.8, 1.6, 1.2, 7.5);
        setGui(true, true);
        waitTicks(20);
        server(server -> {
            player(server).gameMode.destroyBlock(target);
            return null;
        });
        waitTicks(12);
        shot("reddit_mining_hud");

        // The clip: walk-free quick buy at the bread stall, 30 fps
        server(server -> {
            setBalance(server, 1_284_50L);
            restock(server, bread);
            player(server).getInventory().clearContent();
            return null;
        });
        look(o, -3.5, 0.0, 3.4, -3.5, 0.55, 5.5);
        setGui(true, true);
        waitTicks(60);
        clearChatAndToasts();
        Path raw = minecraft.gameDirectory.toPath().resolve("clip.raw");
        client(() -> {
            try {
                CleanHud.startClip(raw, CLIP_FPS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        waitSeconds(1.2);
        client(() -> minecraft.options.keyShift.setDown(true));
        waitTicks(2);
        client(() -> minecraft.options.keyUse.setDown(true));
        waitSeconds(3.2);
        client(() -> {
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyShift.setDown(false);
        });
        waitSeconds(1.6);
        long frames = client(() -> {
            try {
                return CleanHud.stopClip();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Files.writeString(minecraft.gameDirectory.toPath().resolve("clip.txt"), String.format(Locale.ROOT, "%d %d %d %d%n",
            frames, CLIP_FPS, minecraft.getMainRenderTarget().width, minecraft.getMainRenderTarget().height));
        LOGGER.info("Tradery shots: clip {} frames", frames);
    }

    // ------------------------------------------------------------------ world

    private void createWorld() {
        client(() -> {
            minecraft.options.onboardAccessibility = false;
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.tutorialStep = TutorialSteps.NONE;
            minecraft.options.guiScale().set(3);
            minecraft.options.renderDistance().set(12);
            minecraft.options.bobView().set(false);
            CreateWorldScreen.openFresh(minecraft, new TitleScreen());
        });
        waitFor("create world screen", mc -> mc.screen instanceof CreateWorldScreen);
        client(() -> {
            CreateWorldScreen screen = (CreateWorldScreen) minecraft.screen;
            WorldCreationUiState ui = screen.getUiState();
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(
                ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL)));
            ui.setSeed("tradery");
            ui.setGenerateStructures(true);
            ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            ui.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            String create = Component.translatable("selectWorld.create").getString();
            for (var child : screen.children()) {
                if (child instanceof Button button && create.equals(button.getMessage().getString())) {
                    button.onPress();
                    break;
                }
            }
        });
        waitFor("world loaded", mc -> mc.level != null && mc.player != null && mc.screen == null
            && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().getPlayerList().getPlayers().isEmpty());
        waitTicks(100);
    }

    private BlockPos findVillage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Holder<Structure> plains = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.VILLAGE_PLAINS);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level, HolderSet.direct(plains), player(server).blockPosition(), 100, false);
        if (found == null) {
            throw new IllegalStateException("No plains village near spawn");
        }
        return found.getFirst();
    }

    /**
     * The flattest natural (grass/dirt, no village blocks, no water) rectangle near the village, so its houses are in
     * the background; the market levels what's left. Returns the market origin at the median ground height.
     */
    private static BlockPos findPlaza(ServerLevel level, BlockPos village) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -96; dx <= 96; dx += 2) {
            for (int dz = -96; dz <= 96; dz += 2) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                // North of the village and roughly centred: the stalls face north, so its houses are behind them
                if (distance < 16 || dz > -16 || Math.abs(dx + 8) > 32) {
                    continue;
                }
                int[] ground = naturalGround(level, village.offset(dx, 0, dz));
                if (ground == null || ground[1] > 6) {
                    continue;
                }
                double score = ground[1] * 12 + distance;
                if (score < bestScore) {
                    bestScore = score;
                    best = new BlockPos(village.getX() + dx + 8, ground[0], village.getZ() + dz);
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("No open ground near the village at " + village);
        }
        LOGGER.info("Tradery shots: plaza score {}", bestScore);
        return best;
    }

    /** {median height, spread} of the rectangle at {@code corner}, or null if it's not open natural ground. */
    private static int[] naturalGround(ServerLevel level, BlockPos corner) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int x = -1; x <= WIDTH; x++) {
            for (int z = -3; z <= DEPTH + 3; z++) {
                int cx = corner.getX() + x;
                int cz = corner.getZ() + z;
                if (!level.hasChunk(cx >> 4, cz >> 4)) {
                    return null;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                BlockState top = level.getBlockState(new BlockPos(cx, y - 1, cz));
                if (!top.is(Blocks.GRASS_BLOCK) && !top.is(Blocks.DIRT) && !top.is(Blocks.COARSE_DIRT) && !top.is(Blocks.PODZOL)) {
                    return null;
                }
                heights.add(y);
            }
        }
        java.util.Collections.sort(heights);
        return new int[] {heights.get(heights.size() / 2), heights.get(heights.size() - 1) - heights.get(0)};
    }

    // ------------------------------------------------------------------ scenes

    private static void buildMarket(MinecraftServer server, BlockPos o) {
        ServerLevel level = server.overworld();
        // Level ground (grass at o.y - 1) under the market, and a slope over the next few blocks back to the
        // natural height, so the plaza doesn't sit in a pit or on a step
        int minX = -9;
        int maxX = 9;
        int minZ = -2;
        int maxZ = DEPTH + 2;
        int margin = 5;
        int[][] natural = new int[maxX - minX + 1 + 2 * margin][maxZ - minZ + 1 + 2 * margin];
        for (int x = minX - margin; x <= maxX + margin; x++) {
            for (int z = minZ - margin; z <= maxZ + margin; z++) {
                natural[x - minX + margin][z - minZ + margin] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    o.getX() + x, o.getZ() + z) - o.getY();
            }
        }
        for (int x = minX - margin; x <= maxX + margin; x++) {
            for (int z = minZ - margin; z <= maxZ + margin; z++) {
                int outside = Math.max(Math.max(minX - x, x - maxX), Math.max(minZ - z, z - maxZ));
                int ground = natural[x - minX + margin][z - minZ + margin];
                int target = outside <= 0 ? 0 : Math.round(ground * outside / (float) (margin + 1));
                for (int y = Math.min(target, ground) - 4; y < target; y++) {
                    level.setBlockAndUpdate(o.offset(x, y, z), (y == target - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
                }
                for (int y = target; y <= Math.max(target, ground) + 2; y++) {
                    level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // Path in front of the stalls, flowers behind them
        for (int x = -9; x <= 9; x++) {
            for (int z = 2; z <= 4; z++) {
                level.setBlockAndUpdate(o.offset(x, -1, z), Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET};
        for (int x = -9; x <= 9; x++) {
            for (int z = 7; z <= 10; z++) {
                if ((x * 7 + z * 13) % 5 == 0) {
                    level.setBlockAndUpdate(o.offset(x, 0, z), flowers[Math.floorMod(x + z, flowers.length)].defaultBlockState());
                } else if ((x * 3 + z * 5) % 7 == 0) {
                    level.setBlockAndUpdate(o.offset(x, 0, z), Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
        // Lantern posts behind the stalls, crates at the ends of the row
        for (int x : new int[] {-7, -3, 1, 5}) {
            level.setBlockAndUpdate(o.offset(x, 0, 6), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 1, 6), Blocks.LANTERN.defaultBlockState());
        }
        level.setBlockAndUpdate(o.offset(-8, 0, 5), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(o.offset(-8, 0, 6), Blocks.HAY_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(o.offset(-8, 1, 6), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(o.offset(8, 0, 5), Blocks.HAY_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(o.offset(8, 0, 6), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(o.offset(8, 0, 4), Blocks.COMPOSTER.defaultBlockState());

        stall(level, o.offset(-4, 0, 5), MIRA, "Mira", new ItemStack(Items.BREAD, 4), PriceMode.CURRENCY, 250, ItemStack.EMPTY, false,
            DisplayAnimation.SPIN_BOB, Blocks.SPRUCE_PLANKS.defaultBlockState(), new ItemStack(Items.BREAD, 64));
        stall(level, o.offset(-2, 0, 5), KAI, "Kai", new ItemStack(Items.DIAMOND_SWORD), PriceMode.ITEM, 0, new ItemStack(Items.DIAMOND, 2),
            false, DisplayAnimation.SPIN, null, new ItemStack(Items.DIAMOND_SWORD));
        VendingBlockEntity apples = stall(level, o.offset(0, 0, 5), null, "Server", new ItemStack(Items.GOLDEN_APPLE), PriceMode.CURRENCY,
            1500, ItemStack.EMPTY, false, DisplayAnimation.SPIN_BOB, Blocks.POLISHED_ANDESITE.defaultBlockState(), ItemStack.EMPTY);
        apples.setAdmin(new AdminFlags(true, false, true));
        stall(level, o.offset(2, 0, 5), LENA, "Lena", PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), PriceMode.CURRENCY,
            800, ItemStack.EMPTY, false, DisplayAnimation.BOB, Blocks.BIRCH_PLANKS.defaultBlockState(),
            PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS).copyWithCount(1));
        stall(level, o.offset(4, 0, 5), MIRA, "Mira", new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true,
            DisplayAnimation.STATIC, Blocks.COBBLESTONE.defaultBlockState(), ItemStack.EMPTY);
        EconomyService.INSTANCE.deposit(EconomyService.INSTANCE.account(MIRA), 100_000, Reason.of(Reasons.ADMIN_GIVE, "shots"));

        showcase(level, o.offset(-6, 0, 5), new ItemStack(Items.TOTEM_OF_UNDYING));
        showcase(level, o.offset(6, 0, 5), new ItemStack(Items.TRIDENT));
    }

    private static VendingBlockEntity stall(ServerLevel level, BlockPos pos, UUID owner, String name, ItemStack goods, PriceMode mode,
                                            long price, ItemStack priceItem, boolean buyback, DisplayAnimation animation,
                                            BlockState facade, ItemStack stock) {
        level.setBlockAndUpdate(pos, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = (VendingBlockEntity) level.getBlockEntity(pos);
        vendor.setOwner(owner == null ? AccountId.system(EconomyService.SERVER_ACCOUNT) : AccountId.player(owner), name);
        vendor.setSettings(new VendingSettings(goods, mode, price, priceItem, buyback, animation));
        if (facade != null) {
            vendor.setFacade(facade);
        }
        if (!stock.isEmpty()) {
            vendor.stock().setItem(0, stock);
            vendor.stock().setChanged();
        }
        return vendor;
    }

    private static void showcase(ServerLevel level, BlockPos pos, ItemStack item) {
        level.setBlockAndUpdate(pos, TraderyBlocks.DISPLAY_BLOCK.get().defaultBlockState().setValue(DisplayBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
            display.setOwner(AccountId.player(LENA));
            display.setShown(item);
            display.setAnimation(DisplayAnimation.SPIN_BOB);
        }
    }

    private static void restock(MinecraftServer server, BlockPos pos) {
        VendingBlockEntity vendor = vendor(server, pos);
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 64));
        vendor.stock().setItem(1, new ItemStack(Items.BREAD, 64));
        vendor.stock().setChanged();
    }

    /** A timbered mine tunnel in mixed stone: coin ore of all three tiers in the walls, torches, a rail. */
    private static Map<String, BlockPos> buildMine(ServerLevel level, BlockPos m) {
        BlockState[] rock = {Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 5; y++) {
                for (int z = -3; z <= 18; z++) {
                    boolean inside = x >= -1 && x <= 1 && y >= 0 && y <= 2 && z >= -2 && z <= 16;
                    BlockState state = inside ? Blocks.AIR.defaultBlockState() : rock[Math.floorMod(x * 31 + y * 17 + z * 13 + x * z, rock.length)];
                    level.setBlockAndUpdate(m.offset(x, y, z), state);
                }
            }
        }
        // Supports: fence posts and a plank beam every four blocks
        for (int z : new int[] {1, 5, 9, 13}) {
            for (int y = 0; y <= 1; y++) {
                level.setBlockAndUpdate(m.offset(-1, y, z), Blocks.OAK_FENCE.defaultBlockState());
                level.setBlockAndUpdate(m.offset(1, y, z), Blocks.OAK_FENCE.defaultBlockState());
            }
            for (int x = -1; x <= 1; x++) {
                level.setBlockAndUpdate(m.offset(x, 2, z), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        for (int z = -2; z <= 16; z++) {
            level.setBlockAndUpdate(m.offset(0, 0, z), Blocks.RAIL.defaultBlockState());
        }
        // Ore patches in both walls, close to the camera
        int[][] ores = {{-2, 1, 3, 0}, {-2, 2, 3, 0}, {-2, 1, 4, 0}, {-2, 0, 7, 1}, {-2, 1, 7, 1}, {-2, 2, 11, 2},
            {2, 1, 3, 1}, {2, 1, 4, 1}, {2, 2, 4, 1}, {2, 0, 6, 2}, {2, 1, 6, 2}, {2, 1, 10, 0}, {2, 2, 10, 0}};
        for (int[] ore : ores) {
            level.setBlockAndUpdate(m.offset(ore[0], ore[1], ore[2]), TraderyItems.ore(CoinTier.values()[ore[3]], false).defaultBlockState());
        }
        level.setBlockAndUpdate(m.offset(-1, 1, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(m.offset(1, 1, 8), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(m.offset(-1, 1, 14), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        Map<String, BlockPos> result = new HashMap<>();
        result.put("target", m.offset(2, 2, 4));
        return result;
    }

    // ------------------------------------------------------------------ camera and capture

    /** Feet at {@code o + (fx, fy, fz)}, eyes on {@code o + (tx, ty, tz)}. */
    private void look(BlockPos o, double fx, double fy, double fz, double tx, double ty, double tz) {
        double x = o.getX() + fx;
        double y = o.getY() + fy;
        double z = o.getZ() + fz;
        double dx = o.getX() + tx - x;
        double dy = o.getY() + ty - (y + 1.62);
        double dz = o.getZ() + tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        command(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        waitTicks(5);
        client(() -> {
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            minecraft.player.setYHeadRot(yaw);
        });
        waitTicks(5);
    }

    private void setGui(boolean visible, boolean cleanVanilla) {
        client(() -> {
            minecraft.options.hideGui = !visible;
            CleanHud.hideVanillaHud = cleanVanilla;
        });
    }

    private void clearChatAndToasts() {
        client(() -> {
            minecraft.gui.getChat().clearMessages(false);
            minecraft.getToasts().clear();
        });
    }

    private void shot(String name) {
        clearChatAndToasts();
        waitTicks(3);
        client(() -> Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), message -> {
        }));
        waitTicks(5);
        LOGGER.info("Tradery shots: {}", name);
    }

    private void openScreen(String name, Class<?> screen) {
        client(() -> KeyMapping.click(InputConstants.getKey(minecraft.options.keyUse.saveString())));
        waitFor(name, mc -> screen.isInstance(mc.screen));
        waitTicks(15);
        shot(name);
        client(() -> minecraft.player.closeContainer());
        waitFor(name + " closed", mc -> mc.screen == null);
    }

    /** Sneak, then hold use for {@code useTicks}, then let go (as a player does at a stall). */
    private void hold(int useTicks) {
        client(() -> minecraft.options.keyShift.setDown(true));
        waitTicks(2);
        client(() -> minecraft.options.keyUse.setDown(true));
        waitTicks(useTicks);
        client(() -> {
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyShift.setDown(false);
        });
    }

    // ------------------------------------------------------------------ helpers

    private void setBalance(MinecraftServer server, long amount) {
        EconomyService eco = EconomyService.INSTANCE;
        eco.setBalance(eco.account(player(server).getUUID()), amount, Reason.of(Reasons.ADMIN_SET, "shots"));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static VendingBlockEntity vendor(MinecraftServer server, BlockPos pos) {
        return (VendingBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private void command(String command) {
        server(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            return null;
        });
    }

    private <T> T client(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, minecraft).join();
    }

    private void client(Runnable action) {
        CompletableFuture.runAsync(action, minecraft).join();
    }

    private <T> T server(Function<MinecraftServer, T> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        return CompletableFuture.supplyAsync(() -> action.apply(server), server).join();
    }

    private void waitTicks(int count) {
        int target = ticks.get() + count;
        while (ticks.get() < target) {
            sleep(5);
        }
    }

    private static void waitSeconds(double seconds) {
        sleep((long) (seconds * 1000));
    }

    private void waitFor(String what, Predicate<Minecraft> condition) {
        int deadline = ticks.get() + TIMEOUT_TICKS;
        while (!client(() -> condition.test(minecraft))) {
            if (ticks.get() > deadline) {
                throw new IllegalStateException("timed out waiting for " + what);
            }
            sleep(5);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
