package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.StatsData;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/** Coin ore rules: who gets paid, the daily cap and inflation damping. Server thread. */
public final class CoinMining {
    private CoinMining() {
    }

    /** A real player in survival/adventure while direct-to-balance is on. Machines (fake players) get coin items. */
    static boolean paysToBalance(ServerPlayer player) {
        return TraderyConfig.server().ore().directToBalance() && !player.isCreative() && !player.isSpectator()
            && !Platform.get().isFakePlayer(player) && EconomyService.INSTANCE.isReady();
    }

    /**
     * Inflation damping: {@code factor = clamp(targetSupply / supply, minFactor, 1)}, with the fraction rounded
     * randomly so small amounts still pay on average.
     */
    static long damp(long amount, RandomSource random) {
        ServerConfig.InflationDamping damping = TraderyConfig.server().ore().inflationDamping();
        if (!damping.enabled() || amount <= 0 || !EconomyService.INSTANCE.isReady()) {
            return amount;
        }
        long supply = EconomyService.INSTANCE.moneySupply();
        long target = EconomyService.INSTANCE.toMinorOrMax(damping.targetSupply(), "ore.inflationDamping.targetSupply");
        if (supply <= target || supply <= 0) {
            return amount;
        }
        double factor = Math.max(damping.minFactor(), Math.min(1.0, target / (double) supply));
        return roundRandomly(amount * factor, random);
    }

    static long roundRandomly(double value, RandomSource random) {
        long whole = (long) Math.floor(value);
        return whole + (random.nextDouble() < value - whole ? 1 : 0);
    }

    /** Credits the miner, within {@code ore.dailyCap}; tells them when the cap cuts the payout. */
    static void payToBalance(ServerPlayer player, long amount) {
        EconomyService economy = EconomyService.INSTANCE;
        StatsData stats = economy.stats();
        long cap = economy.toMinorOrMax(TraderyConfig.server().ore().dailyCap(), "ore.dailyCap");
        long pay = amount;
        if (cap > 0) {
            long left = Math.max(0, cap - stats.earnedToday(player.getUUID(), StatsData.Earning.ORE));
            pay = Math.min(amount, left);
            if (pay < amount) {
                player.displayClientMessage(Messages.tr("tradery.ore.daily_cap", "Daily limit for coin ore reached"), true);
            }
        }
        if (pay <= 0) {
            return;
        }
        TransactionResult result = economy.deposit(economy.account(player.getUUID()), pay, Reason.of(Reasons.ORE_MINED));
        if (result.isSuccess()) {
            stats.addEarned(player.getUUID(), StatsData.Earning.ORE, pay);
        } else if (result instanceof TransactionResult.Failure failure) {
            player.displayClientMessage(Messages.failure(failure), true);
        }
    }

    /** Coin items for {@code amount}; the part no coin covers is rounded randomly to one smallest coin. */
    static List<ItemStack> mintCoins(long amount, RandomSource random) {
        Coins.Split split = Coins.split(amount);
        List<ItemStack> coins = new java.util.ArrayList<>(split.stacks());
        long smallest = CoinTier.COPPER.value();
        if (split.remainder() > 0 && smallest > 0 && random.nextDouble() < split.remainder() / (double) smallest) {
            coins.add(new ItemStack(dev.eliasnvx.tradery.registry.TraderyItems.coin(CoinTier.COPPER)));
        }
        long minted = 0;
        for (ItemStack coin : coins) {
            minted += Coins.value(coin);
        }
        if (minted > 0 && EconomyService.INSTANCE.isReady()) {
            EconomyService.INSTANCE.stats().recordCoinsMinted(minted);
        }
        return coins;
    }

    /** Value of a config amount, for messages. */
    static BigDecimal major(long minor) {
        return EconomyService.INSTANCE.defaultCurrency().toMajor(minor);
    }
}
