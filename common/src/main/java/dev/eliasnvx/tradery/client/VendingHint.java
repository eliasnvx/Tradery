package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.vending.AdminFlags;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a vending block offers, shown at the top of the screen while the crosshair is on it. A small tooltip card,
 * everything centered: the owner, "Sells [goods] for [price]" with item icons and counts (no names, so it stays
 * narrow), and the quick-trade keys or why no trade is possible. Holding sneak (the quick-trade modifier) adds the
 * item names and their full tooltips. With Jade installed it's off by default: Jade's Tradery plugin shows the same.
 * The rows are rebuilt only when the target or what it shows changes.
 */
public final class VendingHint {
    private static final int TITLE = 0xFFFFD75E;
    private static final int LABEL = 0xFFA8A8A8;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFC23A;
    private static final int KEYS = 0xFFB8B8B8;
    private static final int WARN = 0xFFFF6060;
    private static final int ICON = 16;
    private static final int GAP = 4;
    private static final int INDENT = 6;
    private static final int OFFER_ROW = 19;
    private static final int TEXT_ROW = 11;
    private static final int DETAIL_LINES = 6;

    private static final int CENTERED = 0;
    private static final int DETAIL = 1;

    /** A text row: centered, or left-aligned with an indent (item details). */
    private record Row(int kind, Component text, int color, int indent) {
    }

    private static final List<Row> top = new ArrayList<>();
    private static final List<Row> bottom = new ArrayList<>();
    private static boolean hasOffer;
    private static Component offerLabel = Component.empty();
    private static Component offerFor = Component.empty();
    private static ItemStack offerGoods = ItemStack.EMPTY;
    private static ItemStack offerPriceItem = ItemStack.EMPTY;
    private static @Nullable Component offerMoney;
    private static int offerWidth;

    private static @Nullable Boolean jadeLoaded;
    private static @Nullable BlockPos shownPos;
    private static @Nullable VendingSettings shownSettings;
    private static @Nullable AdminFlags shownAdmin;
    private static ClientEconomy.@Nullable CurrencyView shownCurrency;
    private static String shownOwner = "";
    private static boolean shownStocked;
    private static boolean shownIsOwner;
    private static boolean shownDetailed;
    private static int width;
    private static int height;

    private VendingHint() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled() || minecraft.player == null || minecraft.level == null || minecraft.screen != null
            || minecraft.options.hideGui
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
            || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof VendingBlockEntity vendor)) {
            return;
        }
        refresh(minecraft, vendor);

        Font font = minecraft.font;
        int x = (graphics.guiWidth() - width) / 2;
        int y = TraderyConfig.client().vending().hintY() + TooltipRenderUtil.PADDING_TOP;
        TooltipRenderUtil.renderTooltipBackground(graphics, x, y, width, height, 0);
        int rowY = drawRows(graphics, font, top, x, y);
        if (hasOffer) {
            drawOffer(graphics, font, x + (width - offerWidth) / 2, rowY);
            rowY += OFFER_ROW;
        }
        drawRows(graphics, font, bottom, x, rowY);
    }

    /** The quick-trade keys for this block and viewer: "Shift + RMB: buy". Also used by the Jade plugin. */
    public static Component keys(VendingSettings settings, boolean owner) {
        var options = Minecraft.getInstance().options;
        if (owner) {
            return Component.translatable("tradery.hint.owner_keys", keyName(options.keyUse));
        }
        return settings.isBuyback()
            ? Component.translatable("tradery.hint.sell_keys", keyName(options.keyShift), keyName(options.keyAttack))
            : Component.translatable("tradery.hint.buy_keys", keyName(options.keyShift), keyName(options.keyUse));
    }

    /** Short key names for the usual bindings ("Shift", "RMB"); the game's own name for anything else. */
    private static Component keyName(KeyMapping key) {
        return switch (key.saveString()) {
            case "key.keyboard.left.shift", "key.keyboard.right.shift" -> Component.literal("Shift");
            case "key.mouse.left" -> Component.translatable("tradery.key.lmb");
            case "key.mouse.right" -> Component.translatable("tradery.key.rmb");
            default -> key.getTranslatedKeyMessage();
        };
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

    private static int drawRows(GuiGraphics graphics, Font font, List<Row> rows, int x, int y) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowX = row.kind() == CENTERED ? x + (width - font.width(row.text())) / 2 : x + row.indent();
            graphics.drawString(font, row.text(), rowX, y, row.color(), true);
            y += TEXT_ROW;
        }
        return y;
    }

    /** "Sells [icon×4] for (coin)2.50", centered; the counts are drawn on the icons as in an inventory. */
    private static void drawOffer(GuiGraphics graphics, Font font, int x, int y) {
        int textY = y + 5;
        graphics.drawString(font, offerLabel, x, textY, LABEL, true);
        x += font.width(offerLabel) + GAP;
        graphics.renderItem(offerGoods, x, y + 1);
        graphics.renderItemDecorations(font, offerGoods, x, y + 1);
        x += ICON + GAP;
        graphics.drawString(font, offerFor, x, textY, LABEL, true);
        x += font.width(offerFor) + GAP;
        if (offerMoney != null) {
            graphics.drawString(font, offerMoney, x, textY, GOLD, true);
        } else {
            graphics.renderItem(offerPriceItem, x, y + 1);
            graphics.renderItemDecorations(font, offerPriceItem, x, y + 1);
        }
    }

    private static void refresh(Minecraft minecraft, VendingBlockEntity vendor) {
        BlockState state = vendor.getBlockState();
        boolean stocked = state.hasProperty(VendingBlock.STOCKED) && state.getValue(VendingBlock.STOCKED);
        boolean isOwner = vendor.isOwnedBy(minecraft.player.getUUID());
        boolean detailed = minecraft.player.isShiftKeyDown();
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (vendor.getBlockPos().equals(shownPos) && vendor.settings() == shownSettings && vendor.admin() == shownAdmin
            && vendor.ownerName().equals(shownOwner) && stocked == shownStocked && isOwner == shownIsOwner
            && detailed == shownDetailed && currency == shownCurrency) {
            return;
        }
        shownPos = vendor.getBlockPos().immutable();
        shownSettings = vendor.settings();
        shownAdmin = vendor.admin();
        shownOwner = vendor.ownerName();
        shownStocked = stocked;
        shownIsOwner = isOwner;
        shownDetailed = detailed;
        shownCurrency = currency;
        build(minecraft, vendor, currency);
        layout(minecraft.font);
    }

    private static void build(Minecraft minecraft, VendingBlockEntity vendor, ClientEconomy.@Nullable CurrencyView currency) {
        top.clear();
        bottom.clear();
        VendingSettings settings = vendor.settings();
        top.add(centered(Component.literal(vendor.ownerName().isEmpty() ? "?" : vendor.ownerName()), TITLE));
        hasOffer = settings.isConfigured();
        if (!hasOffer) {
            bottom.add(centered(Component.translatable("tradery.hint.not_set_up"), WARN));
            if (shownIsOwner) {
                bottom.add(centered(keys(settings, true), KEYS));
            }
            return;
        }

        boolean money = settings.isBuyback() || settings.priceMode() == PriceMode.CURRENCY;
        offerLabel = Component.translatable(settings.isBuyback() ? "tradery.hint.buys" : "tradery.hint.sells");
        offerFor = Component.translatable("tradery.hint.for");
        offerGoods = settings.goods();
        offerPriceItem = money ? ItemStack.EMPTY : settings.priceItem();
        offerMoney = !money ? null
            : settings.price() == 0 ? Component.translatable("tradery.vending.free")
            : currency != null ? currency.money(settings.price()) : Component.literal(String.valueOf(settings.price()));

        if (shownDetailed) {
            details(minecraft, settings.goods(), settings.perTrade());
            if (!money) {
                details(minecraft, settings.priceItem(), settings.pricePerTrade());
            }
        }
        if (!shownIsOwner && !settings.isBuyback() && !shownStocked && !vendor.admin().infiniteStock()) {
            bottom.add(centered(Component.translatable("tradery.hint.sold_out"), WARN));
        } else {
            bottom.add(centered(keys(settings, shownIsOwner), KEYS));
        }
    }

    private static Row centered(Component text, int color) {
        return new Row(CENTERED, text, color, 0);
    }

    /** Sneaking: the item's name and count, then its own tooltip lines, as in an inventory. */
    private static void details(Minecraft minecraft, ItemStack stack, int count) {
        bottom.add(new Row(DETAIL, Component.empty().append(stack.getHoverName())
            .append(Component.literal(" ×" + count).withStyle(ChatFormatting.GRAY)), TEXT, 0));
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
        int added = 0;
        for (int i = 1; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getString().isBlank()) {
                continue;
            }
            if (added == DETAIL_LINES) {
                bottom.add(new Row(DETAIL, Component.literal("…"), LABEL, INDENT));
                return;
            }
            bottom.add(new Row(DETAIL, line, LABEL, INDENT));
            added++;
        }
    }

    private static void layout(Font font) {
        width = 0;
        height = -2;
        for (List<Row> rows : List.of(top, bottom)) {
            for (Row row : rows) {
                width = Math.max(width, row.indent() + font.width(row.text()));
                height += TEXT_ROW;
            }
        }
        if (hasOffer) {
            offerWidth = font.width(offerLabel) + GAP + ICON + GAP + font.width(offerFor) + GAP
                + (offerMoney != null ? font.width(offerMoney) : ICON);
            width = Math.max(width, offerWidth);
            height += OFFER_ROW;
        }
    }
}
