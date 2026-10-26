package dev.eliasnvx.tradery.registry;

import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.vending.DisplayBlock;
import dev.eliasnvx.tradery.vending.DisplayBlockEntity;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendorKeyItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;
import java.util.function.Supplier;

/** Blocks, block entities, items and the creative tab. {@link #init()} runs once from {@code Tradery.init}. */
public final class TraderyBlocks {
    /** Explosions can't break vending or display blocks (bedrock-like resistance). */
    private static final float BLAST_PROOF = 3_600_000f;

    public static final Supplier<VendingBlock> VENDING_BLOCK = block("vending_block", VendingBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, BLAST_PROOF).sound(SoundType.METAL)
            .noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final Supplier<DisplayBlock> DISPLAY_BLOCK = block("display_block", DisplayBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5f, BLAST_PROOF).sound(SoundType.GLASS)
            .noOcclusion().pushReaction(PushReaction.BLOCK));

    public static final Supplier<BlockItem> VENDING_BLOCK_ITEM = blockItem("vending_block", VENDING_BLOCK);
    public static final Supplier<BlockItem> DISPLAY_BLOCK_ITEM = blockItem("display_block", DISPLAY_BLOCK);
    public static final Supplier<VendorKeyItem> VENDOR_KEY = item("vendor_key",
        properties -> new VendorKeyItem(properties.stacksTo(1).rarity(Rarity.EPIC)));
    public static final Supplier<dev.eliasnvx.tradery.village.VillageBoardBlock> VILLAGE_BOARD = block("village_board",
        dev.eliasnvx.tradery.village.VillageBoardBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final Supplier<BlockItem> VILLAGE_BOARD_ITEM = blockItem("village_board", VILLAGE_BOARD);
    public static final Supplier<dev.eliasnvx.tradery.vending.MarketCounterBlock> MARKET_COUNTER = block("market_counter",
        dev.eliasnvx.tradery.vending.MarketCounterBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, BLAST_PROOF).sound(SoundType.WOOD)
            .noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final Supplier<BlockItem> MARKET_COUNTER_ITEM = blockItem("market_counter", MARKET_COUNTER);
    public static final Supplier<dev.eliasnvx.tradery.vending.ShopLedgerItem> SHOP_LEDGER = item("shop_ledger",
        properties -> new dev.eliasnvx.tradery.vending.ShopLedgerItem(properties.stacksTo(1)));

    // No data fixer type: mod block entities have no vanilla fixes
    public static final Supplier<BlockEntityType<VendingBlockEntity>> VENDING_BLOCK_ENTITY = Platform.get().register(
        Registries.BLOCK_ENTITY_TYPE, "vending_block", () -> BlockEntityType.Builder.of(VendingBlockEntity::new, VENDING_BLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<DisplayBlockEntity>> DISPLAY_BLOCK_ENTITY = Platform.get().register(
        Registries.BLOCK_ENTITY_TYPE, "display_block", () -> BlockEntityType.Builder.of(DisplayBlockEntity::new, DISPLAY_BLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<dev.eliasnvx.tradery.vending.MarketCounterBlockEntity>> MARKET_COUNTER_BLOCK_ENTITY =
        Platform.get().register(Registries.BLOCK_ENTITY_TYPE, "market_counter",
            () -> BlockEntityType.Builder.of(dev.eliasnvx.tradery.vending.MarketCounterBlockEntity::new, MARKET_COUNTER.get()).build(null));

    public static final Supplier<CreativeModeTab> TAB = Platform.get().register(Registries.CREATIVE_MODE_TAB, "main",
        () -> Platform.get().creativeTabBuilder()
            .title(Component.translatable("itemGroup.tradery.main"))
            .icon(() -> new ItemStack(VENDING_BLOCK_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(VENDING_BLOCK_ITEM.get());
                output.accept(DISPLAY_BLOCK_ITEM.get());
                output.accept(VILLAGE_BOARD_ITEM.get());
                output.accept(MARKET_COUNTER_ITEM.get());
                output.accept(SHOP_LEDGER.get());
                output.accept(VENDOR_KEY.get());
                TraderyItems.tabItems().forEach(item -> output.accept(item.get()));
            })
            .build());

    private TraderyBlocks() {
    }

    /** Forces class loading, so every register call above runs during mod construction. */
    public static void init() {
        TraderyItems.init();
        TraderyMenus.init();
    }

    static <B extends Block> Supplier<B> block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
        return Platform.get().register(Registries.BLOCK, name, () -> factory.apply(properties));
    }

    /** A block item; its name is the block's ({@code block.tradery.<name>}). */
    static Supplier<BlockItem> blockItem(String name, Supplier<? extends Block> block) {
        return Platform.get().register(Registries.ITEM, name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    static <I extends Item> Supplier<I> item(String name, Function<Item.Properties, I> factory) {
        return Platform.get().register(Registries.ITEM, name, () -> factory.apply(new Item.Properties()));
    }
}
