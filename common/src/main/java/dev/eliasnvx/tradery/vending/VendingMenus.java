package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.menu.DisplayMenu;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.menu.VendingMenu;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.Optional;

/** Opens Tradery menus and closes them when their block changes or goes away. */
public final class VendingMenus {
    private VendingMenus() {
    }

    public static void openBuyer(ServerPlayer player, VendingBlockEntity vendor) {
        VendingBuyerMenu.Data data = new VendingBuyerMenu.Data(vendor.getBlockPos(), vendor.settings(), vendor.ownerName());
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new VendingBuyerMenu(id, inventory, data, vendor), title(vendor)),
            (buf, d) -> d.write(buf), data);
    }

    public static void openOwner(ServerPlayer player, VendingBlockEntity vendor, boolean adminMode) {
        VendingOwnerMenu.Data data = new VendingOwnerMenu.Data(vendor.getBlockPos(), vendor.settings(), vendor.admin(),
            Optional.ofNullable(vendor.facade()), adminMode, vendor.owner() instanceof AccountId.System, VendingConfigurator.feeText());
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new VendingOwnerMenu(id, inventory, data, vendor), title(vendor)),
            (buf, d) -> d.write(buf), data);
    }

    public static void openDisplay(ServerPlayer player, DisplayBlockEntity display) {
        DisplayMenu.Data data = new DisplayMenu.Data(display.getBlockPos(), display.shown(), display.animation());
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new DisplayMenu(id, inventory, data, display),
                Component.translatable("block.tradery.display_block")),
            (buf, d) -> d.write(buf), data);
    }

    private static Component title(VendingBlockEntity vendor) {
        return vendor.ownerName().isEmpty()
            ? Component.translatable("block.tradery.vending_block")
            : Component.translatable("tradery.vending.title", vendor.ownerName());
    }

    /** Closes every Tradery menu of this block (it was broken). */
    public static void closeAllFor(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.containerMenu instanceof VendingMenu menu && menu.pos().equals(pos)) {
                player.closeContainer();
            }
        }
    }

    /** Closes the buyer screens of this block (its offer changed). */
    public static void closeBuyers(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.containerMenu instanceof VendingBuyerMenu menu && menu.pos().equals(pos)) {
                player.closeContainer();
            }
        }
    }
}
