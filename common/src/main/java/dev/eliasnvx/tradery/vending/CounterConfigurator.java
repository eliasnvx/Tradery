package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.event.VendingConfiguredEvent;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.CounterOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Save" in the market counter's owner menu, validated like the vending block's: the goods are the server's own copies
 * of the menu's samples, only the prices come from the client (bounded, clamped to the balance cap). Every configured
 * offer passes {@link VendingConfiguredEvent} (protection mods may refuse or change a price); one refusal saves
 * nothing. Server side.
 */
public final class CounterConfigurator {
    private CounterConfigurator() {
    }

    /** Applies the owner's draft with {@code prices} (one per offer). Replies to the player with the result. */
    public static void save(ServerPlayer player, CounterOwnerMenu menu, List<Long> prices) {
        MarketCounterBlockEntity counter = menu.counter();
        if (counter == null || !menu.stillValid(player) || !(counter.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!VendingTrades.allow(player)) {
            reply(player, menu, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return;
        }
        if (prices.size() != CounterSettings.MAX_OFFERS) {
            reply(player, menu, false, Messages.failure(dev.eliasnvx.tradery.api.FailReason.INVALID_AMOUNT));
            return;
        }
        long maxPrice = Math.max(0, EconomyService.INSTANCE.maxBalance());
        List<Long> clamped = new ArrayList<>(CounterSettings.MAX_OFFERS);
        for (long price : prices) {
            clamped.add(Math.max(0, maxPrice > 0 ? Math.min(price, maxPrice) : price));
        }
        CounterSettings draft = menu.draft(clamped);
        for (CounterOffer offer : draft.offers()) {
            if (offer.isConfigured() && !VendingConfigurator.isTradeable(offer.goods())) {
                reply(player, menu, false, Messages.tr("tradery.vending.blacklisted", "%s can't be traded here", offer.goods().getHoverName()));
                return;
            }
        }

        List<CounterOffer> applied = new ArrayList<>(CounterSettings.MAX_OFFERS);
        for (CounterOffer offer : draft.offers()) {
            if (!offer.isConfigured()) {
                applied.add(CounterOffer.EMPTY);
                continue;
            }
            VendingConfiguredEvent event = VendingConfiguredEvent.EVENT.post(new VendingConfiguredEvent(player, level, counter.getBlockPos(),
                offer.goods(), PriceMode.CURRENCY, ItemStack.EMPTY, offer.price(), false));
            if (event.isCancelled()) {
                reply(player, menu, false, event.cancelMessage() != null ? event.cancelMessage()
                    : Messages.tr("tradery.vending.config_denied", "These settings aren't allowed here"));
                return;
            }
            applied.add(new CounterOffer(offer.goods(), Math.max(0, event.price())));
        }
        CounterSettings settings = new CounterSettings(applied, draft.villagers());
        boolean offersChanged = !settings.sameOffers(counter.settings());
        counter.setSettings(settings);
        if (offersChanged) {
            // Buyers must never pay a price they didn't see: close their screens
            CounterMenus.closeBuyers(level, counter.getBlockPos());
        }
        reply(player, menu, true, Messages.tr("tradery.vending.saved", "Saved"));
    }

    private static void reply(ServerPlayer player, CounterOwnerMenu menu, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(menu.containerId, success, message));
    }
}
