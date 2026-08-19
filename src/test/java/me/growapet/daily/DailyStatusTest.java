package me.growapet.daily;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fast")
final class DailyStatusTest {
    @Test
    void emptyStatusIsImmediatelyAvailable() {
        DailyManager.Status status = new DailyManager.Status(0, 0);
        assertTrue(status.ready(1));
        assertEquals(0, status.availableAt());
        assertEquals(0, status.remainingMillis(1));
    }

    @Test
    void cooldownBoundaryIsInclusive() {
        long claimedAt = 1_000_000L;
        DailyManager.Status status = new DailyManager.Status(claimedAt, 3);
        assertEquals(claimedAt + DailyManager.COOLDOWN_MILLIS, status.availableAt());
        assertFalse(status.ready(claimedAt + DailyManager.COOLDOWN_MILLIS - 1));
        assertTrue(status.ready(claimedAt + DailyManager.COOLDOWN_MILLIS));
        assertEquals(1, status.remainingMillis(claimedAt + DailyManager.COOLDOWN_MILLIS - 1));
    }
}
