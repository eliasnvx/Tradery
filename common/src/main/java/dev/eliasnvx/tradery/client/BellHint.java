package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.config.ClientConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The village of a bell, shown at the top of the screen while the crosshair is on it (SPEC "Доска деревни"): its
 * name, level and prosperity, and the next market day. The server answers {@link TraderyPayloads.BellInfoRequestPayload}
 * (asked again every two seconds while looking); the rows are built when the answer arrives, so drawing allocates
 * nothing. Shown unless the look-at hints are off ({@code vending.hint: OFF}).
 */
public final class BellHint {
    private static final int TITLE = 0xFFFFD75E;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFC23A;
    private static final int LABEL = 0xFFA8A8A8;
    private static final int ROW = 11;
    /** Ticks between two questions about the same bell. */
    private static final int REFRESH_TICKS = 40;
    private static final int MAX_ROWS = 3;

    private static @Nullable BlockPos askedPos;
    private static long askedAt;
    private static @Nullable BlockPos shownPos;
    private static final Component[] rows = new Component[MAX_ROWS];
    private static final int[] colors = new int[MAX_ROWS];
    private static final int[] widths = new int[MAX_ROWS];
    private static int rowCount;
    private static int width;

    private BellHint() {
    }

    private static boolean enabled() {
        return TraderyConfig.client().vending().hint() != ClientConfig.HintMode.OFF;
    }

    /** The bell under the crosshair, or null. */
    private static @Nullable BlockPos lookedAtBell(Minecraft minecraft) {
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
            || !minecraft.level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
            return null;
        }
        return hit.getBlockPos();
    }

    /** Client tick: asks the server about the bell under the crosshair, again every two seconds. */
    public static void tick(Minecraft minecraft) {
        if (!enabled() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        BlockPos bell = lookedAtBell(minecraft);
        if (bell == null || !Platform.get().canSendToServer(TraderyPayloads.BellInfoRequestPayload.TYPE)) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (!bell.equals(askedPos) || now - askedAt >= REFRESH_TICKS || now < askedAt) {
            askedPos = bell.immutable();
            askedAt = now;
            Platform.get().sendToServer(new TraderyPayloads.BellInfoRequestPayload(askedPos));
        }
    }

    /** The server's answer: builds the rows once. */
    public static void onInfo(TraderyPayloads.BellInfoPayload info) {
        shownPos = info.pos();
        rowCount = 0;
        if (!info.known()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        add(font, info.name(), TITLE);
        Component prosperity = info.nextThreshold() >= 0
            ? Component.translatable("tradery.bell.prosperity_next", info.prosperity(), info.nextThreshold())
            : Component.translatable("tradery.bell.prosperity", info.prosperity());
        add(font, Component.translatable("tradery.bell.level", Villages.levelName(info.level()), prosperity), TEXT);
        if (info.marketDayIn() == 0) {
            add(font, Component.translatable("tradery.board.market_day_today"), GOLD);
        } else if (info.marketDayIn() == 1) {
            add(font, Component.translatable("tradery.board.market_day_tomorrow"), LABEL);
        } else if (info.marketDayIn() > 1) {
            add(font, Component.translatable("tradery.board.market_day_in", info.marketDayIn()), LABEL);
        }
        width = 0;
        for (int i = 0; i < rowCount; i++) {
            width = Math.max(width, widths[i]);
        }
    }

    private static void add(Font font, Component text, int color) {
        rows[rowCount] = text;
        colors[rowCount] = color;
        widths[rowCount] = font.width(text);
        rowCount++;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (rowCount == 0 || shownPos == null || !enabled() || minecraft.player == null || minecraft.gui.screen() != null
            || minecraft.gui.hud.isHidden()) {
            return;
        }
        BlockPos bell = lookedAtBell(minecraft);
        if (bell == null || !bell.equals(shownPos)) {
            return;
        }
        Font font = minecraft.font;
        int height = rowCount * ROW - 2;
        int x = (graphics.guiWidth() - width) / 2;
        int y = TraderyConfig.client().vending().hintY() + TooltipRenderUtil.PADDING_TOP;
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, null);
        for (int i = 0; i < rowCount; i++) {
            graphics.text(font, rows[i], x + (width - widths[i]) / 2, y + i * ROW, colors[i], true);
        }
    }

    public static void reset() {
        askedPos = null;
        shownPos = null;
        rowCount = 0;
    }
}
