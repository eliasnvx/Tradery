package dev.eliasnvx.tradery.compat.jade;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.client.ClientEconomy;
import dev.eliasnvx.tradery.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tooltip lines of a bell: the village's name, level, treasury and what it is saving for. */
enum BellJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Tradery.id("bell_village");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BellJadeData.INSTANCE.decodeFromData(accessor).ifPresent(info -> {
            if (!info.known()) {
                return;
            }
            ClientEconomy.CurrencyView currency = ClientEconomy.currency();
            tooltip.add(info.name().copy().withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tradery.jade.village_level", Villages.levelName(info.level()), info.prosperity()));
            tooltip.add(Component.translatable("tradery.board.treasury", money(currency, info.treasury())).withStyle(ChatFormatting.GRAY));
            if (!info.saving().getString().isEmpty()) {
                tooltip.add(Component.translatable("tradery.board.saving", info.saving(), money(currency, info.savings()),
                    money(currency, info.savingsNeed())).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private static Component money(ClientEconomy.CurrencyView currency, long amount) {
        return currency != null ? currency.money(amount) : Component.literal(String.valueOf(amount));
    }
}
