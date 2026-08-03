/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 */
package me.growapet.bosses;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import me.growapet.bosses.ActiveBoss;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public class BossManager {
    private final GrowAPet plugin;
    private final Map<UUID, ActiveBoss> activeBosses = new ConcurrentHashMap<UUID, ActiveBoss>();

    public BossManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public ConfigurationSection getBossConfig(String bossId) {
        return this.plugin.getConfigManager().bosses().getConfigurationSection("bosses." + bossId);
    }

    public ActiveBoss getActive(UUID entityUuid) {
        return this.activeBosses.get(entityUuid);
    }

    public ActiveBoss getActiveByBossId(String bossId) {
        return this.activeBosses.values().stream().filter(b -> b.getBossId().equals(bossId)).findFirst().orElse(null);
    }

    public Map<UUID, ActiveBoss> getActiveBosses() {
        return this.activeBosses;
    }

    public boolean isBossAlreadyActive(String bossId) {
        return this.getActiveByBossId(bossId) != null;
    }

    public ActiveBoss spawn(String bossId) {
        EntityType type;
        ConfigurationSection cfg = this.getBossConfig(bossId);
        if (cfg == null) {
            return null;
        }
        if (this.isBossAlreadyActive(bossId)) {
            return null;
        }
        Location loc = this.resolveSpawnLocation(cfg);
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        try {
            type = EntityType.valueOf((String)cfg.getString("entity", "ZOMBIE"));
        }
        catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }
        LivingEntity entity = (LivingEntity)loc.getWorld().spawnEntity(loc, type);
        double health = cfg.getDouble("health", 1000.0);
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(health);
        }
        entity.setHealth(Math.min(health, maxHealthAttr != null ? maxHealthAttr.getValue() : health));
        entity.setCustomName("\u00a7c\u00a7l" + cfg.getString("display-name", bossId));
        entity.setCustomNameVisible(true);
        entity.setPersistent(true);
        ActiveBoss active = new ActiveBoss(bossId, entity, cfg);
        this.activeBosses.put(entity.getUniqueId(), active);
        return active;
    }

    public void remove(UUID entityUuid) {
        this.activeBosses.remove(entityUuid);
    }

    private Location resolveSpawnLocation(ConfigurationSection cfg) {
        String worldName = cfg.getString("world");
        if (worldName != null) {
            World world = Bukkit.getWorld((String)worldName);
            if (world == null) {
                return null;
            }
            return new Location(world, cfg.getDouble("x"), cfg.getDouble("y"), cfg.getDouble("z"));
        }
        String zoneId = cfg.getString("zone");
        Zone zone = zoneId != null ? this.plugin.getZoneManager().getZone(zoneId) : null;
        return zone != null ? zone.getWarp() : null;
    }
}

