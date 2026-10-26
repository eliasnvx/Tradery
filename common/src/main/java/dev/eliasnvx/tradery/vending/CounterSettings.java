package dev.eliasnvx.tradery.vending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.network.codec.ByteBufCodecs;
import dev.eliasnvx.tradery.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What a market counter trades: always {@link #MAX_OFFERS} places, each an offer or empty, all sold for money from
 * one shared stock. With {@code villagers} (the owner's toggle) villagers may come and buy the offer they need.
 */
public record CounterSettings(List<CounterOffer> offers, boolean villagers) {
    /** Offers on one counter (spec: up to 6). */
    public static final int MAX_OFFERS = 6;

    public static final CounterSettings EMPTY = new CounterSettings(List.of(), true);

    public static final Codec<CounterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        CounterOffer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(CounterSettings::offers),
        Codec.BOOL.optionalFieldOf("villagers", true).forGetter(CounterSettings::villagers)
    ).apply(i, CounterSettings::new));

    public static final StreamCodec<FriendlyByteBuf, CounterSettings> STREAM_CODEC = StreamCodec.composite(
        CounterOffer.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OFFERS)), CounterSettings::offers,
        ByteBufCodecs.BOOL, CounterSettings::villagers,
        CounterSettings::new);

    /** Always exactly {@link #MAX_OFFERS} offers: missing places are empty, extra ones dropped. */
    public CounterSettings {
        List<CounterOffer> normalized = new ArrayList<>(MAX_OFFERS);
        for (int i = 0; i < MAX_OFFERS; i++) {
            normalized.add(i < offers.size() && offers.get(i) != null ? offers.get(i) : CounterOffer.EMPTY);
        }
        offers = List.copyOf(normalized);
    }

    /** The offer at {@code index}; empty outside 0..{@link #MAX_OFFERS}-1. */
    public CounterOffer offer(int index) {
        return index >= 0 && index < MAX_OFFERS ? offers.get(index) : CounterOffer.EMPTY;
    }

    public int configuredCount() {
        int count = 0;
        for (CounterOffer offer : offers) {
            if (offer.isConfigured()) {
                count++;
            }
        }
        return count;
    }

    /** At least one offer is set: the counter can trade. */
    public boolean isConfigured() {
        return configuredCount() > 0;
    }

    public CounterSettings withVillagers(boolean villagers) {
        return new CounterSettings(offers, villagers);
    }

    /** Every offer has the same goods and price (what buyers see). */
    public boolean sameOffers(CounterSettings other) {
        for (int i = 0; i < MAX_OFFERS; i++) {
            if (!offer(i).sameAs(other.offer(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean sameAs(CounterSettings other) {
        return villagers == other.villagers && sameOffers(other);
    }
}
