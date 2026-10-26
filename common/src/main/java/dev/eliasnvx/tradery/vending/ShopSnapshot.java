package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * What a shop (vending block or market counter) last looked like, kept in the vending index ({@link VendorsData}) so
 * the shop ledger can list shops in unloaded chunks. Small on purpose: items without their components, at most
 * {@link CounterSettings#MAX_OFFERS} offers. Updated when the block's offer or stock changes; compared by value, so an
 * unchanged snapshot doesn't mark the index dirty.
 *
 * @param kind         vending block or market counter
 * @param buyback      a vending block buying from players
 * @param villagers    the owner's villagers toggle
 * @param offers       configured offers only (one for a vending block)
 * @param revenueItem  an item waiting in the revenue slots ({@link Items#AIR} for none)
 * @param revenue      items waiting in the revenue slots, all kinds together
 * @param alerts       bits: {@link #NOT_CONFIGURED}, {@link #EMPTY}, {@link #NO_ROOM}
 */
public record ShopSnapshot(Kind kind, boolean buyback, boolean villagers, List<Offer> offers, Item revenueItem, int revenue, int alerts) {
    /** No offer is set up. */
    public static final int NOT_CONFIGURED = 1;
    /** Nothing to sell: the vending block's stock, or at least one counter offer, is out. */
    public static final int EMPTY = 2;
    /** No room: for item payments (item-priced sale) or for the goods (buyback). */
    public static final int NO_ROOM = 4;

    public enum Kind {
        VENDING, COUNTER
    }

    /**
     * One offer.
     *
     * @param goods      the goods
     * @param perTrade   goods per trade
     * @param price      money per trade (minor units); unused with a price item
     * @param priceItem  the price item, {@link Items#AIR} for money
     * @param priceCount price items per trade
     * @param stock      trades in stock (a sale) or trades of room (a buyback), at most {@link VendingTrades#UNLIMITED}
     */
    public record Offer(Item goods, int perTrade, long price, Item priceItem, int priceCount, int stock) {
        /** An item id that must exist: 1.20.1's {@code byNameCodec} reads an unknown id as air. */
        private static final Codec<Item> ITEM = ResourceLocation.CODEC.comapFlatMap(
            id -> BuiltInRegistries.ITEM.getOptional(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
            BuiltInRegistries.ITEM::getKey);
        static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
            ITEM.fieldOf("goods").forGetter(Offer::goods),
            Codec.intRange(1, 99).optionalFieldOf("per_trade", 1).forGetter(Offer::perTrade),
            Codec.LONG.optionalFieldOf("price", 0L).forGetter(Offer::price),
            ITEM.optionalFieldOf("price_item", Items.AIR).forGetter(Offer::priceItem),
            Codec.intRange(0, 99).optionalFieldOf("price_count", 0).forGetter(Offer::priceCount),
            Codec.intRange(0, VendingTrades.UNLIMITED).optionalFieldOf("stock", 0).forGetter(Offer::stock)
        ).apply(i, Offer::new));

        /** Paid with money (not a price item). */
        public boolean money() {
            return priceItem == Items.AIR;
        }
    }

    public static final Codec<ShopSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
        ConfigCodecs.enumCodec(Kind.class).fieldOf("kind").forGetter(ShopSnapshot::kind),
        Codec.BOOL.optionalFieldOf("buyback", false).forGetter(ShopSnapshot::buyback),
        Codec.BOOL.optionalFieldOf("villagers", true).forGetter(ShopSnapshot::villagers),
        // Strict: an offer naming a removed item fails the whole snapshot, which the index then drops
        ConfigCodecs.strictOptionalField(Offer.CODEC.listOf().flatXmap(ConfigCodecs.<Offer>sizeCheck(0, CounterSettings.MAX_OFFERS),
            DataResult::success), "offers", List.<Offer>of()).forGetter(ShopSnapshot::offers),
        BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("revenue_item", Items.AIR).forGetter(ShopSnapshot::revenueItem),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("revenue", 0).forGetter(ShopSnapshot::revenue),
        Codec.INT.optionalFieldOf("alerts", 0).forGetter(ShopSnapshot::alerts)
    ).apply(i, ShopSnapshot::new));

    public ShopSnapshot {
        offers = List.copyOf(offers.size() > CounterSettings.MAX_OFFERS ? offers.subList(0, CounterSettings.MAX_OFFERS) : offers);
    }

    public boolean has(int alert) {
        return (alerts & alert) != 0;
    }

    /** Configured offers that are out of stock (a sale) or out of room (a buyback). */
    public int emptyOffers() {
        int empty = 0;
        for (Offer offer : offers) {
            if (offer.stock() <= 0) {
                empty++;
            }
        }
        return empty;
    }

    // ------------------------------------------------------------------ taking a snapshot (server side)

    public static ShopSnapshot of(VendingBlockEntity vendor) {
        VendingSettings settings = vendor.settings();
        AdminFlags admin = vendor.admin();
        List<ItemStack> revenueSlots = vendor.revenue().getItems();
        Item revenueItem = Items.AIR;
        long revenue = 0;
        for (ItemStack stack : revenueSlots) {
            if (!stack.isEmpty()) {
                if (revenueItem == Items.AIR) {
                    revenueItem = stack.getItem();
                }
                revenue += stack.getCount();
            }
        }
        if (!settings.isConfigured()) {
            return new ShopSnapshot(Kind.VENDING, settings.isBuyback(), settings.villagers(), List.of(), revenueItem, (int) Math.min(Integer.MAX_VALUE, revenue),
                NOT_CONFIGURED);
        }
        int alerts = 0;
        int stock;
        if (settings.isBuyback()) {
            stock = admin.infiniteStock() ? VendingTrades.UNLIMITED
                : (int) Math.min(VendingTrades.UNLIMITED, StackMath.space(vendor.stock().getItems(), settings.goods()) / settings.perTrade());
            if (stock <= 0) {
                alerts |= NO_ROOM;
            }
        } else {
            stock = vendor.tradesInStock();
            if (stock <= 0) {
                alerts |= EMPTY;
            }
            if (settings.priceMode() == PriceMode.ITEM && !admin.burnPayment()
                && StackMath.space(revenueSlots, settings.priceItem()) < settings.pricePerTrade()) {
                alerts |= NO_ROOM;
            }
        }
        boolean itemPrice = settings.priceMode() == PriceMode.ITEM && !settings.isBuyback();
        Offer offer = new Offer(settings.goods().getItem(), settings.perTrade(), itemPrice ? 0 : settings.price(),
            itemPrice ? settings.priceItem().getItem() : Items.AIR, itemPrice ? settings.pricePerTrade() : 0, Math.max(0, stock));
        return new ShopSnapshot(Kind.VENDING, settings.isBuyback(), settings.villagers(), List.of(offer), revenueItem,
            (int) Math.min(Integer.MAX_VALUE, revenue), alerts);
    }

    public static ShopSnapshot of(MarketCounterBlockEntity counter) {
        CounterSettings settings = counter.settings();
        List<Offer> offers = new ArrayList<>();
        int alerts = 0;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            CounterOffer offer = settings.offer(i);
            if (!offer.isConfigured()) {
                continue;
            }
            int stock = counter.tradesInStock(i);
            if (stock <= 0) {
                alerts |= EMPTY;
            }
            offers.add(new Offer(offer.goods().getItem(), offer.perTrade(), offer.price(), Items.AIR, 0, stock));
        }
        if (offers.isEmpty()) {
            alerts |= NOT_CONFIGURED;
        }
        return new ShopSnapshot(Kind.COUNTER, false, settings.villagers(), offers, Items.AIR, 0, alerts);
    }
}
