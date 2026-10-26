package dev.eliasnvx.tradery.registry;

import dev.eliasnvx.tradery.menu.DisplayMenu;
import dev.eliasnvx.tradery.menu.VendingBuyerMenu;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/** Menu types. Each one sends its opening data to the client (see the menus' {@code Data} records). */
public final class TraderyMenus {
    public static final Supplier<MenuType<VendingBuyerMenu>> VENDING_BUYER = Platform.get().register(Registries.MENU, "vending_buyer",
        () -> Platform.get().menuType(VendingBuyerMenu::new, VendingBuyerMenu.Data::read));
    public static final Supplier<MenuType<VendingOwnerMenu>> VENDING_OWNER = Platform.get().register(Registries.MENU, "vending_owner",
        () -> Platform.get().menuType(VendingOwnerMenu::new, VendingOwnerMenu.Data::read));
    public static final Supplier<MenuType<DisplayMenu>> DISPLAY = Platform.get().register(Registries.MENU, "display",
        () -> Platform.get().menuType(DisplayMenu::new, DisplayMenu.Data::read));
    public static final Supplier<MenuType<VillagerTradeMenu>> VILLAGER_TRADE = Platform.get().register(Registries.MENU, "villager_trade",
        () -> Platform.get().menuType(VillagerTradeMenu::new, VillagerTradeMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<dev.eliasnvx.tradery.menu.VillageBoardMenu>> VILLAGE_BOARD = Platform.get().register(Registries.MENU,
        "village_board", () -> Platform.get().menuType(dev.eliasnvx.tradery.menu.VillageBoardMenu::new,
            dev.eliasnvx.tradery.menu.VillageBoardMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<dev.eliasnvx.tradery.menu.CounterOwnerMenu>> COUNTER_OWNER = Platform.get().register(Registries.MENU,
        "counter_owner", () -> Platform.get().menuType(dev.eliasnvx.tradery.menu.CounterOwnerMenu::new,
            dev.eliasnvx.tradery.menu.CounterOwnerMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<dev.eliasnvx.tradery.menu.CounterBuyerMenu>> COUNTER_BUYER = Platform.get().register(Registries.MENU,
        "counter_buyer", () -> Platform.get().menuType(dev.eliasnvx.tradery.menu.CounterBuyerMenu::new,
            dev.eliasnvx.tradery.menu.CounterBuyerMenu.Data.STREAM_CODEC));
    public static final Supplier<MenuType<dev.eliasnvx.tradery.menu.ShopLedgerMenu>> SHOP_LEDGER = Platform.get().register(Registries.MENU,
        "shop_ledger", () -> Platform.get().menuType(dev.eliasnvx.tradery.menu.ShopLedgerMenu::new,
            dev.eliasnvx.tradery.menu.ShopLedgerMenu.Data.STREAM_CODEC));

    private TraderyMenus() {
    }

    static void init() {
    }
}
