package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.menu.CounterBuyerMenu;
import dev.eliasnvx.tradery.menu.CounterOwnerMenu;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the market counter's menus and closes the buyers' ones when its offers change. */
public final class CounterMenus {
    private CounterMenus() {
    }

    public static void openBuyer(ServerPlayer player, MarketCounterBlockEntity counter) {
        CounterBuyerMenu.Data data = new CounterBuyerMenu.Data(counter.getBlockPos(), counter.settings(),
            dev.eliasnvx.tradery.network.TraderyPayloads.clip(counter.ownerName(), 64));
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new CounterBuyerMenu(id, inventory, data, counter), title(counter)),
            CounterBuyerMenu.Data.STREAM_CODEC, data);
    }

    public static void openOwner(ServerPlayer player, MarketCounterBlockEntity counter, boolean adminMode) {
        CounterOwnerMenu.Data data = new CounterOwnerMenu.Data(counter.getBlockPos(), counter.settings(), adminMode,
            VendingConfigurator.feeText());
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new CounterOwnerMenu(id, inventory, data, counter), title(counter)),
            CounterOwnerMenu.Data.STREAM_CODEC, data);
    }

    private static Component title(MarketCounterBlockEntity counter) {
        return counter.ownerName().isEmpty()
            ? Messages.tr("block.tradery.market_counter", "Market Counter")
            : Messages.tr("tradery.counter.title", "%s's Market Counter", counter.ownerName());
    }

    /** Closes the buyer screens of this counter (its offers changed: nobody pays a price they didn't see). */
    public static void closeBuyers(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.containerMenu instanceof CounterBuyerMenu menu && menu.pos().equals(pos)) {
                player.closeContainer();
            }
        }
    }
}
