/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.projectiles.ProjectileSource
 */
package me.growapet.listeners;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.growapet.GrowAPet;
import me.growapet.bosses.ActiveBoss;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public class BossListener
implements Listener {
    private final GrowAPet plugin;

    public BossListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        ActiveBoss active = this.plugin.getBossManager().getActive(event.getEntity().getUniqueId());
        if (active == null) {
            return;
        }
        Player attacker = this.resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        active.addDamage(attacker.getUniqueId(), event.getFinalDamage());
    }

    private Player resolvePlayer(Entity damager) {
        Projectile projectile;
        ProjectileSource projectileSource;
        if (damager instanceof Player) {
            Player player = (Player)damager;
            return player;
        }
        if (damager instanceof Projectile && (projectileSource = (projectile = (Projectile)damager).getShooter()) instanceof Player) {
            Player player = (Player)projectileSource;
            return player;
        }
        return null;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        ActiveBoss active = this.plugin.getBossManager().getActive(event.getEntity().getUniqueId());
        if (active == null) {
            return;
        }
        this.plugin.getBossManager().remove(event.getEntity().getUniqueId());
        ConfigurationSection rewards = active.getConfig().getConfigurationSection("rewards");
        if (rewards == null) {
            return;
        }
        List ranked = active.getDamageByPlayer().entrySet().stream().sorted((a, b) -> Double.compare((Double)b.getValue(), (Double)a.getValue())).toList();
        long top1 = rewards.getLong("top1-credits", 0L);
        long top2 = rewards.getLong("top2-credits", 0L);
        long top3 = rewards.getLong("top3-credits", 0L);
        long partCoins = rewards.getLong("participation-coins", 0L);
        long partGems = rewards.getLong("participation-gems", 0L);
        long partExp = rewards.getLong("participation-exp", 0L);
        for (int i = 0; i < ranked.size(); ++i) {
            long bonus;
            PlayerData data;
            UUID uuid = (UUID)((Map.Entry)ranked.get(i)).getKey();
            Player player = Bukkit.getPlayer((UUID)uuid);
            if (player == null || (data = this.plugin.getPlayerManager().get(player)) == null) continue;
            data.setBossKills(data.getBossKills() + 1L);
            data.addCoins(partCoins);
            data.addGems(partGems);
            this.plugin.getPlayerManager().addExp(player, partExp);
            switch (i) {
                case 0: {
                    long l = top1;
                    break;
                }
                case 1: {
                    long l = top2;
                    break;
                }
                case 2: {
                    long l = top3;
                    break;
                }
                default: {
                    long l = bonus = 0L;
                }
            }
            if (bonus > 0L) {
                data.setCredits(data.getCredits() + bonus);
                player.sendMessage("\u00a76\u00a7lBOSS DEFEATED! \u00a7r\u00a77You placed \u00a7e#" + (i + 1) + " \u00a77and earned \u00a7e" + bonus + " credits\u00a77!");
                continue;
            }
            player.sendMessage("\u00a76\u00a7lBOSS DEFEATED! \u00a7r\u00a77You helped and earned \u00a7e" + partCoins + " coins\u00a77!");
        }
    }
}

