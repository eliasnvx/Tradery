package dev.eliasnvx.tradery.rewards;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.event.RewardGrantedEvent;
import dev.eliasnvx.tradery.config.ConfigFile;
import dev.eliasnvx.tradery.config.RewardsConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.StatsData;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money for mobs and actions ({@code rewards.json5}). Each reward: chance → random amount in [min, max] →
 * diminishing returns → daily cap → {@link RewardGrantedEvent} → deposit with reason {@code tradery:reward/<type>}.
 */
public final class Rewards {
    /** Scoreboard tag of mobs from (trial) spawners; set when they spawn, read when they die. */
    public static final String SPAWNER_TAG = "tradery.spawner";

    private static ConfigFile<RewardsConfig> file;
    private static volatile RewardsConfig config = RewardsConfig.DEFAULT;
    private static volatile Tables tables;
    /** Reward times per player and type, for diminishing returns. Not saved (a restart forgives). */
    private static final Map<UUID, Map<RewardGrantedEvent.Type, Deque<Long>>> RECENT = new HashMap<>();

    private record Tables(RewardTable<net.minecraft.world.entity.EntityType<?>> kill, RewardTable<Block> mine,
                          RewardTable<net.minecraft.world.item.Item> craft, RewardTable<net.minecraft.world.item.Item> fish,
                          RewardTable<Object> advancement) {
    }

    private Rewards() {
    }

    /** (Re)reads {@code rewards.json5}. */
    public static RewardsConfig load() {
        if (file == null) {
            file = new ConfigFile<>(TraderyConfig.directory().resolve("rewards.json5"), RewardsConfig.CODEC, RewardsConfig.DEFAULT,
                RewardsConfig.COMMENTS, RewardsConfig.HEADER);
        }
        config = file.load();
        tables = new Tables(
            new RewardTable<>(config.kill(), Registries.ENTITY_TYPE, "kill"),
            new RewardTable<>(config.mine(), Registries.BLOCK, "mine"),
            new RewardTable<>(config.craft(), Registries.ITEM, "craft"),
            new RewardTable<>(config.fish(), Registries.ITEM, "fish"),
            new RewardTable<>(config.advancement(), null, "advancement"));
        return config;
    }

    public static RewardsConfig config() {
        return config;
    }

    /** GameTests only: swaps the rewards config in memory (tests restore it before they finish). */
    public static void setConfigForTests(RewardsConfig replacement) {
        config = replacement;
        tables = new Tables(
            new RewardTable<>(replacement.kill(), Registries.ENTITY_TYPE, "kill"),
            new RewardTable<>(replacement.mine(), Registries.BLOCK, "mine"),
            new RewardTable<>(replacement.craft(), Registries.ITEM, "craft"),
            new RewardTable<>(replacement.fish(), Registries.ITEM, "fish"),
            new RewardTable<>(replacement.advancement(), null, "advancement"));
    }

    // ------------------------------------------------------------------ hooks (server thread)

    /** A living entity died; pays its killer. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer killer) || victim instanceof Player || tables == null || tables.kill().isEmpty()) {
            return;
        }
        if (config.rules().ignoreSpawnerMobs() && victim.entityTags().contains(SPAWNER_TAG)) {
            return;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        RewardsConfig.Reward reward = tables.kill().find(id, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(victim.getType()));
        grant(killer, RewardGrantedEvent.Type.KILL, id, reward);
    }

    /** A player broke a block (not cancelled). */
    public static void onBlockBroken(Player player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isCreative() || tables == null || tables.mine().isEmpty()) {
            return;
        }
        boolean placed = PlacedBlocks.isPlaced(level, pos);
        PlacedBlocks.unmark(level, pos);
        if (placed) {
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        grant(serverPlayer, RewardGrantedEvent.Type.MINE, id, tables.mine().find(id, BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock())));
    }

    /** A block was placed by a player: remember it if mining it would pay. */
    public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (tables == null || tables.mine().isEmpty()) {
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (tables.mine().find(id, BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock())) != null) {
            PlacedBlocks.mark(level, pos);
        }
    }

    /** Items taken from a crafting result slot. */
    public static void onCrafted(Player player, ItemStack result, int count) {
        if (!(player instanceof ServerPlayer serverPlayer) || count <= 0 || tables == null || tables.craft().isEmpty()) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(result.getItem());
        RewardsConfig.Reward reward = tables.craft().find(id, BuiltInRegistries.ITEM.wrapAsHolder(result.getItem()));
        // One reward per crafted item: shift-clicking a stack pays like crafting one by one
        int crafts = Math.max(1, count / Math.max(1, result.getCount()));
        for (int i = 0; i < crafts && reward != null; i++) {
            grant(serverPlayer, RewardGrantedEvent.Type.CRAFT, id, reward);
        }
    }

    /** A fishing rod brought up items. */
    public static void onFished(Player player, List<ItemStack> catches) {
        if (!(player instanceof ServerPlayer serverPlayer) || tables == null || tables.fish().isEmpty()) {
            return;
        }
        for (ItemStack stack : catches) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            RewardsConfig.Reward reward = tables.fish().find(id, BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
            if (reward != null) {
                grant(serverPlayer, RewardGrantedEvent.Type.FISH, id, reward);
                return; // one reward per catch
            }
        }
    }

    /** An advancement was completed for the first time. */
    public static void onAdvancement(ServerPlayer player, Identifier advancement) {
        if (tables == null || tables.advancement().isEmpty()) {
            return;
        }
        grant(player, RewardGrantedEvent.Type.ADVANCEMENT, advancement, tables.advancement().find(advancement, null));
    }

    public static void forget(ServerPlayer player) {
        RECENT.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ payout

    static void grant(ServerPlayer player, RewardGrantedEvent.Type type, Identifier source, @Nullable RewardsConfig.Reward reward) {
        EconomyService economy = EconomyService.INSTANCE;
        if (reward == null || !economy.isReady()) {
            return;
        }
        RewardsConfig.Rules rules = config.rules();
        if (rules.ignoreFakePlayers() && Platform.get().isFakePlayer(player)) {
            return;
        }
        if (reward.chance() < 1.0 && player.getRandom().nextDouble() >= reward.chance()) {
            return;
        }
        long min = economy.toMinorOrMax(reward.min(), "rewards min");
        long max = Math.max(min, economy.toMinorOrMax(reward.max(), "rewards max"));
        long amount = max > min ? min + (long) Math.floor(player.getRandom().nextDouble() * (max - min + 1)) : min;
        amount = diminish(player.getUUID(), type, amount, rules.diminishing());

        StatsData stats = economy.stats();
        long cap = economy.toMinorOrMax(rules.dailyCap(), "rules.dailyCap");
        if (cap > 0) {
            amount = Math.min(amount, Math.max(0, cap - stats.earnedToday(player.getUUID(), StatsData.Earning.REWARD)));
        }
        if (amount <= 0) {
            return;
        }
        RewardGrantedEvent event = RewardGrantedEvent.EVENT.post(new RewardGrantedEvent(player, type, source, amount));
        if (event.isCancelled() || event.amount() <= 0) {
            return;
        }
        Identifier reason = switch (type) {
            case KILL -> Reasons.REWARD_KILL;
            case MINE -> Reasons.REWARD_MINE;
            case CRAFT -> Reasons.REWARD_CRAFT;
            case FISH -> Reasons.REWARD_FISH;
            case ADVANCEMENT -> Reasons.REWARD_ADVANCEMENT;
        };
        if (economy.deposit(economy.account(player.getUUID()), event.amount(), Reason.of(reason, source.toString())).isSuccess()) {
            stats.addEarned(player.getUUID(), StatsData.Earning.REWARD, event.amount());
        }
    }

    /** After {@code after} rewards of one kind in the window, each further one is multiplied by {@code factor}. */
    static long diminish(UUID player, RewardGrantedEvent.Type type, long amount, RewardsConfig.Diminishing rules) {
        if (!rules.enabled()) {
            return amount;
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = RECENT.computeIfAbsent(player, k -> new EnumMap<>(RewardGrantedEvent.Type.class))
            .computeIfAbsent(type, k -> new ArrayDeque<>());
        long window = rules.windowSec() * 1000L;
        while (!times.isEmpty() && now - times.peekFirst() >= window) {
            times.pollFirst();
        }
        times.addLast(now);
        return times.size() > rules.after() ? (long) Math.floor(amount * rules.factor()) : amount;
    }
}
