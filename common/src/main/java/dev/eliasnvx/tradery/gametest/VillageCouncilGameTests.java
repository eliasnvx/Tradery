package dev.eliasnvx.tradery.gametest;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.menu.VillageBoardMenu;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageBoard;
import dev.eliasnvx.tradery.village.VillageCommerce;
import dev.eliasnvx.tradery.village.VillageCouncil;
import dev.eliasnvx.tradery.village.VillageFunding;
import dev.eliasnvx.tradery.village.VillageSpecialization;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import dev.eliasnvx.tradery.village.VillageCommerce;
import dev.eliasnvx.tradery.village.VillageProjects;
import dev.eliasnvx.tradery.village.VillagerCustomers;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** The village patron and specialization (SPEC "Покровитель деревни", "Специализация"). */
public final class VillageCouncilGameTests {
    private static final BlockPos BELL = new BlockPos(0, 1, 0);

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("council_patron_elected", VillageCouncilGameTests::patronElected),
        new TraderyGameTests.Entry("council_rename", VillageCouncilGameTests::rename),
        new TraderyGameTests.Entry("council_tax_share", VillageCouncilGameTests::taxShare),
        new TraderyGameTests.Entry("council_specialization", VillageCouncilGameTests::specialization),
        new TraderyGameTests.Entry("council_specialization_project", VillageCouncilGameTests::specializationProject));
    private static final ResourceLocation EXCHANGE_OFFICE = ResourceLocation.fromNamespaceAndPath("tradery", "exchange_office");

    private VillageCouncilGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static VillageState village(GameTestHelper helper) {
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        VillageState village = Villages.at(server(helper), helper.getLevel().dimension(), helper.absolutePos(BELL));
        Villages.set(server(helper), village, 0, 0);
        return village;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, long balance) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setPos(pos.x, pos.y, pos.z);
        EconomyService.INSTANCE.setBalance(account(player), balance, Reason.of(Reasons.ADMIN_SET, "test"));
        return player;
    }

    private static LedgerAccount account(ServerPlayer player) {
        return EconomyService.INSTANCE.account(player.getUUID());
    }

    private static long balance(ServerPlayer player) {
        return account(player).balance(EconomyService.INSTANCE.defaultCurrency());
    }

    private static long minor(double major) {
        return Math.round(major * Math.pow(10, EconomyService.INSTANCE.defaultCurrency().decimals()));
    }

    private static void donate(GameTestHelper helper, ServerPlayer player, VillageState village, long amount) {
        VillageBoard.Outcome outcome = VillageFunding.donate(player, village, amount);
        helper.assertTrue(outcome.success(), "donated: " + outcome.message().getString());
    }

    private static VillageBoardMenu menu(GameTestHelper helper, ServerPlayer player, VillageState village) {
        BlockPos board = new BlockPos(1, 1, 2);
        helper.setBlock(board, TraderyBlocks.VILLAGE_BOARD.get());
        return new VillageBoardMenu(1, player.getInventory(), VillageBoard.snapshot(player, village, helper.absolutePos(board)),
            helper.getLevel(), village.key());
    }

    /** Prosperity for village level {@code level}. */
    private static double prosperityFor(int level) {
        return TraderyConfig.server().villages().levels().thresholds().get(level - 1);
    }

    // ------------------------------------------------------------------ tests

    /**
     * The biggest contributor from the minimum up is the patron; a tie keeps the patron; projects and donations both
     * count; the window shows it all and survives its codec.
     */
    public static void patronElected(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper);
        ServerPlayer alice = player(helper, minor(1000));
        ServerPlayer bob = player(helper, minor(1000));
        long min = VillageCouncil.minContribution();
        helper.assertTrue(min > 0, "a minimum");

        donate(helper, alice, village, min / 2);
        helper.assertTrue(village.patron().isEmpty(), "below the minimum: no patron");
        donate(helper, alice, village, min - min / 2);
        helper.assertValueEqual(village.patron().orElse(null), alice.getUUID(), "the minimum reached: Alice is the patron");
        helper.assertValueEqual(VillageCouncil.contribution(village, alice.getUUID()), min, "her contribution");

        donate(helper, bob, village, min);
        helper.assertValueEqual(village.patron().orElse(null), alice.getUUID(), "a tie keeps the patron");
        donate(helper, bob, village, 1);
        helper.assertValueEqual(village.patron().orElse(null), bob.getUUID(), "one more: Bob is the patron");
        List<VillageState.Contribution> top = VillageCouncil.top(village);
        helper.assertValueEqual(top.size(), 2, "two contributors");
        helper.assertValueEqual(top.getFirst().amount(), min + 1, "Bob first");
        helper.assertValueEqual(top.get(1).amount(), min, "then Alice");

        // The window: what it shows and that it survives the trip
        VillageBoardMenu menu = menu(helper, bob, village);
        VillageBoardMenu.Council council = menu.data().council();
        helper.assertTrue(council.viewerIsPatron(), "Bob sees he's the patron");
        helper.assertValueEqual(council.patronContribution(), min + 1, "the patron's contribution");
        helper.assertValueEqual(council.minContribution(), min, "the minimum");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        VillageBoardMenu.Data.STREAM_CODEC.encode(buf, menu.data());
        VillageBoardMenu.Data decoded = VillageBoardMenu.Data.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(decoded.council(), council, "the Patron tab survives the codec");
        helper.assertFalse(VillageBoard.snapshot(alice, village, menu.data().board()).council().viewerIsPatron(), "Alice isn't any more");
        helper.assertValueEqual(VillageCouncil.contribution(village, UUID.randomUUID()), 0L, "a stranger gave nothing");
        VendingTrades.forget(alice);
        VendingTrades.forget(bob);
        helper.succeed();
    }

    /** Only the patron renames; the name is cleaned and bounded; an empty one brings the old name back. */
    public static void rename(GameTestHelper helper) {
        VillageState village = village(helper);
        ServerPlayer patron = player(helper, minor(1000));
        ServerPlayer other = player(helper, minor(1000));
        donate(helper, patron, village, VillageCouncil.minContribution());
        String original = Villages.displayName(village).getString();

        helper.assertFalse(VillageCouncil.rename(other, village, "Mine").success(), "not the patron");
        helper.assertValueEqual(village.name(), "", "unchanged");
        helper.assertTrue(VillageCouncil.rename(patron, village, "  Green §cHollow\u0007  ").success(), "renamed");
        helper.assertValueEqual(village.name(), "Green Hollow", "formatting, control characters and extra spaces gone");
        helper.assertValueEqual(Villages.displayName(village).getString(), "Green Hollow", "the new name everywhere");
        helper.assertFalse(VillageCouncil.rename(patron, village, "x".repeat(VillageCouncil.MAX_NAME + 1)).success(), "too long");
        helper.assertFalse(VillageCouncil.rename(patron, village, "Green Hollow").success(), "the same name");
        helper.assertValueEqual(village.name(), "Green Hollow", "kept");

        // Through the window (the payload's path), with its checks
        VillageBoardMenu menu = menu(helper, patron, village);
        menu.rename(patron, "Oakford");
        helper.assertValueEqual(village.name(), "Oakford", "renamed through the window");
        helper.assertValueEqual(menu.data().info().name().getString(), "Oakford", "the window got fresh data");
        Vec3 far = Vec3.atCenterOf(menu.data().board()).add(20, 0, 0);
        patron.setPos(far.x, far.y, far.z);
        menu.rename(patron, "Faraway");
        helper.assertValueEqual(village.name(), "Oakford", "nothing from afar");
        Vec3 near = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        patron.setPos(near.x, near.y, near.z);
        VendingTrades.forget(patron);

        helper.assertTrue(VillageCouncil.rename(patron, village, "   ").success(), "an empty name");
        helper.assertValueEqual(village.name(), "", "cleared");
        helper.assertValueEqual(Villages.displayName(village).getString(), original, "the old name is back");
        VendingTrades.forget(other);
        helper.succeed();
    }

    /** The patron's share comes out of the trade tax; when it can't be paid, the treasury keeps the whole tax. */
    public static void taxShare(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER)
            .setLevel(2));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BELL)));
        var config = TraderyConfig.server().villages();
        long paid = minor(100);
        long tax = Money.percentOf(paid, config.tradeTaxPercent());
        long share = Money.percentOf(tax, config.council().patronTaxSharePercent());
        helper.assertTrue(share > 0, "a share to test");

        // No patron: the treasury gets it all
        helper.assertValueEqual(Villages.onWindowTrade(villager, paid, 0), tax, "the tax");
        helper.assertValueEqual(village.treasury(), tax, "all of it in the treasury");

        ServerPlayer patron = player(helper, minor(1000));
        donate(helper, patron, village, VillageCouncil.minContribution());
        long treasury = village.treasury();
        long before = balance(patron);
        helper.assertValueEqual(Villages.onWindowTrade(villager, paid, 0), tax, "the same tax");
        helper.assertValueEqual(balance(patron), before + share, "the patron got the share");
        helper.assertValueEqual(village.treasury(), treasury + tax - share, "out of the tax");

        // A locked account can't take it: the treasury keeps the whole tax
        EconomyService.INSTANCE.setLocked(account(patron), true);
        try {
            treasury = village.treasury();
            Villages.onWindowTrade(villager, paid, 0);
            helper.assertValueEqual(balance(patron), before + share, "nothing more for a locked patron");
            helper.assertValueEqual(village.treasury(), treasury + tax, "the treasury keeps it all");
        } finally {
            EconomyService.INSTANCE.setLocked(account(patron), false);
        }
        villager.discard();
        VendingTrades.forget(patron);
        helper.succeed();
    }

    /**
     * A specialization's project: refused (nothing charged) until the village has that specialization, then built by
     * the bell; its effects (cheaper trading places, more visits) hold only while the specialization does.
     */
    public static void specializationProject(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = -2; y <= 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
                for (int y = 2; y < 32; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.BARRIER)) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }
        BlockPos bell = new BlockPos(2, 1, 4);
        helper.setBlock(bell, Blocks.BELL);
        VillageState village = Villages.at(server, level.dimension(), helper.absolutePos(bell));
        int minLevel = TraderyConfig.server().villages().council().specializationMinLevel();
        Villages.set(server, village, prosperityFor(minLevel), 0);
        ServerPlayer patron = player(helper, minor(5000));
        // Out of the way of the building
        Vec3 aside = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 4)));
        patron.setPos(aside.x, aside.y, aside.z);
        donate(helper, patron, village, VillageCouncil.minContribution());
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(4, 5, 4)));
        VillageProjects.Project project = VillageProjects.get(EXCHANGE_OFFICE).orElseThrow();
        helper.assertValueEqual(project.specialization(), VillageSpecialization.TRADE, "the exchange office is a trade project");
        long rent = VillageCommerce.rentPerDay(village);
        double visits = VillagerCustomers.visitFactor(server, village, UUID.randomUUID(), false);

        long before = balance(patron);
        helper.assertFalse(VillageFunding.fund(patron, village, EXCHANGE_OFFICE, 4, area).success(), "not a trade village yet");
        helper.assertValueEqual(balance(patron), before, "nothing charged");
        helper.assertTrue(VillageCouncil.specialize(patron, village, VillageSpecialization.TRADE).success(), "trade");
        VillageBoard.Outcome built = VillageFunding.fund(patron, village, EXCHANGE_OFFICE, 4, area);
        helper.assertTrue(built.success(), "built: " + built.message().getString());
        helper.assertValueEqual(balance(patron), before - EconomyService.INSTANCE.defaultCurrency().toMinor(project.cost()), "paid");
        int tables = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) {
                tables++;
            }
        }
        helper.assertValueEqual(tables, 1, "the office's cartography table");
        helper.assertValueEqual(VillageCommerce.rentPerDay(village), Math.round(rent * 0.75), "trading places 25% cheaper");
        double bonus = 1 + TraderyConfig.server().villages().council().specializationBonus();
        helper.assertTrue(Math.abs(VillagerCustomers.visitFactor(server, village, UUID.randomUUID(), false) - visits * bonus * 1.1) < 1e-9,
            "visits: the specialization and the office");
        helper.assertTrue(VillageCouncil.contribution(village, patron.getUUID()) > VillageCouncil.minContribution(), "the project counts too");

        // Another specialization: the office stands, its effects sleep
        VillageCouncil.backdateSpecialization(server, village, TraderyConfig.server().villages().council().specializationChangeDays());
        helper.assertTrue(VillageCouncil.specialize(patron, village, VillageSpecialization.CRAFTS).success(), "crafts");
        helper.assertValueEqual(VillageCommerce.rentPerDay(village), rent, "the full rent again");
        helper.assertTrue(Math.abs(VillagerCustomers.visitFactor(server, village, UUID.randomUUID(), false) - visits) < 1e-9, "the usual visits");
        VendingTrades.forget(patron);
        helper.succeed();
    }

    /**
     * The patron picks the specialization from Town level, once every few days; it gives its bonus while the village
     * stays a Town; nobody else picks it.
     */
    public static void specialization(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        VillageState village = village(helper);
        ServerPlayer patron = player(helper, minor(1000));
        ServerPlayer other = player(helper, minor(1000));
        donate(helper, patron, village, VillageCouncil.minContribution());
        int minLevel = TraderyConfig.server().villages().council().specializationMinLevel();
        double bonus = 1 + TraderyConfig.server().villages().council().specializationBonus();

        Villages.set(server, village, prosperityFor(minLevel - 1), village.treasury());
        helper.assertFalse(VillageCouncil.specialize(patron, village, VillageSpecialization.FARMING).success(), "too small a village");
        Villages.set(server, village, prosperityFor(minLevel), village.treasury());
        double wages = Villages.wageFactor(village);
        double visits = VillageCommerce.visitFactor(server, village, other.getUUID());
        helper.assertFalse(VillageCouncil.specialize(other, village, VillageSpecialization.FARMING).success(), "not the patron");

        VillageBoardMenu menu = menu(helper, patron, village);
        helper.assertTrue(menu.clickMenuButton(patron, VillageBoardMenu.buttonId(VillageBoardMenu.SPECIALIZE, VillageSpecialization.FARMING.ordinal())),
            "the button");
        helper.assertValueEqual(village.specialization().orElse(null), VillageSpecialization.FARMING, "farming through the window");
        helper.assertTrue(Math.abs(VillageCouncil.factor(village, VillageSpecialization.FARMING) - bonus) < 1e-9, "the farming bonus");
        helper.assertValueEqual(VillageCouncil.factor(village, VillageSpecialization.CRAFTS), 1.0, "no crafts bonus");
        helper.assertFalse(menu.clickMenuButton(patron, VillageBoardMenu.buttonId(VillageBoardMenu.SPECIALIZE, 7)), "an unknown index");
        VendingTrades.forget(patron);

        helper.assertFalse(VillageCouncil.specialize(patron, village, VillageSpecialization.FARMING).success(), "the same one");
        helper.assertFalse(VillageCouncil.specialize(patron, village, VillageSpecialization.CRAFTS).success(), "too soon");
        helper.assertTrue(VillageCouncil.daysUntilChange(server, village) > 0, "days to wait");
        VillageCouncil.backdateSpecialization(server, village, TraderyConfig.server().villages().council().specializationChangeDays());
        helper.assertTrue(VillageCouncil.specialize(patron, village, VillageSpecialization.CRAFTS).success(), "crafts after the wait");
        helper.assertTrue(Math.abs(Villages.wageFactor(village) - wages * bonus) < 1e-9, "wages and income up");
        VillageCouncil.backdateSpecialization(server, village, TraderyConfig.server().villages().council().specializationChangeDays());
        helper.assertTrue(VillageCouncil.specialize(patron, village, VillageSpecialization.TRADE).success(), "trade");
        helper.assertTrue(Math.abs(VillageCommerce.visitFactor(server, village, other.getUUID()) - visits * bonus) < 1e-9, "more visits");
        helper.assertTrue(Math.abs(Villages.wageFactor(village) - wages) < 1e-9, "no crafts bonus any more");

        // Below Town the bonus sleeps, the choice stays
        Villages.set(server, village, prosperityFor(minLevel - 1), village.treasury());
        helper.assertTrue(VillageCouncil.active(village).isEmpty(), "not in effect below Town");
        helper.assertValueEqual(VillageCouncil.factor(village, VillageSpecialization.TRADE), 1.0, "no bonus");
        helper.assertValueEqual(village.specialization().orElse(null), VillageSpecialization.TRADE, "still picked");
        VendingTrades.forget(other);
        helper.succeed();
    }
}
