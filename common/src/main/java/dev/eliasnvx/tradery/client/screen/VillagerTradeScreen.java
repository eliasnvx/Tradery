package dev.eliasnvx.tradery.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.menu.VillagerTradeMenu;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.village.TradeTerms;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A villager's trades in money (SPEC "Торговля с жителями за валюту"): the offers on the left with coin prices in
 * place of emeralds, the chosen one on the right with what the player has, the balance and the villager's wallet. A
 * caravan's trader (SPEC "Торговля между деревнями") shows its own prices and its purse.
 * The server does the trade; this only shows what it sent and asks.
 */
public class VillagerTradeScreen extends AbstractContainerScreen<VillagerTradeMenu> implements VendingResultView {
    private static final ResourceLocation OUT_OF_STOCK_ARROW = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
    private static final ResourceLocation XP_BACKGROUND = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_background");
    private static final ResourceLocation XP_CURRENT = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_current");
    private static final ResourceLocation XP_RESULT = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_result");

    private static final int LIST_X = 6;
    private static final int LIST_Y = 18;
    private static final int ROW_W = 150;
    private static final int ROW_H = 20;
    private static final int ROWS = 7;
    private static final long DOUBLE_CLICK_MS = 250L;
    private static final int ARROW_X = 92;
    private static final int RESULT_X = 108;
    private static final int SCROLL_X = LIST_X + ROW_W + 2;
    private static final int SCROLLER_H = 27;
    private static final int PANEL_X = 170;
    private static final int PANEL_W = 128;
    private static final int BAR_X = 196;
    private static final int BAR_Y = 7;
    private static final int BUTTON_Y = 128;

    private static final int GOLD = 0xFFE0A800;
    private static final int SHORT = 0xFFE04040;
    private static final int OK = 0xFF207020;
    private static final int ERROR = 0xFFA02020;
    private static final int MUTED = 0xFF707070;
    private static final int ROW = 0xFF9A9A9A;
    private static final int ROW_HOVER = 0xFFAAAAAA;
    private static final int ROW_SELECTED = 0xFFC2C2C2;
    private static final int ROW_DIM = 0x99C6C6C6;
    private static final int ARROW_TINT = 0xFF606060;

    private int selected;
    private int scrollOff;
    private boolean dragging;
    private int lastClickRow = -1;
    private long lastClickTime;
    private VillagerTradeMenu.Data shownData;
    /** Items of the chosen offer's cost in the inventory, counted on the tick ({@link #containerTick}). */
    private long ownedOfSelected;
    private List<TradeTerms> terms = List.of();
    private Button tradeButton;
    private Button tradeAllButton;

    private Component resultMessage = Component.empty();
    private boolean resultSuccess;
    private long resultAt;

    public VillagerTradeScreen(VillagerTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 306;
        imageHeight = 176;
    }

    @Override
    protected void init() {
        super.init();
        tradeButton = addRenderableWidget(Button.builder(Component.translatable("tradery.villager_trade.trade"), b -> trade(false))
            .bounds(leftPos + PANEL_X, topPos + BUTTON_Y, 62, 20).build());
        tradeAllButton = addRenderableWidget(Button.builder(Component.translatable("tradery.villager_trade.trade_all"), b -> trade(true))
            .bounds(leftPos + PANEL_X + 66, topPos + BUTTON_Y, 62, 20)
            .tooltip(Tooltip.create(Component.translatable("tradery.villager_trade.trade_all_tip"))).build());
    }

    private void trade(boolean all) {
        if (selected < offers().size()) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, VillagerTradeMenu.buttonId(selected, all));
        }
    }

    @Override
    public void showResult(boolean success, Component message) {
        resultSuccess = success;
        resultMessage = message;
        resultAt = System.currentTimeMillis();
    }

    private MerchantOffers offers() {
        return menu.data().offers();
    }

    /** Offers in money terms, worked out again only when the server sent new data. */
    private List<TradeTerms> terms() {
        VillagerTradeMenu.Data data = menu.data();
        if (data != shownData) {
            List<TradeTerms> list = new ArrayList<>(data.offers().size());
            for (int i = 0; i < data.offers().size(); i++) {
                list.add(TradeTerms.of(data.offers().get(i), data.emeraldValue(i)));
            }
            terms = list;
            shownData = data;
            selected = Math.min(selected, Math.max(0, list.size() - 1));
            scrollOff = Math.min(scrollOff, maxScroll());
        }
        return terms;
    }

    private int maxScroll() {
        return Math.max(0, offers().size() - ROWS);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        Panels.inset(graphics, leftPos + LIST_X - 1, topPos + LIST_Y - 1, ROW_W + 2, ROWS * ROW_H + 1);
        Panels.inset(graphics, leftPos + SCROLL_X - 1, topPos + LIST_Y - 1, 8, ROWS * ROW_H + 1);
        Panels.inset(graphics, leftPos + PANEL_X, topPos + LIST_Y - 1, PANEL_W, 62);
        List<TradeTerms> all = terms();
        boolean usable = selected < all.size() && !offers().get(selected).isOutOfStock();
        tradeButton.active = usable;
        tradeAllButton.active = usable;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerTradeMenu.Data data = menu.data();
        Component heading = data.showProgress() && data.level() >= 1 && data.level() <= 5
            ? Component.translatable("merchant.title", title, Component.translatable("merchant.level." + data.level()))
            : title;
        graphics.drawString(font, Panels.fit(font, heading, BAR_X - 14), 8, 6, Panels.LABEL, false);
        List<TradeTerms> all = terms();
        MerchantOffers offers = data.offers();
        experienceBar(graphics, data, selected < offers.size() ? offers.get(selected).getXp() : 0);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scrollOff + i < all.size(); i++) {
            int index = scrollOff + i;
            int y = LIST_Y + i * ROW_H;
            graphics.fill(LIST_X, y, LIST_X + ROW_W, y + ROW_H - 1, index == selected ? ROW_SELECTED : index == hovered ? ROW_HOVER : ROW);
            row(graphics, offers.get(index), all.get(index), data, LIST_X, y);
        }
        scroller(graphics, offers.size());
        if (selected < all.size()) {
            details(graphics, offers.get(selected), all.get(selected), data);
        }

        if (!resultMessage.getString().isEmpty() && System.currentTimeMillis() - resultAt < 6000) {
            List<FormattedCharSequence> lines = font.split(resultMessage, PANEL_W);
            int y = BUTTON_Y + 24;
            for (FormattedCharSequence line : lines.subList(0, Math.min(2, lines.size()))) {
                graphics.drawString(font, line, PANEL_X, y, resultSuccess ? OK : ERROR, false);
                y += 10;
            }
        }
    }

    /** One offer in the list: what the player pays, the arrow, what they get. */
    private void row(GuiGraphics graphics, MerchantOffer offer, TradeTerms terms, VillagerTradeMenu.Data data, int x, int y) {
        int px = x + 4;
        if (terms.price() > 0) {
            Component price = money(terms.price());
            graphics.drawString(font, price, px, y + 6, affordable(terms) ? GOLD : SHORT, true);
            px += font.width(price) + 3;
        }
        for (ItemStack cost : new ItemStack[] {terms.costA(), terms.costB()}) {
            if (!cost.isEmpty()) {
                graphics.renderFakeItem(cost, px, y + 2);
                graphics.renderItemDecorations(font, cost, px, y + 2);
                px += 18;
            }
        }
        // Vanilla's arrow is drawn for its dark buttons; tinted, it reads on the light rows too
        if (!offer.isOutOfStock()) {
            graphics.setColor((ARROW_TINT >> 16 & 0xFF) / 255f, (ARROW_TINT >> 8 & 0xFF) / 255f, (ARROW_TINT & 0xFF) / 255f, 1f);
        }
        graphics.blitSprite(offer.isOutOfStock() ? OUT_OF_STOCK_ARROW : ARROW, x + ARROW_X, y + 5, 10, 9);
        graphics.setColor(1f, 1f, 1f, 1f);
        if (!terms.result().isEmpty()) {
            graphics.renderFakeItem(terms.result(), x + RESULT_X, y + 2);
            graphics.renderItemDecorations(font, terms.result(), x + RESULT_X, y + 2);
        } else {
            graphics.drawString(font, money(terms.payout()), x + RESULT_X, y + 6, walletCovers(terms, data) ? GOLD : SHORT, true);
        }
        if (offer.isOutOfStock()) {
            graphics.fill(x, y, x + ROW_W, y + ROW_H - 1, ROW_DIM);
        }
    }

    /** The chosen offer, spelled out: price (with the usual one struck out), result, what the player has. */
    private void details(GuiGraphics graphics, MerchantOffer offer, TradeTerms terms, VillagerTradeMenu.Data data) {
        int x = PANEL_X + 4;
        graphics.drawString(font, Component.translatable("tradery.villager_trade.you_pay"), x, LIST_Y + 2, Panels.LABEL, false);
        int px = x;
        int rowY = LIST_Y + 12;
        if (terms.price() > 0) {
            if (terms.basePrice() != terms.price()) {
                Component usual = money(terms.basePrice()).withStyle(ChatFormatting.STRIKETHROUGH);
                graphics.drawString(font, usual, px, rowY + 4, MUTED, false);
                px += font.width(usual) + 3;
            }
            Component price = money(terms.price());
            graphics.drawString(font, price, px, rowY + 4, affordable(terms) ? GOLD : SHORT, true);
            px += font.width(price) + 3;
        }
        for (ItemStack cost : new ItemStack[] {terms.costA(), terms.costB()}) {
            if (!cost.isEmpty()) {
                graphics.renderItem(cost, px, rowY);
                graphics.renderItemDecorations(font, cost, px, rowY);
                px += 18;
            }
        }
        graphics.drawString(font, Component.translatable("tradery.villager_trade.you_get"), x, LIST_Y + 31, Panels.LABEL, false);
        int getY = LIST_Y + 41;
        if (!terms.result().isEmpty()) {
            graphics.renderItem(terms.result(), x, getY);
            graphics.renderItemDecorations(font, terms.result(), x, getY);
            graphics.drawString(font, Panels.fit(font, terms.result().getHoverName(), PANEL_W - 26), x + 20, getY + 4, Panels.LABEL, false);
        } else {
            graphics.drawString(font, money(terms.payout()), x, getY + 4, walletCovers(terms, data) ? GOLD : SHORT, true);
        }

        int y = LIST_Y + 66;
        boolean firstCost = !terms.costA().isEmpty();
        ItemStack itemCost = firstCost ? terms.costA() : terms.costB();
        if (!itemCost.isEmpty()) {
            ItemCost cost = firstCost ? offer.getItemCostA() : offer.getItemCostB().orElseThrow();
            Component have = Component.translatable("tradery.villager_trade.you_have", ownedOfSelected, itemCost.getHoverName());
            graphics.drawString(font, Panels.fit(font, have, PANEL_W), PANEL_X, y, Panels.LABEL, false);
            y += 10;
        }
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        if (currency != null) {
            graphics.drawString(font, Panels.fit(font, Component.translatable("tradery.screen.balance", currency.money(ClientEconomy.balance())), PANEL_W),
                PANEL_X, y, Panels.LABEL, false);
            y += 10;
            Component wallet = data.caravan()
                ? Component.translatable("tradery.caravan.purse", currency.money(data.wallet()))
                : Component.translatable("tradery.villager_trade.wallet", currency.money(data.wallet()));
            graphics.drawString(font, Panels.fit(font, wallet, PANEL_W),
                PANEL_X, y, walletCovers(terms, data) ? Panels.LABEL : ERROR, false);
            y += 10;
        }
        Component uses = offer.isOutOfStock()
            ? Component.translatable(data.caravan() ? "tradery.caravan.sold_out" : "tradery.villager_trade.used_up")
            : Component.translatable("tradery.villager_trade.uses_left", offer.getMaxUses() - offer.getUses());
        graphics.drawString(font, Panels.fit(font, uses, PANEL_W), PANEL_X, y, offer.isOutOfStock() ? ERROR : MUTED, false);
    }

    /** Vanilla's experience bar: progress to the next level, plus what the chosen trade adds. */
    private void experienceBar(GuiGraphics graphics, VillagerTradeMenu.Data data, int tradeXp) {
        int level = data.level();
        if (!data.showProgress() || level < 1 || level >= 5) {
            return;
        }
        graphics.blitSprite(XP_BACKGROUND, BAR_X, BAR_Y, 102, 5);
        int min = VillagerData.getMinXpPerLevel(level);
        if (data.xp() >= min && VillagerData.canLevelUp(level)) {
            float scale = 102.0f / (VillagerData.getMaxXpPerLevel(level) - min);
            int width = Math.min(Mth.floor(scale * (data.xp() - min)), 102);
            graphics.blitSprite(XP_CURRENT, 102, 5, 0, 0, BAR_X, BAR_Y, width, 5);
            if (tradeXp > 0) {
                int future = Math.min(Mth.floor(tradeXp * scale), 102 - width);
                graphics.blitSprite(XP_RESULT, 102, 5, width, 0, BAR_X + width, BAR_Y, future, 5);
            }
        }
    }

    private void scroller(GuiGraphics graphics, int count) {
        int max = Math.max(0, count - ROWS);
        if (max == 0) {
            graphics.blitSprite(SCROLLER_DISABLED, SCROLL_X, LIST_Y, 6, SCROLLER_H);
            return;
        }
        int travel = ROWS * ROW_H - 1 - SCROLLER_H;
        graphics.blitSprite(SCROLLER, SCROLL_X, LIST_Y + travel * scrollOff / max, 6, SCROLLER_H);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        List<TradeTerms> all = terms();
        MerchantOffers offers = offers();
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            int y = LIST_Y + (row - scrollOff) * ROW_H;
            TradeTerms rowTerms = all.get(row);
            ItemStack under = itemUnder(rowTerms, LIST_X, y, true, mouseX, mouseY);
            if (!under.isEmpty()) {
                graphics.renderTooltip(font, under, mouseX, mouseY);
            } else if (offers.get(row).isOutOfStock()) {
                graphics.renderTooltip(font, Component.translatable("merchant.deprecated"), mouseX, mouseY);
            } else if (!menu.data().caravan() && (rowTerms.price() > 0 || rowTerms.payout() > 0)) {
                // A caravan's prices are its own, not emeralds
                graphics.renderTooltip(font, insteadOf(offers.get(row)), mouseX, mouseY);
            }
            return;
        }
        if (selected < all.size()) {
            TradeTerms chosen = all.get(selected);
            ItemStack under = itemUnder(chosen, PANEL_X + 4, LIST_Y + 12, false, mouseX, mouseY);
            if (under.isEmpty() && !chosen.result().isEmpty() && isHovering(PANEL_X + 4, LIST_Y + 41, 16, 16, mouseX, mouseY)) {
                under = chosen.result();
            }
            if (!under.isEmpty()) {
                graphics.renderTooltip(font, under, mouseX, mouseY);
            }
        }
    }

    /** The cost or result item drawn under the mouse in a list row or the chosen offer's "you pay" line. */
    private ItemStack itemUnder(TradeTerms terms, int x, int y, boolean listRow, int mouseX, int mouseY) {
        int px = x + (listRow ? 4 : 0);
        int itemY = listRow ? y + 2 : y;
        if (terms.price() > 0) {
            if (!listRow && terms.basePrice() != terms.price()) {
                px += font.width(money(terms.basePrice())) + 3;
            }
            px += font.width(money(terms.price())) + 3;
        }
        for (ItemStack cost : new ItemStack[] {terms.costA(), terms.costB()}) {
            if (!cost.isEmpty()) {
                if (isHovering(px, itemY, 16, 16, mouseX, mouseY)) {
                    return cost;
                }
                px += 18;
            }
        }
        if (listRow && !terms.result().isEmpty() && isHovering(x + RESULT_X, itemY, 16, 16, mouseX, mouseY)) {
            return terms.result();
        }
        return ItemStack.EMPTY;
    }

    /** "Instead of 5 × Emerald": what the money stands for. */
    private static Component insteadOf(MerchantOffer offer) {
        int emeralds = 0;
        for (ItemStack stack : new ItemStack[] {offer.getCostA(), offer.getCostB(), offer.getResult()}) {
            if (TradeTerms.isEmerald(stack)) {
                emeralds += stack.getCount();
            }
        }
        return Component.translatable("tradery.villager_trade.instead_of", emeralds, Items.EMERALD.getName(new ItemStack(Items.EMERALD)));
    }

    // ------------------------------------------------------------------ state for display

    private MutableComponent money(long amount) {
        ClientEconomy.CurrencyView currency = ClientEconomy.currency();
        return currency != null ? currency.money(amount) : Component.literal(String.valueOf(amount));
    }

    private static boolean affordable(TradeTerms terms) {
        return ClientEconomy.balance() >= terms.price() - terms.payout();
    }

    private static boolean walletCovers(TradeTerms terms, VillagerTradeMenu.Data data) {
        return terms.payout() - terms.price() <= data.wallet();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshOwned();
    }

    private void refreshOwned() {
        List<TradeTerms> all = terms();
        if (selected >= all.size()) {
            ownedOfSelected = 0;
            return;
        }
        TradeTerms chosen = all.get(selected);
        MerchantOffer offer = offers().get(selected);
        boolean firstCost = !chosen.costA().isEmpty();
        ItemStack itemCost = firstCost ? chosen.costA() : chosen.costB();
        ownedOfSelected = itemCost.isEmpty() ? 0 : owned(firstCost ? offer.getItemCostA() : offer.getItemCostB().orElseThrow());
    }

    /** Items in the player's inventory that pay for {@code cost} (as the server counts them). */
    private long owned(ItemCost cost) {
        Predicate<ItemStack> test = cost::test;
        return StackMath.countMatching(minecraft.player.getInventory().items, test);
    }

    // ------------------------------------------------------------------ input

    /** Offer index of the list row under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x < 0 || x >= ROW_W || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = scrollOff + (int) (y / ROW_H);
        return index < offers().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && button == InputConstants.MOUSE_BUTTON_LEFT) {
            long now = Util.getMillis();
            boolean doubleClick = row == lastClickRow && now - lastClickTime < DOUBLE_CLICK_MS;
            lastClickRow = row;
            lastClickTime = now;
            selected = row;
            refreshOwned();
            if (doubleClick) {
                trade(false);
            }
            return true;
        }
        if (maxScroll() > 0 && mouseX >= leftPos + SCROLL_X && mouseX < leftPos + SCROLL_X + 6
            && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + ROWS * ROW_H) {
            dragging = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollTo(double mouseY) {
        float position = (float) (mouseY - topPos - LIST_Y - SCROLLER_H / 2.0) / (ROWS * ROW_H - SCROLLER_H);
        scrollOff = Mth.clamp(Math.round(position * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        scrollOff = Mth.clamp((int) (scrollOff - scrollY), 0, maxScroll());
        return true;
    }
}
