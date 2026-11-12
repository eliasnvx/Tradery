package dev.eliasnvx.tradery.compat;

import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.vending.StackMath;
import dev.eliasnvx.tradery.vending.VendingBlockEntity;
import dev.eliasnvx.tradery.vending.VendingSettings;
import dev.eliasnvx.tradery.vending.VendingTrades;
import dev.eliasnvx.tradery.village.VillageBuildings;
import dev.eliasnvx.tradery.village.VillageCouncil;
import dev.eliasnvx.tradery.village.VillageSpecialization;
import dev.eliasnvx.tradery.village.VillageState;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

/**
 * The server side of the look-at tooltips (Jade, WTHIT): what clients don't have, computed on the server and sent
 * with the tooltip. {@link TooltipLines} turns it into lines on the client.
 */
public final class TooltipData {
    private TooltipData() {
    }

    /** Server: trades in stock, or room for buyback; the stock isn't synced to clients. */
    public static int vendingStock(VendingBlockEntity vendor) {
        VendingSettings settings = vendor.settings();
        if (settings.isBuyback() && !vendor.admin().infiniteStock()) {
            return (int) Math.min(VendingTrades.UNLIMITED, StackMath.space(vendor.stock().getItems(), settings.goods()) / settings.perTrade());
        }
        return vendor.tradesInStock();
    }

    /**
     * What a bell's tooltip shows of its village; {@code known} false: not a village Tradery knows (yet).
     *
     * @param specialization the {@link VillageSpecialization} id in effect, or empty
     * @param patron         the patron's name, or empty
     */
    public record Bell(boolean known, Component name, int level, int prosperity, long treasury, Component saving, long savings, long savingsNeed,
                       String specialization, String patron) {
        public static final Bell NONE = new Bell(false, Component.empty(), 0, 0, 0, Component.empty(), 0, 0, "", "");
        public static final StreamCodec<RegistryFriendlyByteBuf, Bell> STREAM_CODEC = dev.eliasnvx.tradery.network.PortCodecs.composite(
            ByteBufCodecs.BOOL, Bell::known,
            ComponentSerialization.TRUSTED_STREAM_CODEC, Bell::name,
            ByteBufCodecs.VAR_INT, Bell::level,
            ByteBufCodecs.VAR_INT, Bell::prosperity,
            ByteBufCodecs.VAR_LONG, Bell::treasury,
            ComponentSerialization.TRUSTED_STREAM_CODEC, Bell::saving,
            ByteBufCodecs.VAR_LONG, Bell::savings,
            ByteBufCodecs.VAR_LONG, Bell::savingsNeed,
            ByteBufCodecs.stringUtf8(64), Bell::specialization,
            ByteBufCodecs.stringUtf8(64), Bell::patron,
            Bell::new);
    }

    /** Server: the village of the bell at {@code pos}, if Tradery knows it (looking never creates one). */
    public static Bell bell(ServerLevel level, BlockPos pos) {
        VillageState village = Villages.byKey(level.getServer(), Villages.key(level.dimension(), pos));
        if (village == null) {
            return Bell.NONE;
        }
        VillageBuildings.Building next = VillageBuildings.next(village);
        return new Bell(true, Villages.displayName(village), Villages.level(village),
            (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.floor(village.prosperity()))), village.treasury(),
            next == null ? Component.empty() : VillageBuildings.name(next.id()), village.savings(),
            next == null ? 0 : VillageBuildings.costMinor(next),
            VillageCouncil.active(village).map(VillageSpecialization::id).orElse(""),
            TraderyPayloads.clip(VillageCouncil.patronName(village), 64));
    }

}
