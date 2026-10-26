package dev.eliasnvx.tradery.market;

import com.mojang.serialization.MapCodec;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.registry.TraderyWorldgen;
import dev.eliasnvx.tradery.util.Money;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code tradery:market_stock}: stocks the vending blocks of the market stalls as they generate. A stall template's
 * vendor is already server-owned with infinite stock, burned payment and no fee; its {@value #MARKER} tag says what to
 * sell: {@code {"role": "offer" | "emerald_sell" | "emerald_buy", "table": "<village type>"}}. Offers are rolled from
 * the {@link MarketTables market table} (seeded by world seed and position); the changer uses the current
 * {@code market.emeraldSellPrice} / {@code emeraldBuyPrice}. The tag is removed; a vendor whose roll fails stays
 * unconfigured (an admin can set it up with the vendor key). Runs on world generation threads: reads only volatile
 * config and immutable data.
 */
public final class MarketStockProcessor extends StructureProcessor {
    public static final MarketStockProcessor INSTANCE = new MarketStockProcessor();
    public static final MapCodec<MarketStockProcessor> MAP_CODEC = MapCodec.unit(INSTANCE);
    /** Block entity tag of a stall vendor in the templates. */
    public static final String MARKER = "tradery_market";
    public static final String ROLE_OFFER = "offer";
    public static final String ROLE_EMERALD_SELL = "emerald_sell";
    public static final String ROLE_EMERALD_BUY = "emerald_buy";
    /** The config whose bad changer prices were already reported (once, not for every generated vendor). */
    private static volatile ServerConfig.@Nullable MarketSection warnedAbout;

    private MarketStockProcessor() {
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TraderyWorldgen.MARKET_STOCK.get();
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot,
                                                                      StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo info,
                                                                      StructurePlaceSettings settings) {
        CompoundTag nbt = info.nbt();
        if (nbt == null || !nbt.contains(MARKER) || !info.state().is(TraderyBlocks.VENDING_BLOCK.get())) {
            return info;
        }
        CompoundTag marker = nbt.getCompound(MARKER);
        CompoundTag stocked = nbt.copy();
        stocked.remove(MARKER);
        BlockState state = info.state();
        if (level instanceof ServerLevelAccessor accessor) {
            ServerLevel serverLevel = accessor.getLevel();
            Optional<VendingSettings> offer = offer(serverLevel, info.pos(), marker.getString("role"), marker.getString("table"));
            if (offer.isPresent()) {
                stocked.put("settings", VendingSettings.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), offer.get())
                    .getOrThrow());
                state = state.setValue(VendingBlock.STOCKED, true);
            }
        }
        return new StructureTemplate.StructureBlockInfo(info.pos(), state, stocked);
    }

    /** What the vendor at {@code pos} (world position) sells. */
    static Optional<VendingSettings> offer(ServerLevel level, BlockPos pos, String role, String table) {
        MinecraftServer server = level.getServer();
        ServerConfig config = TraderyConfig.server();
        int decimals = decimals(server);
        VendingSettings base = VendingSettings.EMPTY.withPriceMode(PriceMode.CURRENCY).withAnimation(config.vending().defaultAnimation());
        return switch (role) {
            case ROLE_OFFER -> {
                RandomSource random = RandomSource.create(level.getSeed()).forkPositional().at(pos);
                Optional<MarketTables.Table> found = MarketTables.get(server, table);
                if (found.isEmpty()) {
                    Tradery.LOGGER.warn("No market table \"{}\" for the stall vendor at {}", table, pos);
                }
                yield found.flatMap(t -> t.roll(level, pos, random, decimals))
                    .map(rolled -> base.withGoods(rolled.goods()).withPrice(rolled.price()));
            }
            case ROLE_EMERALD_SELL -> Optional.of(base.withGoods(new ItemStack(Items.EMERALD))
                .withPrice(changerPrices(config.market(), decimals).sell()));
            case ROLE_EMERALD_BUY -> Optional.of(base.withGoods(new ItemStack(Items.EMERALD))
                .withPrice(changerPrices(config.market(), decimals).buy()).withBuyback(true));
            default -> {
                Tradery.LOGGER.warn("Unknown market stall role \"{}\" at {}", role, pos);
                yield Optional.empty();
            }
        };
    }

    /**
     * The changer's prices in minor units.
     *
     * @param sell what the changer sells an emerald for
     * @param buy  what it pays for one: never above {@code sell}, or buying and selling back would print money
     */
    public record ChangerPrices(long sell, long buy) {
    }

    public static ChangerPrices changerPrices(ServerConfig.MarketSection market, int decimals) {
        long sell = minorOrMax(market.emeraldSellPrice(), decimals);
        long buy = minorOrMax(market.emeraldBuyPrice(), decimals);
        if (buy > sell) {
            if (warnedAbout != market) {
                warnedAbout = market;
                Tradery.LOGGER.warn("market.emeraldBuyPrice ({}) is above market.emeraldSellPrice ({}); the changer pays the sell price",
                    market.emeraldBuyPrice().toPlainString(), market.emeraldSellPrice().toPlainString());
            }
            buy = sell;
        }
        return new ChangerPrices(sell, buy);
    }

    /**
     * Decimals of the world's currency. Once the server runs, the economy knows them; while the server starts
     * (spawn chunks generate before the economy binds) the configured ones, which a new world adopts.
     */
    public static int decimals(@Nullable MinecraftServer server) {
        return server != null && server.isReady()
            ? EconomyService.INSTANCE.defaultCurrency().decimals()
            : TraderyConfig.server().currency().decimals();
    }

    private static long minorOrMax(BigDecimal major, int decimals) {
        try {
            return Money.toMinor(major, decimals);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
