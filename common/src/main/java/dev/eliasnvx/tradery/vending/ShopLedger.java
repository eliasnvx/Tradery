package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.menu.ShopLedgerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The shop ledger (SPEC "Удобство для серверов", {@code tradery:shop_ledger} and {@code /tradery shops}): a window
 * listing the player's own vending blocks and market counters from the vending index. Shops in loaded chunks are read
 * live (and their snapshot refreshed); the others show their last snapshot. Alerts first, then by distance. "Show"
 * sends the client a position to draw a particle beam over; nothing ticks on the server. Server thread.
 */
public final class ShopLedger {
    /** At most this many shops are listed (the payload stays bounded). */
    public static final int MAX_ENTRIES = 512;
    /** Rows per page sent to the client. */
    public static final int PAGE_SIZE = 64;

    private ShopLedger() {
    }

    /**
     * One listed shop, server side.
     *
     * @param snapshot   the shop as read now (live) or last known; null when the index has none yet
     * @param distanceSq squared distance to the player, {@link Double#MAX_VALUE} in another dimension
     */
    public record Listing(GlobalPos pos, @Nullable ShopSnapshot snapshot, boolean live, double distanceSq) {
        int group() {
            return snapshot == null ? 2 : snapshot.alerts() != 0 ? 0 : 1;
        }
    }

    /** What an action did; nothing changed when {@code success} is false. */
    public record Outcome(boolean success, Component message) {
        static Outcome fail(Component message) {
            return new Outcome(false, message);
        }
    }

    private static final Comparator<Listing> ORDER = Comparator.comparingInt(Listing::group)
        .thenComparingDouble(Listing::distanceSq)
        .thenComparing(listing -> listing.pos().dimension().identifier().toString())
        .thenComparingInt(listing -> listing.pos().pos().getX())
        .thenComparingInt(listing -> listing.pos().pos().getZ())
        .thenComparingInt(listing -> listing.pos().pos().getY());

    /** Opens the ledger window (the book or the command). */
    public static void open(ServerPlayer player) {
        List<Listing> all = list(player);
        ShopLedgerMenu.Data data = page(all, 0);
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, opener) -> new ShopLedgerMenu(id, inventory, data, all),
                Messages.tr("item.tradery.shop_ledger", "Shop Ledger")),
            ShopLedgerMenu.Data.STREAM_CODEC, data);
    }

    /** The player's own shops, sorted: alerts first, then shops without a snapshot last; by distance within each group. */
    public static List<Listing> list(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        VendorsData index = VendorsData.get(server);
        List<Listing> listings = new ArrayList<>();
        for (VendorsData.Entry entry : index.ownedBy(AccountId.player(player.getUUID()))) {
            GlobalPos pos = entry.pos();
            ShopSnapshot snapshot = entry.snapshot();
            boolean live = false;
            ServerLevel level = server.getLevel(pos.dimension());
            if (level != null && level.isLoaded(pos.pos())) {
                ShopSnapshot now = liveSnapshot(level.getBlockEntity(pos.pos()));
                if (now != null) {
                    snapshot = now;
                    live = true;
                    index.updateSnapshot(level, pos.pos(), now);
                }
            }
            double distanceSq = pos.dimension() == player.level().dimension()
                ? player.distanceToSqr(Vec3.atCenterOf(pos.pos())) : Double.MAX_VALUE;
            listings.add(new Listing(pos, snapshot, live, distanceSq));
        }
        listings.sort(ORDER);
        return listings.size() > MAX_ENTRIES ? List.copyOf(listings.subList(0, MAX_ENTRIES)) : List.copyOf(listings);
    }

    private static @Nullable ShopSnapshot liveSnapshot(@Nullable BlockEntity blockEntity) {
        return switch (blockEntity) {
            case VendingBlockEntity vendor -> ShopSnapshot.of(vendor);
            case MarketCounterBlockEntity counter -> ShopSnapshot.of(counter);
            case null, default -> null;
        };
    }

    public static int pages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Page {@code page} (clamped) of the list, as the client gets it. */
    public static ShopLedgerMenu.Data page(List<Listing> all, int page) {
        int pages = pages(all.size());
        int shown = Math.max(0, Math.min(page, pages - 1));
        int from = shown * PAGE_SIZE;
        List<ShopLedgerMenu.Row> rows = new ArrayList<>();
        for (int i = from; i < Math.min(all.size(), from + PAGE_SIZE); i++) {
            rows.add(row(all.get(i)));
        }
        return new ShopLedgerMenu.Data(shown, pages, all.size(), List.copyOf(rows));
    }

    private static ShopLedgerMenu.Row row(Listing listing) {
        ShopSnapshot snapshot = listing.snapshot();
        if (snapshot == null) {
            return new ShopLedgerMenu.Row(listing.pos(), ShopLedgerMenu.KIND_UNKNOWN, false, false, false, false, List.of(),
                ItemStack.EMPTY, 0, 0);
        }
        List<ShopLedgerMenu.OfferView> offers = new ArrayList<>();
        boolean money = true;
        for (ShopSnapshot.Offer offer : snapshot.offers()) {
            ItemStack priceItem = offer.money() ? ItemStack.EMPTY : new ItemStack(offer.priceItem(), Math.max(1, offer.priceCount()));
            money &= offer.money();
            offers.add(new ShopLedgerMenu.OfferView(new ItemStack(offer.goods(), offer.perTrade()), offer.price(), priceItem, offer.stock()));
        }
        boolean counter = snapshot.kind() == ShopSnapshot.Kind.COUNTER;
        ItemStack revenue = snapshot.revenueItem() == Items.AIR || snapshot.revenue() <= 0 ? ItemStack.EMPTY : new ItemStack(snapshot.revenueItem());
        return new ShopLedgerMenu.Row(listing.pos(), counter ? ShopLedgerMenu.KIND_COUNTER : ShopLedgerMenu.KIND_VENDING, listing.live(),
            snapshot.buyback(), snapshot.villagers(), counter || (money && !snapshot.offers().isEmpty()), List.copyOf(offers), revenue,
            revenue.isEmpty() ? 0 : snapshot.revenue(), snapshot.alerts());
    }

    // ------------------------------------------------------------------ actions

    /** "Show": the client draws a particle beam over the shop for a while. */
    public static void show(ServerPlayer player, GlobalPos pos) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.ShopBeamPayload(pos));
    }

    /**
     * Flips the villagers toggle of the player's own shop at {@code pos}. Only in a loaded chunk: an unloaded block
     * isn't touched (the ledger shows its last snapshot).
     */
    public static Outcome toggleVillagers(ServerPlayer player, GlobalPos pos) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(pos.dimension());
        BlockPos at = pos.pos();
        if (level == null || !level.isLoaded(at)) {
            return Outcome.fail(Messages.tr("tradery.ledger.not_loaded", "That shop isn't loaded: change it at the shop"));
        }
        BlockEntity blockEntity = level.getBlockEntity(at);
        if (blockEntity instanceof VendingBlockEntity vendor && vendor.isOwner(player)) {
            VendingSettings settings = vendor.settings();
            if (settings.priceMode() != PriceMode.CURRENCY) {
                return Outcome.fail(Messages.tr("tradery.ledger.no_villagers", "Villagers only trade for money"));
            }
            VendingSettings changed = settings.withVillagers(!settings.villagers());
            vendor.setSettings(changed);
            String key = changed.isBuyback()
                ? (changed.villagers() ? "tradery.screen.villagers_sell_on" : "tradery.screen.villagers_sell_off")
                : (changed.villagers() ? "tradery.screen.villagers_on" : "tradery.screen.villagers_off");
            return new Outcome(true, Component.translatable(key));
        }
        if (blockEntity instanceof MarketCounterBlockEntity counter && counter.isOwner(player)) {
            CounterSettings changed = counter.settings().withVillagers(!counter.settings().villagers());
            counter.setSettings(changed);
            return new Outcome(true, Component.translatable(changed.villagers() ? "tradery.screen.villagers_on" : "tradery.screen.villagers_off"));
        }
        return Outcome.fail(Messages.tr("tradery.ledger.gone", "That shop isn't yours any more"));
    }
}
