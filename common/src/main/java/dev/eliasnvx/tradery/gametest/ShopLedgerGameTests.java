package dev.eliasnvx.tradery.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.command.TraderyPermission;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.ShopLedgerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.ShopLedger;
import dev.eliasnvx.tradery.vending.ShopSnapshot;
import dev.eliasnvx.tradery.vending.VendingBlock;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.vending.VendorsData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The shop ledger (SPEC "Удобство для серверов"): it lists the player's own vending blocks and counters only, with the
 * right alerts, alerts first and then by distance; shops in unloaded chunks show the snapshot kept in the vending
 * index (saved with it, a broken one dropped); its window validates every click; the list and its payload are bounded.
 */
public final class ShopLedgerGameTests {
    /** Far enough that the chunk is never loaded in a test. */
    private static final int FAR = 2_000_000;

    public static final List<TraderyGameTests.Entry> ALL = List.of(
        new TraderyGameTests.Entry("ledger_lists_own_shops_only", ShopLedgerGameTests::listsOwnShopsOnly),
        new TraderyGameTests.Entry("ledger_alerts_and_order", ShopLedgerGameTests::alertsAndOrder),
        new TraderyGameTests.Entry("ledger_snapshot_for_unloaded", ShopLedgerGameTests::snapshotForUnloaded),
        new TraderyGameTests.Entry("ledger_snapshot_saved_with_index", ShopLedgerGameTests::snapshotSavedWithIndex),
        new TraderyGameTests.Entry("ledger_menu_actions", ShopLedgerGameTests::menuActions),
        new TraderyGameTests.Entry("ledger_payload_bounds", ShopLedgerGameTests::payloadBounds));

    private ShopLedgerGameTests() {
    }

    // ------------------------------------------------------------------ helpers

    /** A vending block placed as a player would (owner, index entry, snapshot). */
    private static VendingBlockEntity vending(GameTestHelper helper, BlockPos at, AccountId owner, VendingSettings settings) {
        helper.setBlock(at, TraderyBlocks.VENDING_BLOCK.get().defaultBlockState().setValue(VendingBlock.FACING, Direction.NORTH));
        VendingBlockEntity vendor = helper.getBlockEntity(at);
        vendor.setOwner(owner, "owner");
        VendorsData.get(helper.getLevel().getServer()).add(helper.getLevel(), helper.absolutePos(at), owner, true);
        vendor.setSettings(settings);
        return vendor;
    }

    private static VendingSettings sell(Item goods, int perTrade, long price) {
        return new VendingSettings(new ItemStack(goods, perTrade), PriceMode.CURRENCY, price, ItemStack.EMPTY, false, DisplayAnimation.STATIC);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(at));
        player.setPos(pos.x, pos.y, pos.z);
        player.getInventory().clearContent();
        return player;
    }

    private static GlobalPos key(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    private static List<GlobalPos> positions(List<ShopLedger.Listing> listings) {
        List<GlobalPos> positions = new ArrayList<>();
        listings.forEach(listing -> positions.add(listing.pos()));
        return positions;
    }

    private static ShopSnapshot unloadedSnapshot(Item goods, int stock, int alerts) {
        return new ShopSnapshot(ShopSnapshot.Kind.VENDING, false, true,
            List.of(new ShopSnapshot.Offer(goods, 1, 500, Items.AIR, 0, stock)), Items.AIR, 0, alerts);
    }

    // ------------------------------------------------------------------ tests

    public static void listsOwnShopsOnly(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new BlockPos(0, 1, 0));
        AccountId me = AccountId.player(player.getUUID());
        VendingBlockEntity mine = vending(helper, new BlockPos(1, 1, 1), me, sell(Items.BREAD, 1, 100));
        mine.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        mine.stock().setChanged();
        MarketCounterGameTests.counter(helper, new BlockPos(3, 1, 1), me, CounterSettings.EMPTY);
        vending(helper, new BlockPos(1, 1, 3), AccountId.player(UUID.randomUUID()), sell(Items.APPLE, 1, 100));
        vending(helper, new BlockPos(3, 1, 3), AccountId.system(EconomyService.SERVER_ACCOUNT), sell(Items.CARROT, 1, 100));

        List<ShopLedger.Listing> listed = ShopLedger.list(player);
        helper.assertValueEqual(listed.size(), 2, "only my two shops");
        helper.assertTrue(positions(listed).contains(key(helper, new BlockPos(1, 1, 1))), "my vending block");
        helper.assertTrue(positions(listed).contains(key(helper, new BlockPos(3, 1, 1))), "my counter");
        for (ShopLedger.Listing listing : listed) {
            helper.assertTrue(listing.live(), "loaded shops are read live");
        }

        helper.assertTrue(Platform.get().hasPermission(player, TraderyPermission.SHOPS), "everyone may use the ledger by default");
        VendingTrades.forget(player);
        TraderyBlocks.SHOP_LEDGER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof ShopLedgerMenu, "the book opens the ledger");
        ShopLedgerMenu menu = (ShopLedgerMenu) player.containerMenu;
        helper.assertValueEqual(menu.data().total(), 2, "the window lists the two shops");
        helper.assertValueEqual(menu.data().rows().size(), 2, "on one page");
        helper.assertTrue(menu.slots.isEmpty(), "the ledger has no item slots");
        player.closeContainer();
        VendingTrades.forget(player);
        helper.succeed();
    }

    public static void alertsAndOrder(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new BlockPos(0, 1, 0));
        AccountId me = AccountId.player(player.getUUID());

        VendingBlockEntity ok = vending(helper, new BlockPos(1, 1, 0), me, sell(Items.BREAD, 1, 100));
        ok.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        ok.stock().setChanged();
        MarketCounterBlockEntity okCounter = MarketCounterGameTests.counter(helper, new BlockPos(4, 1, 4), me,
            MarketCounterGameTests.offers(MarketCounterGameTests.offer(Items.BREAD, 1, 100)));
        okCounter.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        okCounter.stock().setChanged();
        vending(helper, new BlockPos(4, 1, 0), me, sell(Items.APPLE, 1, 100));
        VendingBlockEntity full = vending(helper, new BlockPos(0, 1, 4), me,
            new VendingSettings(new ItemStack(Items.GOLDEN_APPLE), PriceMode.ITEM, 0, new ItemStack(Items.DIAMOND, 2), false, DisplayAnimation.STATIC));
        full.stock().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 3));
        for (int i = 0; i < VendingBlockEntity.REVENUE_SIZE; i++) {
            full.revenue().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        full.stock().setChanged();
        MarketCounterGameTests.counter(helper, new BlockPos(2, 1, 2), me, CounterSettings.EMPTY);
        VendingBlockEntity buyback = vending(helper, new BlockPos(2, 1, 4), me,
            new VendingSettings(new ItemStack(Items.COBBLESTONE, 8), PriceMode.CURRENCY, 100, ItemStack.EMPTY, true, DisplayAnimation.STATIC));
        for (int i = 0; i < VendingBlockEntity.STOCK_SIZE; i++) {
            buyback.stock().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        buyback.stock().setChanged();
        MarketCounterBlockEntity part = MarketCounterGameTests.counter(helper, new BlockPos(4, 1, 2), me,
            MarketCounterGameTests.offers(MarketCounterGameTests.offer(Items.BREAD, 1, 100), MarketCounterGameTests.offer(Items.APPLE, 1, 100)));
        part.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        part.stock().setChanged();

        List<ShopLedger.Listing> listed = ShopLedger.list(player);
        // Alerts first (by distance; ties by x), then the shops without alerts (by distance)
        List<GlobalPos> expected = List.of(key(helper, new BlockPos(2, 1, 2)), key(helper, new BlockPos(0, 1, 4)),
            key(helper, new BlockPos(4, 1, 0)), key(helper, new BlockPos(2, 1, 4)), key(helper, new BlockPos(4, 1, 2)),
            key(helper, new BlockPos(1, 1, 0)), key(helper, new BlockPos(4, 1, 4)));
        helper.assertValueEqual(positions(listed), expected, "alerts first, then by distance");

        int[] alerts = new int[listed.size()];
        for (int i = 0; i < listed.size(); i++) {
            alerts[i] = listed.get(i).snapshot().alerts();
        }
        helper.assertValueEqual(alerts[0], ShopSnapshot.NOT_CONFIGURED, "a blank counter: not set up");
        helper.assertValueEqual(alerts[1], ShopSnapshot.NO_ROOM, "item revenue full: no room for payment");
        helper.assertValueEqual(alerts[2], ShopSnapshot.EMPTY, "no stock: empty");
        helper.assertValueEqual(alerts[3], ShopSnapshot.NO_ROOM, "buyback stock full: no room");
        helper.assertValueEqual(alerts[4], ShopSnapshot.EMPTY, "one counter offer out: empty");
        helper.assertValueEqual(listed.get(4).snapshot().emptyOffers(), 1, "one of two offers sold out");
        helper.assertValueEqual(alerts[5], 0, "stocked vending block: fine");
        helper.assertValueEqual(alerts[6], 0, "stocked counter: fine");

        ShopLedgerMenu.Row revenue = ShopLedger.page(listed, 0).rows().get(1);
        helper.assertValueEqual(revenue.revenueCount(), 64 * VendingBlockEntity.REVENUE_SIZE, "item revenue waiting is listed");
        helper.assertTrue(revenue.offers().getFirst().priceItem().is(Items.DIAMOND), "with its price item");
        helper.assertFalse(revenue.villagersUsed(), "no villagers toggle for an item price");
        helper.succeed();
    }

    public static void snapshotForUnloaded(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new BlockPos(0, 1, 0));
        AccountId me = AccountId.player(player.getUUID());
        ServerLevel level = helper.getLevel();
        VendorsData index = VendorsData.get(level.getServer());
        VendingBlockEntity loaded = vending(helper, new BlockPos(2, 1, 2), me, sell(Items.BREAD, 4, 100));
        loaded.stock().setItem(0, new ItemStack(Items.BREAD, 12));
        loaded.stock().setChanged();
        BlockPos far = new BlockPos(FAR, 64, FAR);
        BlockPos farNoSnapshot = new BlockPos(FAR + 16, 64, FAR);
        ShopSnapshot stored = unloadedSnapshot(Items.DIAMOND, 0, ShopSnapshot.EMPTY);
        try {
            index.add(level, far, me, true);
            index.updateSnapshot(level, far, stored);
            index.add(level, farNoSnapshot, me, true);
            helper.assertFalse(level.isLoaded(far), "the far shop's chunk isn't loaded");

            List<ShopLedger.Listing> listed = ShopLedger.list(player);
            helper.assertValueEqual(listed.size(), 3, "three shops");
            ShopLedger.Listing first = listed.get(0);
            helper.assertValueEqual(first.pos(), GlobalPos.of(level.dimension(), far), "the far one has an alert: first");
            helper.assertFalse(first.live(), "not loaded: last known state");
            helper.assertValueEqual(first.snapshot(), stored, "the stored snapshot");
            helper.assertTrue(listed.get(1).live() && listed.get(1).snapshot().offers().getFirst().stock() == 3, "the loaded one, read live");
            helper.assertTrue(listed.get(2).snapshot() == null, "no snapshot yet: listed last");

            List<ShopLedgerMenu.Row> rows = ShopLedger.page(listed, 0).rows();
            helper.assertValueEqual(rows.get(0).kind(), ShopLedgerMenu.KIND_VENDING, "row kind from the snapshot");
            helper.assertTrue(rows.get(0).offers().getFirst().goods().is(Items.DIAMOND), "row goods from the snapshot");
            helper.assertValueEqual(rows.get(0).offers().getFirst().price(), 500L, "row price from the snapshot");
            helper.assertTrue(rows.get(0).has(ShopSnapshot.EMPTY) && !rows.get(0).live(), "row alert and state");
            helper.assertValueEqual(rows.get(2).kind(), ShopLedgerMenu.KIND_UNKNOWN, "no snapshot: unknown kind");

            // A sale changes the stock: the index follows (it's what the ledger shows once the chunk unloads)
            ServerPlayer buyer = MarketCounterGameTests.playerAt(helper, new BlockPos(2, 1, 2));
            MarketCounterGameTests.setBalance(buyer.getUUID(), 1_000);
            helper.assertTrue(VendingTrades.execute(buyer, loaded, 2).success(), "two lots sold");
            ShopSnapshot after = index.get(GlobalPos.of(level.dimension(), loaded.getBlockPos())).snapshot();
            helper.assertValueEqual(after.offers().getFirst().stock(), 1, "the snapshot follows the sale");
            VendingTrades.forget(buyer);
        } finally {
            index.remove(level, far);
            index.remove(level, farNoSnapshot);
        }
        helper.succeed();
    }

    // NeoForge deprecates the vanilla codec getter and buffer constructor for level-aware ones; no level is needed here
    @SuppressWarnings("deprecation")
    public static void snapshotSavedWithIndex(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AccountId owner = AccountId.player(UUID.randomUUID());
        VendorsData data = new VendorsData();
        BlockPos withSnapshot = new BlockPos(FAR, 64, FAR + 32);
        BlockPos broken = new BlockPos(FAR + 16, 64, FAR + 32);
        BlockPos without = new BlockPos(FAR + 32, 64, FAR + 32);
        ShopSnapshot counterSnapshot = new ShopSnapshot(ShopSnapshot.Kind.COUNTER, false, false,
            List.of(new ShopSnapshot.Offer(Items.BREAD, 4, 250, Items.AIR, 0, 7), new ShopSnapshot.Offer(Items.APPLE, 2, 100, Items.AIR, 0, 0)),
            Items.AIR, 0, ShopSnapshot.EMPTY);
        data.add(level, withSnapshot, owner, true);
        data.updateSnapshot(level, withSnapshot, counterSnapshot);
        data.add(level, broken, owner, true);
        data.updateSnapshot(level, broken, unloadedSnapshot(Items.DIAMOND, 3, 0));
        data.add(level, without, owner, true);

        JsonElement json = VendorsData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        // An item that no longer exists (a removed mod): that snapshot is dropped, the entry stays
        for (JsonElement element : json.getAsJsonObject().getAsJsonArray("vendors")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("snapshot") && entry.toString().contains("minecraft:diamond")) {
                JsonArray offers = entry.getAsJsonObject("snapshot").getAsJsonArray("offers");
                offers.get(0).getAsJsonObject().addProperty("goods", "tradery:no_such_item");
            }
        }
        VendorsData loaded = VendorsData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        VendorsData.Entry restored = loaded.get(GlobalPos.of(level.dimension(), withSnapshot));
        helper.assertTrue(restored != null && counterSnapshot.equals(restored.snapshot()), "the snapshot is saved with the index");
        VendorsData.Entry dropped = loaded.get(GlobalPos.of(level.dimension(), broken));
        helper.assertTrue(dropped != null && dropped.snapshot() == null, "a broken snapshot is dropped, the entry kept");
        VendorsData.Entry plain = loaded.get(GlobalPos.of(level.dimension(), without));
        helper.assertTrue(plain != null && plain.snapshot() == null && plain.owner().equals(owner), "an entry without a snapshot loads");

        // Bounded: more offers than a counter has are cut
        ShopSnapshot.Offer one = new ShopSnapshot.Offer(Items.BREAD, 1, 1, Items.AIR, 0, 1);
        List<ShopSnapshot.Offer> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(one);
        }
        helper.assertValueEqual(new ShopSnapshot(ShopSnapshot.Kind.COUNTER, false, true, many, Items.AIR, 0, 0).offers().size(),
            CounterSettings.MAX_OFFERS, "a snapshot keeps at most six offers");
        helper.succeed();
    }

    public static void menuActions(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new BlockPos(0, 1, 0));
        AccountId me = AccountId.player(player.getUUID());
        ServerLevel level = helper.getLevel();
        VendorsData index = VendorsData.get(level.getServer());
        VendingBlockEntity vendor = vending(helper, new BlockPos(1, 1, 1), me, sell(Items.BREAD, 1, 100));
        vendor.stock().setItem(0, new ItemStack(Items.BREAD, 5));
        vendor.stock().setChanged();
        MarketCounterBlockEntity counter = MarketCounterGameTests.counter(helper, new BlockPos(2, 1, 1), me,
            MarketCounterGameTests.offers(MarketCounterGameTests.offer(Items.APPLE, 1, 100)));
        counter.stock().setItem(0, new ItemStack(Items.APPLE, 5));
        counter.stock().setChanged();
        VendingBlockEntity itemPrice = vending(helper, new BlockPos(3, 1, 1), me,
            new VendingSettings(new ItemStack(Items.CARROT), PriceMode.ITEM, 0, new ItemStack(Items.EMERALD), false, DisplayAnimation.STATIC));
        itemPrice.stock().setItem(0, new ItemStack(Items.CARROT, 5));
        itemPrice.stock().setChanged();
        BlockPos far = new BlockPos(FAR, 64, FAR + 64);
        ShopSnapshot stored = unloadedSnapshot(Items.DIAMOND, 4, 0);
        try {
            index.add(level, far, me, true);
            index.updateSnapshot(level, far, stored);
            List<ShopLedger.Listing> all = ShopLedger.list(player);
            helper.assertValueEqual(all.size(), 4, "four shops");
            ShopLedgerMenu menu = new ShopLedgerMenu(42, player.getInventory(), ShopLedger.page(all, 0), all);
            VendingTrades.forget(player);

            helper.assertFalse(menu.clickMenuButton(player, -1), "negative id");
            helper.assertFalse(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.PAGE, 1)), "there is no second page");
            helper.assertFalse(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.SHOW, 4)), "no fifth row");
            helper.assertFalse(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 999)), "no such row");
            helper.assertFalse(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(9, 0)), "unknown action");
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.PAGE, 0)), "page 1");
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.SHOW, 0)), "show the first shop");

            // Rows by distance: the vending block, the counter, the item-priced block, the far one
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 0)), "toggle the vending block");
            helper.assertFalse(vendor.settings().villagers(), "villagers off on the loaded vending block");
            helper.assertFalse(index.get(GlobalPos.of(level.dimension(), vendor.getBlockPos())).snapshot().villagers(), "the snapshot follows");
            helper.assertFalse(menu.data().rows().get(0).villagersFlag(), "the window got the new state");
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 1)), "toggle the counter");
            helper.assertFalse(counter.settings().villagers(), "villagers off on the counter");
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 2)), "answered");
            helper.assertTrue(itemPrice.settings().villagers(), "an item price has no villagers to switch");
            helper.assertTrue(menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 3)), "answered");
            helper.assertValueEqual(index.get(GlobalPos.of(level.dimension(), far)).snapshot(), stored, "an unloaded shop isn't touched");

            // Someone else's shop: refused, unchanged
            ServerPlayer stranger = playerAt(helper, new BlockPos(0, 1, 0));
            helper.assertFalse(ShopLedger.toggleVillagers(stranger, GlobalPos.of(level.dimension(), counter.getBlockPos())).success(),
                "not your shop");
            helper.assertFalse(counter.settings().villagers(), "the counter is unchanged");
            helper.assertTrue(ShopLedger.list(stranger).isEmpty(), "a stranger's ledger is empty");

            // Rate limit: at most 10 actions a second
            VendingTrades.forget(player);
            int toggles = 0;
            boolean last = vendor.settings().villagers();
            for (int i = 0; i < 15; i++) {
                menu.clickMenuButton(player, ShopLedgerMenu.buttonId(ShopLedgerMenu.VILLAGERS, 0));
                if (vendor.settings().villagers() != last) {
                    toggles++;
                    last = vendor.settings().villagers();
                }
            }
            helper.assertValueEqual(toggles, 10, "only 10 actions went through");
        } finally {
            index.remove(level, far);
            VendingTrades.forget(player);
        }
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    public static void payloadBounds(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new BlockPos(0, 1, 0));
        AccountId me = AccountId.player(player.getUUID());
        ServerLevel level = helper.getLevel();
        VendorsData index = VendorsData.get(level.getServer());
        List<ShopSnapshot.Offer> six = new ArrayList<>();
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            six.add(new ShopSnapshot.Offer(Items.BREAD, 64, Long.MAX_VALUE, Items.AIR, 0, VendingTrades.UNLIMITED));
        }
        ShopSnapshot big = new ShopSnapshot(ShopSnapshot.Kind.COUNTER, false, true, six, Items.AIR, 0, 0);
        List<BlockPos> added = new ArrayList<>();
        try {
            for (int i = 0; i < 600; i++) {
                BlockPos pos = new BlockPos(FAR + i * 16, 64, FAR + 128);
                index.add(level, pos, me, true);
                index.updateSnapshot(level, pos, big);
                added.add(pos);
            }
            List<ShopLedger.Listing> all = ShopLedger.list(player);
            helper.assertValueEqual(all.size(), ShopLedger.MAX_ENTRIES, "the list is capped");
            ShopLedgerMenu.Data first = ShopLedger.page(all, 0);
            helper.assertValueEqual(first.rows().size(), ShopLedger.PAGE_SIZE, "one page of rows");
            helper.assertValueEqual(first.pages(), ShopLedger.MAX_ENTRIES / ShopLedger.PAGE_SIZE, "pages");
            ShopLedgerMenu.Data clamped = ShopLedger.page(all, 99);
            helper.assertValueEqual(clamped.page(), first.pages() - 1, "a page past the end is the last page");

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            ShopLedgerMenu.Data.STREAM_CODEC.encode(buf, first);
            int bytes = buf.readableBytes();
            ShopLedgerMenu.Data decoded = ShopLedgerMenu.Data.STREAM_CODEC.decode(buf);
            helper.assertValueEqual(decoded.rows().size(), ShopLedger.PAGE_SIZE, "a page survives the wire");
            helper.assertValueEqual(decoded.rows().get(5).pos(), first.rows().get(5).pos(), "rows in order");
            helper.assertTrue(bytes < 64 * 1024, "a full page of full counters stays small: " + bytes + " bytes");

            List<ShopLedgerMenu.Row> tooMany = new ArrayList<>(first.rows());
            tooMany.add(first.rows().getFirst());
            helper.assertTrue(throwsOnEncode(level, new ShopLedgerMenu.Data(0, 1, tooMany.size(), tooMany)), "more rows than a page: refused");

            // Hostile decodes fail fast
            RegistryFriendlyByteBuf save = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            save.writeVarInt(1);
            save.writeVarInt(CounterSettings.MAX_OFFERS + 1);
            for (int i = 0; i <= CounterSettings.MAX_OFFERS; i++) {
                save.writeVarLong(1);
            }
            helper.assertTrue(throwsOnDecode(() -> TraderyPayloads.CounterSavePayload.STREAM_CODEC.decode(save)), "seven prices: refused");
            RegistryFriendlyByteBuf rowCount = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            rowCount.writeVarInt(0);
            rowCount.writeVarInt(1);
            rowCount.writeVarInt(1);
            rowCount.writeVarInt(ShopLedger.PAGE_SIZE + 1);
            helper.assertTrue(throwsOnDecode(() -> ShopLedgerMenu.Data.STREAM_CODEC.decode(rowCount)), "too many rows: refused");
        } finally {
            added.forEach(pos -> index.remove(level, pos));
        }
        helper.assertTrue(ShopLedger.list(player).isEmpty(), "cleaned up");
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    private static boolean throwsOnEncode(ServerLevel level, ShopLedgerMenu.Data data) {
        try {
            ShopLedgerMenu.Data.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess()), data);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean throwsOnDecode(Runnable decode) {
        try {
            decode.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
