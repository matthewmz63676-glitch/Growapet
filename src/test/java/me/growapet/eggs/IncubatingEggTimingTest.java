package me.growapet.eggs;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("fast")
final class IncubatingEggTimingTest {
    @Test
    void remainingSecondsUsesCeilingAndNeverGoesNegative() {
        long hatchAt = 1_000_000L;
        IncubatingEgg egg = new IncubatingEgg(UUID.randomUUID(), EntityType.CHICKEN,
                new Location(null, 1.9, 64.9, 2.9), 60, hatchAt);
        Clock clock = Clock.fixed(Instant.ofEpochMilli(hatchAt - 1_001), ZoneOffset.UTC);

        assertEquals(2, egg.getSecondsRemaining(clock));
        assertEquals(0, egg.getSecondsRemaining(hatchAt));
        assertEquals(60, egg.getTotalSeconds());
        assertEquals(1, egg.getLocation().getBlockX());
    }
}
