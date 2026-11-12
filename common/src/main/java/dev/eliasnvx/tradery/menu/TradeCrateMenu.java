package dev.eliasnvx.tradery.menu;

import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyMenus;
import dev.eliasnvx.tradery.vending.TradeCrateBlockEntity;
import dev.eliasnvx.tradery.vending.VendingConfigurator;
import dev.eliasnvx.tradery.vending.VendingProtection;
import dev.eliasnvx.tradery.village.TradeCrates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The owner's view of a trade crate: three wanted-item samples, the payment store and the received store (9 slots
 * each), and the player's inventory. Samples apply at once (the server sets them on the crate itself); the received
 * store only gives items out.
 */
public class TradeCrateMenu extends AbstractContainerMenu implements VendingMenu {
    public static final int WANT_START = 0;
    public static final int PAYMENT_START = TradeCrateBlockEntity.WANTS;
    public static final int RECEIVED_START = PAYMENT_START + TradeCrateBlockEntity.STORE_SIZE;
    public static final int INVENTORY_START = RECEIVED_START + TradeCrateBlockEntity.STORE_SIZE;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    /** Layout shared with the screen. */
    public static final int GRID_LEFT = 8;
    public static final int WANT_X = 62;
    public static final int WANT_Y = 18;
    public static final int PAYMENT_Y = 50;
    public static final int RECEIVED_Y = 82;
    public static final int INVENTORY_TOP = 114;
    public static final int HEIGHT = INVENTORY_TOP + 3 * 18 + 4 + 18 + 7;

    /**
     * Opening data.
     *
     * @param pos             the crate
     * @param wants           its wanted-item samples, {@link TradeCrateBlockEntity#WANTS} of them (empty ones too)
     * @param adminMode       opened with the vendor key by an admin
     * @param emeraldsAsMoney emeralds in villagers' offers are paid in money ({@code villagerTrading.enabled})
     */
    public record Data(BlockPos pos, List<ItemStack> wants, boolean adminMode, boolean emeraldsAsMoney) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Data::pos,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(TradeCrateBlockEntity.WANTS)), Data::wants,
            ByteBufCodecs.BOOL, Data::adminMode,
            ByteBufCodecs.BOOL, Data::emeraldsAsMoney,
            Data::new);
    }

    private final @Nullable TradeCrateBlockEntity crate;
    private final Data data;

    /** Client side, from the opening data. */
    public TradeCrateMenu(int containerId, Inventory inventory, Data data) {
        this(containerId, inventory, data, null, clientWants(data), new SimpleContainer(TradeCrateBlockEntity.SIZE));
    }

    /** Server side, on the real block entity. */
    public TradeCrateMenu(int containerId, Inventory inventory, Data data, TradeCrateBlockEntity crate) {
        this(containerId, inventory, data, crate, crate.wants(), crate);
    }

    private TradeCrateMenu(int containerId, Inventory inventory, Data data, @Nullable TradeCrateBlockEntity crate, Container wants,
                           Container stores) {
        super(TraderyMenus.TRADE_CRATE.get(), containerId);
        this.crate = crate;
        this.data = data;
        for (int i = 0; i < TradeCrateBlockEntity.WANTS; i++) {
            addSlot(new GhostSlot(wants, i, WANT_X + i * 18, WANT_Y, VendingConfigurator::isTradeable));
        }
        for (int col = 0; col < TradeCrateBlockEntity.STORE_SIZE; col++) {
            addSlot(new Slot(stores, col, GRID_LEFT + col * 18, PAYMENT_Y));
        }
        for (int col = 0; col < TradeCrateBlockEntity.STORE_SIZE; col++) {
            addSlot(new ReceivedSlot(stores, TradeCrateBlockEntity.RECEIVED_START + col, GRID_LEFT + col * 18, RECEIVED_Y));
        }
        addStandardInventorySlots(inventory, GRID_LEFT, INVENTORY_TOP);
    }

    private static Container clientWants(Data data) {
        SimpleContainer wants = new SimpleContainer(TradeCrateBlockEntity.WANTS);
        for (int i = 0; i < Math.min(TradeCrateBlockEntity.WANTS, data.wants().size()); i++) {
            wants.setItem(i, data.wants().get(i));
        }
        return wants;
    }

    /** Opens the owner's window ({@code adminMode}: an admin with the vendor key). */
    public static void open(ServerPlayer player, TradeCrateBlockEntity crate, boolean adminMode) {
        List<ItemStack> wants = new ArrayList<>(TradeCrateBlockEntity.WANTS);
        for (int i = 0; i < TradeCrateBlockEntity.WANTS; i++) {
            wants.add(crate.wants().getItem(i).copy());
        }
        Data data = new Data(crate.getBlockPos(), wants, adminMode, TradeCrates.emeraldsAsMoney());
        Component title = crate.ownerName().isEmpty()
            ? Messages.tr("block.tradery.trade_crate", "Trade Crate")
            : Messages.tr("tradery.crate.title", "%s's Trade Crate", crate.ownerName());
        Platform.get().openMenu(player, new SimpleMenuProvider((id, inventory, opener) -> new TradeCrateMenu(id, inventory, data, crate), title),
            Data.STREAM_CODEC, data);
    }

    public Data data() {
        return data;
    }

    @Override
    public BlockPos pos() {
        return data.pos();
    }

    /** The wanted-item sample in slot {@code index}, as this side sees it. */
    public ItemStack want(int index) {
        return slots.get(WANT_START + index).getItem();
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex) instanceof GhostSlot ghost) {
            GhostSlots.click(this, ghost, buttonNum, containerInput, player.level().registryAccess());
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot instanceof GhostSlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < INVENTORY_START) {
            // Either store → player inventory
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PAYMENT_START, RECEIVED_START, false)) {
            // Player inventory → payment
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
        return !(slot instanceof GhostSlot) && !(slot instanceof ReceivedSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (crate == null) {
            return true;
        }
        return crate.isWithinReach(player) && player instanceof ServerPlayer serverPlayer
            && (crate.isOwner(serverPlayer) || (data.adminMode() && VendingProtection.isAdmin(serverPlayer)));
    }

    /** What villagers left: take only. */
    private static final class ReceivedSlot extends Slot {
        ReceivedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
