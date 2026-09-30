package dev.eliasnvx.tradery.ore;

import dev.eliasnvx.tradery.api.event.CoinOreMinedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Coin ore. Its loot table decides how many coins it gives (data packs may change it, e.g. add Fortune); this
 * block decides where the money goes: straight to the miner's balance (default), or as coin items when no player
 * mined it (explosions, machines) or {@code ore.directToBalance} is off. Every drop path goes through
 * {@link #getDrops}, so no path can skip the rules.
 */
public class CoinOreBlock extends Block {
    private final CoinTier tier;

    public CoinOreBlock(CoinTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CoinTier tier() {
        return tier;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        long value = 0;
        List<ItemStack> other = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (Coins.isCoin(stack)) {
                value += Coins.value(stack);
            } else {
                other.add(stack); // silk touch gives the ore block itself
            }
        }
        if (value <= 0) {
            return drops;
        }
        ServerLevel level = params.getLevel();
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        BlockPos pos = origin != null ? BlockPos.containing(origin) : BlockPos.ZERO;
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        ServerPlayer player = breaker instanceof ServerPlayer serverPlayer && CoinMining.paysToBalance(serverPlayer) ? serverPlayer : null;

        CoinOreMinedEvent event = CoinOreMinedEvent.EVENT.post(new CoinOreMinedEvent(breaker instanceof ServerPlayer sp ? sp : null, level, pos, state,
            CoinMining.damp(value, level.getRandom()), player != null ? CoinOreMinedEvent.Payout.BALANCE : CoinOreMinedEvent.Payout.ITEMS));
        if (event.isCancelled() || event.amount() <= 0) {
            return other;
        }
        if (event.payout() == CoinOreMinedEvent.Payout.BALANCE && event.player() != null) {
            CoinMining.payToBalance(event.player(), event.amount());
            return other;
        }
        other.addAll(CoinMining.mintCoins(event.amount(), level.getRandom()));
        return other;
    }
}
