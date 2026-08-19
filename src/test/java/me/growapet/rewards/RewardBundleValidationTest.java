package me.growapet.rewards;

import me.growapet.boosts.BoostType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("fast")
final class RewardBundleValidationTest {
    @Test
    void currencyBundlesAreImmutableAndKeepAmounts() {
        RewardBundle bundle = RewardBundle.currency(10, 20, 30);
        assertEquals(10, bundle.coins());
        assertEquals(20, bundle.gems());
        assertEquals(30, bundle.credits());
        assertEquals(List.of(), bundle.entitlements());
        assertEquals(List.of(), bundle.boosts());
    }

    @Test
    void entitlementAndBoostBoundariesRejectUnsafePayloads() {
        assertThrows(IllegalArgumentException.class, () -> new RewardBundle("1", -1, 0, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RewardBundle.Entitlement("bad id!", "TAG", "x"));
        assertThrows(IllegalArgumentException.class, () -> new RewardBundle.BoostReward("boost", BoostType.MOB_EXP, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RewardBundle.BoostReward("boost", BoostType.MOB_EXP, 1, 1441));
    }

    @Test
    void boostDurationUsesMinutesWithoutRoundingDrift() {
        RewardBundle.BoostReward boost = new RewardBundle.BoostReward("boost", BoostType.MOB_EXP, 0.25, 30);
        assertEquals(Duration.ofMinutes(30).toMillis(), boost.durationMillis());
    }
}
