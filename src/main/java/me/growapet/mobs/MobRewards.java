package me.growapet.mobs;

import me.growapet.GrowAPet;
import me.growapet.events.EventType;
import me.growapet.boosts.BoostType;
import me.growapet.hud.ActionBarManager;
import me.growapet.models.PlayerData;
import me.growapet.quests.QuestType;
import me.growapet.utils.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class MobRewards {
    private MobRewards() {}

    public static void grant(GrowAPet plugin, Player killer, String mobId) {
        ConfigurationSection config = plugin.getConfigManager().mobs().getConfigurationSection("mobs." + mobId);
        if (config == null || !config.getBoolean("enabled", true)) return;
        String zone = config.getString("zone", "");
        if (!zone.isBlank() && (!plugin.getZoneManager().isUnlocked(killer, zone)
                || !plugin.getZoneManager().isPlayerInZone(killer, zone))) {
            Messages.send(killer, "<red>You cannot earn rewards outside your unlocked mob zone.</red>");
            return;
        }
        PlayerData data = plugin.getPlayerManager().get(killer);
        if (data == null) return;

        double petCoins = plugin.getPetManager().coinMultiplier(killer.getUniqueId());
        double petGems = plugin.getPetManager().gemMultiplier(killer.getUniqueId());
        long coins = scale(config.getLong("coins"), plugin.getEventManager().multiplier(EventType.DOUBLE_COINS) * petCoins * plugin.getPlotBoostManager().multiplier(killer.getUniqueId(), BoostType.COINS));
        long gems = scale(config.getLong("gems"), plugin.getEventManager().multiplier(EventType.DOUBLE_GEMS) * petGems * plugin.getPlotBoostManager().multiplier(killer.getUniqueId(), BoostType.GEMS));
        long baseExp = scale(config.getLong("exp"), plugin.getPlotBoostManager().multiplier(killer.getUniqueId(), BoostType.MOB_EXP));
        long displayedExp = scale(config.getLong("exp"),
                plugin.getEventManager().multiplier(EventType.DOUBLE_EXP) * data.getExpMultiplier() * plugin.getPlotBoostManager().multiplier(killer.getUniqueId(), BoostType.MOB_EXP));

        long beforeCoins = data.getCoins();
        long beforeGems = data.getGems();
        data.addCoins(coins);
        data.addGems(gems);
        long awardedCoins = Math.max(0L, data.getCoins() - beforeCoins);
        long awardedGems = Math.max(0L, data.getGems() - beforeGems);
        data.incrementMobKills();
        plugin.getPlayerManager().addExp(killer, baseExp);
        long petExp = scale(config.getLong("exp"), plugin.getPlotBoostManager().multiplier(killer.getUniqueId(), BoostType.PET_EXP));
        plugin.getPetManager().grantKillExperience(killer.getUniqueId(), petExp);
        plugin.getQuestManager().record(killer.getUniqueId(), QuestType.MOB_KILLS, 1, mobId);
        plugin.getQuestManager().record(killer.getUniqueId(), QuestType.COINS_EARNED, awardedCoins, null);
        plugin.getQuestManager().record(killer.getUniqueId(), QuestType.GEMS_EARNED, awardedGems, null);
        plugin.getCreditMilestoneManager().evaluate(killer.getUniqueId());
        plugin.getTutorialManager().notifyAction(killer, me.growapet.tutorial.TutorialAction.MOB_KILL);

        plugin.getActionBarManager().showTemporary(
                killer,
                Messages.parse("<gold>+<coins> Coins</gold> <dark_gray>•</dark_gray> <green>+<gems> Gems</green> <dark_gray>•</dark_gray> <aqua>+<exp> EXP</aqua>",
                        Messages.value("coins", awardedCoins), Messages.value("gems", awardedGems), Messages.value("exp", displayedExp)),
                Duration.ofSeconds(5),
                ActionBarManager.PRIORITY_KILL_RECEIPT
        );
    }

    static long scale(long value, double multiplier) {
        if (value <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return 0;
        double result = value * multiplier;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(result));
    }
}
