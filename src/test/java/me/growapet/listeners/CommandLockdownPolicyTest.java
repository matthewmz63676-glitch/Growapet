package me.growapet.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandLockdownPolicyTest {
    @Test void administratorsBypassTheEntireBlacklistIncludingNamespacedCommands() {
        assertTrue(CommandLockdownPolicy.permitsLabel("pl", true, false));
        assertTrue(CommandLockdownPolicy.permitsLabel("minecraft:gamemode", true, false));
    }

    @Test void regularPlayersRemainRestrictedToGrowAPetAndAnInstalledServerCommand() {
        assertTrue(CommandLockdownPolicy.permitsLabel("plot", false, false));
        assertTrue(CommandLockdownPolicy.permitsLabel("server", false, true));
        assertFalse(CommandLockdownPolicy.permitsLabel("pl", false, false));
        assertFalse(CommandLockdownPolicy.permitsLabel("minecraft:gamemode", false, false));
        assertFalse(CommandLockdownPolicy.permitsLabel("server", false, false));
    }
}
