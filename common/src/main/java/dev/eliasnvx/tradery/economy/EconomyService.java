package dev.eliasnvx.tradery.economy;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.api.Account;
import dev.eliasnvx.tradery.api.AccountId;
import dev.eliasnvx.tradery.api.Currency;
import dev.eliasnvx.tradery.api.FailReason;
import dev.eliasnvx.tradery.api.Reason;
import dev.eliasnvx.tradery.api.Reasons;
import dev.eliasnvx.tradery.api.TraderyApi;
import dev.eliasnvx.tradery.api.TraderyEconomy;
import dev.eliasnvx.tradery.api.TransactionResult;
import dev.eliasnvx.tradery.api.event.AccountCreatedEvent;
import dev.eliasnvx.tradery.api.event.BalanceChangedEvent;
import dev.eliasnvx.tradery.api.event.TransactionEvent;
import dev.eliasnvx.tradery.config.ServerConfig;
import dev.eliasnvx.tradery.config.TraderyConfig;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import dev.eliasnvx.tradery.platform.Platform;
import dev.eliasnvx.tradery.util.CodecSavedData;
import dev.eliasnvx.tradery.util.Money;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The economy: implements the public API on top of {@link Ledger}. Bound to one running server at a time
 * (singleplayer switches worlds without restarting the game).
 *
 * <p>Every transaction: validate → {@link TransactionEvent.Pre} → {@link Ledger#move} (all checks, then both
 * sides at once) → mark saved data dirty → statistics, history, log, client sync → post events. Nothing after the
 * ledger move can undo it.
 */
public final class EconomyService implements TraderyEconomy {
    public static final EconomyService INSTANCE = new EconomyService();
    public static final ResourceLocation SERVER_ACCOUNT = TraderyApi.id("server");
    /** {@code <world>/tradery}: the world folder's own directory for Tradery files (logs). */
    private static final String TRADERY_DIR = "tradery";

    private @Nullable MinecraftServer server;
    private @Nullable AccountsData accounts;
    private @Nullable HistoryData history;
    private @Nullable StatsData stats;
    private @Nullable TransactionLog log;
    private volatile SimpleCurrency currency = currencyOf(ServerConfig.DEFAULT, ServerConfig.DEFAULT.currency().decimals());
    private long maxBalance;
    private long startingBalance;

    private EconomyService() {
    }

    // ------------------------------------------------------------------ lifecycle

    /** Binds to a server that finished loading its levels. */
    public void start(MinecraftServer server) {
        this.server = server;
        this.accounts = CodecSavedData.get(server, AccountsData.FACTORY, AccountsData.NAME);
        this.history = CodecSavedData.get(server, HistoryData.FACTORY, HistoryData.NAME);
        this.stats = CodecSavedData.get(server, StatsData.FACTORY, StatsData.NAME);
        applyConfig(TraderyConfig.loadServer());
        Ledger ledger = accounts.ledger();
        if (ledger.get(AccountId.system(SERVER_ACCOUNT)).isEmpty()) {
            ledger.create(AccountId.system(SERVER_ACCOUNT), "Server", true);
            accounts.setDirty();
        }
        JsonObject marker = new JsonObject();
        marker.addProperty("event", "server_start");
        marker.addProperty("accounts", ledger.size());
        marker.addProperty("supply", ledger.total(currency.id()));
        if (log != null) {
            log.write(marker);
        }
        Tradery.LOGGER.info("Economy ready: {} accounts, currency {} ({} decimals)", ledger.size(), currency.id(), currency.decimals());
    }

    /** Unbinds when the server stops. Saved data is written by the server itself. */
    public void stop() {
        if (log != null) {
            log.close();
            log = null;
        }
        server = null;
        accounts = null;
        history = null;
        stats = null;
    }

    /** Applies a (re)loaded server config. The world's decimals never change. */
    public void applyConfig(ServerConfig config) {
        AccountsData data = accounts;
        int decimals = config.currency().decimals();
        if (data != null) {
            if (data.decimals() == AccountsData.UNSET) {
                data.setDecimals(decimals);
            } else if (data.decimals() != decimals) {
                Tradery.LOGGER.warn("server.json5 asks for currency.decimals = {}, but this world was created with {}. "
                    + "Amounts are stored in minor units, so the world keeps {}.", decimals, data.decimals(), data.decimals());
                decimals = data.decimals();
            }
        }
        currency = currencyOf(config, decimals);
        maxBalance = toMinorOrMax(config.economy().maxBalance(), "economy.maxBalance");
        startingBalance = toMinorOrMax(config.economy().startingBalance(), "economy.startingBalance");
        if (server != null) {
            if (log != null) {
                log.close();
                log = null;
            }
            if (config.log().enabled()) {
                log = new TransactionLog(server.getWorldPath(LevelResource.ROOT).resolve(TRADERY_DIR).resolve("logs").normalize(),
                    config.log().retentionDays());
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendCurrency(player);
                syncBalance(player);
            }
        }
    }

    private static SimpleCurrency currencyOf(ServerConfig config, int decimals) {
        ServerConfig.CurrencySection section = config.currency();
        return new SimpleCurrency(section.id(), section.name(), section.symbol(), decimals, section.thousandsSeparator());
    }

    /** Config money in minor units; values too large for a {@code long} become {@link Long#MAX_VALUE}. */
    public long toMinorOrMax(BigDecimal major, String key) {
        if (Money.losesPrecision(major, currency.decimals())) {
            Tradery.LOGGER.warn("{} = {} has more decimals than the currency ({}); the extra digits are dropped",
                key, major.toPlainString(), currency.decimals());
        }
        try {
            return currency.toMinor(major);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public boolean isReady() {
        MinecraftServer current = server;
        return current != null && accounts != null && current.isSameThread();
    }

    private void checkReady() {
        if (server == null || accounts == null) {
            throw new IllegalStateException("Tradery economy is not available: no server is running");
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Tradery economy must be used on the server thread, not " + Thread.currentThread().getName());
        }
    }

    public MinecraftServer server() {
        checkReady();
        return server;
    }

    // ------------------------------------------------------------------ accounts

    @Override
    public SimpleCurrency defaultCurrency() {
        return currency;
    }

    @Override
    public Optional<Currency> currency(ResourceLocation id) {
        SimpleCurrency current = currency;
        return current.id().equals(id) ? Optional.of(current) : Optional.empty();
    }

    public Ledger ledger() {
        checkReady();
        return accounts.ledger();
    }

    @Override
    public LedgerAccount account(UUID player) {
        checkReady();
        Optional<LedgerAccount> existing = accounts.ledger().get(AccountId.player(player));
        if (existing.isPresent()) {
            return existing.get();
        }
        GameProfileCache profiles = server.getProfileCache();
        String name = profiles == null ? "" : profiles.get(player).map(GameProfile::getName).orElse("");
        return createAccount(AccountId.player(player), name, false, startingBalance);
    }

    @Override
    public Optional<Account> account(AccountId id) {
        checkReady();
        return accounts.ledger().get(id).map(Account.class::cast);
    }

    @Override
    public LedgerAccount systemAccount(ResourceLocation id) {
        checkReady();
        AccountId accountId = AccountId.system(id);
        return accounts.ledger().get(accountId).orElseGet(() -> createAccount(accountId, "", false, 0));
    }

    @Override
    public LedgerAccount serverAccount() {
        checkReady();
        return accounts.ledger().get(AccountId.system(SERVER_ACCOUNT)).orElseThrow();
    }

    private LedgerAccount createAccount(AccountId id, String name, boolean infinite, long starting) {
        AccountCreatedEvent event = AccountCreatedEvent.EVENT.post(new AccountCreatedEvent(id, currency, starting));
        LedgerAccount account = accounts.ledger().create(id, name, infinite);
        accounts.setDirty();
        if (event.startingBalance() > 0) {
            execute(TransactionEvent.Type.DEPOSIT, null, account, event.startingBalance(), 0, Reason.of(Reasons.STARTING_BALANCE));
        }
        return account;
    }

    /** Resolves any {@link Account} (possibly from another mod or an earlier session) to this world's account. */
    private LedgerAccount resolve(Account account) {
        if (account instanceof LedgerAccount ledgerAccount && accounts.ledger().get(ledgerAccount.id()).orElse(null) == ledgerAccount) {
            return ledgerAccount;
        }
        if (account.id() instanceof AccountId.Player player) {
            return account(player.uuid());
        }
        return systemAccount(((AccountId.System) account.id()).id());
    }

    /** Creates or refreshes a player's account on join and sends the client what the HUD needs. */
    public void onPlayerJoin(ServerPlayer player) {
        checkReady();
        Ledger ledger = accounts.ledger();
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        if (ledger.get(AccountId.player(uuid)).isEmpty()) {
            createAccount(AccountId.player(uuid), name, false, startingBalance);
        } else {
            ledger.rename(uuid, name);
            accounts.setDirty();
        }
        sendCurrency(player);
        syncBalance(player);
    }

    public void sendCurrency(ServerPlayer player) {
        SimpleCurrency c = currency;
        Platform.get().sendToPlayer(player, new TraderyPayloads.CurrencyInfoPayload(
            c.id().toString(), c.displayName(), c.symbol(), c.decimals(), c.thousandsSeparator(),
            dev.eliasnvx.tradery.ore.CoinTier.COPPER.value(), dev.eliasnvx.tradery.ore.CoinTier.SILVER.value(),
            dev.eliasnvx.tradery.ore.CoinTier.GOLD.value()));
    }

    public void syncBalance(ServerPlayer player) {
        if (accounts == null) {
            return;
        }
        accounts.ledger().get(AccountId.player(player.getUUID())).ifPresent(account ->
            Platform.get().sendToPlayer(player, new TraderyPayloads.BalanceSyncPayload(currency.id().toString(), account.balance(currency))));
    }

    public void setLocked(LedgerAccount account, boolean locked) {
        checkReady();
        accounts.ledger().setLocked(account, locked);
        accounts.setDirty();
        if (log != null) {
            JsonObject line = new JsonObject();
            line.addProperty("event", locked ? "lock" : "unlock");
            line.addProperty("account", account.id().serialize());
            log.write(line);
        }
    }

    /** Richest finite player accounts, highest first. */
    public List<LedgerAccount> top(int limit) {
        checkReady();
        ResourceLocation id = currency.id();
        return accounts.ledger().all().stream()
            .filter(a -> a.id() instanceof AccountId.Player && !a.isInfinite() && a.balance(id) > 0)
            .sorted(Comparator.comparingLong((LedgerAccount a) -> a.balance(id)).reversed().thenComparing(LedgerAccount::displayName))
            .limit(limit)
            .toList();
    }

    public List<HistoryData.Entry> history(UUID player) {
        checkReady();
        return history.get(player);
    }

    public StatsData stats() {
        checkReady();
        return stats;
    }

    public long maxBalance() {
        return maxBalance;
    }

    /** Money held on accounts plus the estimated coin items in the world. */
    public long moneySupply() {
        checkReady();
        long onAccounts = accounts.ledger().total(currency.id());
        long cash = stats.cashOutstanding();
        return onAccounts > Long.MAX_VALUE - cash ? Long.MAX_VALUE : onAccounts + cash;
    }

    // ------------------------------------------------------------------ transactions

    @Override
    public TransactionResult transfer(Account from, Account to, long amount, long fee, Reason reason) {
        checkReady();
        return execute(TransactionEvent.Type.TRANSFER, resolve(from), resolve(to), amount, fee, reason);
    }

    @Override
    public TransactionResult deposit(Account to, long amount, Reason reason) {
        checkReady();
        return execute(TransactionEvent.Type.DEPOSIT, null, resolve(to), amount, 0, reason);
    }

    @Override
    public TransactionResult withdraw(Account from, long amount, Reason reason) {
        checkReady();
        return execute(TransactionEvent.Type.WITHDRAW, resolve(from), null, amount, 0, reason);
    }

    /** {@code /eco set}: moves the difference as a deposit or withdrawal, so events, log and stats see it. */
    public TransactionResult setBalance(Account account, long balance, Reason reason) {
        checkReady();
        LedgerAccount target = resolve(account);
        if (balance < 0 || target.isInfinite()) {
            return TransactionResult.Failure.of(FailReason.INVALID_AMOUNT);
        }
        long current = target.balance(currency);
        if (balance == current) {
            return new TransactionResult.Success(new UUID(0, 0), 0, 0, TransactionResult.NO_BALANCE, current);
        }
        return balance > current
            ? execute(TransactionEvent.Type.DEPOSIT, null, target, balance - current, 0, reason)
            : execute(TransactionEvent.Type.WITHDRAW, target, null, current - balance, 0, reason);
    }

    private TransactionResult execute(TransactionEvent.Type type, @Nullable LedgerAccount from, @Nullable LedgerAccount to,
                                      long amount, long fee, Reason reason) {
        if (amount <= 0 || fee < 0 || fee > amount || (from != null && from == to)) {
            return TransactionResult.Failure.of(FailReason.INVALID_AMOUNT);
        }
        SimpleCurrency c = currency;
        TransactionEvent.Pre pre = TransactionEvent.Pre.EVENT.post(new TransactionEvent.Pre(type, from, to, c, amount, fee, reason));
        if (pre.isCancelled()) {
            return new TransactionResult.Failure(FailReason.CANCELLED, pre.cancelMessage());
        }
        long finalAmount = pre.amount();
        long finalFee = type == TransactionEvent.Type.TRANSFER ? pre.fee() : 0;

        Ledger.Outcome outcome = accounts.ledger().move(from, to, c.id(), finalAmount, finalFee, maxBalance);
        if (outcome instanceof Ledger.Refused refused) {
            return TransactionResult.Failure.of(refused.reason());
        }
        Ledger.Moved moved = (Ledger.Moved) outcome;
        accounts.setDirty();
        UUID txId = UUID.randomUUID();

        recordStats(type, from, to, finalAmount, finalFee, reason);
        recordHistory(from, to, finalAmount, finalFee, reason, moved);
        writeLog(type, from, to, c, finalAmount, finalFee, reason, txId, moved);
        syncChanged(from, moved.fromBefore(), moved.fromAfter(), reason);
        syncChanged(to, moved.toBefore(), moved.toAfter(), reason);

        TransactionEvent.Post.EVENT.post(new TransactionEvent.Post(type, from, to, c, finalAmount, finalFee, reason, txId,
            moved.fromAfter(), moved.toAfter()));
        if (from != null && !from.isInfinite() && BalanceChangedEvent.EVENT.hasListeners()) {
            BalanceChangedEvent.EVENT.post(new BalanceChangedEvent(from, c, moved.fromBefore(), moved.fromAfter(), reason, txId));
        }
        if (to != null && !to.isInfinite() && BalanceChangedEvent.EVENT.hasListeners()) {
            BalanceChangedEvent.EVENT.post(new BalanceChangedEvent(to, c, moved.toBefore(), moved.toAfter(), reason, txId));
        }
        return new TransactionResult.Success(txId, finalAmount, finalFee, moved.fromAfter(), moved.toAfter());
    }

    private void recordStats(TransactionEvent.Type type, @Nullable LedgerAccount from, @Nullable LedgerAccount to,
                             long amount, long fee, Reason reason) {
        stats.recordTransaction();
        boolean fromFinite = from != null && !from.isInfinite();
        boolean toFinite = to != null && !to.isInfinite();
        if (reason.type().equals(Reasons.COIN_DEPOSIT) && !fromFinite && toFinite) {
            stats.recordCashIn(amount);
        } else if (reason.type().equals(Reasons.COIN_WITHDRAW) && fromFinite && !toFinite) {
            stats.recordCashOut(amount);
        } else if (!fromFinite && toFinite) {
            stats.recordEmitted(amount - fee);
        } else if (fromFinite && !toFinite) {
            stats.recordBurned(amount);
        } else if (fromFinite) {
            stats.recordBurned(fee);
        }
    }

    private void recordHistory(@Nullable LedgerAccount from, @Nullable LedgerAccount to, long amount, long fee, Reason reason, Ledger.Moved moved) {
        int limit = TraderyConfig.server().history().perPlayer();
        long now = System.currentTimeMillis();
        if (from != null && from.id() instanceof AccountId.Player player && !from.isInfinite()) {
            history.add(player.uuid(), new HistoryData.Entry(now, -amount, moved.fromAfter(), reason.type(), counterparty(to, reason)), limit);
        }
        if (to != null && to.id() instanceof AccountId.Player player && !to.isInfinite()) {
            history.add(player.uuid(), new HistoryData.Entry(now, amount - fee, moved.toAfter(), reason.type(), counterparty(from, reason)), limit);
        }
    }

    private static String counterparty(@Nullable LedgerAccount other, Reason reason) {
        if (other != null && other.id() instanceof AccountId.Player) {
            return other.displayName();
        }
        return reason.note() != null ? reason.note() : "";
    }

    private void writeLog(TransactionEvent.Type type, @Nullable LedgerAccount from, @Nullable LedgerAccount to, SimpleCurrency c,
                          long amount, long fee, Reason reason, UUID txId, Ledger.Moved moved) {
        if (log == null) {
            return;
        }
        JsonObject line = new JsonObject();
        line.addProperty("tx", txId.toString());
        line.addProperty("type", type.name());
        if (from != null) {
            line.addProperty("from", from.id().serialize());
            line.addProperty("fromName", from.displayName());
            line.addProperty("fromBalance", moved.fromAfter());
        }
        if (to != null) {
            line.addProperty("to", to.id().serialize());
            line.addProperty("toName", to.displayName());
            line.addProperty("toBalance", moved.toAfter());
        }
        line.addProperty("currency", c.id().toString());
        line.addProperty("amount", amount);
        if (fee != 0) {
            line.addProperty("fee", fee);
        }
        line.addProperty("reason", reason.type().toString());
        if (reason.note() != null) {
            line.addProperty("note", reason.note());
        }
        log.write(line);
    }

    private void syncChanged(@Nullable LedgerAccount account, long before, long after, Reason reason) {
        if (account == null || account.isInfinite() || !(account.id() instanceof AccountId.Player player) || before == after) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player.uuid());
        if (online != null) {
            Platform.get().sendToPlayer(online, new TraderyPayloads.BalanceDeltaPayload(after - before, after, reason.type().toString()));
        }
    }
}
