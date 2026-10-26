package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.CounterOffer;
import dev.eliasnvx.tradery.vending.CounterSettings;
import dev.eliasnvx.tradery.vending.MarketCounterBlockEntity;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The owner's view of a market counter: the shared stock (27 slots) like a chest on the left, the offers on the right
 * (a goods sample per offer and its money price) and the villagers toggle. Samples and the toggle change a draft;
 * "Save" sends the prices ({@code CounterSavePayload}) and the server validates and applies the whole draft.
 */
public class CounterOwnerMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int STOCK_START = 0;
    public static final int SAMPLE_START = MarketCounterBlockEntity.STOCK_SIZE;
    public static final int INVENTORY_START = SAMPLE_START + CounterSettings.MAX_OFFERS;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    /** Count buttons: {@code offer * STEPS + step}; steps -1, +1, -8, +8. */
    public static final int STEPS = 4;
    public static final int MINUS = 0;
    public static final int PLUS = 1;
    public static final int MINUS_8 = 2;
    public static final int PLUS_8 = 3;
    public static final int TOGGLE_VILLAGERS = 100;

    /** Chest-grid layout shared with the screen. */
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 18;
    public static final int INVENTORY_TOP = GRID_TOP + 3 * 18 + 13;
    public static final int CHEST_HEIGHT = INVENTORY_TOP + 3 * 18 + 4 + 18 + 7;
    public static final int PANEL_LEFT = 176;
    public static final int SAMPLE_X = PANEL_LEFT + 9;
    public static final int OFFER_TOP = 19;
    public static final int OFFER_ROW = 22;

    /**
     * Opening data.
     *
     * @param pos        the counter
     * @param settings   its saved offers (the draft starts from them)
     * @param adminMode  opened with the vendor key by an admin
     * @param feePercent vending fee for the info line
     */
    public record Data(BlockPos pos, CounterSettings settings, boolean adminMode, String feePercent) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Data::pos,
            CounterSettings.STREAM_CODEC, Data::settings,
            ByteBufCodecs.BOOL, Data::adminMode,
            ByteBufCodecs.stringUtf8(16), Data::feePercent,
            Data::new);
    }

    private final @Nullable MarketCounterBlockEntity counter;
    private final Data data;
    private final SimpleContainer samples = new SimpleContainer(CounterSettings.MAX_OFFERS);
    private final DataSlot villagers = DataSlot.standalone();

    /** Client side, from the opening data. */
    public CounterOwnerMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null, new SimpleContainer(MarketCounterBlockEntity.STOCK_SIZE));
    }

    /** Server side, on the real block entity. */
    public CounterOwnerMenu(int containerId, Inventory inventory, Data data, MarketCounterBlockEntity counter) {
        this(containerId, inventory, data, counter, counter.stock());
    }

    private CounterOwnerMenu(int containerId, Inventory inventory, Data data, @Nullable MarketCounterBlockEntity counter, Container stock) {
        super(TraderyMenus.COUNTER_OWNER.get(), containerId);
        this.counter = counter;
        this.data = data;
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            samples.setItem(i, data.settings().offer(i).goods());
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new StockSlot(stock, col + row * 9, GRID_LEFT + col * 18, GRID_TOP + row * 18));
            }
        }
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            addSlot(new GhostSlot(samples, i, SAMPLE_X, sampleY(i), VendingConfigurator::isTradeable));
        }
        PlayerInventorySlots.add(this::addSlot, inventory, GRID_LEFT, INVENTORY_TOP);
        villagers.set(data.settings().villagers() ? 1 : 0);
        addDataSlot(villagers);
    }

    /** Item y of offer {@code index}'s sample slot. */
    public static int sampleY(int index) {
        return OFFER_TOP + index * OFFER_ROW + 2;
    }

    public static int buttonId(int offer, int step) {
        return offer * STEPS + step;
    }

    // ------------------------------------------------------------------ draft (both sides)

    public Data data() {
        return data;
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    /** The goods sample of offer {@code index} (its count is the amount per trade). */
    public ItemStack sample(int index) {
        return samples.getItem(index);
    }

    /** "Villagers can buy" in the draft. */
    public boolean villagers() {
        return villagers.get() != 0;
    }

    public boolean adminMode() {
        return data.adminMode();
    }

    /** The draft with these money prices (one per offer; missing ones are 0). */
    public CounterSettings draft(List<Long> prices) {
        List<CounterOffer> offers = new ArrayList<>(CounterSettings.MAX_OFFERS);
        for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
            ItemStack goods = sample(i);
            long price = i < prices.size() ? prices.get(i) : 0;
            offers.add(goods.isEmpty() ? CounterOffer.EMPTY : new CounterOffer(goods, price));
        }
        return new CounterSettings(offers, villagers());
    }

    public @Nullable MarketCounterBlockEntity counter() {
        return counter;
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void clicked(int slotIndex, int buttonNum, ClickType containerInput, Player player) {
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex) instanceof GhostSlot ghost) {
            GhostSlots.click(this, ghost, buttonNum, containerInput, player.level().registryAccess());
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer) || counter == null || !stillValid(player)) {
            return false;
        }
        if (buttonId == TOGGLE_VILLAGERS) {
            villagers.set(villagers.get() == 0 ? 1 : 0);
            return true;
        }
        if (buttonId < 0 || buttonId >= CounterSettings.MAX_OFFERS * STEPS) {
            return false;
        }
        GhostSlot slot = (GhostSlot) slots.get(SAMPLE_START + buttonId / STEPS);
        int delta = switch (buttonId % STEPS) {
            case MINUS -> -1;
            case PLUS -> 1;
            case MINUS_8 -> -8;
            default -> 8;
        };
        GhostSlots.adjust(slot, delta);
        return true;
    }

    // ------------------------------------------------------------------ vanilla rules

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot instanceof GhostSlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < SAMPLE_START) {
            // Stock → player inventory
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, STOCK_START, SAMPLE_START, false)) {
            // Player inventory → stock (StockSlot.mayPlace only takes the offers' goods)
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (counter == null) {
            return true;
        }
        return counter.isWithinReach(player) && player instanceof ServerPlayer serverPlayer
            && (counter.isOwner(serverPlayer) || (data.adminMode() && VendingProtection.isAdmin(serverPlayer)));
    }

    /** Stock slots take only goods of the draft's offers; anything already there can still be taken out. */
    private final class StockSlot extends Slot {
        StockSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            for (int i = 0; i < CounterSettings.MAX_OFFERS; i++) {
                if (StackMath.matches(stack, sample(i))) {
                    return true;
                }
            }
            return false;
        }
    }
}
