package me.growapet.bosses;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ActiveBoss {
    private final String bossId;
    private final LivingEntity entity;
    private final ConfigurationSection config;
    private final Map<UUID, Double> damageByPlayer = new LinkedHashMap<>();
    private final UUID runId;
    private final double maxHealth;
    private double health;

    public ActiveBoss(String bossId, LivingEntity entity, ConfigurationSection config) {
        this(bossId, entity, config, UUID.randomUUID(), Math.max(1, config.getDouble("health", 1000)), Map.of());
    }

    public ActiveBoss(String bossId, LivingEntity entity, ConfigurationSection config, UUID runId,
                      double health, Map<UUID, Double> damage) {
        this.bossId = bossId;
        this.entity = entity;
        this.config = config;
        this.runId = runId == null ? UUID.randomUUID() : runId;
        this.maxHealth = Math.max(1, config.getDouble("health", 1000));
        this.health = Math.max(1, Math.min(this.maxHealth, health));
        damage.forEach((player, amount) -> {
            if (player != null && amount != null && Double.isFinite(amount) && amount > 0) damageByPlayer.put(player, amount);
        });
    }

    public void addDamage(UUID player, double amount) {
        if (player != null && Double.isFinite(amount) && amount > 0) damageByPlayer.merge(player, amount, Double::sum);
    }

    public boolean damage(double amount) {
        if (Double.isFinite(amount) && amount > 0) health = Math.max(0, health - amount);
        return health <= 0;
    }

    public String getBossId() { return bossId; }
    public LivingEntity getEntity() { return entity; }
    public ConfigurationSection getConfig() { return config; }
    public Map<UUID, Double> getDamageByPlayer() { return damageByPlayer; }
    public UUID getRunId() { return runId; }
    public double getMaxHealth() { return maxHealth; }
    public double getHealth() { return health; }
}
