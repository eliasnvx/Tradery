package dev.eliasnvx.tradery.registry;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.ore.CoinItem;
import dev.eliasnvx.tradery.ore.CoinOreBlock;
import dev.eliasnvx.tradery.ore.CoinTier;
import dev.eliasnvx.tradery.ore.OreEnabledFilter;
import dev.eliasnvx.tradery.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Coins and coin ores. */
public final class TraderyItems {
    private static final Map<CoinTier, Supplier<CoinItem>> COINS = new EnumMap<>(CoinTier.class);
    private static final Map<CoinTier, Supplier<CoinOreBlock>> ORES = new EnumMap<>(CoinTier.class);
    private static final Map<CoinTier, Supplier<CoinOreBlock>> DEEPSLATE_ORES = new EnumMap<>(CoinTier.class);
    private static final List<Supplier<? extends ItemLike>> TAB = new ArrayList<>();

    public static final Supplier<PlacementModifierType<OreEnabledFilter>> ORE_ENABLED_FILTER = Platform.get().register(
        Registries.PLACEMENT_MODIFIER_TYPE, "enabled_in_config", () -> () -> OreEnabledFilter.CODEC);

    static {
        for (CoinTier tier : CoinTier.values()) {
            COINS.put(tier, TraderyBlocks.item(tier.id() + "_coin", properties -> new CoinItem(tier, properties)));
            float hardness = tier == CoinTier.COPPER ? 3.0f : 3.5f;
            Supplier<CoinOreBlock> ore = TraderyBlocks.block(tier.id() + "_coin_ore", props -> new CoinOreBlock(tier, props),
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(hardness, 3.0f));
            Supplier<CoinOreBlock> deepslate = TraderyBlocks.block("deepslate_" + tier.id() + "_coin_ore", props -> new CoinOreBlock(tier, props),
                BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(hardness + 1.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE));
            ORES.put(tier, ore);
            DEEPSLATE_ORES.put(tier, deepslate);
            Supplier<BlockItem> oreItem = TraderyBlocks.blockItem(tier.id() + "_coin_ore", ore);
            Supplier<BlockItem> deepslateItem = TraderyBlocks.blockItem("deepslate_" + tier.id() + "_coin_ore", deepslate);
            TAB.add(COINS.get(tier));
            TAB.add(oreItem);
            TAB.add(deepslateItem);
        }
    }

    private TraderyItems() {
    }

    static void init() {
    }

    public static Item coin(CoinTier tier) {
        return COINS.get(tier).get();
    }

    public static CoinOreBlock ore(CoinTier tier, boolean deepslate) {
        return (deepslate ? DEEPSLATE_ORES : ORES).get(tier).get();
    }

    /** Shown in the creative tab after the blocks. */
    static List<Supplier<? extends ItemLike>> tabItems() {
        return TAB;
    }

    static String name(String path) {
        return Tradery.id(path).toString();
    }
}
