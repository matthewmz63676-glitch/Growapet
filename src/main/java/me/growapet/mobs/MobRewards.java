/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 */
package me.growapet.mobs;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class MobRewards {
    private MobRewards() {
    }

    public static void grant(GrowAPet plugin, Player killer, EntityType type) {
        FileConfiguration mobs = plugin.getConfigManager().mobs();
        ConfigurationSection section = mobs != null ? mobs.getConfigurationSection("mobs." + type.name()) : null;
        long coins = section != null ? section.getLong("coins", 1L) : 1L;
        long gems = section != null ? section.getLong("gems", 0L) : 0L;
        long exp = section != null ? section.getLong("exp", 5L) : 5L;
        PlayerData data = plugin.getPlayerManager().get(killer);
        if (data == null) {
            return;
        }
        data.addCoins(coins);
        data.addGems(gems);
        data.setMobKills(data.getMobKills() + 1L);
        plugin.getPlayerManager().addExp(killer, exp);
    }
}

