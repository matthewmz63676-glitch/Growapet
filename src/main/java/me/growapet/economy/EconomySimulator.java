package me.growapet.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Deterministic, side-effect-free economy gate for reviewing future rewards and shops. */
public final class EconomySimulator {
    private EconomySimulator() { }

    public static SimulationReport simulate(Inputs input, long seed) {
        input.validate();
        List<ScenarioReport> scenarios = new ArrayList<>();
        for (Engagement engagement : Engagement.values()) {
            long scenarioSeed = seed ^ (engagement.ordinal() * 0x9E3779B97F4A7C15L);
            scenarios.add(run(input, engagement, scenarioSeed));
        }
        return new SimulationReport(seed, scenarios, input.catalogChecksum());
    }

    public static Inputs fromConfig(FileConfiguration config, FileConfiguration mobs, FileConfiguration bosses,
                                    FileConfiguration quests, FileConfiguration commerce) {
        long mobCoins = 0, mobGems = 0;
        ConfigurationSection mobRoot = mobs.getConfigurationSection("mobs");
        if (mobRoot != null) for (String id : mobRoot.getKeys(false)) { mobCoins += Math.max(0, mobRoot.getLong(id + ".coins", 0)); mobGems += Math.max(0, mobRoot.getLong(id + ".gems", 0)); }
        long questCredits = 0; int questCount = 0;
        for (String period : List.of("daily", "weekly", "story")) {
            ConfigurationSection root = quests.getConfigurationSection(period); if (root == null) continue;
            for (String id : root.getKeys(false)) { questCredits += Math.max(0, root.getLong(id + ".reward-credits", 0)); questCount++; }
        }
        long bossCredits = 0;
        ConfigurationSection bossRoot = bosses.getConfigurationSection("bosses");
        if (bossRoot != null) for (String id : bossRoot.getKeys(false)) bossCredits += Math.max(0, bossRoot.getLong(id + ".rewards.top1-credits", 0));
        long paidCredits = 0;
        ConfigurationSection skuRoot = commerce.getConfigurationSection("sku-bundles");
        if (skuRoot != null) for (String id : skuRoot.getKeys(false)) paidCredits = Math.addExact(paidCredits, Math.max(0, skuRoot.getLong(id + ".credits", 0)));
        return new Inputs(
                Math.max(1, config.getLong("economy.starting-coins", 100)),
                Math.max(0, mobCoins / Math.max(1, mobRoot == null ? 1 : mobRoot.getKeys(false).size())),
                Math.max(0, mobGems / Math.max(1, mobRoot == null ? 1 : mobRoot.getKeys(false).size())),
                Math.max(1, questCount == 0 ? 1 : questCount), Math.max(0, questCredits / Math.max(1, questCount)),
                Math.max(0, bossCredits), Math.max(0, paidCredits), 250, 1_000_000, 50, 0xC0FFEE);
    }

    private static ScenarioReport run(Inputs input, Engagement engagement, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        long coins = input.startingCoins(), gems = 0, credits = 0, spentCoins = 0, spentGems = 0, spentCredits = 0;
        List<Checkpoint> checkpoints = new ArrayList<>();
        long[] days = {1, 7, 30, 90};
        for (int day = 1; day <= 90; day++) {
            long mobs = Math.multiplyExact(engagement.mobKillsPerDay, day == 1 ? 1 : 1);
            for (int i = 0; i < mobs; i++) { coins = safeAdd(coins, input.mobCoins() + random.nextLong(0, Math.max(1, input.mobCoins() / 5 + 1))); gems = safeAdd(gems, input.mobGems()); }
            credits = safeAdd(credits, input.dailyCredits());
            if (day % 7 == 0) { credits = safeAdd(credits, Math.multiplyExact(input.questCredits(), engagement.questsPerWeek)); credits = safeAdd(credits, input.bossCredits()); }
            long eggs = engagement.eggsPerDay;
            gems = safeAdd(gems, eggs * input.mobGems());
            if (day % 30 == 0) { long spend = Math.min(coins, 50_000L * engagement.shopSpendFactor); coins -= spend; spentCoins = safeAdd(spentCoins, spend); }
            if (day % 14 == 0) { long spend = Math.min(gems, 50L * engagement.shopSpendFactor); gems -= spend; spentGems = safeAdd(spentGems, spend); }
            if (day % 30 == 0 && credits > 0) { long spend = Math.min(credits, 10L * engagement.shopSpendFactor); credits -= spend; spentCredits = safeAdd(spentCredits, spend); }
            for (long checkpoint : days) if (checkpoint == day) checkpoints.add(new Checkpoint(day, coins, gems, credits, spentCoins, spentGems, spentCredits));
        }
        return new ScenarioReport(engagement.label, checkpoints, timeToFirstTier(input, engagement));
    }

    private static int timeToFirstTier(Inputs input, Engagement engagement) {
        long target = Math.max(1, input.firstZoneCost()); long total = input.startingCoins();
        for (int day = 1; day <= 90; day++) { total = safeAdd(total, engagement.mobKillsPerDay * input.mobCoins()); if (total >= target) return day; }
        return -1;
    }

    private static long safeAdd(long left, long right) { if (right <= 0) return left; return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }

    public enum Engagement {
        CASUAL("Casual", 30, 1, 1), REGULAR("Regular", 120, 3, 2), HIGH("High", 400, 7, 4);
        private final String label; private final long mobKillsPerDay, questsPerWeek, eggsPerDay; private final long shopSpendFactor;
        Engagement(String label, long mobKillsPerDay, long questsPerWeek, long eggsPerDay) { this.label = label; this.mobKillsPerDay = mobKillsPerDay; this.questsPerWeek = questsPerWeek; this.eggsPerDay = eggsPerDay; this.shopSpendFactor = questsPerWeek; }
    }

    public record Inputs(long startingCoins, long mobCoins, long mobGems, long questCount, long questCredits,
                         long bossCredits, long paidCreditsPerCatalog, long dailyCredits, long firstZoneCost,
                         long approvedPaidAccelerationPercent, long catalogChecksum) {
        public void validate() {
            if (startingCoins < 0 || mobCoins < 0 || mobGems < 0 || questCount < 0 || questCredits < 0 || bossCredits < 0 || paidCreditsPerCatalog < 0 || dailyCredits < 0 || firstZoneCost < 0)
                throw new IllegalArgumentException("Economy values cannot be negative");
            if (approvedPaidAccelerationPercent < 0 || approvedPaidAccelerationPercent > 50) throw new IllegalArgumentException("Paid acceleration exceeds the approved 50% cap");
        }
    }

    public record Checkpoint(long day, long coins, long gems, long credits, long spentCoins, long spentGems, long spentCredits) { }
    public record ScenarioReport(String name, List<Checkpoint> checkpoints, int timeToFirstZoneDays) { public ScenarioReport { checkpoints = List.copyOf(checkpoints); } }
    public record SimulationReport(long seed, List<ScenarioReport> scenarios, long catalogChecksum) { public SimulationReport { scenarios = List.copyOf(scenarios); } }
}
