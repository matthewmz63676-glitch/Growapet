package me.growapet.mockbukkit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared MockBukkit boundary fixture; real PacketEvents/WorldGuard behavior stays in E2E. */
abstract class GrowAPetMockTest {
    protected ServerMock server;
    protected final Map<String, PluginMock> dependencyStubs = new LinkedHashMap<>();

    @BeforeEach
    void startMockServer() {
        server = MockBukkit.mock();
        for (String name : new String[]{"packetevents", "WorldEdit", "WorldGuard"}) {
            PluginMock stub = PluginMock.builder().withPluginName(name).withPluginVersion("test").build();
            server.getPluginManager().registerLoadedPlugin(stub);
            server.getPluginManager().enablePlugin(stub);
            dependencyStubs.put(name, stub);
        }
    }

    @AfterEach
    void stopMockServer() {
        assertEquals(0, server.getScheduler().getPendingTasks().size(), "test leaked scheduled tasks");
        server.getScheduler().waitAsyncTasksFinished();
        MockBukkit.unmock();
        dependencyStubs.clear();
    }

    protected PlayerMock addPlayer(String name) {
        return server.addPlayer(name);
    }

    protected void tick(long ticks) {
        server.getScheduler().performTicks(ticks);
    }
}
