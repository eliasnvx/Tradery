package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Village projects players can fund (SPEC "Вложения"): data pack {@code data/<ns>/tradery/village_projects/*.json}.
 * The file id is the project id; its name and description are the lang keys
 * {@code tradery.project.<ns>.<path>} and {@code ….desc}. Effects add up over a village's funded projects.
 */
public final class VillageProjects {
    static final Identifier LISTENER = Tradery.id("village_projects");

    /**
     * What funding a project does once, beyond its effects: nothing, a market stall of the village's type, an iron
     * golem, a building from the project's {@code structure} template ({@code tradery:village/<type>/<structure>}),
     * lamp posts along the streets, or the streets paved in stone.
     */
    public enum Action {
        NONE, MARKET_STALL, IRON_GOLEM, STRUCTURE, STREET_LAMPS, PAVE_STREETS;

        static final Codec<Action> CODEC = ConfigCodecs.enumCodec(Action.class);
    }

    /**
     * Lasting effects of a funded project. Multipliers multiply, bonuses add.
     *
     * @param visitChance      added to the customers' visit chance factor (0.1 = +10%)
     * @param wageMultiplier   villagers' wages and treasury income, times this
     * @param treasuryCap      added to the treasury's cap factor (0.5 = holds 50% more)
     * @param walletDays       added to {@code villagers.walletDays}
     * @param raidLoss         losses of a lost raid, times this
     * @param caravanDaysLess  caravans leave this many days sooner
     * @param marketDayEarlier market days come this many days sooner
     */
    public record Effects(double visitChance, double wageMultiplier, double treasuryCap, int walletDays, double raidLoss,
                          int caravanDaysLess, int marketDayEarlier) {
        public static final Effects NONE = new Effects(0, 1, 0, 0, 1, 0, 0);
        static final Codec<Effects> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 10).optionalFieldOf("visit_chance", 0.0).forGetter(Effects::visitChance),
            Codec.doubleRange(0, 10).optionalFieldOf("wage_multiplier", 1.0).forGetter(Effects::wageMultiplier),
            Codec.doubleRange(0, 10).optionalFieldOf("treasury_cap", 0.0).forGetter(Effects::treasuryCap),
            Codec.intRange(0, 30).optionalFieldOf("wallet_days", 0).forGetter(Effects::walletDays),
            Codec.doubleRange(0, 1).optionalFieldOf("raid_loss", 1.0).forGetter(Effects::raidLoss),
            Codec.intRange(0, 30).optionalFieldOf("caravan_days_less", 0).forGetter(Effects::caravanDaysLess),
            Codec.intRange(0, 30).optionalFieldOf("market_day_earlier", 0).forGetter(Effects::marketDayEarlier)
        ).apply(i, Effects::new));

        Effects plus(Effects other) {
            return new Effects(visitChance + other.visitChance, wageMultiplier * other.wageMultiplier, treasuryCap + other.treasuryCap,
                walletDays + other.walletDays, raidLoss * other.raidLoss, caravanDaysLess + other.caravanDaysLess,
                marketDayEarlier + other.marketDayEarlier);
        }
    }

    /**
     * A project.
     *
     * @param cost   major units, paid by the player who funds it
     * @param level  village level it needs (1 = any)
     * @param icon      item shown on the board
     * @param action    done once when funded
     * @param structure template name to build by the bell (with any action but the market stall), else empty
     */
    public record Project(Identifier id, BigDecimal cost, int level, Item icon, Effects effects, Action action, String structure) {
    }

    record ProjectFile(BigDecimal cost, int level, Identifier icon, Effects effects, Action action, String structure) {
        static final Codec<ProjectFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ConfigCodecs.NON_NEGATIVE.fieldOf("cost").forGetter(ProjectFile::cost),
            Codec.intRange(1, 16).optionalFieldOf("level", 1).forGetter(ProjectFile::level),
            Identifier.CODEC.optionalFieldOf("icon", BuiltInRegistries.ITEM.getKey(Items.EMERALD)).forGetter(ProjectFile::icon),
            Effects.CODEC.optionalFieldOf("effects", Effects.NONE).forGetter(ProjectFile::effects),
            Action.CODEC.optionalFieldOf("action", Action.NONE).forGetter(ProjectFile::action),
            Codec.STRING.optionalFieldOf("structure", "").forGetter(ProjectFile::structure)
        ).apply(i, ProjectFile::new));
    }

    private static volatile Map<Identifier, Project> projects = Map.of();

    private VillageProjects() {
    }

    /** Registers the data pack listener; called once by each loader at startup. */
    public static void registerReloadListeners() {
        Platform.get().registerServerReloadListener(LISTENER, new Loader());
    }

    /** Every project, in id order. */
    public static List<Project> all() {
        return List.copyOf(projects.values());
    }

    public static Optional<Project> get(Identifier id) {
        return Optional.ofNullable(projects.get(id));
    }

    /** The effects of a village's funded projects together (projects no longer in the data pack count for nothing). */
    public static Effects effects(VillageState village) {
        Effects total = Effects.NONE;
        for (String id : village.projects) {
            Identifier parsed = Identifier.tryParse(id);
            Project project = parsed == null ? null : projects.get(parsed);
            if (project != null) {
                total = total.plus(project.effects());
            }
        }
        return total;
    }

    static void apply(Map<Identifier, ProjectFile> files) {
        Map<Identifier, Project> loaded = new LinkedHashMap<>();
        for (Map.Entry<Identifier, ProjectFile> entry : new TreeMap<>(files).entrySet()) {
            ProjectFile file = entry.getValue();
            Item icon = BuiltInRegistries.ITEM.getOptional(file.icon()).orElse(Items.EMERALD);
            Action action = file.action();
            if (action == Action.STRUCTURE && file.structure().isEmpty()) {
                Tradery.LOGGER.warn("Village project {} has action \"structure\" but no \"structure\" name; it builds nothing", entry.getKey());
                action = Action.NONE;
            }
            loaded.put(entry.getKey(), new Project(entry.getKey(), file.cost(), file.level(), icon, file.effects(), action, file.structure()));
        }
        projects = java.util.Collections.unmodifiableMap(loaded);
        Tradery.LOGGER.debug("Loaded {} village projects", loaded.size());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<ProjectFile> {
        Loader() {
            super(ProjectFile.CODEC, FileToIdConverter.json("tradery/village_projects"));
        }

        @Override
        protected void apply(Map<Identifier, ProjectFile> files, ResourceManager manager, ProfilerFiller profiler) {
            VillageProjects.apply(files);
        }
    }
}
