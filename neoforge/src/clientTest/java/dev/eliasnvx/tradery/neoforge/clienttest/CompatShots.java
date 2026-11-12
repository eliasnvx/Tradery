package dev.eliasnvx.tradery.neoforge.clienttest;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.TradeCrateBlock;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.VillageBoard;
import dev.eliasnvx.tradery.village.VillageCouncil;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillageSpecialization;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The 1.2 integrations on a NeoForge client ({@code TRADERY_COMPAT_SHOTS=1 ./gradlew :neoforge:runClientTest
 * -Pcompat=wthit,maps,villages -Pneoforge_version=26.3.0.102}): WTHIT's tooltips on a vending block, a trade crate and
 * a village's bell; the Watch Post funded, which brings Guard Villagers' guards; Tradery's layers on BlueMap and
 * squaremap. Accepts BlueMap's download of the Minecraft client for the run; at the end the world stays up while
 * {@code tradery-hold} exists in the run directory (at most 20 minutes).
 */
final class CompatShots {
    private static final Path HOLD = Path.of("tradery-hold");
    private final TraderyClientTest test;

    CompatShots(TraderyClientTest test) {
        this.test = test;
    }

    void run(BlockPos origin, BlockPos vendor) {
        StringBuilder mods = new StringBuilder("compat mods:");
        for (String mod : List.of("wthit", "jade", "bluemap", "squaremap", "villagersplus", "ctov", "guardvillagers")) {
            mods.append(' ').append(mod).append('=').append(ModList.get().isLoaded(mod));
        }
        note(mods.toString());
        if (acceptBlueMapDownload()) {
            test.command("bluemap reload");
        }

        BlockPos bell = origin.offset(0, 0, 16);
        BlockPos crate = origin.offset(6, 0, 3);
        test.server(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
            for (int i = 0; i < 4; i++) {
                Villager villager = EntityTypes.VILLAGER.spawn(level, bell.offset(-3 + 2 * i, 0, 4), EntitySpawnReason.COMMAND);
                villager.setNoAi(true);
                villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                    i == 3 ? VillagerProfession.LIBRARIAN : VillagerProfession.FARMER).withLevel(2));
                villager.setVillagerXp(1);
            }
            Villages.census(level);
            VillageState village = Villages.at(server, level.dimension(), bell);
            var thresholds = TraderyConfig.server().villages().levels().thresholds();
            var currency = EconomyService.INSTANCE.defaultCurrency();
            Villages.set(server, village, thresholds.get(Math.min(2, thresholds.size() - 1)) + 300, currency.toMinor(java.math.BigDecimal.valueOf(640)));
            ServerPlayer player = test.player(server);
            EconomyService.INSTANCE.setBalance(EconomyService.INSTANCE.account(player.getUUID()), currency.toMinor(java.math.BigDecimal.valueOf(5000)),
                Reason.of(Reasons.ADMIN_SET, "client test"));
            VillageCouncil.contribute(server, village, player, VillageCouncil.minContribution() * 3);
            note("rename: " + VillageCouncil.rename(player, village, "Oakford").message().getString());
            note("specialize: " + VillageCouncil.specialize(player, village, VillageSpecialization.TRADE).message().getString());

            level.setBlockAndUpdate(crate, TraderyBlocks.TRADE_CRATE.get().defaultBlockState().setValue(TradeCrateBlock.FACING, Direction.NORTH));
            TradeCrateBlockEntity entity = (TradeCrateBlockEntity) level.getBlockEntity(crate);
            entity.setOwner(AccountId.player(player.getUUID()), player.nameAndId().name());
            VendorsData.get(server).add(level, crate, AccountId.player(player.getUUID()), true);
            entity.wants().setItem(0, new ItemStack(Items.EMERALD));
            entity.wants().setItem(1, new ItemStack(Items.BREAD));
            entity.setItem(0, new ItemStack(Items.WHEAT, 64));
            return null;
        });

        // WTHIT's tooltips, next to Tradery's own hint
        test.aimAt(vendor);
        test.waitTicks(20);
        test.screenshot("compat_wthit_vending");
        test.aimAt(crate);
        test.waitTicks(20);
        test.screenshot("compat_wthit_crate");
        look(bell.offset(1, 0, -2), Vec3.atCenterOf(bell));
        test.waitTicks(60);
        test.screenshot("compat_wthit_bell");

        // The Watch Post: an iron golem, and with Guard Villagers two guards
        String funded = test.server(server -> {
            VillageState village = Villages.at(server, server.overworld().dimension(), bell);
            VillageBoard.Outcome outcome = VillageFunding.fund(test.player(server), village, Identifier.fromNamespaceAndPath("tradery", "watch_post"));
            return outcome.success() + " " + outcome.message().getString();
        });
        note("watch post: " + funded);
        test.waitTicks(40);
        String around = test.server(server -> {
            ServerLevel level = server.overworld();
            AABB box = new AABB(bell).inflate(24);
            EntityType<?> guard = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("guardvillagers", "guard"));
            long guards = level.getEntitiesOfClass(Entity.class, box, e -> e.getType() == guard && guard != EntityTypes.PIG).size();
            long golems = level.getEntitiesOfClass(IronGolem.class, box).size();
            return golems + " golem(s), " + guards + " guard(s)";
        });
        note("around the bell: " + around);
        test.command("gamemode spectator @p");
        look(bell.offset(-6, 2, -7), Vec3.atCenterOf(bell).add(0, 0, 2));
        test.waitTicks(40);
        test.screenshot("compat_watch_post");
        test.command("gamemode survival @p");
        TraderyClientTest.check(!ModList.get().isLoaded("guardvillagers") || around.endsWith(" 2 guard(s)"),
            "the Watch Post brings two guards with Guard Villagers: " + around);

        maps(bell);
    }

    /** The world saved, a render around the village asked for, then the game waits while {@link #HOLD} exists. */
    private void maps(BlockPos center) {
        if (!ModList.get().isLoaded("bluemap") && !ModList.get().isLoaded("squaremap")) {
            return;
        }
        test.command("save-all flush");
        test.waitTicks(40);
        if (ModList.get().isLoaded("bluemap")) {
            // From the console BlueMap wants a map name; as the player it renders around them
            test.command("execute as @p at @p run bluemap force-update 160");
        }
        if (ModList.get().isLoaded("squaremap")) {
            test.command(String.format(Locale.ROOT, "squaremap radiusrender minecraft:overworld 96 %d %d", center.getX(), center.getZ()));
        }
        test.command("gamemode spectator @p");
        look(center.offset(-10, 14, -18), Vec3.atCenterOf(center));
        try {
            Files.writeString(HOLD, "Delete this file to end the run\n");
            note("holding for the web maps (BlueMap http://localhost:8100, squaremap http://localhost:8080); village at "
                + center.getX() + " " + center.getY() + " " + center.getZ());
            for (int seconds = 0; seconds < 20 * 60 && Files.exists(HOLD); seconds++) {
                test.waitTicks(20);
            }
            Files.deleteIfExists(HOLD);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stands the player at {@code feet} looking at {@code target}. */
    private void look(BlockPos feet, Vec3 target) {
        double x = feet.getX() + 0.5;
        double y = feet.getY();
        double z = feet.getZ() + 0.5;
        double dx = target.x - x;
        double dy = target.y - (y + 1.62);
        double dz = target.z - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        test.command(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        test.waitTicks(5);
        test.client(() -> {
            test.minecraft.player.setYRot(yaw);
            test.minecraft.player.setXRot(pitch);
            test.minecraft.player.setYHeadRot(yaw);
        });
        test.waitTicks(5);
    }

    /** Accepts BlueMap's download of the Minecraft client (Mojang's EULA) in its core.conf, as asked for these shots. */
    private boolean acceptBlueMapDownload() {
        Path core = Path.of("config", "bluemap", "core.conf");
        try {
            if (!Files.exists(core)) {
                return false;
            }
            String text = Files.readString(core);
            if (!text.contains("accept-download: false")) {
                return false;
            }
            Files.writeString(core, text.replace("accept-download: false", "accept-download: true"));
            note("accepted BlueMap's download in " + core.toAbsolutePath());
            return true;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void note(String note) {
        test.notes.add(note);
        System.out.println("Tradery compat shots: " + note);
    }
}
