package dev.eliasnvx.tradery.command;

import net.minecraft.server.permissions.PermissionLevel;

import java.util.Locale;

/**
 * Permission nodes. The node is {@code tradery.<path>} (NeoForge PermissionAPI, and what LuckPerms shows on both
 * loaders); Fabric's permission API gets the identifier {@code tradery:<path with / instead of .>}.
 * Without a permission mod, the fallback op level decides.
 */
public enum TraderyPermission {
    BALANCE("balance", PermissionLevel.ALL),
    BALANCE_OTHERS("balance.others", PermissionLevel.GAMEMASTERS),
    PAY("pay", PermissionLevel.ALL),
    BALTOP("baltop", PermissionLevel.ALL),
    WITHDRAW("withdraw", PermissionLevel.ALL),
    HISTORY("history", PermissionLevel.ALL),
    ADMIN_ECO("admin.eco", PermissionLevel.GAMEMASTERS),
    ADMIN_STATS("admin.stats", PermissionLevel.GAMEMASTERS),
    ADMIN_RELOAD("admin.reload", PermissionLevel.ADMINS),
    ADMIN_VENDORS("admin.vendors", PermissionLevel.GAMEMASTERS);

    private final String path;
    private final PermissionLevel fallback;

    TraderyPermission(String path, PermissionLevel fallback) {
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

    /** {@code balance/others}, the identifier path for Fabric's permission API. */
    public String identifierPath() {
        return path.replace('.', '/');
    }

    /** Op level that grants it without a permission mod. */
    public PermissionLevel fallback() {
        return fallback;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
