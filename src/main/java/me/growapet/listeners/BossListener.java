package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.bosses.ActiveBoss;
import me.growapet.events.EventType;
import me.growapet.models.PlayerData;
import me.growapet.quests.QuestType;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Custom boss combat and exactly-once, economy-serialized participation payouts. */
public final class BossListener implements Listener {
    private final GrowAPet plugin;
    private final NamespacedKey projectileDamage;

    public BossListener(GrowAPet plugin) {
        this.plugin = plugin;
        projectileDamage = new NamespacedKey(plugin, "projectile_damage");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ActiveBoss boss = plugin.getBossManager().getActive(event.getEntity().getUniqueId());
        if (boss == null) return;
        event.setCancelled(true);
        Player attacker = player(event.getDamager());
        if (attacker == null) return;
        PlayerData data = plugin.getPlayerManager().get(attacker);
        Double shot = event.getDamager() instanceof Projectile projectile
                ? projectile.getPersistentDataContainer().get(projectileDamage, PersistentDataType.DOUBLE) : null;
        double base = shot == null ? plugin.getMobManager().weaponDamage(attacker) : shot;
        double damage = Math.max(0, base) * (data == null ? 1 : data.getDamageMultiplier())
                * plugin.getPetManager().damageMultiplier(attacker.getUniqueId());
        if (data != null && Math.random() < data.getCriticalChance()) damage *= data.getCriticalDamage();
        if (!Double.isFinite(damage) || damage <= 0) return;
        double configuredMaximum = plugin.getConfigManager().config().getDouble("combat.max-hit-damage", 1_000_000);
        double maximum = Double.isFinite(configuredMaximum) && configuredMaximum > 0 ? configuredMaximum : 1_000_000;
        damage = Math.min(damage, maximum);
        boss.getEntity().setNoDamageTicks(0);
        boss.addDamage(attacker.getUniqueId(), damage);
        if (boss.damage(damage)) { boss.getEntity().setHealth(0); return; }
        var attribute = boss.getEntity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double visualMax = attribute == null ? boss.getEntity().getHealth() : attribute.getValue();
        boss.getEntity().setHealth(Math.max(1, visualMax * (boss.getHealth() / boss.getMaxHealth())));
        boss.getEntity().setNoDamageTicks(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)
                && plugin.getBossManager().getActive(event.getEntity().getUniqueId()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        ActiveBoss boss = plugin.getBossManager().getActive(event.getEntity().getUniqueId());
        if (boss == null) return;
        plugin.getBossManager().defeated(boss);
        event.getDrops().clear();
        event.setDroppedExp(0);
        List<Map.Entry<UUID, Double>> ranked = boss.getDamageByPlayer().entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).toList();
        ConfigurationSection section = boss.getConfig().getConfigurationSection("rewards");
        if (section == null || ranked.isEmpty()) return;
        Reward reward = new Reward(section.getLong("top1-credits"), section.getLong("top2-credits"),
                section.getLong("top3-credits"), section.getLong("participation-coins"),
                section.getLong("participation-gems"), section.getLong("participation-exp"));
        long eventExp = safeScale(reward.exp, plugin.getEventManager().multiplier(EventType.DOUBLE_EXP));
        settle(boss.getBossId(), boss.getRunId(), ranked, reward, eventExp);
    }

    private void settle(String bossId, UUID runId, List<Map.Entry<UUID, Double>> ranked, Reward reward, long eventExp) {
        List<PlayerData> locked = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : ranked) {
            PlayerData data = plugin.getPlayerManager().get(entry.getKey());
            if (data == null) continue;
            if (!data.tryLockEconomy()) {
                locked.forEach(PlayerData::unlockEconomy);
                Bukkit.getScheduler().runTaskLater(plugin, () -> settle(bossId, runId, ranked, reward, eventExp), 1L);
                return;
            }
            locked.add(data);
        }

        plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement history = connection.prepareStatement("INSERT INTO boss_history(boss_run_id,boss_id,killed_at,rankings)VALUES(?,?,?,?)")) {
                history.setString(1, runId.toString()); history.setString(2, bossId);
                history.setLong(3, System.currentTimeMillis()); history.setString(4, ranked.toString()); history.executeUpdate();
            }
            for (int index = 0; index < ranked.size(); index++) {
                try (PreparedStatement payout = connection.prepareStatement(
                        "UPDATE players SET coins=MIN(9223372036854775807,coins+?),gems=MIN(9223372036854775807,gems+?),credits=MIN(9223372036854775807,credits+?)," +
                                "boss_kills=MIN(9223372036854775807,boss_kills+1),boss_damage=MIN(1.7976931348623157e308,boss_damage+?)," +
                                "exp=MIN(9223372036854775807,exp+ROUND(?*exp_multiplier)) WHERE uuid=?")) {
                    payout.setLong(1, reward.coins); payout.setLong(2, reward.gems); payout.setLong(3, reward.credits(index));
                    payout.setDouble(4, ranked.get(index).getValue()); payout.setLong(5, eventExp);
                    payout.setString(6, ranked.get(index).getKey().toString());
                    if (payout.executeUpdate() != 1) throw new IllegalStateException("Missing boss participant");
                }
            }
            return null;
        }).whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) return; // locked caches are deliberately excluded from shutdown saves
            Bukkit.getScheduler().runTask(plugin, () -> {
                locked.forEach(PlayerData::unlockEconomy);
                if (error != null) { plugin.getLogger().severe("Boss payout failed: " + error.getMessage()); return; }
                for (int index = 0; index < ranked.size(); index++) {
                    UUID id = ranked.get(index).getKey();
                    PlayerData data = plugin.getPlayerManager().get(id);
                    if (data != null) {
                        data.addCoinsRaw(reward.coins); data.addGemsRaw(reward.gems); data.addCredits(reward.credits(index));
                        data.incrementBossKills(); data.addBossDamage(ranked.get(index).getValue()); data.addExp(eventExp);
                        Player online = Bukkit.getPlayer(id);
                        if (online != null) plugin.getPlayerManager().syncExpBar(online, data);
                        plugin.getCreditMilestoneManager().evaluate(id);
                    }
                    plugin.getQuestManager().record(id, QuestType.BOSS_KILL, 1, bossId);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) Messages.send(online,
                            "<gold><bold>BOSS DEFEATED</bold></gold> <dark_gray>•</dark_gray> <gray>Placement <yellow>#<rank></yellow> → <light_purple><credits> Credits</light_purple></gray>",
                            Messages.value("rank", index + 1), Messages.value("credits", reward.credits(index)));
                }
            });
        });
    }

    private static Player player(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    private static long safeScale(long amount, double multiplier) {
        if (amount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return 0;
        double result = amount * multiplier;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, Math.round(result));
    }

    private record Reward(long top1, long top2, long top3, long coins, long gems, long exp) {
        private Reward { top1=Math.max(0,top1); top2=Math.max(0,top2); top3=Math.max(0,top3); coins=Math.max(0,coins); gems=Math.max(0,gems); exp=Math.max(0,exp); }
        private long credits(int rank) { return rank == 0 ? top1 : rank == 1 ? top2 : rank == 2 ? top3 : 0; }
    }
}
