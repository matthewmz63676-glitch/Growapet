/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.LivingEntity
 */
package me.growapet.bosses;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Generated;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class ActiveBoss {
    private final String bossId;
    private final LivingEntity entity;
    private final ConfigurationSection config;
    private final Map<UUID, Double> damageByPlayer = new LinkedHashMap<UUID, Double>();

    public ActiveBoss(String bossId, LivingEntity entity, ConfigurationSection config) {
        this.bossId = bossId;
        this.entity = entity;
        this.config = config;
    }

    public void addDamage(UUID player, double amount) {
        this.damageByPlayer.merge(player, amount, Double::sum);
    }

    @Generated
    public String getBossId() {
        return this.bossId;
    }

    @Generated
    public LivingEntity getEntity() {
        return this.entity;
    }

    @Generated
    public ConfigurationSection getConfig() {
        return this.config;
    }

    @Generated
    public Map<UUID, Double> getDamageByPlayer() {
        return this.damageByPlayer;
    }
}

