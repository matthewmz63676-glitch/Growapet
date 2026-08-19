package me.growapet.lifecycle;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("fast")
final class LifecycleCoordinatorTest {
    @Test
    void startupReadinessFailureAndCleanupAreExplicitStates() {
        LifecycleCoordinator lifecycle = new LifecycleCoordinator();
        assertEquals(LifecycleCoordinator.State.NEW, lifecycle.state());
        assertTrue(lifecycle.beginStartup());
        assertFalse(lifecycle.isReady());
        assertTrue(lifecycle.markReady());
        assertTrue(lifecycle.isReady());
        assertFalse(lifecycle.fail(new IllegalStateException("late failure")));
        assertTrue(lifecycle.beginShutdown());
        assertFalse(lifecycle.isReady());
        assertTrue(lifecycle.markStopped());
        assertFalse(lifecycle.beginShutdown());
    }

    @Test
    void startupFailureCannotExposeReadiness() {
        LifecycleCoordinator lifecycle = new LifecycleCoordinator();
        lifecycle.beginStartup();
        assertTrue(lifecycle.fail(new IllegalStateException("database")));
        assertEquals(LifecycleCoordinator.State.FAILED, lifecycle.state());
        assertFalse(lifecycle.isReady());
        assertTrue(lifecycle.beginShutdown());
    }
}
