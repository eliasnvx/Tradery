package dev.eliasnvx.tradery.compat;

import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Information pages shared by the recipe viewer plugins. */
public final class CompatInfo {
    /** One page: the items it describes and its text. */
    public record Page(List<ItemStack> items, Component title, Component text) {
    }

    private CompatInfo() {
    }

    public static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        pages.add(new Page(List.of(new ItemStack(TraderyBlocks.VENDING_BLOCK_ITEM.get())),
            Component.translatable("block.tradery.vending_block"), Component.translatable("tradery.info.vending_block")));
        pages.add(new Page(List.of(new ItemStack(TraderyBlocks.DISPLAY_BLOCK_ITEM.get())),
            Component.translatable("block.tradery.display_block"), Component.translatable("tradery.info.display_block")));
        List<ItemStack> ores = new ArrayList<>();
        List<ItemStack> coins = new ArrayList<>();
        for (CoinTier tier : CoinTier.values()) {
            ores.add(new ItemStack(TraderyItems.ore(tier, false)));
            ores.add(new ItemStack(TraderyItems.ore(tier, true)));
            coins.add(new ItemStack(TraderyItems.coin(tier)));
        }
        pages.add(new Page(ores, Component.translatable("tradery.info.coin_ore_title"), Component.translatable("tradery.info.coin_ore")));
        pages.add(new Page(coins, Component.translatable("tradery.info.coin_title"), Component.translatable("tradery.info.coin")));
        return pages;
    }
}
