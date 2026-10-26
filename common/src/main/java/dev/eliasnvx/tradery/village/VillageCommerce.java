package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * A village's commerce (SPEC "Торговля между деревнями", "Рыночный день", "Торговое место"): regional prices, market
 * days, trading licences, caravans. The board, the customers and the suppliers ask here; everything answers in the
 * village's own terms. Server thread.
 */
public final class VillageCommerce {
    /** Villagers walk this many times farther to a licensed owner's blocks (SPEC "Торговое место": ×1.5). */
    static final double LICENCE_RADIUS_FACTOR = 1.5;
    /** The village's daily spending cap on a market day, times this (SPEC "Рыночный день": ×2). */
    static final double MARKET_DAY_SPEND_CAP = 2.0;
    /** Players this close to the bell hear about the market day (SPEC: 64 blocks). */
    static final int ANNOUNCE_RADIUS = 64;
    /** How often the level tick looks for players to tell about a market day. */
    static final int ANNOUNCE_CHECK_TICKS = 40;

    /** Players told about today's market day, as "village key|player"; cleared when the day changes. */
    private static final Set<String> TOLD = new HashSet<>();
    private static long toldDay = Long.MIN_VALUE;

    private VillageCommerce() {
    }

    /**
     * An item this village sells cheap ({@code factor < 1}) or pays dear for ({@code factor > 1}).
     *
     * @param icon   the item (a tag shows one of its items)
     * @param factor multiplier of the fair price here
     */
    public record PriceShift(ItemStack icon, double factor) {
    }

    // ------------------------------------------------------------------ regional prices

    /** Multiplier of the fair price of {@code goods} in this village: 1 = as everywhere. */
    public static double priceFactor(VillageState village, ItemStack goods) {
        return VillageTypes.factor(village.census().type(), goods);
    }

    /** What this village sells cheap and pays dear for, for the board's Market tab; cheap ones first. */
    public static List<PriceShift> priceShifts(VillageState village) {
        String type = village.census().type();
        if (type.isEmpty()) {
            return List.of();
        }
        List<PriceShift> shifts = new ArrayList<>();
        for (VillageTypes.Entry entry : VillageTypes.region(type).all()) {
            ItemStack icon = entry.icon();
            if (!icon.isEmpty()) {
                shifts.add(new PriceShift(icon, entry.factor()));
            }
        }
        return List.copyOf(shifts);
    }

    // ------------------------------------------------------------------ market days

    /** Days between market days here: by level, sooner with projects; at least 1. */
    public static int marketDayInterval(VillageState village) {
        int every = Villages.byLevel(Villages.config().levels().marketDayEvery(), Villages.level(village));
        return Math.max(1, every - VillageProjects.effects(village).marketDayEarlier());
    }

    /** Today is a market day in this village. */
    public static boolean isMarketDay(MinecraftServer server, VillageState village) {
        long next = nextMarketDay(server, village);
        return next >= 0 && next == VillagerPurchases.today(server);
    }

    /**
     * Day number of the next market day (today if it is one), or -1 when market days are off. A village's first market
     * day falls on a day of its own within the first interval, so neighbouring villages don't all hold theirs on the
     * same day; after a market day the next one is an interval later (the interval of the village's level then).
     */
    public static long nextMarketDay(MinecraftServer server, VillageState village) {
        if (!Villages.config().marketDay().enabled()) {
            return -1;
        }
        long next = village.events.nextMarketDay();
        long planned = planMarketDay(next, VillagerPurchases.today(server), marketDayInterval(village), village.key());
        if (planned != next) {
            VillageState.Events e = village.events;
            Villages.setEvents(server, village, new VillageState.Events(e.ordersDay(), e.nextOrderId(), planned, e.caravanDay(),
                e.restorationUntil(), e.raidActive()));
        }
        return planned;
    }

    /**
     * The market day rule, pure: the planned day {@code next} (-1: none yet) as of {@code today}. Never planned, or
     * further away than an interval (the clock was set back): a day of the village's own (by its key) within the next
     * interval. Passed: moved on by whole intervals to today or later. Otherwise it stays.
     */
    public static long planMarketDay(long next, long today, int interval, String villageKey) {
        int every = Math.max(1, interval);
        if (next < 0 || next - today > every) {
            return today + 1 + Math.floorMod(villageKey.hashCode(), every);
        }
        if (next < today) {
            long missed = (today - next + every - 1) / every;
            return next + missed * every;
        }
        return next;
    }

    /**
     * How much more often villagers of this village come to a vending block of {@code owner}: market day and the
     * owner's trading licence multiplied (1 = as usual).
     */
    public static double visitFactor(MinecraftServer server, VillageState village, UUID owner) {
        VillagesConfig config = Villages.config();
        double factor = isMarketDay(server, village) ? config.marketDay().visitMultiplier() : 1.0;
        if (licenceUntil(server, village, owner).isPresent()) {
            factor *= config.rent().visitMultiplier();
        }
        return factor;
    }

    /** How much farther villagers of this village walk to {@code owner}'s blocks (a trading licence: 1.5). */
    public static double radiusFactor(MinecraftServer server, VillageState village, UUID owner) {
        return licenceUntil(server, village, owner).isPresent() ? LICENCE_RADIUS_FACTOR : 1.0;
    }

    /** {@code villagers.dailyCapPerVillage} today, times this (a market day: 2). */
    public static double spendCapFactor(MinecraftServer server, VillageState village) {
        return isMarketDay(server, village) ? MARKET_DAY_SPEND_CAP : 1.0;
    }

    // ------------------------------------------------------------------ trading places

    /** Price of a trading place per day here (minor units). */
    public static long rentPerDay(VillageState village) {
        BigDecimal perDay = Villages.byLevel(Villages.config().levels().rentPerDay(), Villages.level(village));
        return VillagerPurchases.toMinor(perDay);
    }

    /** First day {@code player}'s trading place here no longer holds, if they have one. */
    public static OptionalLong licenceUntil(MinecraftServer server, VillageState village, UUID player) {
        Long until = village.licences.get(player);
        return until != null && until > VillagerPurchases.today(server) ? OptionalLong.of(until) : OptionalLong.empty();
    }

    /**
     * Rents (or extends) a trading place for {@code villages.rent.days} days: the player pays, the treasury gets it.
     * A licence is extended only once less than one rent period is left on it (so a double click doesn't pay twice).
     * The payment is one ledger transaction (to the server account, the treasury is a counter); when it is refused,
     * nothing changes.
     *
     * @return what to tell the player; {@code success} false when nothing was paid
     */
    public static Result rent(ServerPlayer player, VillageState village) {
        EconomyService economy = EconomyService.INSTANCE;
        if (!economy.isReady()) {
            return new Result(false, Messages.tr("tradery.rent.unavailable", "Trading places aren't available"));
        }
        MinecraftServer server = player.level().getServer();
        long today = VillagerPurchases.today(server);
        int days = Villages.config().rent().days();
        pruneLicences(server, village, today);
        UUID id = player.getUUID();
        long current = village.licences.getOrDefault(id, today);
        if (current - today >= days) {
            return new Result(false, Messages.tr("tradery.rent.already", "Your trading place here still holds for %s days",
                current - today));
        }
        long total;
        try {
            total = Math.multiplyExact(rentPerDay(village), days);
        } catch (ArithmeticException e) {
            return new Result(false, Messages.failure(FailReason.LIMIT_EXCEEDED));
        }
        if (total == Long.MAX_VALUE) {
            return new Result(false, Messages.failure(FailReason.LIMIT_EXCEEDED));
        }
        if (total > 0) {
            LedgerAccount account = economy.account(id);
            TransactionResult result = economy.transfer(account, economy.serverAccount(), total, 0, Reason.of(Reasons.VILLAGE_RENT, village.key()));
            if (result instanceof TransactionResult.Failure failure) {
                return new Result(false, Messages.failure(failure));
            }
        }
        // Paid: nothing below can fail
        Villages.addToTreasury(server, village, total);
        long until = Math.max(today, current) + days;
        village.licences.put(id, until);
        VillageEconomy.get(server).setDirty();
        return new Result(true, Messages.tr("tradery.rent.done", "Your trading place in %s holds for %s days. Paid %s",
            Villages.displayName(village), until - today, Messages.coins(total)));
    }

    /** Sets {@code player}'s licence to hold until {@code until} (exclusive), or removes it when that's not after today (tests and admin tools). */
    public static void setLicence(MinecraftServer server, VillageState village, UUID player, long until) {
        if (until > VillagerPurchases.today(server)) {
            village.licences.put(player, until);
        } else {
            village.licences.remove(player);
        }
        VillageEconomy.get(server).setDirty();
    }

    /** Drops licences that ran out. */
    private static void pruneLicences(MinecraftServer server, VillageState village, long today) {
        boolean changed = false;
        for (Iterator<Map.Entry<UUID, Long>> it = village.licences.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() <= today) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            VillageEconomy.get(server).setDirty();
        }
    }

    // ------------------------------------------------------------------ caravans

    /** Caravans coming to or leaving this village, as lines for the board ("A caravan from … arrives in 2 days"). */
    public static List<Component> caravanNews(MinecraftServer server, VillageState village) {
        return Caravans.news(server, village);
    }

    public record Result(boolean success, Component message) {
    }

    // ------------------------------------------------------------------ level tick

    /** Level tick: market day news for players near a bell, caravans, raids. */
    public static void onLevelTick(ServerLevel level) {
        long time = level.getGameTime();
        if (time % ANNOUNCE_CHECK_TICKS == 0) {
            announceMarketDays(level);
        }
        Caravans.onLevelTick(level);
        VillageRaids.onLevelTick(level);
    }

    /** The server stopped: forget who was told what (a new world starts with its own days). */
    public static void onServerStopped() {
        TOLD.clear();
        toldDay = Long.MIN_VALUE;
        Caravans.onServerStopped();
    }

    /** Tells players near a bell (once a day per village) that it's market day there. */
    static void announceMarketDays(ServerLevel level) {
        if (!Villages.config().marketDay().enabled() || level.players().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        long today = VillagerPurchases.today(server);
        if (today != toldDay) {
            TOLD.clear();
            toldDay = today;
        }
        double radiusSqr = (double) ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
        for (VillageState village : VillageEconomy.get(server).all()) {
            if (village.dimension() != level.dimension() || !village.census().known()) {
                continue;
            }
            Vec3 bell = Vec3.atCenterOf(village.bell());
            Boolean marketDay = null;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(bell) > radiusSqr) {
                    continue;
                }
                if (marketDay == null) {
                    marketDay = isMarketDay(server, village);
                }
                if (!marketDay) {
                    break;
                }
                if (TOLD.add(village.key() + "|" + player.getUUID())) {
                    player.sendSystemMessage(Messages.tr("tradery.market_day.today", "Market day in %s today! Villagers come to the stalls more often",
                        Villages.displayName(village)));
                }
            }
        }
    }
}
