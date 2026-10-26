package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.TradePersistence;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Orders on the village board (SPEC "Заказы"). On the first look at the board on a new day the village posts orders
 * up to its level's count: things its villagers want ({@code villager_wants} of the professions in the census, and
 * "any") and things it pays dear for ({@link VillageCommerce#priceShifts}), 8 to 64 items as the budget allows. The
 * whole reward is reserved from the treasury when an order is posted and goes back when it expires undelivered.
 * After a won raid, restoration orders (planks, glass, cobblestone, torches) come on top, at a higher reward.
 * Anyone delivers plain stacks (no name, enchantments or other data), all or part; the reward is minted from
 * {@code tradery:server} (the treasury is a counter, like the wallets). Server thread.
 */
public final class VillageOrders {
    /** Fewest and most items one order asks for. */
    public static final int MIN_AMOUNT = 8;
    public static final int MAX_AMOUNT = 64;
    /** Prosperity points per major unit of reward. */
    static final double POINTS_PER_UNIT = 1.0;
    /** An order is worth between this share of its part of the treasury and all of it, so amounts vary. */
    private static final double MIN_BUDGET_SHARE = 0.5;
    /** Dear goods weigh like this many villagers wanting them, per villager of the village. */
    private static final double DEAR_GOODS_WEIGHT = 1.0;

    private VillageOrders() {
    }

    // ------------------------------------------------------------------ posting and expiry

    /**
     * Expires old orders (their unpaid rewards go back to the treasury) and, on a new day, posts the day's orders.
     * Called when somebody opens the board.
     */
    public static void refresh(MinecraftServer server, VillageState village) {
        long today = VillagerPurchases.today(server);
        Villages.touch(server, village);
        expire(server, village, today);
        if (village.events.ordersDay() != today) {
            post(server, village, today, RandomSource.create(seed(village, today)));
        }
    }

    /** Removes orders that are done, past their day, or from a future the clock was set back from; refunds them. */
    static void expire(MinecraftServer server, VillageState village, long today) {
        int lifetime = Villages.config().orders().lifetimeDays();
        long refund = 0;
        boolean changed = false;
        Iterator<VillageState.Order> it = village.orders.iterator();
        while (it.hasNext()) {
            VillageState.Order order = it.next();
            if (order.remaining() <= 0 || order.expiresDay() <= today || order.expiresDay() > today + lifetime) {
                refund = Villages.saturatedAdd(refund, reserved(order));
                it.remove();
                changed = true;
            }
        }
        if (refund > 0) {
            Villages.addToTreasury(server, village, refund);
        }
        if (changed) {
            VillageEconomy.get(server).setDirty();
        }
    }

    /** Money still reserved for an order: its undelivered items' reward. */
    static long reserved(VillageState.Order order) {
        return Villages.saturatedMultiply(order.remaining(), order.rewardPerItem());
    }

    private static long seed(VillageState village, long today) {
        return village.key().hashCode() * 31L + today;
    }

    /**
     * Posts today's orders: restoration orders while they are due, then wanted goods up to the level's count of open
     * orders. Each order gets an equal part of what the treasury holds. Marks the day as done.
     *
     * @return orders posted
     */
    static int post(MinecraftServer server, VillageState village, long today, RandomSource random) {
        VillagesConfig config = Villages.config();
        long expires = today + config.orders().lifetimeDays();
        Set<Item> open = new HashSet<>();
        int openRegular = 0;
        for (VillageState.Order order : village.orders) {
            BuiltInRegistries.ITEM.getOptional(order.item()).ifPresent(open::add);
            if (!order.restoration()) {
                openRegular++;
            }
        }

        List<Item> restoration = new ArrayList<>();
        if (Villages.restorationDue(server, village)) {
            for (Item item : restorationItems(village)) {
                if (!open.contains(item)) {
                    restoration.add(item);
                }
            }
        }
        int regular = Math.max(0, Villages.byLevel(config.levels().ordersPerDay(), Villages.level(village)) - openRegular);
        int slots = restoration.size() + regular;
        int posted = 0;

        double restorationMarkup = config.orders().rewardMarkup() * config.threats().restorationMarkup();
        for (Item item : restoration) {
            ItemStack stack = new ItemStack(item);
            Optional<BigDecimal> fair = VillagerMarket.fairPrice(stack);
            if (fair.isPresent() && postOne(server, village, stack, fair.get(), restorationMarkup, slots, expires, true, random)) {
                posted++;
                open.add(item);
            }
            slots--;
        }

        List<Candidate> candidates = candidates(server, village, open);
        while (regular > 0 && !candidates.isEmpty()) {
            Candidate pick = pick(candidates, random);
            if (postOne(server, village, new ItemStack(pick.item()), pick.fair(), config.orders().rewardMarkup(), regular, expires, false, random)) {
                posted++;
                regular--;
            }
        }

        VillageState.Events events = village.events;
        Villages.setEvents(server, village, new VillageState.Events(today, events.nextOrderId(), events.nextMarketDay(), events.caravanDay(),
            events.restorationUntil(), events.raidActive()));
        return posted;
    }

    /**
     * Posts one order for {@code stack} if the treasury's part for it ({@code treasury / slots}) pays for at least
     * {@link #MIN_AMOUNT} items.
     */
    private static boolean postOne(MinecraftServer server, VillageState village, ItemStack stack, BigDecimal fair, double markup, int slots,
                                   long expiresDay, boolean restoration, RandomSource random) {
        long reward = rewardPerItem(village, stack, fair, markup);
        if (reward <= 0 || reward == Long.MAX_VALUE || slots <= 0) {
            return false;
        }
        long budget = village.treasury / slots;
        double share = MIN_BUDGET_SHARE + random.nextDouble() * (1 - MIN_BUDGET_SHARE);
        long amount = Math.min(MAX_AMOUNT, (long) (budget * share) / reward);
        if (amount < MIN_AMOUNT) {
            return false;
        }
        return add(server, village, stack.getItem(), (int) amount, reward, expiresDay, restoration) != null;
    }

    /**
     * Adds an order, reserving its whole reward from the treasury.
     *
     * @return the order, or null when the treasury doesn't hold its reward
     */
    static VillageState.@Nullable Order add(MinecraftServer server, VillageState village, Item item, int count, long rewardPerItem,
                                            long expiresDay, boolean restoration) {
        long total;
        try {
            total = Math.multiplyExact(count, rewardPerItem);
        } catch (ArithmeticException e) {
            return null;
        }
        if (count <= 0 || rewardPerItem <= 0 || !Villages.takeFromTreasury(server, village, total)) {
            return null;
        }
        VillageState.Events events = village.events;
        VillageState.Order order = new VillageState.Order(events.nextOrderId(), BuiltInRegistries.ITEM.getKey(item), count, 0, rewardPerItem,
            expiresDay, restoration);
        village.orders.add(order);
        Villages.setEvents(server, village, new VillageState.Events(events.ordersDay(), events.nextOrderId() + 1, events.nextMarketDay(),
            events.caravanDay(), events.restorationUntil(), events.raidActive()));
        return order;
    }

    /** Reward per item: fair price × markup × the village's price factor for it, in minor units (rounded down). */
    static long rewardPerItem(VillageState village, ItemStack stack, BigDecimal fair, double markup) {
        double factor = VillageCommerce.priceFactor(village, stack);
        if (!(factor > 0) || !(markup > 0) || Double.isInfinite(factor)) {
            return 0;
        }
        return VillagerPurchases.toMinor(fair.multiply(BigDecimal.valueOf(markup)).multiply(BigDecimal.valueOf(factor)));
    }

    /** Planks of the village's wood, glass, cobblestone and torches. */
    static List<Item> restorationItems(VillageState village) {
        String type = village.census().type();
        Item planks = switch (type.contains(":") ? type.substring(type.indexOf(':') + 1) : type) {
            case "savanna" -> Items.ACACIA_PLANKS;
            case "taiga", "snow" -> Items.SPRUCE_PLANKS;
            case "jungle" -> Items.JUNGLE_PLANKS;
            default -> Items.OAK_PLANKS;
        };
        return List.of(planks, Items.GLASS, Items.COBBLESTONE, Items.TORCH);
    }

    /** An item the village could order, with its fair price and how much the village wants it. */
    private record Candidate(Item item, BigDecimal fair, double weight) {
    }

    /**
     * Items with a fair price that the village's villagers want or that the village pays dear for, weighted by how
     * many villagers want them and how much. In registry order, so a seed gives the same orders.
     */
    private static List<Candidate> candidates(MinecraftServer server, VillageState village, Set<Item> exclude) {
        VillageState.Census census = village.census();
        Map<Holder<VillagerProfession>, Integer> professions = new HashMap<>();
        for (Map.Entry<String, Integer> entry : census.professions().entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id != null) {
                BuiltInRegistries.VILLAGER_PROFESSION.get(id).ifPresent(holder -> professions.merge(holder, entry.getValue(), Integer::sum));
            }
        }
        // Villagers without a profession still want what everybody wants; a village before its first census counts as one
        int jobless = Math.max(census.known() ? 0 : 1, census.population() - census.employed());
        if (jobless > 0) {
            professions.merge(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), jobless, Integer::sum);
        }
        Set<Item> dear = new HashSet<>();
        for (VillageCommerce.PriceShift shift : VillageCommerce.priceShifts(village)) {
            if (shift.factor() > 1 && !shift.icon().isEmpty()) {
                dear.add(shift.icon().getItem());
            }
        }
        double dearWeight = DEAR_GOODS_WEIGHT * Math.max(1, census.population());

        List<Candidate> candidates = new ArrayList<>();
        for (Item item : pricedItems()) {
            if (exclude.contains(item)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            Optional<BigDecimal> fair = VillagerMarket.fairPrice(stack);
            if (fair.isEmpty()) {
                continue;
            }
            double weight = dear.contains(item) ? dearWeight : 0;
            for (Map.Entry<Holder<VillagerProfession>, Integer> entry : professions.entrySet()) {
                weight += entry.getValue() * (double) VillagerMarket.weight(entry.getKey(), stack);
            }
            if (weight > 0) {
                candidates.add(new Candidate(item, fair.get(), weight));
            }
        }
        return candidates;
    }

    private static List<Item> pricedItems = List.of();
    private static int pricedGeneration = -1;

    /**
     * Every item with a fair price, in registry order: found once per data pack load (a modpack has tens of thousands
     * of items, the priced ones are a few hundred), on the server thread.
     */
    private static List<Item> pricedItems() {
        int generation = VillagerMarket.generation();
        if (pricedGeneration != generation) {
            List<Item> priced = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && VillagerMarket.fairPrice(new ItemStack(item)).isPresent()) {
                    priced.add(item);
                }
            }
            pricedItems = List.copyOf(priced);
            pricedGeneration = generation;
        }
        return pricedItems;
    }

    /** Takes one candidate out of the list, by weight. */
    private static Candidate pick(List<Candidate> candidates, RandomSource random) {
        double total = 0;
        for (Candidate candidate : candidates) {
            total += candidate.weight();
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= candidates.get(i).weight();
            if (roll < 0) {
                return candidates.remove(i);
            }
        }
        return candidates.removeLast();
    }

    // ------------------------------------------------------------------ delivery

    /** Plain stacks of the item: no name, enchantments, damage or other data. */
    static Predicate<ItemStack> plain(Item item) {
        return stack -> stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    static VillageState.@Nullable Order find(VillageState village, int orderId) {
        for (VillageState.Order order : village.orders) {
            if (order.id() == orderId) {
                return order;
            }
        }
        return null;
    }

    /**
     * Delivers as many plain items of order {@code orderId} as the player has, up to what's still needed. Checked
     * first (the order, the items), then the reward is minted (the one step that may be refused: balance ceiling,
     * locked account, an API listener), then the items are taken. Nothing changes when it fails.
     */
    public static VillageBoard.Outcome deliver(ServerPlayer player, VillageState village, int orderId) {
        MinecraftServer server = player.level().getServer();
        EconomyService economy = EconomyService.INSTANCE;
        if (!economy.isReady()) {
            return VillageBoard.Outcome.fail(Messages.failure(FailReason.CANCELLED));
        }
        long today = VillagerPurchases.today(server);
        Villages.touch(server, village);
        expire(server, village, today);
        VillageState.Order order = find(village, orderId);
        Optional<Item> found = order == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(order.item());
        if (order == null || found.isEmpty()) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.board.order_gone", "This order is no longer on the board"));
        }
        Item item = found.get();
        ItemStack sample = new ItemStack(item);
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        Predicate<ItemStack> plain = plain(item);
        long have = StackMath.countMatching(inventory, plain);
        int amount = (int) Math.min(order.remaining(), have);
        if (amount <= 0) {
            boolean special = StackMath.countMatching(inventory, stack -> stack.is(item)) > 0;
            return VillageBoard.Outcome.fail(special
                ? Messages.tr("tradery.board.only_plain", "The village takes only plain %s: no name, enchantments or damage", sample.getHoverName())
                : Messages.tr("tradery.board.nothing_to_deliver", "You have no %s to deliver", sample.getHoverName()));
        }
        long reward;
        try {
            reward = Math.multiplyExact(amount, order.rewardPerItem());
        } catch (ArithmeticException e) {
            return VillageBoard.Outcome.fail(Messages.failure(FailReason.LIMIT_EXCEEDED));
        }

        Reason reason = Reason.of(Reasons.VILLAGE_ORDER, BuiltInRegistries.ITEM.getKey(item) + "x" + amount + " @ " + village.key());
        TransactionResult result = economy.transfer(economy.serverAccount(), economy.account(player.getUUID()), reward, 0, reason);
        if (result instanceof TransactionResult.Failure failure) {
            return VillageBoard.Outcome.fail(Messages.failure(failure));
        }

        // The money moved; the items were counted above, on this thread, with only the transaction in between
        int taken = StackMath.takeMatching(inventory, plain, amount);
        if (taken != amount) {
            Tradery.LOGGER.error("Village order delivery took {} of {} items; please report this", taken, amount);
        }
        player.getInventory().setChanged();
        int index = village.orders.indexOf(order);
        VillageState.Order delivered = order.withDelivered(order.delivered() + amount);
        if (delivered.remaining() <= 0) {
            village.orders.remove(index);
        } else {
            village.orders.set(index, delivered);
        }
        VillageEconomy.get(server).setDirty();
        Villages.addProsperity(server, village, Villages.points(reward, POINTS_PER_UNIT));
        // One vanilla trade's worth of reputation per started 8 items
        int trades = (amount + MIN_AMOUNT - 1) / MIN_AMOUNT;
        VillageBoard.gossip(server, village, player.getUUID(), GossipType.TRADING, GossipType.REPUTATION_CHANGE_PER_TRADE * trades);
        TradePersistence.tradedWithVillager(player);
        return VillageBoard.Outcome.done(Messages.tr("tradery.board.delivered", "Delivered %s × %s for %s", amount, sample.getHoverName(),
            Messages.coins(reward)));
    }

    // ------------------------------------------------------------------ tests

    /**
     * Posts an order directly, reserving its reward (tests and admin tools).
     *
     * @return the order, or null when the treasury doesn't hold its reward
     */
    public static VillageState.@Nullable Order postForTests(MinecraftServer server, VillageState village, Item item, int count, long rewardPerItem,
                                                            long expiresDay, boolean restoration) {
        return add(server, village, item, count, rewardPerItem, expiresDay, restoration);
    }

    /** Posts the day's orders now with this seed, whatever day the board was last filled (tests). */
    public static int postNow(MinecraftServer server, VillageState village, long seed) {
        long today = VillagerPurchases.today(server);
        expire(server, village, today);
        return post(server, village, today, RandomSource.create(seed));
    }
}
