package dev.eliasnvx.tradery.vending;

import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.event.VendingConfiguredEvent;
import dev.eliasnvx.tradery.api.vending.PriceMode;
import dev.eliasnvx.tradery.command.Messages;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.menu.VendingOwnerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.registry.TraderyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Validates and applies what the owner (or an admin) sets in the owner menu. Server side. */
public final class VendingConfigurator {
    private VendingConfigurator() {
    }

    /** Items that may be sold, shown or used as a price (not blacklisted, not our own vendor key). */
    public static boolean isTradeable(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof VendorKeyItem) {
            return false;
        }
        return !TraderyConfig.server().vending().itemBlacklist().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Full, plain blocks only: no block entities, no special renderers, not blacklisted, not a vending block. */
    public static boolean isFacadeCandidate(ItemStack stack) {
        return facadeFor(stack) != null;
    }

    public static @Nullable BlockState facadeFor(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        if (block == TraderyBlocks.VENDING_BLOCK.get() || state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL
            || !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            || TraderyConfig.server().vending().facadeBlacklist().contains(BuiltInRegistries.BLOCK.getKey(block))) {
            return null;
        }
        return state;
    }

    /** "Save" in the owner menu. Replies to the player with the result. */
    public static void save(ServerPlayer player, VendingOwnerMenu menu, long requestedPrice) {
        VendingBlockEntity vendor = menu.vendor();
        if (vendor == null || !menu.stillValid(player)) {
            return;
        }
        long maxPrice = Math.max(0, EconomyService.INSTANCE.maxBalance());
        long price = Math.max(0, maxPrice > 0 ? Math.min(requestedPrice, maxPrice) : requestedPrice);
        VendingSettings draft = menu.draft(price);
        Component problem = validate(draft);
        ItemStack facadeSample = menu.facadeSample();
        BlockState facade = facadeSample.isEmpty() ? null : facadeFor(facadeSample);
        if (problem == null && !facadeSample.isEmpty() && facade == null) {
            problem = Messages.tr("tradery.vending.bad_facade", "This block can't be a facade");
        }
        if (problem != null) {
            reply(player, menu, false, problem);
            return;
        }
        VendingConfiguredEvent event = VendingConfiguredEvent.EVENT.post(new VendingConfiguredEvent(player, (ServerLevel) vendor.getLevel(),
            vendor.getBlockPos(), draft.goods(), draft.priceMode(), draft.priceItem(),
            draft.priceMode() == PriceMode.CURRENCY ? draft.price() : draft.pricePerTrade(), draft.isBuyback()));
        if (event.isCancelled()) {
            reply(player, menu, false, event.cancelMessage() != null ? event.cancelMessage()
                : Messages.tr("tradery.vending.config_denied", "These settings aren't allowed here"));
            return;
        }
        VendingSettings applied = draft.priceMode() == PriceMode.CURRENCY
            ? draft.withPrice(event.price())
            : draft.withPriceItem(draft.priceItem().copyWithCount((int) Math.max(1, Math.min(event.price(), draft.priceItem().getMaxStackSize()))));
        boolean offerChanged = !applied.sameAs(vendor.settings());
        vendor.setSettings(applied);
        vendor.setFacade(facade);
        if (offerChanged) {
            // Buyers must never pay a price they didn't see: close their screens
            VendingMenus.closeBuyers((ServerLevel) vendor.getLevel(), vendor.getBlockPos());
        }
        reply(player, menu, true, Messages.tr("tradery.vending.saved", "Saved"));
    }

    private static @Nullable Component validate(VendingSettings draft) {
        if (draft.goods().isEmpty()) {
            return Messages.tr("tradery.vending.no_goods", "Put the goods sample in the goods slot");
        }
        if (!isTradeable(draft.goods())) {
            return Messages.tr("tradery.vending.blacklisted", "%s can't be traded here", draft.goods().getHoverName());
        }
        if (draft.priceMode() == PriceMode.ITEM) {
            if (draft.priceItem().isEmpty()) {
                return Messages.tr("tradery.vending.no_price_item", "Put the price item in the price slot");
            }
            if (!isTradeable(draft.priceItem())) {
                return Messages.tr("tradery.vending.blacklisted", "%s can't be traded here", draft.priceItem().getHoverName());
            }
        }
        return null;
    }

    /** Admin toggles (vendor key): apply at once. Returns false without the permission. */
    public static boolean applyAdminToggle(ServerPlayer player, VendingBlockEntity vendor, int buttonId) {
        if (!VendingProtection.isAdmin(player)) {
            return false;
        }
        AdminFlags flags = vendor.admin();
        switch (buttonId) {
            case VendingOwnerMenu.ADMIN_INFINITE -> vendor.setAdmin(new AdminFlags(!flags.infiniteStock(), flags.burnPayment(), flags.noFee()));
            case VendingOwnerMenu.ADMIN_BURN -> vendor.setAdmin(new AdminFlags(flags.infiniteStock(), !flags.burnPayment(), flags.noFee()));
            case VendingOwnerMenu.ADMIN_NO_FEE -> vendor.setAdmin(new AdminFlags(flags.infiniteStock(), flags.burnPayment(), !flags.noFee()));
            case VendingOwnerMenu.ADMIN_SERVER_OWNER -> {
                ServerLevel level = (ServerLevel) vendor.getLevel();
                AccountId newOwner = vendor.owner() instanceof AccountId.System
                    ? AccountId.player(player.getUUID())
                    : AccountId.system(EconomyService.SERVER_ACCOUNT);
                vendor.setOwner(newOwner, newOwner instanceof AccountId.System ? "Server" : player.nameAndId().name());
                VendorsData.get(level.getServer()).setOwner(level, vendor.getBlockPos(), newOwner);
            }
            default -> {
                return false;
            }
        }
        VendingMenus.closeBuyers((ServerLevel) vendor.getLevel(), vendor.getBlockPos());
        return true;
    }

    private static void reply(ServerPlayer player, VendingOwnerMenu menu, boolean success, Component message) {
        Platform.get().sendToPlayer(player, new TraderyPayloads.VendingResultPayload(menu.containerId, success, message));
    }

    /** Fee text for the owner screen ("2%"). */
    public static String feeText() {
        ServerConfig.VendingSection vending = TraderyConfig.server().vending();
        return vending.feePercent().stripTrailingZeros().toPlainString() + "%";
    }
}
