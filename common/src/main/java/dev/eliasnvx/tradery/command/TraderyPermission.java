package dev.eliasnvx.tradery.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Permission nodes. The node is {@code tradery.<path>} (NeoForge PermissionAPI, the Fabric permission API and what
 * LuckPerms shows on both loaders). Without a permission mod, the fallback op level decides.
 */
public enum TraderyPermission {
    BALANCE("balance", Commands.LEVEL_ALL),
    BALANCE_OTHERS("balance.others", Commands.LEVEL_GAMEMASTERS),
    PAY("pay", Commands.LEVEL_ALL),
    BALTOP("baltop", Commands.LEVEL_ALL),
    WITHDRAW("withdraw", Commands.LEVEL_ALL),
    HISTORY("history", Commands.LEVEL_ALL),
    ADMIN_ECO("admin.eco", Commands.LEVEL_GAMEMASTERS),
    ADMIN_STATS("admin.stats", Commands.LEVEL_GAMEMASTERS),
    ADMIN_RELOAD("admin.reload", Commands.LEVEL_ADMINS),
    ADMIN_VENDORS("admin.vendors", Commands.LEVEL_GAMEMASTERS);

    private final String path;
    private final int fallback;

    TraderyPermission(String path, int fallback) {
        this.path = path;
        this.fallback = fallback;
    }

    /** Dotted path under {@code tradery.}, e.g. {@code balance.others}. */
    public String path() {
        return path;
    }

    /** {@code tradery.balance.others}. */
    public String node() {
        return "tradery." + path;
    }

    /** {@code balance/others}, an identifier-friendly path. */
    public String identifierPath() {
        return path.replace('.', '/');
    }

    /** Op level ({@code 0..4}, see {@link Commands#LEVEL_ALL} and the other levels) that grants it without a permission mod. */
    public int fallback() {
        return fallback;
    }

    /** Whether the source's own op level grants it (console, command blocks, or no permission mod). */
    public boolean fallbackAllows(CommandSourceStack source) {
        return source.hasPermission(fallback);
    }

    /** Whether the player's op level grants it. */
    public boolean fallbackAllows(ServerPlayer player) {
        return player.hasPermissions(fallback);
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
