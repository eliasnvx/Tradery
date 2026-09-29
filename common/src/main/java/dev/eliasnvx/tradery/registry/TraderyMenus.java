package dev.eliasnvx.tradery.registry;

import dev.eliasnvx.tradery.menu.DisplayMenu;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/** Menu types. Each one sends its opening data to the client (see the menus' {@code Data} records). */
public final class TraderyMenus {
    public static final Supplier<MenuType<VendingBuyerMenu>> VENDING_BUYER = Platform.get().register(Registries.MENU, "vending_buyer",
        () -> Platform.get().menuType(VendingBuyerMenu::new, VendingBuyerMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<VendingOwnerMenu>> VENDING_OWNER = Platform.get().register(Registries.MENU, "vending_owner",
        () -> Platform.get().menuType(VendingOwnerMenu::new, VendingOwnerMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<DisplayMenu>> DISPLAY = Platform.get().register(Registries.MENU, "display",
        () -> Platform.get().menuType(DisplayMenu::new, DisplayMenu.Data.STREAM_CODEC));

    private TraderyMenus() {
    }

    static void init() {
    }
}
