package dev.eliasnvx.tradery.compat;

import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.client.CounterHint;
import dev.eliasnvx.tradery.client.VendingHint;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageSpecialization;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The lines of the look-at tooltips, shared by the Jade and WTHIT plugins so both say the same: a vending block, a
 * market counter, a trade crate, a display block and a bell's village. Client only: they read the block's own client
 * copy and what {@link TooltipData} sent from the server.
 */
public final class TooltipLines {
    private TooltipLines() {
    }

    // ------------------------------------------------------------------ vending block

    /** Client: owner, offer, the stock the server sent (-1: none yet) and the quick-trade keys. */
    public static void vending(VendingBlockEntity vendor, UUID viewer, int stock, Consumer<Component> out) {
        VendingSettings settings = vendor.settings();
        if (!vendor.ownerName().isEmpty()) {
            out.accept(Component.translatable("tradery.jade.owner", vendor.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        if (!settings.isConfigured()) {
            out.accept(Component.translatable("tradery.vending.not_configured").withStyle(ChatFormatting.RED));
            return;
        }
        Component goods = Component.literal(settings.perTrade() + " × ").append(settings.goods().getHoverName());
        Component price;
        if (settings.priceMode() == PriceMode.ITEM) {
            price = Component.literal(settings.pricePerTrade() + " × ").append(settings.priceItem().getHoverName());
        } else {
            price = settings.price() == 0 ? Component.translatable("tradery.vending.free").withStyle(ChatFormatting.GOLD)
                : money(settings.price()).withStyle(ChatFormatting.GOLD);
        }
        out.accept(Component.translatable(settings.isBuyback() ? "tradery.jade.buys" : "tradery.jade.sells", goods, price));
        if (stock >= 0) {
            out.accept(Component.translatable(settings.isBuyback() ? "tradery.screen.room" : "tradery.screen.in_stock",
                stock >= VendingTrades.UNLIMITED ? "∞" : String.valueOf(stock)).withStyle(stock > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        out.accept(VendingHint.keys(settings, vendor.isOwnedBy(viewer)).copy().withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ market counter

    /** Client: owner, one line per offer (red when sold out) and the key to open it. */
    public static void counter(MarketCounterBlockEntity counter, UUID viewer, Consumer<Component> out) {
        if (!counter.ownerName().isEmpty()) {
            out.accept(Component.translatable("tradery.jade.owner", counter.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        CounterSettings settings = counter.settings();
        if (!settings.isConfigured()) {
            out.accept(Component.translatable("tradery.counter.not_configured").withStyle(ChatFormatting.RED));
            return;
        }
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            CounterOffer offer = settings.offer(i);
            if (!offer.isConfigured()) {
                continue;
            }
            Component goods = Component.literal(offer.perTrade() + " × ").append(offer.goods().getHoverName());
            Component price = (offer.price() == 0 ? Component.translatable("tradery.vending.free") : money(offer.price()))
                .withStyle(ChatFormatting.GOLD);
            MutableComponent line = Component.translatable("tradery.jade.sells", goods, price);
            if (!counter.isStocked(i)) {
                line = line.withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(" · ")
                    .append(Component.translatable("tradery.hint.sold_out")).withStyle(ChatFormatting.RED));
            }
            out.accept(line);
        }
        out.accept(CounterHint.keys(counter.isOwnedBy(viewer)).copy().withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ trade crate and display

    /** Client: owner and what the crate wants. */
    public static void crate(TradeCrateBlockEntity crate, Consumer<Component> out) {
        if (!crate.ownerName().isEmpty()) {
            out.accept(Component.translatable("tradery.jade.owner", crate.ownerName()).withStyle(ChatFormatting.GRAY));
        }
        if (crate.wanted().isEmpty()) {
            out.accept(Component.translatable("tradery.jade.crate_no_wants").withStyle(ChatFormatting.RED));
            return;
        }
        for (ItemStack want : crate.wanted()) {
            out.accept(Component.translatable("tradery.ledger.crate_want", want.getHoverName()));
        }
    }

    /** Client: what a display block shows. */
    public static void display(DisplayBlockEntity display, Consumer<Component> out) {
        if (!display.shown().isEmpty()) {
            out.accept(Component.translatable("tradery.jade.shows", display.shown().getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ bell

    /** Client: the bell's lines. */
    public static void bell(TooltipData.Bell info, Consumer<Component> out) {
        if (!info.known()) {
            return;
        }
        out.accept(info.name().copy().withStyle(ChatFormatting.GOLD));
        out.accept(Component.translatable("tradery.jade.village_level", Villages.levelName(info.level()), info.prosperity()));
        VillageSpecialization.byId(info.specialization())
            .ifPresent(specialization -> out.accept(Component.translatable("tradery.bell.specialization", specialization.displayName())));
        if (!info.patron().isEmpty()) {
            out.accept(Component.translatable("tradery.bell.patron", info.patron()).withStyle(ChatFormatting.GRAY));
        }
        out.accept(Component.translatable("tradery.board.treasury", money(info.treasury())).withStyle(ChatFormatting.GRAY));
        if (!info.saving().getString().isEmpty()) {
            out.accept(Component.translatable("tradery.board.saving", info.saving(), money(info.savings()), money(info.savingsNeed()))
                .withStyle(ChatFormatting.GRAY));
        }
    }

    private static MutableComponent money(long amount) {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.money(amount) : Component.literal(String.valueOf(amount));
    }
}
