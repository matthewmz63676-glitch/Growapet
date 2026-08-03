/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package me.growapet.mobs;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import me.growapet.mobs.MobRewards;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class MobManager {
    private final GrowAPet plugin;
    private final Map<UUID, ArmorStand> holograms = new ConcurrentHashMap<UUID, ArmorStand>();
    private final Map<UUID, String> displayNames = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, Double> customHealth = new ConcurrentHashMap<UUID, Double>();
    private final Map<UUID, Double> customMaxHealth = new ConcurrentHashMap<UUID, Double>();
    private BukkitTask task;

    public MobManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }
        for (ArmorStand hologram : this.holograms.values()) {
            hologram.remove();
        }
        this.holograms.clear();
        this.displayNames.clear();
        this.customHealth.clear();
        this.customMaxHealth.clear();
    }

    public ConfigurationSection getMobConfig(String mobId) {
        FileConfiguration mobs = this.plugin.getConfigManager().mobs();
        return mobs != null ? mobs.getConfigurationSection("mobs." + mobId) : null;
    }

    public LivingEntity spawnMob(String mobId, Location location) {
        LivingEntity entity;
        EntityType type;
        if (location == null || location.getWorld() == null) {
            return null;
        }
        try {
            type = EntityType.valueOf((String)mobId.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return null;
        }
        ConfigurationSection cfg = this.getMobConfig(mobId.toUpperCase());
        if (cfg != null && !cfg.getBoolean("enabled", true)) {
            return null;
        }
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        if ((entity = (LivingEntity)location.getWorld().spawnEntity(location, type)) == null) {
            return null;
        }
        double health = cfg != null ? cfg.getDouble("health", 20.0) : 20.0;
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(health);
        }
        entity.setHealth(Math.min(health, maxHealthAttr != null ? maxHealthAttr.getValue() : health));
        String displayName = cfg != null ? cfg.getString("display-name", mobId) : mobId;
        boolean noAi = cfg == null || cfg.getBoolean("no-ai", true);
        entity.setAI(!noAi);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        this.customMaxHealth.put(entity.getUniqueId(), health);
        this.customHealth.put(entity.getUniqueId(), health);
        this.spawnHologram(entity, displayName);
        return entity;
    }

    public boolean isTracked(UUID entityId) {
        return this.customHealth.containsKey(entityId);
    }

    public void applyCustomDamage(LivingEntity entity, Player attacker, double rawDamage) {
        UUID id = entity.getUniqueId();
        if (!this.customHealth.containsKey(id)) {
            return;
        }
        PlayerData data = this.plugin.getPlayerManager().get(attacker);
        double multiplier = data != null ? data.getDamageMultiplier() : 1.0;
        double damage = rawDamage * (multiplier *= this.equippedPetDamageMultiplier(attacker.getUniqueId()));
        if (data != null && Math.random() < data.getCriticalChance()) {
            damage *= data.getCriticalDamage();
        }
        double remaining = this.customHealth.merge(id, -damage, Double::sum);
        remaining = Math.max(0.0, remaining);
        this.customHealth.put(id, remaining);
        ArmorStand hologram = this.holograms.get(id);
        if (hologram != null) {
            this.updateHologramText(hologram, this.displayNames.getOrDefault(id, entity.getType().name()), entity);
        }
        if (remaining <= 0.0) {
            this.killTracked(entity, attacker);
        }
    }

    private double equippedPetDamageMultiplier(UUID owner) {
        List<Pet> pets = this.plugin.getPetManager().getPets(owner);
        for (Pet pet : pets) {
            if (!pet.isEquipped()) continue;
            return pet.getDamageMultiplier();
        }
        return 1.0;
    }

    private void killTracked(LivingEntity entity, Player killer) {
        int respawnSeconds;
        UUID id = entity.getUniqueId();
        this.customHealth.remove(id);
        this.customMaxHealth.remove(id);
        ArmorStand hologram = this.holograms.remove(id);
        if (hologram != null) {
            hologram.remove();
        }
        this.displayNames.remove(id);
        String mobId = entity.getType().name();
        Location deathLocation = entity.getLocation();
        entity.getWorld().spawnParticle(Particle.EXPLOSION, deathLocation.clone().add(0.0, 0.5, 0.0), 1);
        entity.remove();
        MobRewards.grant(this.plugin, killer, EntityType.valueOf((String)mobId));
        ConfigurationSection cfg = this.getMobConfig(mobId);
        int n = respawnSeconds = cfg != null ? cfg.getInt("respawn-seconds", 5) : 5;
        if (respawnSeconds > 0) {
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.spawnMob(mobId, deathLocation), (long)respawnSeconds * 20L);
        }
    }

    private void spawnHologram(LivingEntity entity, String displayName) {
        Location holoLoc = entity.getLocation().add(0.0, entity.getHeight() + 0.35, 0.0);
        ArmorStand hologram = (ArmorStand)entity.getWorld().spawn(holoLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setSmall(true);
            stand.setCustomNameVisible(true);
        });
        this.holograms.put(entity.getUniqueId(), hologram);
        this.displayNames.put(entity.getUniqueId(), displayName);
        this.updateHologramText(hologram, displayName, entity);
    }

    private void updateHologramText(ArmorStand hologram, String displayName, LivingEntity entity) {
        double hp;
        double max;
        UUID id = entity.getUniqueId();
        if (this.customHealth.containsKey(id)) {
            max = this.customMaxHealth.getOrDefault(id, 20.0);
            hp = Math.max(0.0, this.customHealth.getOrDefault(id, 0.0));
        } else {
            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            max = maxHealthAttr != null ? maxHealthAttr.getValue() : entity.getHealth();
            hp = Math.max(0.0, entity.getHealth());
        }
        hologram.setCustomName(Utils.colorize("&f" + displayName + " &7[&c\u2764 " + Math.round(hp) + "/" + Math.round(max) + "&7]"));
    }

    private void tick() {
        this.holograms.entrySet().removeIf(entry -> {
            LivingEntity living;
            ArmorStand hologram = (ArmorStand)entry.getValue();
            Entity entity = Bukkit.getEntity((UUID)((UUID)entry.getKey()));
            if (!(entity instanceof LivingEntity) || (living = (LivingEntity)entity).isDead() || !living.isValid()) {
                hologram.remove();
                this.displayNames.remove(entry.getKey());
                return true;
            }
            hologram.teleport(living.getLocation().add(0.0, living.getHeight() + 0.35, 0.0));
            String displayName = this.displayNames.getOrDefault(entry.getKey(), living.getType().name());
            this.updateHologramText(hologram, displayName, living);
            return false;
        });
    }
}

