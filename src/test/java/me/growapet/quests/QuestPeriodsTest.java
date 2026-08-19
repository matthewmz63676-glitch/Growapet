package me.growapet.quests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("fast")
final class QuestPeriodsTest {
    private static final Instant TUESDAY_NIGHT = Instant.parse("2026-08-18T23:15:00Z");
    private static final Clock CLOCK = Clock.fixed(TUESDAY_NIGHT, ZoneOffset.UTC);

    @Test
    void keysUseUtcDailyAndIsoWeeklyBoundaries() {
        assertEquals("2026-08-18", QuestPeriods.key("daily", CLOCK));
        assertEquals("2026-W34", QuestPeriods.key("weekly", CLOCK));
        assertEquals("story", QuestPeriods.key("story", CLOCK));
    }

    @Test
    void resetCountdownIsStableAndStoryDoesNotExpire() {
        assertEquals("0h 45m", QuestPeriods.timeUntilReset("daily", CLOCK));
        assertEquals("120h 45m", QuestPeriods.timeUntilReset("weekly", CLOCK));
        assertEquals("never", QuestPeriods.timeUntilReset("story", CLOCK));
    }

    @Test
    void unknownGroupsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> QuestPeriods.key("monthly", CLOCK));
        assertThrows(IllegalArgumentException.class, () -> QuestPeriods.timeUntilReset("monthly", CLOCK));
    }
}
