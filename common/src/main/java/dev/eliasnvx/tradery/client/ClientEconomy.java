package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.util.MoneyText;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * What the client knows about money: only what the server sent. {@code currency == null} means the server has no
 * Tradery (or hasn't said hello yet), and the HUD stays hidden.
 */
public final class ClientEconomy {
    /** Display settings of the server's currency. */
    public record CurrencyView(String id, String name, String symbol, int decimals, String thousandsSeparator,
                               long copperValue, long silverValue, long goldValue) {
        public String format(long amount) {
            return Money.format(amount, decimals, thousandsSeparator, symbol);
        }

        public String formatShort(long amount) {
            return Money.formatShort(amount, decimals, symbol);
        }

        /** The number alone, for places where the coin icon stands in for the symbol (the HUD). */
        public String formatNumber(long amount) {
            return Money.formatNumber(amount, decimals, thousandsSeparator);
        }

        /** Coin icon + number, for text in screens and tooltips. */
        public MutableComponent money(long amount) {
            return MoneyText.of(formatNumber(amount), symbol);
        }

        /** Short form without the symbol: "1.2K". */
        public String formatShortNumber(long amount) {
            return Money.formatShort(amount, decimals, "");
        }
    }

    private static @Nullable CurrencyView currency;
    private static long balance;
    private static boolean balanceKnown;

    private ClientEconomy() {
    }

    public static @Nullable CurrencyView currency() {
        return currency;
    }

    public static long balance() {
        return balance;
    }

    public static boolean isActive() {
        return currency != null && balanceKnown;
    }

    static void onCurrency(TraderyPayloads.CurrencyInfoPayload payload) {
        currency = new CurrencyView(payload.currencyId(), payload.name(), payload.symbol(), payload.decimals(), payload.thousandsSeparator(),
            payload.copperValue(), payload.silverValue(), payload.goldValue());
        BalanceHud.invalidateText();
    }

    static void onSync(TraderyPayloads.BalanceSyncPayload payload) {
        boolean first = !balanceKnown;
        balance = payload.balance();
        balanceKnown = true;
        BalanceHud.onBalance(balance, first);
    }

    static void onDelta(TraderyPayloads.BalanceDeltaPayload payload) {
        balance = payload.balance();
        balanceKnown = true;
        BalanceHud.onBalance(balance, false);
        BalanceHud.addPopup(payload.delta());
    }

    static void reset() {
        currency = null;
        balance = 0;
        balanceKnown = false;
        BalanceHud.reset();
    }
}
