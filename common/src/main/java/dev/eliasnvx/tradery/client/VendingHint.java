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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * What a vending block offers, shown at the top of the screen while the crosshair is on it: the owner, the goods and
 * the price with item icons, and the quick-trade keys or why no trade is possible. Holding sneak (the quick-trade
 * modifier) adds the items' full tooltips. With Jade installed it's off by default: Jade's Tradery plugin shows the same.
 * The rows are rebuilt only when the target or what it shows changes.
 */
public final class VendingHint {
    private static final int PANEL = 0x90000000;
    private static final int TITLE = 0xFFFFD75E;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFC23A;
    private static final int KEYS = 0xFFB8B8B8;
    private static final int WARN = 0xFFFF6060;
    private static final int PAD = 4;
    private static final int ICON = 16;
    private static final int GAP = 4;
    private static final int ITEM_ROW = 18;
    private static final int TEXT_ROW = 10;
    private static final int DETAIL_LINES = 6;

    private static final int CENTERED = 0;
    private static final int ITEM = 1;
    private static final int MONEY = 2;
    private static final int DETAIL = 3;

    private record Row(int kind, @Nullable Component label, ItemStack icon, Component text, int color) {
        int height() {
            return kind == ITEM ? ITEM_ROW : TEXT_ROW;
        }
    }

    private static final List<Row> rows = new ArrayList<>();
    private static @Nullable Boolean jadeLoaded;
    private static @Nullable BlockPos shownPos;
    private static @Nullable VendingSettings shownSettings;
    private static @Nullable AdminFlags shownAdmin;
    private static @Nullable ClientEconomy.CurrencyView shownCurrency;
    private static String shownOwner = "";
    private static boolean shownStocked;
    private static boolean shownIsOwner;
    private static boolean shownDetailed;
    private static int width;
    private static int height;
    private static int iconX;
    private static int textX;

    private VendingHint() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled() || minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null
            || minecraft.gui.hud.isHidden()
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
            || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof VendingBlockEntity vendor)) {
            return;
        }
        refresh(minecraft, vendor);

        Font font = minecraft.font;
        int x = (graphics.guiWidth() - width) / 2;
        int y = TraderyConfig.client().vending().hintY();
        graphics.fill(x, y, x + width, y + height, PANEL);
        int rowY = y + PAD;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            switch (row.kind()) {
                case CENTERED -> graphics.text(font, row.text(), x + (width - font.width(row.text())) / 2, rowY + 1, row.color(), true);
                case ITEM -> {
                    label(graphics, font, row, x, rowY + 5);
                    graphics.item(row.icon(), x + iconX, rowY + 1);
                    graphics.text(font, row.text(), x + textX, rowY + 5, row.color(), true);
                }
                case MONEY -> {
                    label(graphics, font, row, x, rowY + 1);
                    graphics.text(font, row.text(), x + iconX, rowY + 1, row.color(), true);
                }
                default -> graphics.text(font, row.text(), x + textX, rowY + 1, row.color(), true);
            }
            rowY += row.height();
        }
    }

    /**
     * The keys line for this block and viewer, e.g. "Left Shift + Right Button: buy · Right Button: window". Also
     * used by the Jade plugin.
     */
    public static Component keys(VendingSettings settings, boolean owner) {
        Component sneak = Component.keybind("key.sneak");
        Component use = Component.keybind("key.use");
        if (owner) {
            return Component.translatable("tradery.hint.owner_keys", use, sneak);
        }
        return settings.isBuyback()
            ? Component.translatable("tradery.hint.sell_keys", sneak, Component.keybind("key.attack"), use)
            : Component.translatable("tradery.hint.buy_keys", sneak, use);
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

    private static void label(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y) {
        if (row.label() != null) {
            graphics.text(font, row.label(), x + PAD, y, LABEL, true);
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
        rows.clear();
        VendingSettings settings = vendor.settings();
        String owner = vendor.ownerName().isEmpty() ? "?" : vendor.ownerName();
        rows.add(new Row(CENTERED, null, ItemStack.EMPTY, Component.literal(owner), TITLE));
        if (!settings.isConfigured()) {
            rows.add(new Row(CENTERED, null, ItemStack.EMPTY, Component.translatable("tradery.hint.not_set_up"), WARN));
            if (shownIsOwner) {
                rows.add(new Row(CENTERED, null, ItemStack.EMPTY, keys(settings, true), KEYS));
            }
            return;
        }
        boolean buyback = settings.isBuyback();
        rows.add(new Row(ITEM, Component.translatable(buyback ? "tradery.hint.buys" : "tradery.hint.sells"), settings.goods(),
            amount(settings.goods(), settings.perTrade()), TEXT));
        if (shownDetailed) {
            details(minecraft, settings.goods());
        }
        Component priceLabel = Component.translatable(buyback ? "tradery.hint.pays" : "tradery.hint.for");
        if (buyback || settings.priceMode() == PriceMode.CURRENCY) {
            Component price = settings.price() == 0 ? Component.translatable("tradery.vending.free")
                : currency != null ? currency.money(settings.price()) : Component.literal(String.valueOf(settings.price()));
            rows.add(new Row(MONEY, priceLabel, ItemStack.EMPTY, price, GOLD));
        } else {
            rows.add(new Row(ITEM, priceLabel, settings.priceItem(), amount(settings.priceItem(), settings.pricePerTrade()), TEXT));
            if (shownDetailed) {
                details(minecraft, settings.priceItem());
            }
        }
        if (!shownIsOwner && !buyback && !shownStocked && !vendor.admin().infiniteStock()) {
            rows.add(new Row(CENTERED, null, ItemStack.EMPTY, Component.translatable("tradery.hint.sold_out"), WARN));
        } else {
            rows.add(new Row(CENTERED, null, ItemStack.EMPTY, keys(settings, shownIsOwner), KEYS));
        }
    }

    private static Component amount(ItemStack stack, int count) {
        return Component.empty().append(stack.getHoverName()).append(Component.literal(" ×" + count).withStyle(ChatFormatting.GRAY));
    }

    /** The item's own tooltip without its name line, as in an inventory. */
    private static void details(Minecraft minecraft, ItemStack stack) {
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
        int added = 0;
        for (int i = 1; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.getString().isBlank()) {
                continue;
            }
            if (added == DETAIL_LINES) {
                rows.add(new Row(DETAIL, null, ItemStack.EMPTY, Component.literal("…"), LABEL));
                return;
            }
            rows.add(new Row(DETAIL, null, ItemStack.EMPTY, line, LABEL));
            added++;
        }
    }

    private static void layout(Font font) {
        int labelWidth = 0;
        for (Row row : rows) {
            if (row.label() != null) {
                labelWidth = Math.max(labelWidth, font.width(row.label()));
            }
        }
        iconX = PAD + (labelWidth > 0 ? labelWidth + GAP : 0);
        textX = iconX + ICON + 3;
        int contentWidth = 0;
        height = PAD * 2 - 1;
        for (Row row : rows) {
            int rowWidth = switch (row.kind()) {
                case CENTERED -> PAD + font.width(row.text()) + PAD;
                case MONEY -> iconX + font.width(row.text()) + PAD;
                default -> textX + font.width(row.text()) + PAD;
            };
            contentWidth = Math.max(contentWidth, rowWidth);
            height += row.height();
        }
        width = contentWidth;
    }
}
