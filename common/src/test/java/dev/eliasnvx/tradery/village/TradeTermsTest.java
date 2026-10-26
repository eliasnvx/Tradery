package dev.eliasnvx.tradery.village;

import dev.eliasnvx.tradery.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A villager's offer in money: emeralds become amounts, everything else stays an item, nothing overflows silently. */
class TradeTermsTest {
    private static final long EMERALD = 300;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.bootstrap();
    }

    @Test
    void emeraldsOnTheCostSideBecomeAPrice() {
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.BOOKSHELF),
            0, 12, 10, 0.05f);
        TradeTerms terms = TradeTerms.of(offer, EMERALD);
        assertEquals(5 * EMERALD, terms.price());
        assertEquals(5 * EMERALD, terms.basePrice());
        assertTrue(terms.costA().isEmpty(), "the emeralds are money now");
        assertTrue(terms.costB().is(Items.BOOK), "the book stays an item");
        assertEquals(0, terms.payout());
        assertTrue(terms.result().is(Items.BOOKSHELF));
        assertTrue(terms.valid());
    }

    @Test
    void emeraldsOnTheResultSideBecomeAPayout() {
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.PAPER, 24), Optional.empty(), new ItemStack(Items.EMERALD), 0, 16, 2, 0.05f);
        TradeTerms terms = TradeTerms.of(offer, EMERALD);
        assertEquals(0, terms.price());
        assertEquals(24, terms.costA().getCount());
        assertEquals(EMERALD, terms.payout());
        assertTrue(terms.result().isEmpty(), "the emerald is money now");
    }

    @Test
    void discountsShowInThePriceButNotInTheBasePrice() {
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 10), Optional.empty(), new ItemStack(Items.BREAD), 0, 16, 2, 0.05f);
        offer.setSpecialPriceDiff(-3);
        TradeTerms terms = TradeTerms.of(offer, EMERALD);
        assertEquals(7 * EMERALD, terms.price());
        assertEquals(10 * EMERALD, terms.basePrice());
    }

    @Test
    void aNamedEmeraldIsAnItemNotMoney() {
        ItemStack special = new ItemStack(Items.EMERALD, 3);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom"));
        assertFalse(TradeTerms.isEmerald(special));
        assertTrue(TradeTerms.isEmerald(new ItemStack(Items.EMERALD, 3)));
    }

    @Test
    void overflowIsFlaggedNotWrapped() {
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 64), Optional.empty(), new ItemStack(Items.BREAD), 0, 16, 2, 0.05f);
        TradeTerms terms = TradeTerms.of(offer, Long.MAX_VALUE / 2);
        assertFalse(terms.valid());
        assertEquals(Long.MAX_VALUE, terms.price());
    }
}
