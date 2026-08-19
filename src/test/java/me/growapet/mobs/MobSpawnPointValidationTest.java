package me.growapet.mobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fast")
@Tag("regression")
final class MobSpawnPointValidationTest {
    @Test void acceptsStableAdminIdsAndRejectsAmbiguousValues() {
        assertTrue(MobSpawnPointManager.isValidId("forest-zombies_1"));
        assertTrue(MobSpawnPointManager.isValidId("A"));
        assertFalse(MobSpawnPointManager.isValidId(""));
        assertFalse(MobSpawnPointManager.isValidId("forest zombies"));
        assertFalse(MobSpawnPointManager.isValidId("forest/../../database"));
        assertFalse(MobSpawnPointManager.isValidId("x".repeat(65)));
    }
}
