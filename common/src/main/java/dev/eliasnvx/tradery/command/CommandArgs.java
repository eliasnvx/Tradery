package dev.eliasnvx.tradery.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Arguments shared by the economy commands: a known player (online or offline) and a money amount. */
final class CommandArgs {
    static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(name ->
        Messages.tr("tradery.error.unknown_player", "No account for player %s", name));
    static final DynamicCommandExceptionType BAD_AMOUNT = new DynamicCommandExceptionType(text ->
        Messages.tr("tradery.error.bad_amount", "'%s' is not a valid amount", text));

    private CommandArgs() {
    }

    /** A player name; suggests online players and players the economy has seen. */
    static RequiredArgumentBuilder<CommandSourceStack, String> player(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            Set<String> names = new HashSet<>(context.getSource().getOnlinePlayerNames());
            if (EconomyService.INSTANCE.isReady()) {
                names.addAll(EconomyService.INSTANCE.ledger().knownPlayerNames());
            }
            return SharedSuggestionProvider.suggest(names, builder);
        });
    }

    /** An amount in major units ("12", "12.50"). */
    static RequiredArgumentBuilder<CommandSourceStack, String> amount(String name) {
        return Commands.argument(name, StringArgumentType.word());
    }

    /** Resolves a player argument to an existing account: online name first, then the last names the economy saw. */
    static LedgerAccount account(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, argument);
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(name);
        EconomyService economy = EconomyService.INSTANCE;
        if (online != null) {
            return economy.account(online.getUUID());
        }
        UUID uuid = economy.ledger().playerByName(name).orElseThrow(() -> UNKNOWN_PLAYER.create(name));
        return economy.account(uuid);
    }

    static long amount(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, argument);
        return EconomyService.INSTANCE.defaultCurrency().parse(text).orElseThrow(() -> BAD_AMOUNT.create(text));
    }
}
