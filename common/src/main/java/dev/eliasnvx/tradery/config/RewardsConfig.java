package dev.eliasnvx.tradery.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.eliasnvx.tradery.config.ConfigCodecs.field;

/**
 * {@code config/tradery/rewards.json5}: money for mobs and actions. Keys are ids ({@code minecraft:zombie}),
 * tags ({@code #minecraft:raiders}) or {@code "*"} (anything of that kind). Amounts are in major units.
 */
public record RewardsConfig(Section kill, Section mine, Section craft, Section fish, Section advancement, Rules rules) {

    /** A reward range; {@code chance} is the probability to pay at all. */
    public record Reward(BigDecimal min, BigDecimal max, double chance) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
            ConfigCodecs.NON_NEGATIVE.fieldOf("min").forGetter(Reward::min),
            field(ConfigCodecs.NON_NEGATIVE, "max", BigDecimal.valueOf(-1)).forGetter(Reward::max),
            field(Codec.doubleRange(0, 1), "chance", 1.0).forGetter(Reward::chance)
        ).apply(i, (min, max, chance) -> new Reward(min, max.signum() < 0 ? min : max.max(min), chance)));

        static Reward of(double min, double max) {
            return new Reward(BigDecimal.valueOf(min), BigDecimal.valueOf(max), 1.0);
        }
    }

    public record Section(boolean enabled, Map<String, Reward> rewards) {
        static Codec<Section> codec(Section defaults) {
            return RecordCodecBuilder.create(i -> i.group(
                field(Codec.BOOL, "enabled", defaults.enabled).forGetter(Section::enabled),
                field(Codec.unboundedMap(Codec.STRING, Reward.CODEC), "rewards", defaults.rewards).forGetter(Section::rewards)
            ).apply(i, Section::new));
        }
    }

    /**
     * @param windowSec   window of the diminishing returns
     * @param after       rewards of one kind within the window before it kicks in
     * @param factor      multiplier for each reward after that
     */
    public record Diminishing(boolean enabled, int windowSec, int after, double factor) {
        static final Diminishing DEFAULT = new Diminishing(true, 60, 20, 0.5);
        static final Codec<Diminishing> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Diminishing::enabled),
            field(Codec.intRange(1, 86_400), "windowSec", DEFAULT.windowSec).forGetter(Diminishing::windowSec),
            field(Codec.intRange(0, 100_000), "after", DEFAULT.after).forGetter(Diminishing::after),
            field(Codec.doubleRange(0, 1), "factor", DEFAULT.factor).forGetter(Diminishing::factor)
        ).apply(i, Diminishing::new));
    }

    public record Rules(boolean ignoreSpawnerMobs, boolean ignoreFakePlayers, BigDecimal dailyCap, Diminishing diminishing) {
        static final Rules DEFAULT = new Rules(true, true, BigDecimal.valueOf(1000), Diminishing.DEFAULT);
        static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "ignoreSpawnerMobs", DEFAULT.ignoreSpawnerMobs).forGetter(Rules::ignoreSpawnerMobs),
            field(Codec.BOOL, "ignoreFakePlayers", DEFAULT.ignoreFakePlayers).forGetter(Rules::ignoreFakePlayers),
            field(ConfigCodecs.NON_NEGATIVE, "dailyCap", DEFAULT.dailyCap).forGetter(Rules::dailyCap),
            field(Diminishing.CODEC, "diminishing", DEFAULT.diminishing).forGetter(Rules::diminishing)
        ).apply(i, Rules::new));
    }

    private static Map<String, Reward> map(Object... pairs) {
        Map<String, Reward> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Reward) pairs[i + 1]);
        }
        return map;
    }

    /** Hostile mobs pay by default; the rest is off until the server enables it. */
    static final Section KILL = new Section(true, map(
        "minecraft:zombie", Reward.of(1, 3),
        "minecraft:husk", Reward.of(1, 3),
        "minecraft:drowned", Reward.of(1, 3),
        "minecraft:zombie_villager", Reward.of(1, 3),
        "minecraft:skeleton", Reward.of(1, 3),
        "minecraft:stray", Reward.of(1, 3),
        "minecraft:bogged", Reward.of(1, 3),
        "minecraft:spider", Reward.of(1, 2),
        "minecraft:cave_spider", Reward.of(1, 2),
        "minecraft:creeper", Reward.of(2, 4),
        "minecraft:witch", Reward.of(3, 6),
        "minecraft:slime", Reward.of(0.5, 1),
        "minecraft:magma_cube", Reward.of(0.5, 1),
        "minecraft:phantom", Reward.of(2, 4),
        "minecraft:enderman", Reward.of(3, 6),
        "minecraft:silverfish", Reward.of(0.5, 1),
        "minecraft:endermite", Reward.of(0.5, 1),
        "minecraft:blaze", Reward.of(3, 6),
        "minecraft:ghast", Reward.of(4, 8),
        "minecraft:wither_skeleton", Reward.of(4, 8),
        "minecraft:hoglin", Reward.of(2, 4),
        "minecraft:zoglin", Reward.of(2, 4),
        "minecraft:piglin_brute", Reward.of(5, 10),
        "minecraft:guardian", Reward.of(3, 6),
        "minecraft:elder_guardian", Reward.of(50, 80),
        "minecraft:shulker", Reward.of(4, 8),
        "minecraft:vex", Reward.of(1, 2),
        "minecraft:breeze", Reward.of(5, 10),
        "#minecraft:raiders", Reward.of(5, 10),
        "minecraft:warden", Reward.of(100, 150),
        "minecraft:wither", Reward.of(200, 300),
        "minecraft:ender_dragon", Reward.of(500, 500)));
    static final Section MINE = new Section(false, map(
        "minecraft:diamond_ore", Reward.of(5, 5),
        "minecraft:deepslate_diamond_ore", Reward.of(5, 5),
        "minecraft:emerald_ore", Reward.of(5, 5),
        "minecraft:ancient_debris", Reward.of(10, 10)));
    static final Section CRAFT = new Section(false, map(
        "minecraft:bread", Reward.of(0.1, 0.1),
        "minecraft:cake", Reward.of(1, 1)));
    static final Section FISH = new Section(false, map(
        "*", Reward.of(1, 1)));
    static final Section ADVANCEMENT = new Section(false, map(
        "minecraft:story/enter_the_nether", Reward.of(50, 50),
        "minecraft:story/enter_the_end", Reward.of(100, 100),
        "minecraft:end/kill_dragon", Reward.of(500, 500)));

    public static final RewardsConfig DEFAULT = new RewardsConfig(KILL, MINE, CRAFT, FISH, ADVANCEMENT, Rules.DEFAULT);

    public static final Codec<RewardsConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        field(Section.codec(KILL), "kill", KILL).forGetter(RewardsConfig::kill),
        field(Section.codec(MINE), "mine", MINE).forGetter(RewardsConfig::mine),
        field(Section.codec(CRAFT), "craft", CRAFT).forGetter(RewardsConfig::craft),
        field(Section.codec(FISH), "fish", FISH).forGetter(RewardsConfig::fish),
        field(Section.codec(ADVANCEMENT), "advancement", ADVANCEMENT).forGetter(RewardsConfig::advancement),
        field(Rules.CODEC, "rules", Rules.DEFAULT).forGetter(RewardsConfig::rules)
    ).apply(i, RewardsConfig::new));

    public static final String HEADER = """
        Tradery rewards: money for mobs and actions. JSON5. Apply with /tradery reload.
        Keys: an id ("minecraft:zombie"), a tag ("#minecraft:raiders") or "*" for anything of that kind.
        Amounts are in major units: { min: 1, max: 3, chance: 1.0 } pays 1..3 every time.""";

    public static final Map<String, String> COMMENTS = Map.ofEntries(
        Map.entry("kill", "Killing mobs (on by default: hostile mobs)"),
        Map.entry("mine", "Mining blocks. Blocks placed by players never pay. Blocks moved by pistons lose that mark, keep it off unless you need it"),
        Map.entry("craft", "Crafting items. Careful: reversible recipes (9 ingots <-> block) turn into free money"),
        Map.entry("fish", "Fishing: \"*\" for any catch, or item ids / tags"),
        Map.entry("advancement", "Completing advancements (paid once per player)"),
        Map.entry("rules.ignoreSpawnerMobs", "Mobs from spawners and trial spawners pay nothing (mob farms)"),
        Map.entry("rules.ignoreFakePlayers", "Machines acting as players (Deployer, auto-swords) get nothing"),
        Map.entry("rules.dailyCap", "Most money one player can get from rewards per day (UTC); 0 = no limit"),
        Map.entry("rules.diminishing", "After `after` rewards of one kind within `windowSec` seconds, each further one pays `factor` times as much")
    );
}
