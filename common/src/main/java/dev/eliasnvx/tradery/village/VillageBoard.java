package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.menu.VillageBoardMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The village board (SPEC "Доска деревни"): opening its window, what the window shows, the villagers who hear about
 * what players do for the village, and the look-at hint over a bell. Orders are in {@link VillageOrders}, projects in
 * {@link VillageFunding}. Server thread.
 */
public final class VillageBoard {
    /** Villagers this close to the bell hear of deliveries and funding, and give the viewer's reputation. */
    public static final int VILLAGERS_RADIUS = 32;
    /** A player asks about a bell at most this often (ticks); the client asks every two seconds while looking. */
    static final int BELL_REQUEST_TICKS = 10;
    private static final EntityTypeTest<Entity, Villager> VILLAGERS = EntityTypeTest.forClass(Villager.class);
    private static final Map<UUID, Long> LAST_BELL_REQUEST = new HashMap<>();
    /** Game time of each player's last project funding; a stall search is costly, so one per {@link #FUND_TICKS}. */
    private static final Map<UUID, Long> LAST_FUND = new HashMap<>();
    static final int FUND_TICKS = 20;

    private VillageBoard() {
    }

    /** What an action did; nothing changed when {@code success} is false. */
    public record Outcome(boolean success, Component message) {
        public static Outcome fail(Component message) {
            return new Outcome(false, message);
        }

        public static Outcome done(Component message) {
            return new Outcome(true, message);
        }
    }

    // ------------------------------------------------------------------ the window

    /** The player used a board at {@code board}: its village's window, or "There's no village here". */
    public static void open(ServerPlayer player, BlockPos board) {
        ServerLevel level = player.serverLevel();
        VillageState village = Villages.near(level, board);
        if (village == null) {
            player.displayClientMessage(Messages.tr("tradery.board.no_village", "There's no village here"), true);
            return;
        }
        VillageOrders.refresh(level.getServer(), village);
        VillageBoardMenu.Data data = snapshot(player, village, board);
        String key = village.key();
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new VillageBoardMenu(id, inventory, data, level, key),
                Messages.tr("block.tradery.village_board", "Village Board")),
            VillageBoardMenu.Data.STREAM_CODEC, data);
    }

    /** Everything the window shows to {@code viewer}, cut to what the payload carries. */
    public static VillageBoardMenu.Data snapshot(ServerPlayer viewer, VillageState village, BlockPos board) {
        MinecraftServer server = viewer.level().getServer();
        Villages.touch(server, village);
        long today = VillagerPurchases.today(server);
        VillageState.Census census = village.census();

        List<VillageBoardMenu.Profession> professions = new ArrayList<>();
        census.professions().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(VillageBoardMenu.MAX_PROFESSIONS)
            .forEach(entry -> professions.add(new VillageBoardMenu.Profession(TraderyPayloads.clip(entry.getKey(), 256), entry.getValue())));
        ServerLevel villageLevel = server.getLevel(village.dimension());
        int reputation = villageLevel == null ? 0 : reputation(villageLevel, village, viewer.getUUID());
        VillageBoardMenu.Info info = new VillageBoardMenu.Info(Villages.displayName(village), Villages.level(village), village.prosperity(),
            Villages.nextThreshold(village), census.population(), census.employed(), List.copyOf(professions), village.treasury(),
            Villages.dailyIncome(village), reputation, village.history(), saving(village));

        List<VillageBoardMenu.OrderView> orders = new ArrayList<>();
        for (VillageState.Order order : village.orders) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(order.item());
            if (item.isPresent() && orders.size() < VillageBoardMenu.MAX_ORDERS) {
                orders.add(new VillageBoardMenu.OrderView(order.id(), new ItemStack(item.get()), order.count(), order.delivered(),
                    order.rewardPerItem(), order.expiresDay(), order.restoration()));
            }
        }

        List<VillageBoardMenu.ProjectView> projects = new ArrayList<>();
        for (VillageProjects.Project project : VillageProjects.all()) {
            if (projects.size() == VillageBoardMenu.MAX_PROJECTS) {
                break;
            }
            projects.add(new VillageBoardMenu.ProjectView(project.id(), new ItemStack(project.icon()), VillagerPurchases.toMinor(project.cost()),
                project.level(), village.projects.contains(project.id().toString())));
        }

        List<VillageBoardMenu.ShiftView> shifts = new ArrayList<>();
        for (VillageCommerce.PriceShift shift : VillageCommerce.priceShifts(village)) {
            if (shifts.size() == VillageBoardMenu.MAX_SHIFTS) {
                break;
            }
            shifts.add(new VillageBoardMenu.ShiftView(shift.icon().copyWithCount(1), shift.factor()));
        }
        List<Component> news = VillageCommerce.caravanNews(server, village);
        VillageBoardMenu.Market market = new VillageBoardMenu.Market(List.copyOf(shifts), VillageCommerce.nextMarketDay(server, village),
            VillageCommerce.isMarketDay(server, village), VillageCommerce.rentPerDay(village), Villages.config().rent().days(),
            VillageCommerce.licenceUntil(server, village, viewer.getUUID()).orElse(-1),
            List.copyOf(news.subList(0, Math.min(news.size(), VillageBoardMenu.MAX_NEWS))));

        return new VillageBoardMenu.Data(board.immutable(), today, info, List.copyOf(orders), List.copyOf(projects), market);
    }

    /** What the village saves for, for the Village tab. */
    static VillageBoardMenu.Saving saving(VillageState village) {
        VillageBuildings.Building next = VillageBuildings.next(village);
        return next == null ? VillageBoardMenu.Saving.NONE
            : new VillageBoardMenu.Saving(VillageBuildings.name(next.id()), village.savings(), VillageBuildings.costMinor(next));
    }

    /** "Rent" on the Market tab. */
    public static Outcome rent(ServerPlayer player, VillageState village) {
        VillageCommerce.Result result = VillageCommerce.rent(player, village);
        return new Outcome(result.success(), result.message());
    }

    // ------------------------------------------------------------------ the village's villagers

    /** The village's loaded adult villagers within {@link #VILLAGERS_RADIUS} blocks of its bell. */
    public static List<Villager> villagers(ServerLevel level, VillageState village) {
        if (level.dimension() != village.dimension()) {
            return List.of();
        }
        GlobalPos bell = GlobalPos.of(village.dimension(), village.bell());
        AABB area = new AABB(village.bell()).inflate(VILLAGERS_RADIUS);
        return level.getEntities(VILLAGERS, area, villager -> villager.isAlive()
            && Villages.bellOf(villager).map(bell::equals).orElse(false)
            && villager.blockPosition().closerThan(village.bell(), VILLAGERS_RADIUS));
    }

    /** The player's reputation with the village: vanilla gossip of its villagers near the bell, averaged (0 without any). */
    public static int reputation(ServerLevel level, VillageState village, UUID player) {
        List<Villager> villagers = villagers(level, village);
        if (villagers.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (Villager villager : villagers) {
            total += villager.getGossips().getReputation(player, type -> true);
        }
        return (int) Math.round(total / (double) villagers.size());
    }

    /** Every villager of the village near the bell remembers {@code amount} of {@code type} about the player. */
    static void gossip(MinecraftServer server, VillageState village, UUID player, GossipType type, int amount) {
        ServerLevel level = server.getLevel(village.dimension());
        if (level == null || amount <= 0) {
            return;
        }
        for (Villager villager : villagers(level, village)) {
            villager.getGossips().add(player, type, amount);
        }
    }

    // ------------------------------------------------------------------ bell hint

    /**
     * What the hint over a bell shows.
     *
     * @param prosperity    points, rounded down
     * @param nextThreshold points for the next level, -1 at the top
     * @param marketDayIn   days to the next market day: 0 today, -1 none planned
     */
    public record BellInfo(Component name, int level, int prosperity, int nextThreshold, int marketDayIn) {
    }

    /** The village of this bell if it is one Tradery knows; looking never creates a village. */
    public static Optional<BellInfo> bellInfo(ServerLevel level, BlockPos bell) {
        if (!level.getBlockState(bell).is(Blocks.BELL)) {
            return Optional.empty();
        }
        MinecraftServer server = level.getServer();
        VillageState village = Villages.byKey(server, Villages.key(level.dimension(), bell));
        if (village == null) {
            return Optional.empty();
        }
        long next = VillageCommerce.nextMarketDay(server, village);
        long today = VillagerPurchases.today(server);
        int marketDayIn = next < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, next - today));
        double threshold = Villages.nextThreshold(village);
        return Optional.of(new BellInfo(Villages.displayName(village), Villages.level(village), clampToInt(village.prosperity()),
            threshold < 0 ? -1 : clampToInt(threshold), marketDayIn));
    }

    /**
     * A client asks about the bell it looks at. Checked: rate (one request per {@link #BELL_REQUEST_TICKS}), reach,
     * a loaded chunk, a bell there. Unknown bells get an answer too, so the client stops asking for a while.
     */
    public static void onBellRequest(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        long now = level.getServer().getTickCount();
        Long last = LAST_BELL_REQUEST.get(player.getUUID());
        if (last != null && now - last < BELL_REQUEST_TICKS && now >= last) {
            return;
        }
        LAST_BELL_REQUEST.put(player.getUUID(), now);
        if (!player.canInteractWithBlock(pos, 1.0) || !level.isLoaded(pos)) {
            return;
        }
        Optional<BellInfo> info = bellInfo(level, pos);
        Platform.get().sendToPlayer(player, info
            .map(i -> new TraderyPayloads.BellInfoPayload(pos.immutable(), true, i.name(), i.level(), i.prosperity(), i.nextThreshold(), i.marketDayIn()))
            .orElseGet(() -> new TraderyPayloads.BellInfoPayload(pos.immutable(), false, Component.empty(), 0, 0, -1, -1)));
    }

    /** One project funding per {@link #FUND_TICKS} per player. */
    public static boolean allowFund(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_FUND.get(player.getUUID());
        if (last != null && now - last < FUND_TICKS) {
            return false;
        }
        LAST_FUND.put(player.getUUID(), now);
        return true;
    }

    public static void forget(ServerPlayer player) {
        LAST_FUND.remove(player.getUUID());
        LAST_BELL_REQUEST.remove(player.getUUID());
    }

    public static void clear() {
        LAST_FUND.clear();
        LAST_BELL_REQUEST.clear();
    }

    private static int clampToInt(double value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.floor(value)));
    }
}
