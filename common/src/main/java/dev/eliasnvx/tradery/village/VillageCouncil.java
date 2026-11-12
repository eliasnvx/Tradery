package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.VillagesConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The village patron and specialization (SPEC "Покровитель деревни", "Специализация"). Every project and donation a
 * player pays for counts as their contribution; the biggest contributor with at least
 * {@code villages.council.patronMinContribution} is the patron (a tie keeps the patron there is). The patron renames the
 * village, picks its specialization from Town level and gets {@code patronTaxSharePercent} of its trade tax, out of the
 * tax. Server thread.
 */
public final class VillageCouncil {
    /** A village name is at most this long. */
    public static final int MAX_NAME = 32;
    /** The board lists this many top contributors. */
    public static final int TOP = 5;

    private VillageCouncil() {
    }

    static VillagesConfig.Council config() {
        return Villages.config().council();
    }

    /** {@code villages.council.patronMinContribution} in minor units. */
    public static long minContribution() {
        return VillagerPurchases.toMinor(config().patronMinContribution());
    }

    // ------------------------------------------------------------------ contributions and the patron

    /** {@code player} gave the village {@code amount} (a project or a donation): remember it and pick the patron again. */
    public static void contribute(MinecraftServer server, VillageState village, ServerPlayer player, long amount) {
        if (amount <= 0) {
            return;
        }
        UUID id = player.getUUID();
        VillageState.Contribution old = village.contributions.get(id);
        long total = old == null ? amount : saturatingAdd(old.amount(), amount);
        village.contributions.put(id, new VillageState.Contribution(player.nameAndId().name(), total));
        VillageEconomy.get(server).setDirty();
        elect(server, village);
    }

    /**
     * Picks the patron: the biggest contribution of at least the minimum. A tie keeps the patron the village has (and
     * between others goes to the lower UUID, so it never flips back and forth). Tells the old and the new patron.
     */
    static void elect(MinecraftServer server, VillageState village) {
        long min = Math.max(1, minContribution());
        UUID best = null;
        long bestAmount = -1;
        for (Map.Entry<UUID, VillageState.Contribution> entry : village.contributions.entrySet()) {
            long amount = entry.getValue().amount();
            if (amount < min) {
                continue;
            }
            UUID id = entry.getKey();
            boolean better = amount > bestAmount
                || amount == bestAmount && (id.equals(village.patron) || !Objects.equals(best, village.patron) && id.compareTo(best) < 0);
            if (better) {
                best = id;
                bestAmount = amount;
            }
        }
        if (Objects.equals(best, village.patron)) {
            return;
        }
        UUID old = village.patron;
        village.patron = best;
        VillageEconomy.get(server).setDirty();
        if (best != null) {
            ServerPlayer now = server.getPlayerList().getPlayer(best);
            if (now != null) {
                now.sendSystemMessage(Messages.tr("tradery.council.patron_new",
                    "You are now the patron of %s. Rename it and pick its specialization on the village board", Villages.displayName(village)));
            }
        }
        if (old != null) {
            ServerPlayer before = server.getPlayerList().getPlayer(old);
            if (before != null) {
                before.sendSystemMessage(Messages.tr("tradery.council.patron_lost", "%s gave more to %s and is its patron now",
                    Messages.name(patronName(village)), Villages.displayName(village)));
            }
        }
    }

    /** The patron's name as they last contributed, or empty without a patron. */
    public static String patronName(VillageState village) {
        if (village.patron == null) {
            return "";
        }
        VillageState.Contribution contribution = village.contributions.get(village.patron);
        return contribution == null ? "" : contribution.name();
    }

    /** What {@code player} gave the village, minor units. */
    public static long contribution(VillageState village, UUID player) {
        VillageState.Contribution contribution = village.contributions.get(player);
        return contribution == null ? 0 : contribution.amount();
    }

    /** The biggest contributors, biggest first, at most {@link #TOP}. */
    public static List<VillageState.Contribution> top(VillageState village) {
        List<VillageState.Contribution> all = new ArrayList<>(village.contributions.values());
        all.sort(Comparator.comparingLong(VillageState.Contribution::amount).reversed().thenComparing(VillageState.Contribution::name));
        return List.copyOf(all.subList(0, Math.min(TOP, all.size())));
    }

    public static boolean isPatron(VillageState village, UUID player) {
        return player.equals(village.patron);
    }

    // ------------------------------------------------------------------ the patron's actions

    /** The patron renames the village; an empty name gives it back its old one. */
    public static VillageBoard.Outcome rename(ServerPlayer player, VillageState village, String raw) {
        MinecraftServer server = player.level().getServer();
        if (!isPatron(village, player.getUUID())) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.not_patron", "Only the village's patron can do this"));
        }
        String name = cleanName(raw);
        if (name.length() > MAX_NAME) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.name_long", "A village name is at most %s characters", MAX_NAME));
        }
        if (name.equals(village.name)) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.name_same", "The village already has this name"));
        }
        village.name = name;
        VillageEconomy.get(server).setDirty();
        return VillageBoard.Outcome.done(name.isEmpty()
            ? Messages.tr("tradery.council.name_reset", "The village has its old name again")
            : Messages.tr("tradery.council.renamed", "The village is now called %s", name));
    }

    /** Without formatting codes and control characters, single spaces, trimmed. */
    public static String cleanName(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), 4 * MAX_NAME));
        boolean space = false;
        for (int i = 0; i < raw.length() && out.length() <= MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (Character.isISOControl(c)) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** The patron picks the specialization: from {@code specializationMinLevel}, once every {@code specializationChangeDays}. */
    public static VillageBoard.Outcome specialize(ServerPlayer player, VillageState village, VillageSpecialization specialization) {
        MinecraftServer server = player.level().getServer();
        if (!isPatron(village, player.getUUID())) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.not_patron", "Only the village's patron can do this"));
        }
        Villages.touch(server, village);
        if (Villages.level(village) < config().specializationMinLevel()) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.spec_level", "A village picks a specialization once it's a %s",
                Villages.levelName(config().specializationMinLevel())));
        }
        if (specialization.id().equals(village.specialization)) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.spec_same", "The village already specializes in %s",
                specialization.displayName()));
        }
        long wait = daysUntilChange(server, village);
        if (wait > 0) {
            return VillageBoard.Outcome.fail(Messages.tr("tradery.council.spec_wait", "The specialization can change again in %s days", wait));
        }
        village.specialization = specialization.id();
        village.specializationDay = VillagerPurchases.today(server);
        VillageEconomy.get(server).setDirty();
        return VillageBoard.Outcome.done(Messages.tr("tradery.council.specialized", "%s now specializes in %s", Villages.displayName(village),
            specialization.displayName()));
    }

    /** Days until the specialization may change again, 0 when it may now. */
    public static long daysUntilChange(MinecraftServer server, VillageState village) {
        if (village.specializationDay < 0 || village.specialization.isEmpty()) {
            return 0;
        }
        long next = village.specializationDay + config().specializationChangeDays();
        return Math.max(0, next - VillagerPurchases.today(server));
    }

    // ------------------------------------------------------------------ effects

    /** The specialization in effect: picked, and the village still at {@code specializationMinLevel} or up. */
    public static Optional<VillageSpecialization> active(VillageState village) {
        if (village.specialization.isEmpty() || Villages.level(village) < config().specializationMinLevel()) {
            return Optional.empty();
        }
        return VillageSpecialization.byId(village.specialization);
    }

    /** 1 + {@code specializationBonus} when {@code specialization} is in effect here, else 1. */
    public static double factor(@Nullable VillageState village, VillageSpecialization specialization) {
        return village != null && active(village).orElse(null) == specialization ? 1 + config().specializationBonus() : 1.0;
    }

    /** {@code specializationBonus} in percent, for the texts. */
    public static int bonusPercent() {
        return (int) Math.round(config().specializationBonus() * 100);
    }

    /**
     * The patron's share of a trade tax of {@code tax} (minor units): paid to them from the server account
     * ({@link Reasons#VILLAGE_PATRON}). Returns what was paid: 0 without a patron, or when the payment is refused
     * (their balance at its cap), so the whole tax stays with the treasury.
     */
    static long payPatron(MinecraftServer server, VillageState village, long tax) {
        EconomyService economy = EconomyService.INSTANCE;
        if (tax <= 0 || village.patron == null || !economy.isReady()) {
            return 0;
        }
        long share = Money.percentOf(tax, config().patronTaxSharePercent());
        if (share <= 0) {
            return 0;
        }
        TransactionResult result = economy.transfer(economy.serverAccount(), economy.account(village.patron), share, 0,
            Reason.of(Reasons.VILLAGE_PATRON, village.key()));
        return result instanceof TransactionResult.Failure ? 0 : share;
    }

    /** Moves the day the specialization was picked {@code days} back (tests and admin tools). */
    public static void backdateSpecialization(MinecraftServer server, VillageState village, int days) {
        if (village.specializationDay >= 0) {
            village.specializationDay -= days;
            VillageEconomy.get(server).setDirty();
        }
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
