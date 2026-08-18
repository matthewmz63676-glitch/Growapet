package me.growapet.rewards;

import me.growapet.boosts.BoostType;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable, validated reward payload shared by every reward-producing integration. */
public record RewardBundle(
        String version,
        long coins,
        long gems,
        long credits,
        List<Entitlement> entitlements,
        List<BoostReward> boosts) {

    public RewardBundle {
        version = Objects.requireNonNullElse(version, "1");
        if (coins < 0 || gems < 0 || credits < 0) throw new IllegalArgumentException("Reward amounts cannot be negative");
        entitlements = List.copyOf(Objects.requireNonNullElse(entitlements, List.of()));
        boosts = List.copyOf(Objects.requireNonNullElse(boosts, List.of()));
        // Some trusted gameplay rewards (for example an EXP-only quest) use the
        // shared receipt/guard transaction while applying their non-currency
        // effect in the same guarded callback.
    }

    public static RewardBundle currency(long coins, long gems, long credits) {
        return new RewardBundle("1", coins, gems, credits, List.of(), List.of());
    }

    public record Entitlement(String id, String kind, String value) {
        public Entitlement {
            if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("Invalid entitlement id");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("Entitlement kind is required");
            value = Objects.requireNonNullElse(value, "");
        }
    }

    public record BoostReward(String id, BoostType type, double bonus, long durationMinutes) {
        public BoostReward {
            if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("Invalid boost id");
            Objects.requireNonNull(type, "boost type");
            if (!Double.isFinite(bonus) || bonus <= 0 || bonus > 9.0) throw new IllegalArgumentException("Boost must be between 0 and 900%");
            if (durationMinutes < 1 || durationMinutes > 24 * 60) throw new IllegalArgumentException("Invalid boost duration");
        }

        public long durationMillis() { return Duration.ofMinutes(durationMinutes).toMillis(); }
    }
}
