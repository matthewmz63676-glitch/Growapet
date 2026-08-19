package me.growapet.models;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fast")
final class PlayerDataBoundaryTest {
    @Test
    void scaledExperienceCanCrossMultipleExactThresholds() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "tester");
        data.setExpMultiplier(2.0);

        data.addExp((PlayerData.expToLevelUp(1) + PlayerData.expToLevelUp(2)) / 2 + 1);

        assertEquals(3, data.getLevel());
        assertEquals(1, data.getExp());
        assertTrue(data.getExpProgress() >= 0.0 && data.getExpProgress() <= 1.0);
    }

    @Test
    void economyLockIsSingleOwnerUntilReleased() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "tester");
        assertTrue(data.tryLockEconomy());
        assertFalse(data.tryLockEconomy());
        data.unlockEconomy();
        assertTrue(data.tryLockEconomy());
    }

    @Test
    void invalidMultipliersAndCountersAreSafe() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "tester");
        data.setCoinMultiplier(Double.NaN);
        data.setGemMultiplier(-2);
        data.setCriticalChance(2);
        data.setBossDamage(Double.NaN);
        data.setCoins(Long.MAX_VALUE);
        data.addCoinsRaw(Long.MAX_VALUE);

        assertEquals(1.0, data.getCoinMultiplier());
        assertEquals(0.0, data.getGemMultiplier());
        assertEquals(1.0, data.getCriticalChance());
        assertEquals(0.0, data.getBossDamage());
        assertEquals(Long.MAX_VALUE, data.getCoins());
    }
}
