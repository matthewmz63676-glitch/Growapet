package me.growapet.listeners;

import java.util.Locale;
import java.util.Set;

/** Pure command-label policy kept separate so the security boundary is regression-testable. */
final class CommandLockdownPolicy {
    private static final Set<String> PLAYER_COMMANDS = Set.of(
            "plot", "pets", "stats", "warp", "zones", "visit", "leaderboard", "quests", "trade",
            "options", "shop", "spawn", "store", "autokill", "daily", "boss", "growapet", "leaderboards"
    );
    private static final Set<String> ADMIN_COMMANDS = Set.of("getmob", "getegg", "getpet", "setspawn", "unlockzone");

    private CommandLockdownPolicy() {}

    static boolean permitsLabel(String rawLabel, boolean administrator, boolean serverCommandAvailable) {
        if (administrator) return true;
        if (rawLabel == null || rawLabel.isBlank()) return false;
        String label = rawLabel.toLowerCase(Locale.ROOT);
        if (label.contains(":")) return false;
        if (label.equals("server")) return serverCommandAvailable;
        return PLAYER_COMMANDS.contains(label) || ADMIN_COMMANDS.contains(label);
    }
}
