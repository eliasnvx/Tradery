package dev.eliasnvx.tradery.menu;

import net.minecraft.core.BlockPos;

/** A menu bound to a Tradery block; closed for everyone when that block goes away. */
public interface VendingMenu {
    BlockPos pos();
}
