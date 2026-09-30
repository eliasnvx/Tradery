package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Tooltip lines of a vending block. Owner and offer come from the block's own client copy; the stock comes from
 * {@link VendingStockData} on the server.
 */
enum VendingJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Tradery.id("vending_block");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof VendingBlockEntity vendor)) {
            return;
        }
        VendingSettings settings = vendor.settings();
        if (!vendor.ownerName().isEmpty()) {
            tooltip.add(Component.translatable("tradery.jade.owner", vendor.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        if (!settings.isConfigured()) {
            tooltip.add(Component.translatable("tradery.vending.not_configured").withStyle(ChatFormatting.RED));
            return;
        }
        Component goods = Component.literal(settings.perTrade() + " × ").append(settings.goods().getHoverName());
        Component price;
        if (settings.priceMode() == PriceMode.ITEM) {
            price = Component.literal(settings.pricePerTrade() + " × ").append(settings.priceItem().getHoverName());
        } else {
            ClientEconomy.CurrencyView currency = ClientEconomy.currency();
            price = Component.literal(settings.price() == 0 ? Component.translatable("tradery.vending.free").getString()
                : currency != null ? currency.format(settings.price()) : String.valueOf(settings.price())).withStyle(ChatFormatting.GOLD);
        }
        tooltip.add(Component.translatable(settings.isBuyback() ? "tradery.jade.buys" : "tradery.jade.sells", goods, price));
        VendingStockData.INSTANCE.decodeFromData(accessor).ifPresent(count -> tooltip.add(Component.translatable(
            settings.isBuyback() ? "tradery.screen.room" : "tradery.screen.in_stock",
            count >= VendingTrades.UNLIMITED ? "∞" : String.valueOf(count)).withStyle(count > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
    }
}
