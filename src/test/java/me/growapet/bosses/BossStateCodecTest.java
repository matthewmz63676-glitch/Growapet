package me.growapet.bosses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fast")
class BossStateCodecTest {
    @Test void roundTripsDamageWithoutAcceptingInvalidValues() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Map<UUID, Double> original = new LinkedHashMap<>();
        original.put(first, 125.5);
        original.put(second, 3.25);
        original.put(UUID.randomUUID(), Double.NaN);

        String encoded = BossManager.serializeDamage(original);
        assertEquals(Map.of(first, 125.5, second, 3.25), BossManager.parseDamage(encoded));
        assertTrue(BossManager.parseDamage("bad;" + first + "=-1;" + second + "=NaN").isEmpty());
    }
}
