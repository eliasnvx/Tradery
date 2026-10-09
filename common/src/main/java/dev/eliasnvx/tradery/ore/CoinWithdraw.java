package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.vending.StackMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@code /tradery withdraw <amount>}: balance → coin items, only when the coins fit the inventory. */
public final class CoinWithdraw {
    private CoinWithdraw() {
    }

    public static int withdraw(ServerPlayer player, long amount) {
        EconomyService economy = EconomyService.INSTANCE;
        if (amount <= 0) {
            player.sendSystemMessage(Messages.failure(dev.eliasnvx.tradery.api.FailReason.INVALID_AMOUNT));
            return 0;
        }
        Coins.Split split = Coins.split(amount);
        if (split.remainder() != 0) {
            player.sendSystemMessage(Messages.tr("tradery.withdraw.not_coins", "%s can't be paid out in coins (smallest coin: %s)",
                Messages.money(amount), Messages.money(CoinTier.COPPER.value())));
            return 0;
        }
        // Everything must fit before the money moves: dropped coins would be picked up and deposited again
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            simulated.add(stack.copy());
        }
        for (ItemStack coins : split.stacks()) {
            if (StackMath.insert(simulated, coins, coins.getCount()) != coins.getCount()) {
                player.sendSystemMessage(Messages.tr("tradery.vending.no_space", "Not enough room in your inventory"));
                return 0;
            }
        }
        TransactionResult result = economy.withdraw(economy.account(player.getUUID()), amount, Reason.of(Reasons.COIN_WITHDRAW));
        if (result instanceof TransactionResult.Failure failure) {
            player.sendSystemMessage(Messages.failure(failure));
            return 0;
        }
        for (ItemStack coins : split.stacks()) {
            StackMath.insert(player.getInventory().items, coins, coins.getCount());
        }
        player.getInventory().setChanged();
        player.sendSystemMessage(Messages.tr("tradery.withdraw.done", "Withdrew %s in coins", Messages.money(amount)));
        return 1;
    }
}
