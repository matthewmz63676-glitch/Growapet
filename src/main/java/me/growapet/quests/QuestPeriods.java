package me.growapet.quests;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

/** UTC period calculations kept independent from Bukkit and wall-clock globals. */
public final class QuestPeriods {
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private QuestPeriods() {}

    public static String key(String group, Clock clock) {
        return key(group, clock.instant());
    }

    public static String key(String group, Instant now) {
        LocalDate date = now.atZone(UTC).toLocalDate();
        return switch (group.toLowerCase(java.util.Locale.ROOT)) {
            case "daily" -> date.toString();
            case "weekly" -> date.get(WeekFields.ISO.weekBasedYear()) + "-W"
                    + date.get(WeekFields.ISO.weekOfWeekBasedYear());
            case "story" -> "story";
            default -> throw new IllegalArgumentException("Unknown quest group: " + group);
        };
    }

    public static String timeUntilReset(String group, Clock clock) {
        if ("story".equalsIgnoreCase(group)) return "never";
        Duration remaining = Duration.between(clock.instant(), nextReset(group, clock.instant()));
        long hours = remaining.toHours();
        long minutes = remaining.minusHours(hours).toMinutes();
        return hours + "h " + minutes + "m";
    }

    static Instant nextReset(String group, Instant now) {
        ZonedDateTime current = now.atZone(UTC);
        if ("daily".equalsIgnoreCase(group)) {
            return current.toLocalDate().plusDays(1).atStartOfDay(UTC).toInstant();
        }
        if ("weekly".equalsIgnoreCase(group)) {
            long days = 8L - current.getDayOfWeek().getValue();
            return current.toLocalDate().plusDays(days).atStartOfDay(UTC).toInstant();
        }
        throw new IllegalArgumentException("Quest group has no reset: " + group);
    }
}
