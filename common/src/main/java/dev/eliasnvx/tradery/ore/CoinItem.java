package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.economy.EconomyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A coin. Right-click puts the stack in hand on the balance; sneak + right-click puts every coin in the inventory
 * on it. The coins are removed only after the deposit succeeded.
 */
public class CoinItem extends Item {
    private final CoinTier tier;

    public CoinItem(CoinTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CoinTier tier() {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !EconomyService.INSTANCE.isReady()) {
            return InteractionResult.SUCCESS;
        }
        List<ItemStack> inventory = serverPlayer.getInventory().getNonEquipmentItems();
        long amount = 0;
        if (player.isShiftKeyDown()) {
            for (ItemStack stack : inventory) {
                amount = saturatedAdd(amount, Coins.value(stack));
            }
            amount = saturatedAdd(amount, Coins.value(player.getOffhandItem()));
        } else {
            amount = Coins.value(player.getItemInHand(hand));
        }
        if (amount <= 0) {
            return InteractionResult.PASS;
        }
        EconomyService economy = EconomyService.INSTANCE;
        TransactionResult result = economy.deposit(economy.account(player.getUUID()), amount, Reason.of(Reasons.COIN_DEPOSIT));
        if (result instanceof TransactionResult.Failure failure) {
            serverPlayer.sendOverlayMessage(Messages.failure(failure));
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            for (int i = 0; i < inventory.size(); i++) {
                if (Coins.isCoin(inventory.get(i))) {
                    inventory.set(i, ItemStack.EMPTY);
                }
            }
            if (Coins.isCoin(player.getOffhandItem())) {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        } else {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        serverPlayer.getInventory().setChanged();
        return InteractionResult.SUCCESS_SERVER;
    }

    private static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** "Worth 1.00 ₮" with the connected server's values (clients never read the server config). */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        dev.eliasnvx.tradery.client.ClientEconomy.CurrencyView currency = dev.eliasnvx.tradery.client.ClientEconomy.currency();
        if (currency == null) {
            return;
        }
        long each = switch (tier) {
            case COPPER -> currency.copperValue();
            case SILVER -> currency.silverValue();
            case GOLD -> currency.goldValue();
        };
        builder.accept(Component.translatable("tradery.coin.worth", currency.format(each)).withStyle(ChatFormatting.GOLD));
        if (stack.getCount() > 1) {
            builder.accept(Component.translatable("tradery.coin.worth_stack", currency.format(each * stack.getCount())).withStyle(ChatFormatting.GRAY));
        }
        builder.accept(Component.translatable("tradery.coin.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
