package dev.eliasnvx.tradery.village;

import java.util.HashMap;
import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.CounterTrades;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.vending.VendorsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brings villagers to players' vending blocks: customers to money sales ("Villagers can buy"), suppliers to money
 * buybacks ("Villagers can sell", {@link VillagerSupplies}). Vending block entities don't tick: every
 * {@code villagers.checkIntervalTicks} each level walks the vending index for its loaded vendors and, with
 * {@code villagers.visitChance} (scaled by the owner's reputation and the stall's village: market day, trading
 * licence, and for customers the funded projects), calls one eligible villager in {@code villagers.visitRadius}
 * (wider with a licence). The villager walks to the block in front of the stall (vanilla {@code WALK_TARGET}), looks
 * at it ({@code LOOK_TARGET}) for {@code villagers.browseTicks}, then decides ({@link VillagerPurchases#visit},
 * {@link VillagerSupplies#visit}). No AI mixins: the vanilla brain does the walking; the state of a visit lives here,
 * not on the entity. Server thread.
 */
public final class VillagerCustomers {
    /** A customer stands within this (Manhattan) distance of the block in front of the stall. */
    static final int ARRIVED_DISTANCE = 1;
    /** How often a walk target the villager's brain replaced is set again (each try may compute a path). */
    static final int REASSERT_TICKS = 10;
    /** Highest reputation factor: vendors whose roll misses even that skip the villager search. */
    static final double MAX_REPUTATION_FACTOR = 2.0;
    /** Daytime activities in which a villager may go shopping. */
    private static final Set<Activity> SHOPPING_ACTIVITIES = Set.of(Activity.WORK, Activity.MEET, Activity.IDLE, Activity.PLAY);

    private enum Phase { WALKING, BROWSING }

    private static final class Customer {
        final UUID villager;
        final ResourceKey<Level> dimension;
        final BlockPos vendor;
        final BlockPos stand;
        final long deadline;
        final int browseTicks;
        /** Comes to sell its produce to a buyback, not to buy. */
        final boolean supplier;
        /** At a market counter: the offer it came for; -1 at a vending block. */
        final int offer;
        Phase phase = Phase.WALKING;
        long browseUntil;
        long lastAssert;

        Customer(UUID villager, ResourceKey<Level> dimension, BlockPos vendor, BlockPos stand, long now, int walkTimeout, int browseTicks,
                 boolean supplier, int offer) {
            this.villager = villager;
            this.dimension = dimension;
            this.vendor = vendor;
            this.stand = stand;
            this.deadline = now + walkTimeout;
            this.browseTicks = browseTicks;
            this.supplier = supplier;
            this.offer = offer;
            this.lastAssert = now;
        }
    }

    private static final Map<UUID, Customer> CUSTOMERS = new LinkedHashMap<>();
    /** Bells don't move: the nearest bell of a stall is looked up once per {@link #BELL_CACHE_TICKS} (POI search). */
    private static final int BELL_CACHE_TICKS = 1200;
    private record BellLookup(@Nullable BlockPos bell, long until) {
    }
    private static final Map<GlobalPos, BellLookup> BELLS = new HashMap<>();

    private VillagerCustomers() {
    }

    // ------------------------------------------------------------------ hooks

    /** End of each level tick. */
    public static void onLevelTick(ServerLevel level) {
        if (!EconomyService.INSTANCE.isReady()) {
            return;
        }
        ServerConfig.VillagersSection config = TraderyConfig.server().villagers();
        if (!config.enabled()) {
            if (!CUSTOMERS.isEmpty()) {
                cancelAll(level);
            }
            return;
        }
        if (!CUSTOMERS.isEmpty()) {
            tickCustomers(level, config);
        }
        long time = level.getGameTime();
        scan(level, config, time);
        if (level.dimension() == Level.OVERWORLD && time % SharedConstants.TICKS_PER_GAME_DAY == 0) {
            VillagerLedger.get(level.getServer()).prune(VillagerPurchases.today(level.getServer()));
        }
    }

    /** A villager died: forget its visit and its wallet (and its produce). */
    public static void onVillagerDeath(Villager villager) {
        CUSTOMERS.remove(villager.getUUID());
        if (villager.level() instanceof ServerLevel level) {
            VillagerLedger.get(level.getServer()).forget(villager.getUUID());
        }
    }

    public static void onServerStopped() {
        CUSTOMERS.clear();
        BELLS.clear();
    }

    // ------------------------------------------------------------------ who may come

    /**
     * An adult villager, awake, not panicking, not trading with a player, not leashed or riding, in a daytime
     * activity, with a bed and a job site ({@code villagers.requireHomeAndJob}), not on its visit cooldown and not
     * already on its way somewhere.
     */
    public static boolean isEligible(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive() || villager.isBaby() || villager.isSleeping()
            || villager.isTrading() || villager.isLeashed() || villager.isPassenger() || CUSTOMERS.containsKey(villager.getUUID())) {
            return false;
        }
        Brain<Villager> brain = villager.getBrain();
        if (isPanicking(brain) || !brain.getActiveNonCoreActivity().map(SHOPPING_ACTIVITIES::contains).orElse(false)) {
            return false;
        }
        if (TraderyConfig.server().villagers().requireHomeAndJob()
            && !(brain.hasMemoryValue(MemoryModuleType.HOME) && brain.hasMemoryValue(MemoryModuleType.JOB_SITE))) {
            return false;
        }
        return !VillagerLedger.get(level.getServer()).onCooldown(villager.getUUID(), level.getGameTime());
    }

    private static boolean isPanicking(Brain<Villager> brain) {
        return brain.hasMemoryValue(MemoryModuleType.IS_PANICKING) || brain.isActive(Activity.PANIC);
    }

    /** The block in front of the stall, where a customer stands; null if a villager can't stand there. */
    public static @Nullable BlockPos standPos(BlockEntity stall) {
        Level level = stall.getLevel();
        BlockState state = stall.getBlockState();
        if (level == null || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        }
        // The buyers' side: vending blocks and market counters both face their customers
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos stand = stall.getBlockPos().relative(facing);
        boolean free = level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
            && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty();
        return free ? stand : null;
    }

    /** A villager is walking to or standing at a stall (buying or selling). */
    public static boolean isCustomer(UUID villager) {
        return CUSTOMERS.containsKey(villager);
    }

    /** A villager is walking to or standing at a buyback block to sell its produce. */
    public static boolean isSupplier(UUID villager) {
        Customer customer = CUSTOMERS.get(villager);
        return customer != null && customer.supplier;
    }

    private static boolean busy(ResourceKey<Level> dimension, BlockPos vendor) {
        for (Customer customer : CUSTOMERS.values()) {
            if (customer.vendor.equals(vendor) && customer.dimension == dimension) {
                return true;
            }
        }
        return false;
    }

    /** The vendor still takes this kind of visit (its settings may change while the villager walks). */
    private static boolean takes(VendingBlockEntity vendor, boolean supplier) {
        return supplier ? VendingTrades.buysFromVillagers(vendor) : VendingTrades.servesVillagers(vendor);
    }

    // ------------------------------------------------------------------ the stall's village

    /**
     * How much more often villagers come to {@code owner}'s stall in {@code village} (the nearest bell within
     * {@code villages.boardRadius}; null: none): {@link VillageCommerce#visitFactor} (market day, trading licence),
     * and for customers the funded projects' {@code visit_chance} (+10% = ×1.1). Suppliers don't get the projects'
     * bonus.
     */
    public static double visitFactor(MinecraftServer server, @Nullable VillageState village, UUID owner, boolean supplier) {
        if (village == null) {
            return 1.0;
        }
        double factor = VillageCommerce.visitFactor(server, village, owner);
        if (!supplier) {
            factor *= 1.0 + VillageProjects.effects(village).visitChance();
        }
        return Math.max(0.0, factor);
    }

    /** Radius villagers of {@code village} walk to {@code owner}'s stall: {@code villagers.visitRadius} × {@link VillageCommerce#radiusFactor}. */
    public static int visitRadius(MinecraftServer server, @Nullable VillageState village, UUID owner) {
        int base = TraderyConfig.server().villagers().visitRadius();
        if (village == null) {
            return base;
        }
        return (int) Math.max(1, Math.round(base * Math.max(0.0, VillageCommerce.radiusFactor(server, village, owner))));
    }

    /**
     * A villager may come from this far: within the usual radius anyone; beyond it (a licence widens the radius) only
     * villagers of the stall's own village.
     */
    private static boolean inReach(Villager villager, AABB base, @Nullable VillageState village) {
        if (base.contains(villager.position())) {
            return true;
        }
        if (village == null) {
            return false;
        }
        GlobalPos bell = Villages.bellOf(villager).orElse(null);
        return bell != null && bell.dimension() == village.dimension() && bell.pos().equals(village.bell());
    }

    // ------------------------------------------------------------------ calling customers and suppliers

    /**
     * Every stall gets its chance once per {@code villagers.checkIntervalTicks}, but not all in the same tick: each
     * one has its own tick in the interval (from its position), so 200 stalls mean two or three a tick, not 200 at once.
     */
    private static void scan(ServerLevel level, ServerConfig.VillagersSection config, long time) {
        if (config.visitChance() <= 0) {
            return;
        }
        if (time % BELL_CACHE_TICKS == 0) {
            BELLS.values().removeIf(lookup -> time > lookup.until() + BELL_CACHE_TICKS);
        }
        RandomSource random = level.getRandom();
        int interval = config.checkIntervalTicks();
        List<VendorsData.Entry> vendors = VendorsData.get(level.getServer()).all();
        for (VendorsData.Entry entry : vendors) {
            if (entry.pos().dimension() != level.dimension() || !(entry.owner() instanceof AccountId.Player owner)
                || Math.floorMod(time + Long.hashCode(entry.pos().pos().asLong()), interval) != 0) {
                continue;
            }
            BlockPos pos = entry.pos().pos();
            // Only loaded, entity-ticking chunks: nothing is simulated for unloaded stalls
            if (!level.isPositionEntityTicking(pos) || busy(level.dimension(), pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof MarketCounterBlockEntity counter) {
                if (CounterTrades.servesVillagers(counter)) {
                    scanCounter(level, counter, owner.uuid(), config, random);
                }
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof VendingBlockEntity vendor)) {
                continue;
            }
            if (VendingTrades.servesVillagers(vendor)) {
                scanCustomer(level, vendor, owner.uuid(), config, random);
            } else if (VendingTrades.buysFromVillagers(vendor)) {
                scanSupplier(level, vendor, owner.uuid(), config, random);
            }
        }
    }

    /** A money sale: maybe one villager who wants the goods comes to buy. */
    private static void scanCustomer(ServerLevel level, VendingBlockEntity vendor, UUID owner, ServerConfig.VillagersSection config,
                                     RandomSource random) {
        ItemStack goods = vendor.settings().goods();
        if (vendor.tradesInStock() <= 0 || !VillagerMarket.anyoneWants(goods) || VillagerMarket.fairPrice(goods).isEmpty()) {
            return;
        }
        BlockPos stand = standPos(vendor);
        if (stand == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        VillageState village = villageNear(level, vendor.getBlockPos());
        double factor = visitFactor(server, village, owner, false);
        double roll = random.nextDouble();
        if (roll >= config.visitChance() * MAX_REPUTATION_FACTOR * factor) {
            return;
        }
        List<Villager> nearby = candidates(level, vendor.getBlockPos(), config.visitRadius(), visitRadius(server, village, owner), village);
        Villager customer = pickCustomer(nearby, goods, random);
        if (customer != null && roll < config.visitChance() * VillagerPurchases.reputationFactor(customer, owner) * factor) {
            start(level, customer, vendor, stand, config, false);
        }
    }

    /**
     * A market counter: maybe one villager comes for the first offer it wants (its profession's wants, a fair price
     * known); stronger wants are more likely to come.
     */
    private static void scanCounter(ServerLevel level, MarketCounterBlockEntity counter, UUID owner, ServerConfig.VillagersSection config,
                                    RandomSource random) {
        BlockPos stand = standPos(counter);
        if (stand == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        VillageState village = villageNear(level, counter.getBlockPos());
        double factor = visitFactor(server, village, owner, false);
        double roll = random.nextDouble();
        if (roll >= config.visitChance() * MAX_REPUTATION_FACTOR * factor) {
            return;
        }
        List<Villager> nearby = candidates(level, counter.getBlockPos(), config.visitRadius(), visitRadius(server, village, owner), village);
        int[] offers = new int[nearby.size()];
        int[] weights = new int[nearby.size()];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            Holder<VillagerProfession> profession = nearby.get(i).getVillagerData().profession();
            offers[i] = CounterTrades.pickOffer(counter, goods -> VillagerMarket.weight(profession, goods) > 0 && VillagerMarket.fairPrice(goods).isPresent());
            weights[i] = offers[i] < 0 ? 0 : VillagerMarket.weight(profession, counter.settings().offer(offers[i]).goods());
            total += weights[i];
        }
        if (total <= 0) {
            return;
        }
        int pick = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                Villager customer = nearby.get(i);
                if (roll < config.visitChance() * VillagerPurchases.reputationFactor(customer, owner) * factor) {
                    start(level, customer, counter.getBlockPos(), stand, config, false, offers[i]);
                }
                return;
            }
        }
    }

    /**
     * A money buyback with "Villagers can sell": maybe one villager who makes the goods, has a trade's worth and
     * finds the price fair enough comes to sell. Only while the block has room and the owner can pay for a trade.
     */
    private static void scanSupplier(ServerLevel level, VendingBlockEntity vendor, UUID owner, ServerConfig.VillagersSection config,
                                     RandomSource random) {
        VendingSettings settings = vendor.settings();
        ItemStack goods = settings.goods();
        if (!VillagerSupplies.anyoneProduces(goods) || VillagerMarket.fairPrice(goods).isEmpty()
            || StackMath.space(vendor.stock().getItems(), goods) < settings.perTrade() || !ownerCanPay(vendor, settings.price())) {
            return;
        }
        BlockPos stand = standPos(vendor);
        if (stand == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        VillageState village = villageNear(level, vendor.getBlockPos());
        double factor = visitFactor(server, village, owner, true);
        double roll = random.nextDouble();
        if (roll >= config.visitChance() * MAX_REPUTATION_FACTOR * factor) {
            return;
        }
        List<Villager> nearby = candidates(level, vendor.getBlockPos(), config.visitRadius(), visitRadius(server, village, owner), village);
        Villager supplier = pickSupplier(nearby, vendor, VillagerPurchases.today(server), random);
        if (supplier != null && roll < config.visitChance() * VillagerPurchases.reputationFactor(supplier, owner) * factor) {
            start(level, supplier, vendor, stand, config, true);
        }
    }

    private static boolean ownerCanPay(VendingBlockEntity vendor, long price) {
        Account account = VendingTrades.ownerAccount(vendor);
        return account.isInfinite() || account.balance(EconomyService.INSTANCE.defaultCurrency()) >= price;
    }

    /** Eligible villagers in reach of the stall. */
    private static List<Villager> candidates(ServerLevel level, BlockPos vendor, int baseRadius, int radius, @Nullable VillageState village) {
        AABB base = new AABB(vendor).inflate(baseRadius);
        return level.getEntitiesOfClass(Villager.class, new AABB(vendor).inflate(Math.max(baseRadius, radius)),
            villager -> isEligible(villager) && inReach(villager, base, village));
    }

    /** A villager who wants the goods; stronger wants are more likely to come. */
    private static @Nullable Villager pickCustomer(List<Villager> nearby, ItemStack goods, RandomSource random) {
        if (nearby.isEmpty()) {
            return null;
        }
        int[] weights = new int[nearby.size()];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = VillagerMarket.weight(nearby.get(i).getVillagerData().profession(), goods);
            total += weights[i];
        }
        if (total <= 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return nearby.get(i);
            }
        }
        return null;
    }

    /** One of the villagers who could sell here right now ({@link VillagerSupplies#canSupply}), all equally likely. */
    private static @Nullable Villager pickSupplier(List<Villager> nearby, VendingBlockEntity vendor, long today, RandomSource random) {
        List<Villager> able = new ArrayList<>();
        for (Villager villager : nearby) {
            if (VillagerSupplies.canSupply(villager, vendor, today)) {
                able.add(villager);
            }
        }
        return able.isEmpty() ? null : able.get(random.nextInt(able.size()));
    }

    /**
     * Sends an eligible villager to this stall now: to buy at a money sale, or to sell its produce at a buyback with
     * "Villagers can sell" if it {@link VillagerSupplies#canSupply can} (the scan does this by chance; tests and other
     * mods directly).
     *
     * @return false if the villager or the stall can't take part
     */
    public static boolean invite(Villager villager, VendingBlockEntity vendor) {
        if (!(villager.level() instanceof ServerLevel level) || vendor.getLevel() != level || !isEligible(villager)
            || busy(level.dimension(), vendor.getBlockPos())) {
            return false;
        }
        boolean supplier = VendingTrades.buysFromVillagers(vendor);
        if (supplier ? !VillagerSupplies.canSupply(villager, vendor, VillagerPurchases.today(level.getServer()))
            : !VendingTrades.servesVillagers(vendor)) {
            return false;
        }
        BlockPos stand = standPos(vendor);
        if (stand == null) {
            return false;
        }
        start(level, villager, vendor, stand, TraderyConfig.server().villagers(), supplier);
        return true;
    }

    /** Ends the villager's visit now without trading; its cooldown starts as after any visit. */
    public static void cancel(Villager villager) {
        Customer customer = CUSTOMERS.remove(villager.getUUID());
        if (customer != null && villager.level() instanceof ServerLevel level) {
            finish(level, villager, customer, TraderyConfig.server().villagers());
        }
    }

    private static void start(ServerLevel level, Villager villager, VendingBlockEntity vendor, BlockPos stand, ServerConfig.VillagersSection config,
                              boolean supplier) {
        start(level, villager, vendor.getBlockPos(), stand, config, supplier, -1);
    }

    private static void start(ServerLevel level, Villager villager, BlockPos stall, BlockPos stand, ServerConfig.VillagersSection config,
                              boolean supplier, int offer) {
        Customer customer = new Customer(villager.getUUID(), level.dimension(), stall.immutable(), stand.immutable(),
            level.getGameTime(), config.walkTimeoutTicks(), config.browseTicks(), supplier, offer);
        CUSTOMERS.put(customer.villager, customer);
        walkTo(villager, customer);
    }

    // ------------------------------------------------------------------ visits in progress

    private static void tickCustomers(ServerLevel level, ServerConfig.VillagersSection config) {
        long now = level.getGameTime();
        Iterator<Customer> it = CUSTOMERS.values().iterator();
        while (it.hasNext()) {
            Customer customer = it.next();
            if (customer.dimension != level.dimension()) {
                continue;
            }
            Villager villager = level.getEntity(customer.villager) instanceof Villager v ? v : null;
            BlockEntity stall = level.isLoaded(customer.vendor) ? level.getBlockEntity(customer.vendor) : null;
            VendingBlockEntity vendor = stall instanceof VendingBlockEntity v ? v : null;
            MarketCounterBlockEntity counter = stall instanceof MarketCounterBlockEntity c ? c : null;
            boolean open = vendor != null ? customer.offer < 0 && takes(vendor, customer.supplier)
                : counter != null && !customer.supplier && CounterTrades.servesVillagers(counter, customer.offer);
            if (villager == null || !villager.isAlive() || villager.isSleeping() || villager.isTrading() || isPanicking(villager.getBrain())
                || !open) {
                it.remove();
                finish(level, villager, customer, config);
                continue;
            }
            if (customer.phase == Phase.WALKING) {
                if (villager.blockPosition().distManhattan(customer.stand) <= ARRIVED_DISTANCE) {
                    customer.phase = Phase.BROWSING;
                    customer.browseUntil = now + customer.browseTicks;
                    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    villager.getNavigation().stop();
                    lookAtStall(villager, customer);
                } else if (now >= customer.deadline) {
                    it.remove();
                    finish(level, villager, customer, config);
                } else if (!walksToStall(villager, customer) && now - customer.lastAssert >= REASSERT_TICKS) {
                    walkTo(villager, customer);
                    customer.lastAssert = now;
                }
                continue;
            }
            // Browsing: stay at the stall and look at the goods
            Brain<Villager> brain = villager.getBrain();
            if (villager.blockPosition().distManhattan(customer.stand) > ARRIVED_DISTANCE + 1) {
                if (!walksToStall(villager, customer) && now - customer.lastAssert >= REASSERT_TICKS) {
                    walkTo(villager, customer);
                    customer.lastAssert = now;
                }
            } else if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET) && !walksToStall(villager, customer)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            }
            lookAtStall(villager, customer);
            if (now >= customer.browseUntil) {
                it.remove();
                if (counter != null) {
                    VillagerPurchases.visit(villager, counter, customer.offer, level.getRandom().nextDouble());
                } else if (customer.supplier) {
                    VillagerSupplies.visit(villager, vendor);
                } else {
                    VillagerPurchases.visit(villager, vendor, level.getRandom().nextDouble());
                }
                finish(level, villager, customer, config);
            }
        }
    }

    private static boolean walksToStall(Villager villager, Customer customer) {
        return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
            .map(target -> target.getTarget().currentBlockPosition().equals(customer.stand)).orElse(false);
    }

    private static void walkTo(Villager villager, Customer customer) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(customer.stand, Villager.SPEED_MODIFIER, 0));
    }

    private static void lookAtStall(Villager villager, Customer customer) {
        Brain<Villager> brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET).map(t -> t.currentBlockPosition().equals(customer.vendor)).orElse(false)) {
            return;
        }
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(customer.vendor));
    }

    /** The stall's village, through the bell cache. */
    private static @Nullable VillageState villageNear(ServerLevel level, BlockPos pos) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        long now = level.getGameTime();
        BellLookup cached = BELLS.get(key);
        if (cached == null || now >= cached.until()) {
            BlockPos bell = Villages.nearestBell(level, pos, Villages.config().boardRadius()).orElse(null);
            cached = new BellLookup(bell, now + BELL_CACHE_TICKS);
            BELLS.put(key, cached);
        }
        return cached.bell() == null ? null : Villages.at(level.getServer(), level.dimension(), cached.bell());
    }

    /** The visit is over (traded, declined, gave up or interrupted): release the villager, start its cooldown. */
    private static void finish(ServerLevel level, @Nullable Villager villager, Customer customer, ServerConfig.VillagersSection config) {
        MinecraftServer server = level.getServer();
        if (villager != null && villager.isAlive()) {
            Brain<Villager> brain = villager.getBrain();
            if (walksToStall(villager, customer)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            }
            if (brain.getMemory(MemoryModuleType.LOOK_TARGET).map(t -> t.currentBlockPosition().equals(customer.vendor)).orElse(false)) {
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            }
        }
        VillagerLedger.get(server).setCooldown(customer.villager, level.getGameTime() + config.customerCooldownTicks(),
            VillagerPurchases.today(server));
    }

    private static void cancelAll(ServerLevel level) {
        ServerConfig.VillagersSection config = TraderyConfig.server().villagers();
        Iterator<Customer> it = CUSTOMERS.values().iterator();
        while (it.hasNext()) {
            Customer customer = it.next();
            if (customer.dimension == level.dimension()) {
                it.remove();
                finish(level, level.getEntity(customer.villager) instanceof Villager v ? v : null, customer, config);
            }
        }
    }
}
