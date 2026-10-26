package dev.eliasnvx.tradery.market;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.config.ConfigCodecs;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.List;

/**
 * One entry of a market table ({@code data/<ns>/tradery/market/<village type>.json}):
 *
 * <pre>{@code
 * { "weight": 4, "item": "minecraft:book", "modifier": { "function": "minecraft:enchant_randomly", ... },
 *   "count": 1, "price": { "min": 30, "max": 75 } }
 * }</pre>
 *
 * @param weight   relative chance among the table's offers
 * @param item     an item id, or a full item stack with NBT ({@code {"id": ..., "Count": 1, "tag": {...}}})
 * @param modifier loot item functions applied when the stall generates (like a loot table entry's
 *                 {@code functions}): an inline function, a list of them, or an {@code item_modifiers} id; empty: none
 * @param count    items per trade, at most the result's stack size
 * @param price    price per trade in major units; a number or {@code {min, max}}, rolled in steps of the
 *                 smallest unit written ({@code 8..14} gives whole units, {@code 0.5..2} halves)
 */
public record MarketOffer(int weight, ItemStack item, List<LootItemFunction> modifier, int count, PriceRange price) {
    private static final Codec<ItemStack> ITEM = Codec.either(BuiltInRegistries.ITEM.byNameCodec(), ItemStack.CODEC)
        .xmap(either -> either.map(ItemStack::new, stack -> stack), Either::right);
    /** 1.20.1 reads loot functions with Gson, not codecs. */
    private static final Gson LOOT = Deserializers.createFunctionSerializer().create();
    private static final Codec<List<LootItemFunction>> MODIFIER = Codec.PASSTHROUGH.comapFlatMap(MarketOffer::parseModifier,
        functions -> new Dynamic<>(JsonOps.INSTANCE, LOOT.toJsonTree(functions.toArray(LootItemFunction[]::new), LootItemFunction[].class)));

    public static final Codec<MarketOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
        ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(MarketOffer::weight),
        ITEM.fieldOf("item").forGetter(MarketOffer::item),
        MODIFIER.optionalFieldOf("modifier", List.of()).forGetter(MarketOffer::modifier),
        Codec.intRange(1, VendingSettings.MAX_PER_TRADE).optionalFieldOf("count", 1).forGetter(MarketOffer::count),
        PriceRange.CODEC.fieldOf("price").forGetter(MarketOffer::price)
    ).apply(i, MarketOffer::new));

    public MarketOffer {
        item = item.copyWithCount(1);
        modifier = List.copyOf(modifier);
    }

    /** One function, a list of them, or the id of an item modifier ({@code minecraft:reference}). */
    private static DataResult<List<LootItemFunction>> parseModifier(Dynamic<?> input) {
        JsonElement json = input.convert(JsonOps.INSTANCE).getValue();
        try {
            if (json.isJsonPrimitive()) {
                JsonObject reference = new JsonObject();
                reference.addProperty("function", "minecraft:reference");
                reference.addProperty("name", json.getAsString());
                json = reference;
            }
            if (json.isJsonArray()) {
                return DataResult.success(List.of(LOOT.fromJson(json, LootItemFunction[].class)));
            }
            return DataResult.success(List.of(LOOT.fromJson(json, LootItemFunction.class)));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "Bad modifier: " + e.getMessage());
        }
    }

    /**
     * The goods of one trade: the item with the modifier applied, {@link #count} of them (capped at the stack size).
     *
     * @throws RuntimeException when the modifier fails (a broken data pack)
     */
    public ItemStack createGoods(ServerLevel level, BlockPos pos, RandomSource random) {
        ItemStack goods = item.copy();
        if (!modifier.isEmpty()) {
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
            LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(random.nextLong()).create(null);
            for (LootItemFunction function : modifier) {
                goods = function.apply(goods, context);
            }
        }
        return goods.isEmpty() ? ItemStack.EMPTY : goods.copyWithCount(Math.min(count, goods.getMaxStackSize()));
    }

    /** Whether {@code goods} for {@code price} minor units can come from this offer (GameTests). */
    public boolean couldProduce(ItemStack goods, long price, int decimals) {
        boolean sameGoods = !modifier.isEmpty() || ItemStack.isSameItemSameTags(goods, item);
        boolean sameCount = goods.getCount() == Math.min(count, goods.getMaxStackSize());
        return sameGoods && sameCount && price >= this.price.minMinor(decimals) && price <= this.price.maxMinor(decimals);
    }

    /**
     * A price range in major units.
     *
     * @param min lowest price, {@code >= 0}
     * @param max highest price, {@code >= min}
     */
    public record PriceRange(BigDecimal min, BigDecimal max) {
        private static final Codec<PriceRange> RANGE = RecordCodecBuilder.<PriceRange>create(i -> i.group(
            ConfigCodecs.NON_NEGATIVE.fieldOf("min").forGetter(PriceRange::min),
            ConfigCodecs.NON_NEGATIVE.fieldOf("max").forGetter(PriceRange::max)
        ).apply(i, PriceRange::new)).flatXmap(range -> range.min.compareTo(range.max) <= 0
            ? DataResult.success(range)
            : DataResult.error(() -> "Price min " + range.min.toPlainString() + " is above max " + range.max.toPlainString()), DataResult::success);

        public static final Codec<PriceRange> CODEC = Codec.either(ConfigCodecs.NON_NEGATIVE, RANGE).xmap(
            either -> either.map(price -> new PriceRange(price, price), range -> range),
            range -> range.min.compareTo(range.max) == 0 ? Either.left(range.min) : Either.right(range));

        /**
         * A random price in minor units, in steps of the smallest unit {@code min} and {@code max} are written with.
         *
         * @throws ArithmeticException if the price doesn't fit in a {@code long}
         */
        public long roll(RandomSource random, int decimals) {
            long step = step(decimals);
            long low = -Math.floorDiv(-minMinor(decimals), step);
            long high = Math.floorDiv(maxMinor(decimals), step);
            long span = high - low + 1;
            long units = span <= 1 ? low : low + Math.floorMod(random.nextLong(), span);
            return Math.multiplyExact(units, step);
        }

        long minMinor(int decimals) {
            return Money.toMinor(min, decimals);
        }

        long maxMinor(int decimals) {
            return Money.toMinor(max, decimals);
        }

        /** Minor units per step: {@code 8..14} at 2 decimals rolls in steps of 100. */
        private long step(int decimals) {
            int written = Math.max(min.stripTrailingZeros().scale(), max.stripTrailingZeros().scale());
            int scale = Math.max(0, Math.min(written, decimals));
            return Money.unit(decimals) / Money.unit(scale);
        }
    }
}
