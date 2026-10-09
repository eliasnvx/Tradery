package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.client.screen.VendingResultView;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client entry points, called by the loaders on the client thread. */
public final class TraderyClient {
    private TraderyClient() {
    }

    public static void init() {
        TraderyConfig.loadClient();
    }

    /** Every clientbound payload lands here, on the client thread. */
    public static void handle(CustomPacketPayload payload) {
        switch (payload) {
            case TraderyPayloads.CurrencyInfoPayload p -> ClientEconomy.onCurrency(p);
            case TraderyPayloads.BalanceSyncPayload p -> ClientEconomy.onSync(p);
            case TraderyPayloads.BalanceDeltaPayload p -> ClientEconomy.onDelta(p);
            case TraderyPayloads.NotificationPayload p -> showNotification(p);
            case TraderyPayloads.HudTogglePayload ignored -> toggleHud();
            case TraderyPayloads.VendingResultPayload p -> showVendingResult(p);
            default -> {
            }
        }
    }

    public static void onTick(Minecraft minecraft) {
        while (TraderyKeyMappings.TOGGLE_HUD.consumeClick()) {
            toggleHud();
        }
    }

    public static void onDisconnect() {
        ClientEconomy.reset();
    }

    public static void renderHud(GuiGraphics graphics) {
        VendingHint.render(graphics);
        BalanceHud.render(graphics);
    }

    static void toggleHud() {
        ClientConfig config = TraderyConfig.client();
        boolean enabled = !config.hud().enabled();
        TraderyConfig.saveClient(config.withHud(config.hud().withEnabled(enabled)));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(enabled
                ? Component.translatableWithFallback("tradery.hud.shown", "Balance shown")
                : Component.translatableWithFallback("tradery.hud.hidden", "Balance hidden"), true);
        }
    }

    private static void showVendingResult(TraderyPayloads.VendingResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
            && screen.getMenu().containerId == payload.containerId()
            && screen instanceof VendingResultView view) {
            view.showResult(payload.success(), payload.message());
        }
    }

    private static void showNotification(TraderyPayloads.NotificationPayload payload) {
        ClientConfig.Notifications config = TraderyConfig.client().notifications();
        boolean show = switch (payload.kind()) {
            case INFO -> true;
            case SALE -> config.sale();
            case EMPTY -> config.empty();
            case NO_SPACE -> config.noSpace();
            case OFFLINE_SUMMARY -> config.offlineSummary();
        };
        var player = Minecraft.getInstance().player;
        if (!show || player == null) {
            return;
        }
        Object[] args = payload.args().stream().map(arg -> arg.startsWith(TraderyPayloads.TRANSLATABLE_PREFIX)
            ? Component.translatable(arg.substring(TraderyPayloads.TRANSLATABLE_PREFIX.length())).withStyle(ChatFormatting.GOLD)
            : Component.literal(arg).withStyle(ChatFormatting.GOLD)).toArray();
        player.sendSystemMessage(Component.translatable(payload.key(), args).withStyle(ChatFormatting.GRAY));
    }
}
