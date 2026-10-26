package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.village.VillagerTrading;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A villager's trade window in money (SPEC "Торговля с жителями за валюту"), also used by caravan traders (SPEC
 * "Торговля между деревнями"). No item slots at all, like the vending buyer menu: the server takes the costs from the
 * inventory itself. A trade is a menu button click (vanilla checks it's this open menu): {@code offer * 2}, plus one
 * for "trade all".
 */
public class VillagerTradeMenu extends AbstractContainerMenu {
    /** More offers than any villager has; bounds what the client decodes. */
    public static final int MAX_OFFERS = 256;

    /**
     * What the window shows; sent when it opens and again after every trade ({@link TraderyPayloads.VillagerTradeSyncPayload}).
     *
     * @param offers       the villager's offers, with this player's prices
     * @param level        profession level, 1..5
     * @param xp           the villager's trade experience
     * @param showProgress vanilla's experience bar flag
     * @param emeraldValue money for one emerald (minor units)
     * @param wallet       the villager's money, or the caravan's purse (minor units)
     * @param offerValues  a caravan's money for the one emerald of each offer, by offer index; empty for a villager
     *                     (every emerald is worth {@code emeraldValue})
     * @param caravan      the other side is a caravan
     */
    public record Data(MerchantOffers offers, int level, int xp, boolean showProgress, long emeraldValue, long wallet, List<Long> offerValues,
                       boolean caravan) {
        private static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffers> OFFERS =
            ByteBufCodecs.collection(size -> new MerchantOffers(), MerchantOffer.STREAM_CODEC, MAX_OFFERS);
        private static final StreamCodec<ByteBuf, List<Long>> VALUES =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG, MAX_OFFERS);
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = dev.eliasnvx.tradery.network.PortCodecs.composite(
            OFFERS, Data::offers,
            ByteBufCodecs.VAR_INT, Data::level,
            ByteBufCodecs.VAR_INT, Data::xp,
            ByteBufCodecs.BOOL, Data::showProgress,
            ByteBufCodecs.VAR_LONG, Data::emeraldValue,
            ByteBufCodecs.VAR_LONG, Data::wallet,
            VALUES, Data::offerValues,
            ByteBufCodecs.BOOL, Data::caravan,
            Data::new);

        public Data {
            offerValues = List.copyOf(offerValues);
        }

        /** Money for one emerald in the offer at {@code index}. */
        public long emeraldValue(int index) {
            return index >= 0 && index < offerValues.size() ? offerValues.get(index) : emeraldValue;
        }
    }

    private final @Nullable AbstractVillager villager;
    private final Player viewer;
    private Data data;

    /** Client side, from the opening data. */
    public VillagerTradeMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null);
    }

    /** Server side, on the villager (or the caravan's trader). */
    public VillagerTradeMenu(int containerId, Inventory inventory, Data data, @Nullable AbstractVillager villager) {
        super(TraderyMenus.VILLAGER_TRADE.get(), containerId);
        this.villager = villager;
        this.viewer = inventory.player;
        this.data = data;
    }

    public Data data() {
        return data;
    }

    /** Fresh data from the server (client side). */
    public void update(Data data) {
        this.data = data;
    }

    public static int buttonId(int offer, boolean all) {
        return offer * 2 + (all ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || villager == null || !stillValid(player) || buttonId < 0) {
            return false;
        }
        if (!VillagerTrading.allow(serverPlayer)) {
            reply(serverPlayer, false, Messages.tr("tradery.vending.too_fast", "Slow down"));
            return true;
        }
        VillagerTrading.Outcome outcome = VillagerTrading.trade(serverPlayer, villager, buttonId / 2, buttonId % 2 == 1);
        Platform.get().sendToPlayer(serverPlayer, new TraderyPayloads.VillagerTradeSyncPayload(containerId, VillagerTrading.snapshot(villager)));
        reply(serverPlayer, outcome.success(), outcome.message());
        return true;
    }

    private void reply(ServerPlayer player, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(containerId, success, message));
    }

    /**
     * Vanilla sends inventory changes only through the open menu, and this one has no slots: without this the client
     * would see the costs taken and the goods given only after closing. The client applies inventory-menu updates
     * whatever screen is open.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (villager != null) {
            viewer.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    /** Vanilla's rule: this player is the one trading, the villager is alive and within reach. */
    @Override
    public boolean stillValid(Player player) {
        return villager == null
            || villager.getTradingPlayer() == player && villager.isAlive() && player.canInteractWithEntity(villager, 4.0);
    }

    /** The villager stops trading and drops this player's special prices, as when vanilla's window closes. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (villager != null && villager.getTradingPlayer() == player) {
            villager.setTradingPlayer(null);
        }
    }
}
