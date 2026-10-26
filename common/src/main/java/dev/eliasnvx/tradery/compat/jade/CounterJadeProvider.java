package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.client.CounterHint;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Tooltip lines of a market counter: the owner, one line per offer ("Sells 4 × Bread for 2.50", red when sold out)
 * and the key to open it. Everything comes from the block's own client copy (offers and which are in stock).
 */
enum CounterJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Tradery.id("market_counter");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof MarketCounterBlockEntity counter)) {
            return;
        }
        if (!counter.ownerName().isEmpty()) {
            tooltip.add(Component.translatable("tradery.jade.owner", counter.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        CounterSettings settings = counter.settings();
        if (!settings.isConfigured()) {
            tooltip.add(Component.translatable("tradery.counter.not_configured").withStyle(ChatFormatting.RED));
            return;
        }
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            CounterOffer offer = settings.offer(i);
            if (!offer.isConfigured()) {
                continue;
            }
            Component goods = Component.literal(offer.perTrade() + " × ").append(offer.goods().getHoverName());
            Component price = (offer.price() == 0 ? Component.translatable("tradery.vending.free")
                : currency != null ? currency.money(offer.price()) : Component.literal(String.valueOf(offer.price())))
                .withStyle(ChatFormatting.GOLD);
            MutableComponent line = Component.translatable("tradery.jade.sells", goods, price);
            if (!counter.isStocked(i)) {
                line = line.withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(" · ")
                    .append(Component.translatable("tradery.hint.sold_out")).withStyle(ChatFormatting.RED));
            }
            tooltip.add(line);
        }
        tooltip.add(CounterHint.keys(counter.isOwnedBy(accessor.getPlayer().getUUID())).copy().withStyle(ChatFormatting.GRAY));
    }
}
