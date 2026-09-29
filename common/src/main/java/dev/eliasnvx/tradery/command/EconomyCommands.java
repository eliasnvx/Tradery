package dev.eliasnvx.tradery.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.economy.EconomyService;
import dev.eliasnvx.tradery.economy.HistoryData;
import dev.eliasnvx.tradery.economy.LedgerAccount;
import dev.eliasnvx.tradery.economy.StatsData;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Predicate;

import static dev.eliasnvx.tradery.command.Messages.tr;

/**
 * {@code /bal}, {@code /pay}, {@code /baltop}, {@code /eco} and {@code /tradery}. Amounts are typed in major units.
 */
public final class EconomyCommands {
    static final int PAGE_SIZE = 10;

    private EconomyCommands() {
    }

    private static Predicate<CommandSourceStack> allowed(TraderyPermission permission) {
        return source -> Platform.get().hasPermission(source, permission);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> bal = dispatcher.register(Commands.literal("bal")
            .requires(allowed(TraderyPermission.BALANCE))
            .executes(context -> balance(context, null))
            .then(CommandArgs.player("player")
                .requires(allowed(TraderyPermission.BALANCE_OTHERS))
                .executes(context -> balance(context, CommandArgs.account(context, "player")))));
        dispatcher.register(Commands.literal("balance").requires(allowed(TraderyPermission.BALANCE)).redirect(bal)
            .executes(context -> balance(context, null)));

        dispatcher.register(Commands.literal("pay")
            .requires(allowed(TraderyPermission.PAY))
            .then(CommandArgs.player("player")
                .then(CommandArgs.amount("amount")
                    .executes(EconomyCommands::pay))));

        dispatcher.register(Commands.literal("baltop")
            .requires(allowed(TraderyPermission.BALTOP))
            .executes(context -> baltop(context, 1))
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> baltop(context, IntegerArgumentType.getInteger(context, "page")))));

        dispatcher.register(Commands.literal("eco")
            .requires(allowed(TraderyPermission.ADMIN_ECO).or(allowed(TraderyPermission.ADMIN_STATS)))
            .then(adminAmount("give"))
            .then(adminAmount("take"))
            .then(adminAmount("set"))
            .then(Commands.literal("lock").requires(allowed(TraderyPermission.ADMIN_ECO))
                .then(CommandArgs.player("player").executes(context -> lock(context, true))))
            .then(Commands.literal("unlock").requires(allowed(TraderyPermission.ADMIN_ECO))
                .then(CommandArgs.player("player").executes(context -> lock(context, false))))
            .then(Commands.literal("stats").requires(allowed(TraderyPermission.ADMIN_STATS))
                .executes(EconomyCommands::stats)));

        dispatcher.register(traderyRoot());
    }

    /** {@code /tradery ...}; other features add their subcommands to the returned builder before registration. */
    static LiteralArgumentBuilder<CommandSourceStack> traderyRoot() {
        return Commands.literal("tradery")
            .then(Commands.literal("history").requires(allowed(TraderyPermission.HISTORY))
                .executes(context -> history(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> history(context, IntegerArgumentType.getInteger(context, "page")))))
            .then(Commands.literal("hud")
                .executes(EconomyCommands::toggleHud))
            .then(Commands.literal("reload").requires(allowed(TraderyPermission.ADMIN_RELOAD))
                .executes(EconomyCommands::reload));
    }

    // ------------------------------------------------------------------ player commands

    private static int balance(CommandContext<CommandSourceStack> context, LedgerAccount target) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        EconomyService economy = EconomyService.INSTANCE;
        if (target == null) {
            ServerPlayer player = source.getPlayerOrException();
            long balance = economy.account(player.getUUID()).balance(economy.defaultCurrency());
            source.sendSuccess(() -> tr("tradery.command.balance", "Balance: %s", Messages.money(balance)), false);
        } else {
            long balance = target.balance(economy.defaultCurrency());
            source.sendSuccess(() -> tr("tradery.command.balance_other", "%s's balance: %s",
                Messages.name(target.displayName()), Messages.money(balance)), false);
        }
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        ServerConfig.PaySection config = TraderyConfig.server().pay();
        EconomyService economy = EconomyService.INSTANCE;
        if (!config.enabled()) {
            source.sendFailure(tr("tradery.error.pay_disabled", "/pay is disabled on this server"));
            return 0;
        }
        LedgerAccount target = CommandArgs.account(context, "player");
        long amount = CommandArgs.amount(context, "amount");
        if (target.id().equals(AccountId.player(sender.getUUID()))) {
            source.sendFailure(tr("tradery.error.pay_self", "You can't pay yourself"));
            return 0;
        }
        long minimum = economy.toMinorOrMax(config.minAmount(), "pay.minAmount");
        if (amount <= 0 || amount < minimum) {
            source.sendFailure(tr("tradery.error.pay_minimum", "The smallest payment is %s", Messages.money(Math.max(minimum, 1))));
            return 0;
        }
        long tax = Money.percentOf(amount, config.taxPercent());
        LedgerAccount from = economy.account(sender.getUUID());
        TransactionResult result = economy.transfer(from, target, amount, tax, Reason.of(Reasons.PAY, target.displayName()));
        if (!(result instanceof TransactionResult.Success success)) {
            source.sendFailure(Messages.failure((TransactionResult.Failure) result));
            return 0;
        }
        long received = success.amount() - success.fee();
        if (success.fee() > 0) {
            source.sendSuccess(() -> tr("tradery.command.pay_sent_tax", "Sent %s to %s (tax %s)",
                Messages.money(received), Messages.name(target.displayName()), Messages.money(success.fee())), false);
        } else {
            source.sendSuccess(() -> tr("tradery.command.pay_sent", "Sent %s to %s",
                Messages.money(received), Messages.name(target.displayName())), false);
        }
        if (target.id() instanceof AccountId.Player player) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(player.uuid());
            if (online != null) {
                online.sendSystemMessage(tr("tradery.command.pay_received", "Received %s from %s",
                    Messages.money(received), Messages.name(sender.nameAndId().name())));
            }
        }
        return 1;
    }

    private static int baltop(CommandContext<CommandSourceStack> context, int page) {
        EconomyService economy = EconomyService.INSTANCE;
        List<LedgerAccount> top = economy.top(page * PAGE_SIZE);
        int pages = Math.max(1, (int) Math.ceil(top.size() / (double) PAGE_SIZE));
        int from = (page - 1) * PAGE_SIZE;
        CommandSourceStack source = context.getSource();
        if (from >= top.size()) {
            source.sendSuccess(() -> tr("tradery.command.baltop_empty", "Nobody here yet"), false);
            return 0;
        }
        source.sendSuccess(() -> tr("tradery.command.baltop_header", "Richest players (page %s)", page)
            .withStyle(ChatFormatting.YELLOW), false);
        for (int i = from; i < Math.min(top.size(), from + PAGE_SIZE); i++) {
            LedgerAccount account = top.get(i);
            int rank = i + 1;
            source.sendSuccess(() -> Component.literal("#" + rank + " ").withStyle(ChatFormatting.GRAY)
                .append(Messages.name(account.displayName()))
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Messages.money(account.balance(economy.defaultCurrency()))), false);
        }
        if (top.size() > from + PAGE_SIZE || pages > page) {
            source.sendSuccess(() -> tr("tradery.command.next_page", "Next page: /baltop %s", page + 1).withStyle(ChatFormatting.GRAY), false);
        }
        return top.size();
    }

    private static int history(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        List<HistoryData.Entry> entries = EconomyService.INSTANCE.history(player.getUUID());
        int from = (page - 1) * PAGE_SIZE;
        if (from >= entries.size()) {
            source.sendSuccess(() -> tr("tradery.command.history_empty", "No transactions yet"), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        source.sendSuccess(() -> tr("tradery.command.history_header", "Your transactions (page %s)", page)
            .withStyle(ChatFormatting.YELLOW), false);
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            HistoryData.Entry entry = entries.get(i);
            MutableComponent line = Messages.delta(entry.delta())
                .append(Component.literal("  ").append(Messages.reason(entry.reason()).withStyle(ChatFormatting.WHITE)));
            if (!entry.counterparty().isEmpty()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(Messages.name(entry.counterparty()));
            }
            line.append(Component.literal("  ").append(ago(now - entry.time()).withStyle(ChatFormatting.GRAY)));
            source.sendSuccess(() -> line, false);
        }
        if (entries.size() > from + PAGE_SIZE) {
            source.sendSuccess(() -> tr("tradery.command.history_next", "Next page: /tradery history %s", page + 1)
                .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    static MutableComponent ago(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0, millis));
        if (duration.toMinutes() < 1) {
            return tr("tradery.time.just_now", "just now");
        }
        if (duration.toHours() < 1) {
            return tr("tradery.time.minutes_ago", "%sm ago", duration.toMinutes());
        }
        if (duration.toDays() < 1) {
            return tr("tradery.time.hours_ago", "%sh ago", duration.toHours());
        }
        return tr("tradery.time.days_ago", "%sd ago", duration.toDays());
    }

    private static int toggleHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Platform.get().sendToPlayer(context.getSource().getPlayerOrException(), TraderyPayloads.HudTogglePayload.INSTANCE);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        EconomyService.INSTANCE.applyConfig(TraderyConfig.loadServer());
        context.getSource().sendSuccess(() -> tr("tradery.command.reloaded", "Tradery config reloaded (see the server log for problems)"), true);
        return 1;
    }

    // ------------------------------------------------------------------ admin

    private static LiteralArgumentBuilder<CommandSourceStack> adminAmount(String action) {
        return Commands.literal(action).requires(allowed(TraderyPermission.ADMIN_ECO))
            .then(CommandArgs.player("player")
                .then(CommandArgs.amount("amount")
                    .executes(context -> adminAmount(context, action))));
    }

    private static int adminAmount(CommandContext<CommandSourceStack> context, String action) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        LedgerAccount target = CommandArgs.account(context, "player");
        long amount = CommandArgs.amount(context, "amount");
        EconomyService economy = EconomyService.INSTANCE;
        String admin = source.getTextName();
        TransactionResult result = switch (action) {
            case "give" -> economy.deposit(target, amount, Reason.of(Reasons.ADMIN_GIVE, admin));
            case "take" -> economy.withdraw(target, amount, Reason.of(Reasons.ADMIN_TAKE, admin));
            default -> economy.setBalance(target, amount, Reason.of(Reasons.ADMIN_SET, admin));
        };
        if (result instanceof TransactionResult.Failure failure) {
            source.sendFailure(Messages.failure(failure));
            return 0;
        }
        long balance = target.balance(economy.defaultCurrency());
        source.sendSuccess(() -> tr("tradery.command.eco_" + action, "%s: %s, balance now %s",
            Messages.name(target.displayName()), Messages.money(amount), Messages.money(balance)), true);
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> context, boolean locked) throws CommandSyntaxException {
        LedgerAccount target = CommandArgs.account(context, "player");
        EconomyService.INSTANCE.setLocked(target, locked);
        context.getSource().sendSuccess(() -> locked
            ? tr("tradery.command.eco_lock", "%s's account is locked", Messages.name(target.displayName()))
            : tr("tradery.command.eco_unlock", "%s's account is unlocked", Messages.name(target.displayName())), true);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        EconomyService economy = EconomyService.INSTANCE;
        StatsData stats = economy.stats();
        CommandSourceStack source = context.getSource();
        StatsData.Day today = stats.day(StatsData.today());
        long weekEmitted = 0;
        long weekBurned = 0;
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < 7; i++) {
            StatsData.Day d = stats.day(day.minusDays(i).toString());
            weekEmitted += d.emitted();
            weekBurned += d.burned();
        }
        long onAccounts = economy.ledger().total(economy.defaultCurrency().id());
        long emitted7 = weekEmitted;
        long burned7 = weekBurned;
        source.sendSuccess(() -> tr("tradery.command.stats_header", "Tradery economy").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> tr("tradery.command.stats_supply", "Money supply: %s (accounts %s + coins in the world ~%s)",
            Messages.money(economy.moneySupply()), Messages.money(onAccounts), Messages.money(stats.cashOutstanding())), false);
        source.sendSuccess(() -> tr("tradery.command.stats_today", "Today: +%s created, -%s destroyed, %s transactions",
            Messages.money(today.emitted()), Messages.money(today.burned()), today.transactions()), false);
        source.sendSuccess(() -> tr("tradery.command.stats_cash", "Today: %s withdrawn as coins, %s deposited",
            Messages.money(today.cashOut()), Messages.money(today.cashIn())), false);
        source.sendSuccess(() -> tr("tradery.command.stats_week", "Last 7 days: +%s created, -%s destroyed",
            Messages.money(emitted7), Messages.money(burned7)), false);
        source.sendSuccess(() -> tr("tradery.command.stats_accounts", "Accounts: %s", economy.ledger().size() - 1), false);
        return 1;
    }
}
