package dev.eliasnvx.tradery.compat.maps;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.SimpleCurrency;
import dev.eliasnvx.tradery.vending.ShopSnapshot;
import dev.eliasnvx.tradery.vending.VendorsData;
import dev.eliasnvx.tradery.village.VillageCouncil;
import dev.eliasnvx.tradery.village.VillageEconomy;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server web maps show (SPEC "Интеграции 1.2"), as plain data any map can draw: a pin per Tradery village
 * and per player shop (vending block, market counter, trade crate) from the shop index. Texts are the English
 * fallbacks (a dedicated server has no mod translations) and HTML-escaped: village names come from players.
 * Server thread.
 */
public final class MapPins {
    /** A pin: where, an id unique in its layer, a short label and the HTML of its popup. */
    public record Pin(ResourceKey<Level> dimension, BlockPos pos, String id, String label, String html) {
    }

    /** Lines of offers a shop's popup lists at most. */
    static final int MAX_OFFER_LINES = 6;

    private MapPins() {
    }

    /** One pin per village Tradery knows, at its bell. */
    public static List<Pin> villages(MinecraftServer server) {
        List<Pin> pins = new ArrayList<>();
        for (VillageState village : VillageEconomy.get(server).all()) {
            Villages.touch(server, village);
            String name = Villages.displayName(village).getString();
            StringBuilder html = new StringBuilder("<b>").append(escape(name)).append("</b><br>")
                .append(escape(Villages.levelName(Villages.level(village)).getString()))
                .append(" · ").append(escape(Messages.tr("tradery.maps.prosperity", "prosperity %s", (long) Math.floor(village.prosperity())).getString()))
                .append("<br>").append(escape(Messages.tr("tradery.maps.treasury", "Treasury: %s", money(village.treasury())).getString()));
            VillageCouncil.active(village).ifPresent(specialization -> html.append("<br>").append(escape(
                Messages.tr("tradery.bell.specialization", "Specializes in %s", specialization.displayName()).getString())));
            String patron = VillageCouncil.patronName(village);
            if (!patron.isEmpty()) {
                html.append("<br>").append(escape(Messages.tr("tradery.bell.patron", "Patron: %s", patron).getString()));
            }
            BlockPos bell = village.bell();
            pins.add(new Pin(village.dimension(), bell, "village_" + bell.getX() + "_" + bell.getY() + "_" + bell.getZ(), name, popup(html)));
        }
        return pins;
    }

    /** One pin per player shop in the index (server shops, the market stalls, are left out). */
    public static List<Pin> shops(MinecraftServer server) {
        List<Pin> pins = new ArrayList<>();
        for (VendorsData.Entry entry : VendorsData.get(server).all()) {
            if (!(entry.owner() instanceof dev.eliasnvx.tradery.api.AccountId.Player player)) {
                continue;
            }
            String owner = EconomyService.INSTANCE.isReady() ? EconomyService.INSTANCE.account(player.uuid()).name() : "";
            ShopSnapshot snapshot = entry.snapshot();
            String kind = snapshot == null ? "Shop" : switch (snapshot.kind()) {
                case VENDING -> "Vending Block";
                case COUNTER -> "Market Counter";
                case CRATE -> "Trade Crate";
            };
            String label = owner.isEmpty() ? kind : owner + ": " + kind;
            StringBuilder html = new StringBuilder("<b>").append(escape(label)).append("</b>");
            if (snapshot != null) {
                int shown = 0;
                for (ShopSnapshot.Offer offer : snapshot.offers()) {
                    if (shown++ == MAX_OFFER_LINES) {
                        html.append("<br>…");
                        break;
                    }
                    String goods = offer.perTrade() + " × " + new ItemStack(offer.goods()).getHoverName().getString();
                    String line;
                    if (snapshot.kind() == ShopSnapshot.Kind.CRATE) {
                        line = "Wants " + new ItemStack(offer.goods()).getHoverName().getString();
                    } else {
                        String price = offer.money() ? money(offer.price())
                            : offer.priceCount() + " × " + new ItemStack(offer.priceItem()).getHoverName().getString();
                        line = (snapshot.buyback() ? "Buys " : "Sells ") + goods + " for " + price;
                    }
                    html.append("<br>").append(escape(line));
                }
            }
            BlockPos pos = entry.pos().pos();
            pins.add(new Pin(entry.pos().dimension(), pos, "shop_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(), label, popup(html)));
        }
        return pins;
    }

    /** A popup's lines as they are: map popups are narrow and would wrap every word otherwise. */
    static String popup(CharSequence lines) {
        return "<div style=\"white-space:nowrap\">" + lines + "</div>";
    }

    /** {@code text} safe inside HTML. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String money(long amount) {
        if (!EconomyService.INSTANCE.isReady()) {
            return String.valueOf(amount);
        }
        SimpleCurrency currency = EconomyService.INSTANCE.defaultCurrency();
        return currency.formatPlain(amount);
    }

    static boolean villagesOn() {
        return TraderyConfig.server().integrations().maps().villages();
    }

    static boolean shopsOn() {
        return TraderyConfig.server().integrations().maps().shops();
    }
}
