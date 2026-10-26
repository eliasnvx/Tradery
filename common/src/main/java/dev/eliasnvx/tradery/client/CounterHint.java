package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * What a market counter offers, shown at the top of the screen while the crosshair is on it, like {@link VendingHint}:
 * the owner, one line per offer ("[goods×4] for (coin)2.50", or "sold out") and the key to open it. Off by default
 * with Jade (its Tradery plugin shows the same). The lines are rebuilt only when the target or what it shows changes,
 * so drawing allocates nothing.
 */
public final class CounterHint {
    private static final int TITLE = 0xFFFFD75E;
    private static final int LABEL = 0xFFA8A8A8;
    private static final int GOLD = 0xFFFFC23A;
    private static final int KEYS = 0xFFB8B8B8;
    private static final int WARN = 0xFFFF6060;
    private static final int ICON = 16;
    private static final int GAP = 4;
    private static final int OFFER_ROW = 18;
    private static final int TEXT_ROW = 11;

    private static final ItemStack[] goods = new ItemStack[CounterSettings.MAX_OFFERS];
    private static final Component[] prices = new Component[CounterSettings.MAX_OFFERS];
    private static final boolean[] soldOut = new boolean[CounterSettings.MAX_OFFERS];
    private static int offers;
    private static Component title = Component.empty();
    private static Component forText = Component.empty();
    private static Component footer = Component.empty();
    private static boolean footerWarn;

    private static @Nullable Boolean jadeLoaded;
    private static @Nullable BlockPos shownPos;
    private static @Nullable CounterSettings shownSettings;
    private static String shownOwner = "";
    private static int shownMask = -1;
    private static boolean shownIsOwner;
    private static ClientEconomy.@Nullable CurrencyView shownCurrency;
    private static int width;
    private static int height;
    private static int forWidth;
    private static int titleWidth;
    private static int footerWidth;

    private CounterHint() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled() || minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null
            || minecraft.gui.hud.isHidden()
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
            || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof MarketCounterBlockEntity counter)) {
            return;
        }
        refresh(minecraft, counter);

        Font font = minecraft.font;
        int x = (graphics.guiWidth() - width) / 2;
        int y = TraderyConfig.client().vending().hintY() + TooltipRenderUtil.PADDING_TOP;
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, null);
        graphics.text(font, title, x + (width - titleWidth) / 2, y, TITLE, true);
        int rowY = y + TEXT_ROW;
        for (int i = 0; i < offers; i++) {
            ItemStack stack = goods[i];
            graphics.item(stack, x, rowY);
            graphics.itemDecorations(font, stack, x, rowY);
            graphics.text(font, forText, x + ICON + GAP, rowY + 4, LABEL, true);
            graphics.text(font, prices[i], x + ICON + GAP + forWidth + GAP, rowY + 4, soldOut[i] ? WARN : GOLD, true);
            rowY += OFFER_ROW;
        }
        graphics.text(font, footer, x + (width - footerWidth) / 2, rowY, footerWarn ? WARN : KEYS, true);
    }

    private static boolean enabled() {
        ClientConfig.HintMode mode = TraderyConfig.client().vending().hint();
        if (mode != ClientConfig.HintMode.AUTO) {
            return mode == ClientConfig.HintMode.ON;
        }
        if (jadeLoaded == null) {
            jadeLoaded = Platform.get().isModLoaded("jade");
        }
        return !jadeLoaded;
    }

    private static void refresh(Minecraft minecraft, MarketCounterBlockEntity counter) {
        boolean isOwner = counter.isOwnedBy(minecraft.player.getUUID());
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (counter.getBlockPos().equals(shownPos) && counter.settings() == shownSettings && counter.ownerName().equals(shownOwner)
            && counter.stockedMask() == shownMask && isOwner == shownIsOwner && currency == shownCurrency) {
            return;
        }
        shownPos = counter.getBlockPos().immutable();
        shownSettings = counter.settings();
        shownOwner = counter.ownerName();
        shownMask = counter.stockedMask();
        shownIsOwner = isOwner;
        shownCurrency = currency;

        Font font = minecraft.font;
        title = Component.literal(counter.ownerName().isEmpty() ? "?" : counter.ownerName());
        forText = Component.translatable("tradery.hint.for");
        forWidth = font.width(forText);
        titleWidth = font.width(title);
        width = titleWidth;
        offers = 0;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            CounterOffer offer = counter.settings().offer(i);
            if (!offer.isConfigured()) {
                continue;
            }
            goods[offers] = offer.goods();
            soldOut[offers] = !counter.isStocked(i);
            prices[offers] = soldOut[offers] ? Component.translatable("tradery.hint.sold_out")
                : offer.price() == 0 ? Component.translatable("tradery.vending.free")
                : currency != null ? currency.money(offer.price()) : Component.literal(String.valueOf(offer.price()));
            width = Math.max(width, ICON + GAP + forWidth + GAP + font.width(prices[offers]));
            offers++;
        }
        footerWarn = offers == 0;
        footer = offers == 0 ? Component.translatable("tradery.hint.not_set_up") : keys(isOwner);
        footerWidth = font.width(footer);
        width = Math.max(width, footerWidth);
        height = TEXT_ROW + offers * OFFER_ROW + TEXT_ROW - 2;
    }

    /** "RMB: settings" for the owner, "RMB: open the counter" for everyone else. Also used by the Jade plugin. */
    public static Component keys(boolean owner) {
        var options = Minecraft.getInstance().options;
        Component use = "key.mouse.right".equals(options.keyUse.saveString())
            ? Component.translatable("tradery.key.rmb") : options.keyUse.getTranslatedKeyMessage();
        return Component.translatable(owner ? "tradery.hint.owner_keys" : "tradery.hint.counter_keys", use);
    }
}
