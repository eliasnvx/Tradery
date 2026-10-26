package dev.eliasnvx.tradery.village;

import java.util.Map;
import java.util.HashMap;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caravans between villages (SPEC "Торговля между деревнями"). A village of level {@code caravans.minLevel} or more
 * sends one every {@code caravans.intervalDays} (fewer with projects) to a known village of another type within
 * {@code caravans.maxDistance} blocks; the purse is the origin's income for that interval, taken from its treasury.
 * It shows up at the destination's bell when that village is loaded, in daytime, on its market day if one comes
 * before the next caravan is due: a named wandering trader with trader llamas that sells the origin's cheap goods
 * and buys the destination's dear goods for money in Tradery's trade window. It leaves after
 * {@code caravans.stayTicks} (vanilla's despawn delay, which pauses while someone trades); its purse then goes back
 * to the origin's treasury.
 * <p>
 * The trader's offers are vanilla {@link MerchantOffer}s with one plain emerald as the money side; what that emerald
 * is worth in each offer is kept in {@link CaravanData} (the trade window shows and charges that). Ordinary wandering
 * traders are left alone. Server thread.
 */
public final class Caravans {
    /** Entity tag of caravan traders: one whose caravan is over refuses to trade instead of trading the placeholders. */
    public static final String TAG = "tradery_caravan";
    /** Villages that found no destination → first day to look again. */
    private static final Map<String, Long> NO_DESTINATION_UNTIL = new HashMap<>();
    /** How often the level tick sends, brings and sees off caravans. */
    static final int CHECK_TICKS = 100;
    /** Llamas with a caravan, like vanilla's wandering trader. */
    static final int LLAMAS = 2;
    /** The trader shows up within this many blocks of the bell, and stays around it. */
    static final int SPAWN_RADIUS = 8;
    static final int HOME_RADIUS = 16;
    static final int LLAMA_RADIUS = 4;
    static final int SPAWN_ATTEMPTS = 10;
    /** A caravan whose trader hasn't been seen for this many days is written off (its purse goes home). */
    static final long STALE_DAYS = 30;
    /** Players this close to the bell hear that a caravan arrived. */
    static final int NEWS_RADIUS = 64;

    private Caravans() {
    }

    static VillagesConfig.Caravans config() {
        return Villages.config().caravans();
    }

    /** {@code caravans.enabled}, and the economy is running. */
    public static boolean enabled() {
        return config().enabled() && EconomyService.INSTANCE.isReady();
    }

    /** Days between this village's caravans: {@code caravans.intervalDays} minus the projects', at least 1. */
    public static int intervalDays(VillageState village) {
        return Math.max(1, config().intervalDays() - VillageProjects.effects(village).caravanDaysLess());
    }

    /** The village is big enough to send caravans and knows its type. */
    public static boolean canSend(VillageState village) {
        return village.census().known() && !village.census().type().isEmpty() && Villages.level(village) >= config().minLevel();
    }

    // ------------------------------------------------------------------ lookups

    /** The caravan of this trader, or null for an ordinary wandering trader (or a villager). */
    public static @Nullable CaravanData.Caravan of(AbstractVillager merchant) {
        if (!(merchant instanceof WanderingTrader) || !(merchant.level() instanceof ServerLevel level)
            || !merchant.getTags().contains(TAG)) {
            return null;
        }
        return CaravanData.get(level.getServer()).byTrader(merchant.getUUID());
    }

    /** Villages a caravan from {@code origin} may go to: known, of another type, same dimension, close enough. */
    public static List<VillageState> destinations(MinecraftServer server, VillageState origin) {
        String type = origin.census().type();
        long maxSqr = (long) config().maxDistance() * config().maxDistance();
        CaravanData caravans = CaravanData.get(server);
        List<VillageState> found = new ArrayList<>();
        for (VillageState village : VillageEconomy.get(server).all()) {
            if (village == origin || village.dimension() != origin.dimension() || !village.census().known()
                || village.census().type().isEmpty() || village.census().type().equals(type) || caravans.receiving(village.key())) {
                continue;
            }
            long dx = village.bell().getX() - origin.bell().getX();
            long dz = village.bell().getZ() - origin.bell().getZ();
            if (dx * dx + dz * dz <= maxSqr) {
                found.add(village);
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ sending

    /**
     * Sends a caravan from {@code origin} to {@code destination} now: the purse leaves the origin's treasury (its
     * income for one caravan interval, at most what's there), the origin's caravan day is today. It arrives on the
     * destination's market day if that comes before the next caravan would be due, else as soon as it can.
     */
    public static CaravanData.Caravan send(MinecraftServer server, VillageState origin, VillageState destination) {
        long today = VillagerPurchases.today(server);
        int interval = intervalDays(origin);
        long purse = Math.min(origin.treasury(), Villages.saturatedMultiply(Villages.dailyIncome(origin), interval));
        if (purse <= 0 || !Villages.takeFromTreasury(server, origin, purse)) {
            purse = 0;
        }
        long marketDay = VillageCommerce.nextMarketDay(server, destination);
        long arriveDay = marketDay >= today && marketDay < today + interval ? marketDay : today;
        CaravanData.Caravan caravan = new CaravanData.Caravan(UUID.randomUUID(), origin.key(), destination.key(), destination.dimension(),
            destination.bell(), today, arriveDay, purse, Optional.empty(), Optional.empty(), today, List.of());
        CaravanData.get(server).add(caravan);
        VillageState.Events e = origin.events;
        Villages.setEvents(server, origin, new VillageState.Events(e.ordersDay(), e.nextOrderId(), e.nextMarketDay(), today,
            e.restorationUntil(), e.raidActive()));
        return caravan;
    }

    /** First day the village may send its next caravan. */
    public static long nextCaravanDay(VillageState village) {
        long last = village.events.caravanDay();
        return last < 0 ? Long.MIN_VALUE : last + intervalDays(village);
    }

    // ------------------------------------------------------------------ arriving

    /** What a caravan from {@code origin} sells and buys at {@code destination}, and what each offer is worth. */
    public record Goods(List<MerchantOffer> offers, List<Long> values) {
    }

    /**
     * The caravan's offers: the origin's cheap goods for sale at the origin's price (fair price × its factor), the
     * destination's dear goods bought at the destination's price, each {@code lot} items at a time and as often as
     * the type's {@code caravan_trades}. Goods without a fair price are left out, and so is buying what it sells (a
     * round trip through one caravan would make money).
     */
    public static Goods goods(VillageState origin, VillageState destination, RandomSource random) {
        List<MerchantOffer> offers = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        Set<Item> selling = new HashSet<>();
        VillageTypes.Region home = VillageTypes.region(origin.census().type());
        for (VillageTypes.Entry entry : home.all()) {
            if (!entry.cheap() || offers.size() >= VillagerTradeMenu.MAX_OFFERS) {
                continue;
            }
            Item item = entry.pick(random);
            long price = item == null ? 0 : lotPrice(item, entry);
            if (price <= 0 || !selling.add(item)) {
                continue;
            }
            int lot = Math.min(entry.lot(), new ItemStack(item).getMaxStackSize());
            offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(item, lot), 0, home.caravanTrades(), 1, 0.0f));
            values.add(Math.max(1, price));
        }
        VillageTypes.Region here = VillageTypes.region(destination.census().type());
        for (VillageTypes.Entry entry : here.all()) {
            if (entry.cheap() || offers.size() >= VillagerTradeMenu.MAX_OFFERS) {
                continue;
            }
            Item item = entry.pick(random);
            if (item == null || selling.contains(item)) {
                continue;
            }
            long payout = lotPrice(item, entry);
            if (payout <= 0) {
                continue;
            }
            int lot = Math.min(entry.lot(), new ItemStack(item).getMaxStackSize());
            offers.add(new MerchantOffer(new ItemStack(item, lot), ItemStack.EMPTY, new ItemStack(Items.EMERALD), 0, here.caravanTrades(), 1, 0.0f));
            values.add(payout);
        }
        return new Goods(List.copyOf(offers), List.copyOf(values));
    }

    /** Fair price × the entry's factor × its lot (minor units, rounded down); 0 without a fair price. */
    private static long lotPrice(Item item, VillageTypes.Entry entry) {
        Optional<BigDecimal> fair = VillagerMarket.fairPrice(new ItemStack(item));
        if (fair.isEmpty()) {
            return 0;
        }
        int lot = Math.min(entry.lot(), new ItemStack(item).getMaxStackSize());
        BigDecimal price = fair.get().multiply(BigDecimal.valueOf(entry.factor())).multiply(BigDecimal.valueOf(lot));
        long minor = VillagerPurchases.toMinor(price);
        return minor == Long.MAX_VALUE ? 0 : minor;
    }

    /** The caravan's name: "Caravan from Desert village (120, -45)". */
    public static Component name(MinecraftServer server, CaravanData.Caravan caravan) {
        return Component.translatableWithFallback("tradery.caravan.name", "Caravan from %s", originName(server, caravan));
    }

    private static Component originName(MinecraftServer server, CaravanData.Caravan caravan) {
        VillageState origin = Villages.byKey(server, caravan.origin);
        return origin != null ? Villages.displayName(origin)
            : Component.translatableWithFallback("tradery.caravan.far_village", "a distant village");
    }

    /**
     * The caravan arrives at {@code at}: a wandering trader with its llamas, named after the origin, with the
     * caravan's offers. False (and nothing spawned) if the origin or destination is gone or the trader can't spawn.
     */
    public static boolean arrive(ServerLevel level, CaravanData.Caravan caravan, BlockPos at) {
        MinecraftServer server = level.getServer();
        VillageState origin = Villages.byKey(server, caravan.origin);
        VillageState destination = Villages.byKey(server, caravan.destination);
        if (origin == null || destination == null || caravan.arrived()) {
            return false;
        }
        Goods goods = goods(origin, destination, level.getRandom());
        WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, at, MobSpawnType.EVENT);
        if (trader == null) {
            return false;
        }
        trader.addTag(TAG);
        trader.setCustomName(name(server, caravan));
        trader.setDespawnDelay(config().stayTicks());
        trader.setWanderTarget(caravan.bell);
        trader.restrictTo(caravan.bell, HOME_RADIUS);
        MerchantOffers offers = trader.getOffers();
        offers.clear();
        offers.addAll(goods.offers());
        for (int i = 0; i < LLAMAS; i++) {
            BlockPos spot = findSpawn(level, trader.blockPosition(), LLAMA_RADIUS);
            TraderLlama llama = spot == null ? null : EntityType.TRADER_LLAMA.spawn(level, spot, MobSpawnType.EVENT);
            if (llama != null) {
                llama.setLeashedTo(trader, true);
            }
        }
        caravan.trader = trader.getUUID();
        caravan.lastPos = trader.blockPosition();
        caravan.lastSeenDay = VillagerPurchases.today(server);
        caravan.values = goods.values();
        CaravanData.get(server).setDirty();
        Vec3 bell = Vec3.atCenterOf(caravan.bell);
        Component news = Messages.tr("tradery.caravan.arrived", "A caravan from %s has come to the market of %s",
            originName(server, caravan), Villages.displayName(destination));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(bell) <= (double) NEWS_RADIUS * NEWS_RADIUS) {
                player.sendSystemMessage(news);
            }
        }
        return true;
    }

    /** A free spot for a wandering trader near {@code center} (vanilla's spawner rules), or null. */
    static @Nullable BlockPos findSpawn(ServerLevel level, BlockPos center, int radius) {
        SpawnPlacements.Type placement = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);
        RandomSource random = level.getRandom();
        for (int i = 0; i < SPAWN_ATTEMPTS; i++) {
            int x = center.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            int y = level.getHeight(SpawnPlacements.getHeightmapType(EntityType.WANDERING_TRADER), x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (NaturalSpawner.isSpawnPositionOk(placement, level, pos, EntityType.WANDERING_TRADER) && hasRoom(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean hasRoom(LevelReader level, BlockPos pos) {
        for (BlockPos at : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the money

    /**
     * After a trade's money moved: the player paid {@code paid} into the purse and got {@code payout} out of it (the
     * trade window checked the purse covers it). The destination's prosperity grows with the turnover, as for a
     * villager's window trade.
     */
    static void settle(MinecraftServer server, CaravanData.Caravan caravan, long paid, long payout) {
        caravan.purse = Math.max(0, Villages.saturatedAdd(caravan.purse, paid) - payout);
        CaravanData.get(server).setDirty();
        VillageState destination = Villages.byKey(server, caravan.destination);
        if (destination != null) {
            Villages.addProsperity(server, destination, Villages.points(Villages.saturatedAdd(paid, payout), Villages.POINTS_WINDOW_TRADE));
        }
    }

    /** Sets the caravan's purse (tests and admin tools). */
    public static void setPurse(MinecraftServer server, CaravanData.Caravan caravan, long purse) {
        caravan.purse = Math.max(0, purse);
        CaravanData.get(server).setDirty();
    }

    /** The caravan is over: its purse goes home to the origin's treasury (lost if the origin is gone). */
    public static void close(MinecraftServer server, CaravanData.Caravan caravan) {
        VillageState origin = Villages.byKey(server, caravan.origin);
        if (origin != null && caravan.purse > 0) {
            Villages.addToTreasury(server, origin, caravan.purse);
        }
        caravan.purse = 0;
        CaravanData.get(server).remove(caravan.id);
    }

    /** The server stopped: a new world has its own villages. */
    public static void onServerStopped() {
        NO_DESTINATION_UNTIL.clear();
    }

    // ------------------------------------------------------------------ level tick

    /** Level tick: every {@link #CHECK_TICKS}, villages of this level send caravans and caravans here arrive or leave. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (enabled()) {
            sendDue(level);
        }
        for (CaravanData.Caravan caravan : CaravanData.get(server).all()) {
            if (caravan.dimension == level.dimension()) {
                update(level, caravan);
            }
        }
    }

    /** Villages of this level whose caravan is due send one, each to a destination picked at random. */
    static void sendDue(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        CaravanData caravans = CaravanData.get(server);
        for (VillageState origin : VillageEconomy.get(server).all()) {
            if (origin.dimension() != level.dimension() || !canSend(origin) || caravans.sending(origin.key())) {
                continue;
            }
            long last = origin.events.caravanDay();
            if (last >= 0 && today >= last && today - last < intervalDays(origin)) {
                continue;
            }
            Long retry = NO_DESTINATION_UNTIL.get(origin.key());
            if (retry != null && today < retry) {
                continue;
            }
            List<VillageState> destinations = destinations(server, origin);
            if (destinations.isEmpty()) {
                // No village of another type in reach: not worth a look at every village again before tomorrow
                NO_DESTINATION_UNTIL.put(origin.key(), today + 1);
            } else {
                NO_DESTINATION_UNTIL.remove(origin.key());
                send(server, origin, destinations.get(level.getRandom().nextInt(destinations.size())));
            }
        }
    }

    /**
     * One caravan of this level: on the road, it arrives when its day came and the destination is loaded in daytime
     * (no raid on, the bell still there), or gives up after an interval; at the market, it is over once its trader is
     * gone from loaded chunks (left, or killed) or hasn't been seen for {@link #STALE_DAYS}.
     */
    public static void update(ServerLevel level, CaravanData.Caravan caravan) {
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        if (!caravan.arrived()) {
            VillageState origin = Villages.byKey(server, caravan.origin);
            VillageState destination = Villages.byKey(server, caravan.destination);
            int interval = origin != null ? intervalDays(origin) : config().intervalDays();
            if (destination == null || today >= caravan.arriveDay + interval || today < caravan.sentDay) {
                close(server, caravan);
                return;
            }
            if (today >= caravan.arriveDay && enabled() && level.isPositionEntityTicking(caravan.bell) && level.isDay()
                && level.getRaidAt(caravan.bell) == null && level.getPoiManager().existsAtPosition(PoiTypes.MEETING, caravan.bell)) {
                BlockPos at = findSpawn(level, caravan.bell, SPAWN_RADIUS);
                if (at != null) {
                    arrive(level, caravan, at);
                }
            }
            return;
        }
        Entity trader = level.getEntity(caravan.trader);
        if (trader instanceof WanderingTrader alive && alive.isAlive()) {
            BlockPos pos = alive.blockPosition();
            if (caravan.lastPos == null || caravan.lastSeenDay != today || (pos.getX() >> 4) != (caravan.lastPos.getX() >> 4)
                || (pos.getZ() >> 4) != (caravan.lastPos.getZ() >> 4)) {
                CaravanData.get(server).setDirty();
            }
            caravan.lastPos = pos;
            caravan.lastSeenDay = today;
            return;
        }
        // Not found where it was last seen, with that chunk's entities loaded: it left (vanilla's despawn) or died.
        // (One that walked out of the loaded area in the last few seconds is taken for gone too: its caravan ends a
        // bit early, the trader refuses to trade and despawns on its own.)
        BlockPos last = caravan.lastPos != null ? caravan.lastPos : caravan.bell;
        if (level.isPositionEntityTicking(last) || today - caravan.lastSeenDay >= STALE_DAYS || today < caravan.lastSeenDay) {
            close(server, caravan);
        }
    }

    // ------------------------------------------------------------------ the trader

    /**
     * Right-click on a wandering trader ({@code WanderingTraderMixin}). Null: vanilla goes on (an ordinary trader, or
     * vanilla's own exceptions: a spawn egg in hand, a baby, someone else trading). A caravan trader opens Tradery's
     * trade window in money instead of vanilla's; one whose caravan is over, or while caravans are off, refuses (its
     * offers are placeholders, never worth trading for emeralds).
     */
    public static @Nullable InteractionResult interact(WanderingTrader trader, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !trader.getTags().contains(TAG)) {
            return null;
        }
        if (player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG) || !trader.isAlive() || trader.isTrading() || trader.isBaby()) {
            return null;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }
        CaravanData.Caravan caravan = of(trader);
        if (caravan == null) {
            serverPlayer.sendSystemMessage(Messages.tr("tradery.caravan.closed", "This caravan has packed up"), true);
            return InteractionResult.CONSUME;
        }
        if (!enabled()) {
            serverPlayer.sendSystemMessage(Messages.tr("tradery.caravan.off", "The caravan isn't trading right now"), true);
            return InteractionResult.CONSUME;
        }
        if (trader.getOffers().isEmpty()) {
            return InteractionResult.CONSUME;
        }
        VillagerTrading.open(serverPlayer, trader);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ board news

    /** Lines for the board's Market tab: caravans coming here or at the market, this village's own caravan. */
    public static List<Component> news(MinecraftServer server, VillageState village) {
        List<Component> lines = new ArrayList<>();
        long today = VillagerPurchases.today(server);
        boolean sending = false;
        for (CaravanData.Caravan caravan : CaravanData.get(server).all()) {
            if (caravan.destination.equals(village.key())) {
                Component from = originName(server, caravan);
                if (caravan.arrived()) {
                    lines.add(Messages.tr("tradery.caravan.news.here", "A caravan from %s is at the market", from));
                } else if (caravan.arriveDay <= today) {
                    lines.add(Messages.tr("tradery.caravan.news.today", "A caravan from %s arrives today", from));
                } else {
                    lines.add(caravan.arriveDay - today == 1
                        ? Messages.tr("tradery.caravan.news.tomorrow", "A caravan from %s arrives tomorrow", from)
                        : Messages.tr("tradery.caravan.news.coming", "A caravan from %s arrives in %s days", from, caravan.arriveDay - today));
                }
            }
            if (caravan.origin.equals(village.key())) {
                sending = true;
                VillageState destination = Villages.byKey(server, caravan.destination);
                Component to = destination != null ? Villages.displayName(destination)
                    : Component.translatableWithFallback("tradery.caravan.far_village", "a distant village");
                lines.add(Messages.tr("tradery.caravan.news.sent", "Our caravan went to %s", to));
            }
        }
        if (!sending && config().enabled()) {
            if (!canSend(village)) {
                lines.add(Messages.tr("tradery.caravan.news.level", "Caravans set out from villages of level %s (%s)",
                    config().minLevel(), Villages.levelName(config().minLevel())));
            } else {
                long next = nextCaravanDay(village);
                lines.add(next > today
                    ? Messages.tr("tradery.caravan.news.next", "Our next caravan sets out in %s days", next - today)
                    : Messages.tr("tradery.caravan.news.ready", "Our next caravan sets out when a village of another type is near"));
            }
        }
        return List.copyOf(lines);
    }
}
