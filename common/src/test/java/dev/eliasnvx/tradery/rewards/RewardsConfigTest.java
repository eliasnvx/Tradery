package dev.eliasnvx.tradery.rewards;

import dev.eliasnvx.tradery.api.event.RewardGrantedEvent;
import dev.eliasnvx.tradery.config.ConfigFile;
import dev.eliasnvx.tradery.config.RewardsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsConfigTest {
    private static ConfigFile<RewardsConfig> file(Path dir) {
        return new ConfigFile<>(dir.resolve("rewards.json5"), RewardsConfig.CODEC, RewardsConfig.DEFAULT, RewardsConfig.COMMENTS, RewardsConfig.HEADER);
    }

    @Test
    void defaultsRoundTrip(@TempDir Path dir) throws Exception {
        ConfigFile<RewardsConfig> file = file(dir);
        ConfigFile.Result<RewardsConfig> result = file.decode(file.encode(RewardsConfig.DEFAULT));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(RewardsConfig.DEFAULT.kill().rewards().keySet(), result.value().kill().rewards().keySet());
        assertTrue(result.value().kill().enabled());
        assertTrue(!result.value().mine().enabled());
    }

    @Test
    void specExampleShapeDecodes(@TempDir Path dir) throws Exception {
        RewardsConfig config = file(dir).decode("""
            {
              kill: { enabled: true, rewards: {
                "minecraft:zombie":   { min: 1, max: 3, chance: 1.0 },
                "minecraft:creeper":  { min: 2, max: 4 },
                "#minecraft:raiders": { min: 5, max: 10 },
                "minecraft:bat":      { min: 0.5 },
              } },
              rules: { ignoreSpawnerMobs: true, dailyCap: 1000, diminishing: { windowSec: 60, after: 20, factor: 0.5 } }
            }
            """).value();
        RewardsConfig.Reward creeper = config.kill().rewards().get("minecraft:creeper");
        assertEquals(1.0, creeper.chance(), "chance defaults to 1");
        RewardsConfig.Reward bat = config.kill().rewards().get("minecraft:bat");
        assertEquals(new BigDecimal("0.5"), bat.max(), "max defaults to min");
        assertEquals(4, config.kill().rewards().size());
        assertEquals(RewardsConfig.DEFAULT.mine(), config.mine(), "missing sections keep defaults");
    }

    @Test
    void diminishingKicksInAfterTheLimit() {
        RewardsConfig.Diminishing rules = new RewardsConfig.Diminishing(true, 60, 3, 0.5);
        UUID player = UUID.randomUUID();
        long[] paid = new long[5];
        for (int i = 0; i < 5; i++) {
            paid[i] = Rewards.diminish(player, RewardGrantedEvent.Type.KILL, 100, rules);
        }
        assertEquals(100, paid[2], "third reward in full");
        assertEquals(50, paid[3], "fourth one halved");
        assertEquals(100, Rewards.diminish(player, RewardGrantedEvent.Type.FISH, 100, rules), "other kinds count separately");
        assertEquals(100, Rewards.diminish(UUID.randomUUID(), RewardGrantedEvent.Type.KILL, 100, rules), "other players count separately");
    }
}
