package me.growapet.mockbukkit;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("mockbukkit")
final class MockBukkitFoundationTest extends GrowAPetMockTest {
    @Test
    void createsServerWorldAndPlayerOnThePinnedPaperLine() {
        assertNotNull(Bukkit.getServer());
        assertEquals("1.21.11", server.getMinecraftVersion());

        var world = server.addSimpleWorld("test-world");
        var player = server.addPlayer("GrowAPetTester");

        assertEquals("test-world", world.getName());
        assertEquals("GrowAPetTester", player.getName());
        assertEquals(1, server.getOnlinePlayers().size());
    }
}
