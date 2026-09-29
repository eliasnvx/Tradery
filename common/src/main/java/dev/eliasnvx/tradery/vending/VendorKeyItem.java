package dev.eliasnvx.tradery.vending;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Admin key: right-click a vending block to open its admin settings (needs {@code tradery.admin.vendors}). */
public class VendorKeyItem extends Item {
    public VendorKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
