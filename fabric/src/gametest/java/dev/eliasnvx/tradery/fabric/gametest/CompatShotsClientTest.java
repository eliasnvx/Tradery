package dev.eliasnvx.tradery.fabric.gametest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.client.screen.VillagerTradeScreen;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.TradeCrateBlock;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.VillageCouncil;
import dev.eliasnvx.tradery.village.VillageSpecialization;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.VillagerLedger;
import dev.eliasnvx.tradery.village.VillagerPurchases;
import dev.eliasnvx.tradery.village.Villages;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.findMarketVendor;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.lookAt;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.lookFrom;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.openGround;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.placeShop;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.player;
import static dev.eliasnvx.tradery.fabric.gametest.LivingMarketShotsClientTest.setHudHidden;

/**
 * The 1.2 integrations in a real world, for screenshots: market stalls in a ChoiceTheorem's Overhauled Village, WTHIT
 * tooltips (stall, trade crate, bell), a VillagersPlus villager's trade window in money, and Tradery's layers on the
 * BlueMap and squaremap web maps. Only with {@code TRADERY_COMPAT_SHOTS=1}:
 * <pre>TRADERY_COMPAT_SHOTS=1 ./gradlew :fabric:runClientGameTest -Pcompat=wthit,maps,villages -Pfabric_api_version=0.166.2+26.3</pre>
 * Accepts BlueMap's download of the Minecraft client (its {@code accept-download}) for the run. At the end the world
 * stays up while {@code tradery-hold} exists in the run directory (at most 20 minutes): the web maps are at
 * http://localhost:8100 (BlueMap) and http://localhost:8080 (squaremap).
 */
public final class CompatShotsClientTest implements FabricClientGameTest {
    private static final String SHOTS_CONFIG = """
        {
          villagers: { checkIntervalTicks: 20, visitChance: 1.0, customerCooldownTicks: 200 },
          market: { weight: 50 },
          integrations: { maps: { updateSeconds: 10 } }
        }
        """;
    private static final Path HOLD = Path.of("tradery-hold");

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!"1".equals(System.getenv("TRADERY_COMPAT_SHOTS"))) {
            return;
        }
        Path config = Platform.get().configDir().resolve("tradery").resolve("server.json5");
        try {
            Files.createDirectories(config.getParent());
            Files.writeString(config, SHOTS_CONFIG);
            acceptBlueMapDownload();
            run(context);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(config);
            } catch (IOException ignored) {
            }
        }
    }

    private static void run(ClientGameTestContext context) throws IOException {
        for (String mod : new String[] {"wthit", "bluemap", "squaremap", "villagersplus", "ctov", "jade"}) {
            log("mod " + mod + " loaded: " + Platform.get().isModLoaded(mod));
        }
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(mc -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.options.guiScale().set(3);
        });
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(ui -> {
            ui.setWorldType(new net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry(
                ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL)));
            ui.setSeed("tradery");
            ui.setGenerateStructures(true);
        }).create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear 1000000");
            server.runCommand("time set 1000");
            server.runCommand("gamemode creative @p");
            // A config BlueMap wrote just now (its first start) says no yet: accept and load it again
            if (acceptBlueMapDownload()) {
                server.runCommand("bluemap reload");
            }

            BlockPos village = server.computeOnServer(CompatShotsClientTest::findVillage);
            server.runCommand(String.format(Locale.ROOT, "tp @p %d 200 %d", village.getX(), village.getZ()));
            context.runOnClient(mc -> mc.player.getAbilities().flying = true);
            context.waitTicks(200);
            world.getConnection().waitForChunksRender();

            // A market stall Tradery added to the Overhauled Village, from the street; then up close, with WTHIT's tooltip
            BlockPos stall = server.computeOnServer(s -> findMarketVendor(s.overworld(), village));
            log("village at " + village + ", stall " + stall);
            BlockPos reference = stall != null ? stall : server.computeOnServer(s -> openGround(s.overworld(), village, 0, 6));
            if (stall != null) {
                Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(stall).getValue(VendingBlock.FACING));
                setHudHidden(context, true);
                lookFrom(context, server, stall, facing, 6.5, 3.5, 0.8);
                context.waitTicks(40);
                shot(context, "ctov_stall");
                setHudHidden(context, false);
                server.runCommand("gamemode survival @p");
                lookFrom(context, server, stall, facing, 1.8, 0.0, 0.4);
                if (!onTarget(context, stall)) {
                    aim(context, server, stall);
                }
                context.waitTicks(30);
                shot(context, "wthit_stall");
            }

            // The village: a Town with a patron, a new name and a specialization (the bell's tooltip shows them)
            server.runCommand("gamemode survival @p");
            server.runOnServer(s -> Villages.census(s.overworld()));
            BlockPos bell = server.computeOnServer(s -> Villages.nearestBell(s.overworld(), reference, 96).orElse(null));
            log("bell " + bell);
            if (bell != null) {
                server.runOnServer(s -> {
                    VillageState state = Villages.at(s, s.overworld().dimension(), bell);
                    var thresholds = TraderyConfig.server().villages().levels().thresholds();
                    Villages.set(s, state, thresholds.get(Math.min(2, thresholds.size() - 1)) + 300,
                        EconomyService.INSTANCE.defaultCurrency().toMinor(java.math.BigDecimal.valueOf(640)));
                    ServerPlayer p = player(s);
                    VillageCouncil.contribute(s, state, p, VillageCouncil.minContribution() * 3);
                    log("rename: " + VillageCouncil.rename(p, state, "Oakford").message().getString());
                    log("specialize: " + VillageCouncil.specialize(p, state, VillageSpecialization.TRADE).message().getString());
                });
                aim(context, server, bell);
                context.waitTicks(60);
                shot(context, "wthit_bell");
                server.runCommand("gamemode survival @p");
            }

            // A trade crate of the player's by the bell: what it wants, in WTHIT's tooltip
            BlockPos crateAt = server.computeOnServer(s -> openGround(s.overworld(), bell != null ? bell : reference, 3, 8));
            Direction crateFacing = server.computeOnServer(s -> {
                ServerLevel level = s.overworld();
                Direction facing = Direction.Plane.HORIZONTAL.stream()
                    .filter(d -> level.isEmptyBlock(crateAt.relative(d)) && level.isEmptyBlock(crateAt.relative(d, 2))
                        && level.getBlockState(crateAt.relative(d, 2).below()).isSolid())
                    .findFirst().orElse(Direction.NORTH);
                level.setBlockAndUpdate(crateAt, TraderyBlocks.TRADE_CRATE.get().defaultBlockState().setValue(TradeCrateBlock.FACING, facing));
                TradeCrateBlockEntity crate = (TradeCrateBlockEntity) level.getBlockEntity(crateAt);
                ServerPlayer p = player(s);
                crate.setOwner(AccountId.player(p.getUUID()), p.nameAndId().name());
                VendorsData.get(s).add(level, crateAt, AccountId.player(p.getUUID()), true);
                crate.wants().setItem(0, new ItemStack(Items.EMERALD));
                crate.wants().setItem(1, new ItemStack(Items.BREAD));
                crate.wants().setItem(2, new ItemStack(Items.APPLE));
                crate.setItem(0, new ItemStack(Items.WHEAT, 64));
                crate.setItem(1, new ItemStack(Items.PUMPKIN, 32));
                return facing;
            });
            aim(context, server, crateAt);
            context.waitTicks(30);
            shot(context, "wthit_crate");

            // A VillagersPlus oceanographer: its own trades, in money
            villagersPlusTrade(context, server, reference);

            // A shop of the player's for the shops layer
            if (stall != null) {
                Direction facing = server.computeOnServer(s -> s.overworld().getBlockState(stall).getValue(VendingBlock.FACING));
                server.computeOnServer(s -> placeShop(s, stall, facing));
            }
            maps(context, server, bell != null ? bell : reference);
        }
    }

    /** A VillagersPlus villager (an oceanographer, journeyman) next to the player, right-clicked: its offers in money. */
    private static void villagersPlusTrade(ClientGameTestContext context, TestServerContext server, BlockPos near) {
        if (!Platform.get().isModLoaded("villagersplus")) {
            log("VillagersPlus isn't loaded: no trade window");
            return;
        }
        BlockPos stand = server.computeOnServer(s -> openGround(s.overworld(), near, 4, 10));
        BlockPos spot = server.computeOnServer(s -> openGround(s.overworld(), stand, 2, 3));
        lookAt(context, server, stand, Vec3.atBottomCenterOf(spot).add(0, 1.4, 0));
        String result = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            ResourceKey<VillagerProfession> oceanographer = ResourceKey.create(Registries.VILLAGER_PROFESSION,
                Identifier.fromNamespaceAndPath("villagersplus", "oceanographer"));
            if (level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).get(oceanographer).isEmpty()) {
                return "no villagersplus:oceanographer profession";
            }
            Villager villager = EntityTypes.VILLAGER.spawn(level, spot, EntitySpawnReason.COMMAND);
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), oceanographer).withLevel(3));
            villager.setVillagerXp(1);
            ServerPlayer p = player(s);
            villager.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
            var currency = EconomyService.INSTANCE.defaultCurrency();
            VillagerLedger.get(s).setWallet(villager.getUUID(), currency.toMinor(java.math.BigDecimal.valueOf(9)), VillagerPurchases.today(s));
            EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(p.getUUID()), currency.toMinor(java.math.BigDecimal.valueOf(250)),
                Reason.of(Reasons.ADMIN_SET, "shots"));
            StringBuilder offers = new StringBuilder();
            for (var offer : villager.getOffers()) {
                offers.append(offer.getCostA().getHoverName().getString()).append(" x").append(offer.getCostA().getCount())
                    .append(offer.getCostB().isEmpty() ? "" : " + " + offer.getCostB().getHoverName().getString())
                    .append(" -> ").append(offer.getResult().getHoverName().getString()).append(" x").append(offer.getResult().getCount())
                    .append("; ");
            }
            villager.mobInteract(p, InteractionHand.MAIN_HAND);
            return "offers " + offers + "menu " + p.containerMenu.getClass().getSimpleName();
        });
        log("VillagersPlus oceanographer: " + result);
        if (!result.startsWith("no ")) {
            context.waitForScreen(VillagerTradeScreen.class);
            context.waitTicks(10);
            shot(context, "villagersplus_trade");
            context.runOnClient(mc -> mc.player.closeContainer());
        }
    }

    /**
     * The web maps: the world saved, a render around the village asked for, Tradery's layers pushed, then the game
     * stays up (a view over the village) while {@link #HOLD} exists.
     */
    private static void maps(ClientGameTestContext context, TestServerContext server, BlockPos center) throws IOException {
        server.runCommand("save-all flush");
        context.waitTicks(40);
        if (Platform.get().isModLoaded("bluemap")) {
            // From the console BlueMap wants a map name; as the player it renders around them
            server.runCommand("execute as @p at @p run bluemap force-update 160");
        }
        if (Platform.get().isModLoaded("squaremap")) {
            server.runCommand(String.format(Locale.ROOT, "squaremap radiusrender minecraft:overworld 192 %d %d", center.getX(), center.getZ()));
        }
        // An overview for the window meanwhile
        server.runCommand("gamemode spectator @p");
        lookFrom(context, server, center, Direction.SOUTH, 28, 34, 0);
        context.waitTicks(200);
        shot(context, "village_above");
        Files.writeString(HOLD, "Delete this file to end the run\n");
        log("holding for the web maps (BlueMap http://localhost:8100, squaremap http://localhost:8080); village at "
            + center.getX() + " " + center.getY() + " " + center.getZ() + "; delete " + HOLD.toAbsolutePath() + " to end");
        for (int seconds = 0; seconds < 20 * 60 && Files.exists(HOLD); seconds++) {
            context.waitTicks(20);
            if (seconds % 60 == 0) {
                log("still holding, " + seconds + " s");
            }
        }
        Files.deleteIfExists(HOLD);
    }

    /**
     * The nearest plains village of ChoiceTheorem's Overhauled Village (its {@code #ctov:village_plains} tag: open ground
     * for the camera), or a vanilla plains one without CTOV.
     */
    private static BlockPos findVillage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> targets = structures.get(TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("ctov", "village_plains")))
            .<HolderSet<Structure>>map(set -> set)
            .orElseGet(() -> HolderSet.direct(structures.getOrThrow(BuiltinStructures.VILLAGE_PLAINS)));
        log("looking for " + targets.size() + " village structures");
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, targets, player(server).blockPosition(), 100, false);
        if (found == null) {
            throw new AssertionError("no village");
        }
        log("village structure " + found.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
        return found.getFirst();
    }

    /**
     * Stands the player where the crosshair lands on {@code target}: 2..4 blocks away on the ground with a clear view,
     * or else (a bell up on a tower) flying next to it in creative.
     */
    private static boolean aim(ClientGameTestContext context, TestServerContext server, BlockPos target) {
        List<BlockPos> spots = server.computeOnServer(s -> standSpots(s.overworld(), target, true));
        for (BlockPos feet : spots) {
            lookAt(context, server, feet, Vec3.atCenterOf(target));
            if (onTarget(context, target)) {
                return true;
            }
        }
        List<BlockPos> air = server.computeOnServer(s -> standSpots(s.overworld(), target, false));
        log("no ground spot with a clear view of " + target + " among " + spots.size() + "; flying, " + air.size() + " spots");
        server.runCommand("gamemode creative @p");
        context.runOnClient(mc -> mc.player.getAbilities().flying = true);
        for (BlockPos feet : air) {
            lookAt(context, server, feet, Vec3.atCenterOf(target));
            context.runOnClient(mc -> mc.player.getAbilities().flying = true);
            if (onTarget(context, target)) {
                return true;
            }
        }
        log("no spot at all with a clear view of " + target);
        return false;
    }

    private static boolean onTarget(ClientGameTestContext context, BlockPos target) {
        context.waitTicks(3);
        return context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(target));
    }

    /** Spots 2..4 blocks around {@code target} with room for the player: on solid ground, or ({@code ground} false) in the air. */
    private static List<BlockPos> standSpots(ServerLevel level, BlockPos target, boolean ground) {
        List<BlockPos> spots = new ArrayList<>();
        for (int r = 2; r <= (ground ? 4 : 3); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = ground ? -2 : -1; dy <= (ground ? 1 : 0); dy++) {
                        BlockPos feet = target.offset(dx, dy, dz);
                        if ((!ground || level.getBlockState(feet.below()).isSolid()) && level.isEmptyBlock(feet) && level.isEmptyBlock(feet.above())) {
                            spots.add(feet);
                            break;
                        }
                    }
                }
            }
        }
        return spots;
    }

    /**
     * BlueMap waits for its owner to accept the download of the Minecraft client (Mojang's EULA) in
     * {@code config/bluemap/core.conf}; the run accepts it, as asked for these shots.
     *
     * @return whether the file said no and now says yes
     */
    private static boolean acceptBlueMapDownload() throws IOException {
        Path core = Platform.get().configDir().resolve("bluemap").resolve("core.conf");
        if (!Files.exists(core)) {
            return false;
        }
        String text = Files.readString(core);
        if (!text.contains("accept-download: false")) {
            return false;
        }
        Files.writeString(core, text.replace("accept-download: false", "accept-download: true"));
        log("accepted BlueMap's download in " + core);
        return true;
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.waitTicks(2);
        context.takeScreenshot("compat_" + name);
    }

    private static void log(String message) {
        System.out.println("Tradery compat shots: " + message);
    }
}
