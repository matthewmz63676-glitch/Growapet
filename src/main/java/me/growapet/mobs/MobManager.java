package me.growapet.mobs;

import me.growapet.GrowAPet;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;

public final class MobManager {
    private final GrowAPet plugin;
    private final Map<UUID, VirtualTextDisplayService.Handle> holograms = new HashMap<>();
    private final Map<UUID, String> displayNames = new HashMap<>();
    private final Map<UUID, Double> customHealth = new HashMap<>();
    private final Map<UUID, Double> customMaxHealth = new HashMap<>();
    private final Map<UUID, String> mobZones = new HashMap<>();
    private final Map<UUID, String> viewerZones = new HashMap<>();
    private final NamespacedKey mobIdKey;
    private final NamespacedKey rewardedKey;
    private final NamespacedKey currentHealthKey;
    private final NamespacedKey weaponDamageKey;
    private final NamespacedKey respawnIdKey;
    private final Set<UUID> scheduledRespawns = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public MobManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.mobIdKey = new NamespacedKey(plugin, "mob_id");
        this.rewardedKey = new NamespacedKey(plugin, "mob_rewarded");
        this.currentHealthKey = new NamespacedKey(plugin, "mob_health");
        this.weaponDamageKey = new NamespacedKey(plugin, "damage");
        this.respawnIdKey = new NamespacedKey(plugin, "respawn_id");
    }

    public void start() {
        restoreTrackedMobs();
        recoverPendingRespawns();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (VirtualTextDisplayService.Handle handle : List.copyOf(holograms.values())) {
            plugin.getVirtualTextDisplays().remove(handle);
        }
        for (UUID entityId : customHealth.keySet()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null) continue;
            for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showEntity(plugin, entity);
        }
        holograms.clear();
        displayNames.clear();
        customHealth.clear();
        customMaxHealth.clear();
        mobZones.clear();
        viewerZones.clear();
        scheduledRespawns.clear();
    }

    public ConfigurationSection getMobConfig(String mobId) {
        FileConfiguration mobs = plugin.getConfigManager().mobs();
        return mobs == null ? null : mobs.getConfigurationSection("mobs." + mobId.toUpperCase(Locale.ROOT));
    }

    public LivingEntity spawnMob(String mobId, Location location) {
        return spawnMobDetailed(mobId, location, null, true).entity();
    }

    /** Administrative placement intentionally bypasses zone membership, but still validates the configured mob. */
    public SpawnResult spawnAdminMob(String mobId, Location location) {
        return spawnMobDetailed(mobId, location, null, false);
    }

    private SpawnResult spawnMobDetailed(String mobId, Location location, UUID respawnId, boolean enforceConfiguredZone) {
        if (mobId == null || location == null || location.getWorld() == null) return SpawnResult.failed(SpawnFailure.INVALID_LOCATION, "The target world is not loaded.");
        String normalizedId = mobId.toUpperCase(Locale.ROOT);
        ConfigurationSection config = getMobConfig(normalizedId);
        if (config == null) return SpawnResult.failed(SpawnFailure.UNKNOWN_MOB, "No mobs.yml entry exists for " + normalizedId + ".");
        if (!config.getBoolean("enabled", true)) return SpawnResult.failed(SpawnFailure.DISABLED, normalizedId + " is disabled in mobs.yml.");
        String configuredZone = config.getString("zone", "");
        if (enforceConfiguredZone && !configuredZone.isBlank() && !plugin.getZoneManager().isLocationInZone(location, configuredZone)) {
            plugin.getLogger().warning("Refused to spawn mob " + normalizedId + " outside configured zone '" + configuredZone + "'.");
            return SpawnResult.failed(SpawnFailure.WRONG_ZONE, "This mob is configured for zone '" + configuredZone + "'.");
        }
        var actualZone = plugin.getZoneManager().getZoneAt(location);
        String visibilityZone = actualZone == null ? (enforceConfiguredZone ? configuredZone : "") : actualZone.getId();

        EntityType type;
        try {
            type = EntityType.valueOf(config.getString("entity", normalizedId).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Invalid entity type for mob " + normalizedId);
            return SpawnResult.failed(SpawnFailure.INVALID_ENTITY_TYPE, "The configured Bukkit entity type is invalid.");
        }
        if (!type.isAlive() || !type.isSpawnable()) return SpawnResult.failed(SpawnFailure.INVALID_ENTITY_TYPE, type.name() + " cannot be spawned as a living mob.");
        try {
            if (!location.getChunk().isLoaded() && !location.getChunk().load()) {
                return SpawnResult.failed(SpawnFailure.CHUNK_LOAD_FAILED, "The target chunk could not be loaded.");
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to load a chunk for mob " + normalizedId, error);
            return SpawnResult.failed(SpawnFailure.CHUNK_LOAD_FAILED, "The target chunk could not be loaded.");
        }

        Entity spawned;
        try {
            spawned = location.getWorld().spawnEntity(location, type);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "World rejected GrowAPet mob " + normalizedId + " at " + location.getWorld().getName()
                            + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(), error);
            return SpawnResult.failed(SpawnFailure.WORLD_REJECTED, "The world rejected that entity spawn; check its difficulty and spawn rules.");
        }
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return SpawnResult.failed(SpawnFailure.INVALID_ENTITY_TYPE, "The spawned entity was not living.");
        }

        double maxHealth = positiveFinite(config.getDouble("health", 20.0), 20.0);
        AttributeInstance maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) maxHealthAttribute.setBaseValue(Math.min(1024.0, maxHealth));
        entity.setHealth(Math.min(maxHealthAttribute == null ? maxHealth : maxHealthAttribute.getValue(), Math.min(1024.0, maxHealth)));
        AttributeInstance attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) attackDamage.setBaseValue(Math.max(0.0, config.getDouble("damage", attackDamage.getBaseValue())));
        entity.setAI(!config.getBoolean("no-ai", true));
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setMaximumNoDamageTicks(0);
        entity.setNoDamageTicks(0);

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(mobIdKey, PersistentDataType.STRING, normalizedId);
        pdc.set(currentHealthKey, PersistentDataType.DOUBLE, maxHealth);
        if (respawnId != null) pdc.set(respawnIdKey, PersistentDataType.STRING, respawnId.toString());
        pdc.remove(rewardedKey);
        customMaxHealth.put(entity.getUniqueId(), maxHealth);
        customHealth.put(entity.getUniqueId(), maxHealth);
        mobZones.put(entity.getUniqueId(), visibilityZone);
        spawnHologram(entity, config.getString("display-name", normalizedId), visibilityZone);
        reconcileEntityVisibility(entity, visibilityZone);
        playConfiguredSound(entity.getLocation(), config, "spawn-sound");
        return SpawnResult.success(entity);
    }

    public boolean isTracked(UUID entityId) {
        return entityId != null && customHealth.containsKey(entityId);
    }

    public boolean isTrackedRegularMob(Entity entity) {
        return entity instanceof LivingEntity && isTracked(entity.getUniqueId());
    }

    public int getTrackedCount() {
        return customHealth.size();
    }

    public double weaponDamage(Player attacker) {
        if (attacker == null) return defaultDamage();
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta()) return defaultDamage();
        Double configured = held.getItemMeta().getPersistentDataContainer().get(weaponDamageKey, PersistentDataType.DOUBLE);
        return positiveFinite(configured == null ? defaultDamage() : configured, defaultDamage());
    }

    public void setWeaponDamage(ItemStack item, double damage) {
        if (item == null || item.getType().isAir()) return;
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(weaponDamageKey, PersistentDataType.DOUBLE,
                Math.min(maxHitDamage(), positiveFinite(damage, defaultDamage())));
        item.setItemMeta(meta);
    }

    public void applyCustomDamage(LivingEntity entity, Player attacker, double rawDamage) {
        if (entity == null || attacker == null || !isTracked(entity.getUniqueId())) return;
        UUID entityId = entity.getUniqueId();
        PlayerData data = plugin.getPlayerManager().get(attacker);
        double playerMultiplier = data == null ? 1.0 : positiveFinite(data.getDamageMultiplier(), 1.0);
        double damage = positiveFinite(rawDamage, defaultDamage()) * playerMultiplier * equippedPetDamageMultiplier(attacker.getUniqueId());
        if (data != null && Math.random() < Math.max(0.0, Math.min(1.0, data.getCriticalChance()))) {
            damage *= positiveFinite(data.getCriticalDamage(), 1.0);
        }
        damage = Math.min(maxHitDamage(), positiveFinite(damage, defaultDamage()));
        double remaining = Math.max(0.0, customHealth.getOrDefault(entityId, 0.0) - damage);
        customHealth.put(entityId, remaining);
        entity.getPersistentDataContainer().set(currentHealthKey, PersistentDataType.DOUBLE, remaining);
        entity.setNoDamageTicks(0);
        updateHologram(entity);
        if (remaining <= 0.0) killTracked(entity, attacker);
    }

    public boolean killTracked(LivingEntity entity, Player killer) {
        if (entity == null || killer == null || !isTracked(entity.getUniqueId())) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(rewardedKey, PersistentDataType.BYTE)) return false;
        pdc.set(rewardedKey, PersistentDataType.BYTE, (byte) 1);

        UUID entityId = entity.getUniqueId();
        customHealth.remove(entityId);
        customMaxHealth.remove(entityId);
        mobZones.remove(entityId);
        VirtualTextDisplayService.Handle hologram = holograms.remove(entityId);
        if (hologram != null) plugin.getVirtualTextDisplays().remove(hologram);
        displayNames.remove(entityId);

        String mobId = pdc.get(mobIdKey, PersistentDataType.STRING);
        if (mobId == null) return false;
        Location deathLocation = entity.getLocation().clone();
        ConfigurationSection config = getMobConfig(mobId);
        deathLocation.getWorld().spawnParticle(configuredParticle(config, "death-particle", Particle.EXPLOSION),
                deathLocation.clone().add(0.0, 0.5, 0.0), 1);
        playConfiguredSound(deathLocation, config, "death-sound");
        entity.remove();
        MobRewards.grant(plugin, killer, mobId);

        int respawnSeconds = config == null ? 5 : Math.max(0, config.getInt("respawn-seconds", 5));
        if (respawnSeconds > 0) queueRespawn(mobId, deathLocation, respawnSeconds);
        return true;
    }

    private void queueRespawn(String mobId, Location location, int seconds) {
        if (location.getWorld() == null) return;
        UUID id = UUID.randomUUID();
        long due = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        Respawn row = Respawn.capture(id, mobId, location, due);
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO mob_respawns(respawn_id,mob_id,world,x,y,z,yaw,pitch,due_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, row.id.toString()); statement.setString(2, row.mobId); statement.setString(3, row.world);
                statement.setDouble(4, row.x); statement.setDouble(5, row.y); statement.setDouble(6, row.z);
                statement.setFloat(7, row.yaw); statement.setFloat(8, row.pitch); statement.setLong(9, row.dueAt); statement.executeUpdate();
            }
            return null;
        }).whenComplete((ignored, error) -> {
            if (error != null) plugin.getLogger().severe("Failed to persist mob respawn " + id + ": " + error.getMessage());
            else onMain(() -> schedule(row));
        });
    }

    private void recoverPendingRespawns() {
        plugin.getDatabase().async(connection -> {
            List<Respawn> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM mob_respawns"); ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new Respawn(UUID.fromString(result.getString("respawn_id")), result.getString("mob_id"),
                        result.getString("world"), result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                        result.getFloat("yaw"), result.getFloat("pitch"), result.getLong("due_at")));
            }
            return rows;
        }).whenComplete((rows, error) -> {
            if (error != null) { plugin.getLogger().severe("Failed to recover mob respawns: " + error.getMessage()); return; }
            onMain(() -> rows.forEach(this::schedule));
        });
    }

    private void schedule(Respawn row) {
        if (!scheduledRespawns.add(row.id)) return;
        if (findSpawnedRespawn(row.id) != null) { deleteRespawn(row.id); return; }
        long ticks = Math.max(1, (row.dueAt - System.currentTimeMillis() + 49) / 50);
        Bukkit.getScheduler().runTaskLater(plugin, () -> attemptRespawn(row), ticks);
    }

    private void attemptRespawn(Respawn row) {
        if (!scheduledRespawns.contains(row.id)) return;
        World world = Bukkit.getWorld(row.world);
        if (world == null) { scheduledRespawns.remove(row.id); return; }
        Location location = new Location(world, row.x, row.y, row.z, row.yaw, row.pitch);
        LivingEntity entity = spawnMobDetailed(row.mobId, location, row.id, false).entity();
        if (entity == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptRespawn(row), 600L);
            return;
        }
        deleteRespawn(row.id);
    }

    private LivingEntity findSpawnedRespawn(UUID id) {
        String expected = id.toString();
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities())
            if (expected.equals(entity.getPersistentDataContainer().get(respawnIdKey, PersistentDataType.STRING))) return entity;
        return null;
    }

    private void deleteRespawn(UUID id) {
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mob_respawns WHERE respawn_id=?")) { statement.setString(1, id.toString()); statement.executeUpdate(); }
            return null;
        }).whenComplete((ignored, error) -> {
            if (error == null) scheduledRespawns.remove(id);
            else plugin.getLogger().severe("Failed to clear mob respawn " + id + ": " + error.getMessage());
        });
    }

    private void onMain(Runnable action) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private double equippedPetDamageMultiplier(UUID owner) {
        for (Pet pet : plugin.getPetManager().getPets(owner)) {
            if (pet.isEquipped()) return positiveFinite(pet.getDamageMultiplier(), 1.0);
        }
        return 1.0;
    }

    private void restoreTrackedMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                String mobId = pdc.get(mobIdKey, PersistentDataType.STRING);
                if (mobId == null) continue;
                if (pdc.has(rewardedKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    continue;
                }
                ConfigurationSection config = getMobConfig(mobId);
                if (config == null || !config.getBoolean("enabled", true)) {
                    entity.remove();
                    continue;
                }
                double max = positiveFinite(config.getDouble("health", 20.0), 20.0);
                Double saved = pdc.get(currentHealthKey, PersistentDataType.DOUBLE);
                double current = Math.min(max, positiveFinite(saved == null ? max : saved, max));
                entity.setMaximumNoDamageTicks(0);
                entity.setNoDamageTicks(0);
                customMaxHealth.put(entity.getUniqueId(), max);
                customHealth.put(entity.getUniqueId(), current);
                String zoneId = config.getString("zone", "");
                mobZones.put(entity.getUniqueId(), zoneId);
                pdc.set(currentHealthKey, PersistentDataType.DOUBLE, current);
                spawnHologram(entity, config.getString("display-name", mobId), zoneId);
                reconcileEntityVisibility(entity, zoneId);
            }
        }
    }

    private void spawnHologram(LivingEntity entity, String displayName, String zoneId) {
        UUID entityId = entity.getUniqueId();
        VirtualTextDisplayService.Handle previous = holograms.remove(entityId);
        if (previous != null) plugin.getVirtualTextDisplays().remove(previous);
        displayNames.put(entityId, displayName);
        Location location = hologramLocation(entity);
        VirtualTextDisplayService.Handle handle = plugin.getVirtualTextDisplays().create(
                location,
                hologramText(entityId, displayName),
                viewer -> zoneId == null || zoneId.isBlank() || plugin.getZoneManager().isPlayerInZone(viewer, zoneId)
        );
        holograms.put(entityId, handle);
    }

    private void updateHologram(LivingEntity entity) {
        VirtualTextDisplayService.Handle handle = holograms.get(entity.getUniqueId());
        if (handle == null) return;
        plugin.getVirtualTextDisplays().update(handle,
                hologramText(entity.getUniqueId(), displayNames.getOrDefault(entity.getUniqueId(), entity.getType().name())));
    }

    private Component hologramText(UUID entityId, String displayName) {
        long health = Math.round(Math.max(0.0, customHealth.getOrDefault(entityId, 0.0)));
        long max = Math.round(Math.max(1.0, customMaxHealth.getOrDefault(entityId, 20.0)));
        return Component.text(displayName + " ", NamedTextColor.WHITE)
                .append(Component.text("• ", NamedTextColor.DARK_GRAY))
                .append(Component.text("❤ " + health + "/" + max, NamedTextColor.RED));
    }

    private void tick() {
        for (UUID entityId : List.copyOf(customHealth.keySet())) {
            Entity found = Bukkit.getEntity(entityId);
            if (!(found instanceof LivingEntity entity) || entity.isDead() || !entity.isValid()) {
                customHealth.remove(entityId);
                customMaxHealth.remove(entityId);
                mobZones.remove(entityId);
                displayNames.remove(entityId);
                VirtualTextDisplayService.Handle handle = holograms.remove(entityId);
                if (handle != null) plugin.getVirtualTextDisplays().remove(handle);
                continue;
            }
            VirtualTextDisplayService.Handle handle = holograms.get(entityId);
            if (handle != null) plugin.getVirtualTextDisplays().teleport(handle, hologramLocation(entity));
        }
        reconcileViewerZones();
    }

    private void reconcileViewerZones() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            var zone = plugin.getZoneManager().getZoneAt(viewer.getLocation());
            String currentZone = zone == null ? "" : zone.getId();
            if (currentZone.equals(viewerZones.put(viewer.getUniqueId(), currentZone))) continue;
            for (Map.Entry<UUID, String> entry : mobZones.entrySet()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity != null) applyVisibility(viewer, entity, entry.getValue(), currentZone);
            }
            plugin.getVirtualTextDisplays().refreshViewer(viewer);
        }
        viewerZones.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    public void refreshViewer(Player viewer) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mob visibility must update on the server thread");
        var zone = plugin.getZoneManager().getZoneAt(viewer.getLocation());
        String currentZone = zone == null ? "" : zone.getId();
        viewerZones.put(viewer.getUniqueId(), currentZone);
        for (Map.Entry<UUID, String> entry : mobZones.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null) applyVisibility(viewer, entity, entry.getValue(), currentZone);
        }
        plugin.getVirtualTextDisplays().refreshViewer(viewer);
    }

    private void reconcileEntityVisibility(Entity entity, String zoneId) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            var viewerZone = plugin.getZoneManager().getZoneAt(viewer.getLocation());
            applyVisibility(viewer, entity, zoneId, viewerZone == null ? "" : viewerZone.getId());
        }
    }

    private void applyVisibility(Player viewer, Entity entity, String entityZone, String viewerZone) {
        if (entityZone == null || entityZone.isBlank() || entityZone.equals(viewerZone)) {
            viewer.showEntity(plugin, entity);
        } else {
            viewer.hideEntity(plugin, entity);
        }
    }

    private static Location hologramLocation(LivingEntity entity) {
        return entity.getLocation().add(0.0, entity.getHeight() + 0.35, 0.0);
    }

    private double defaultDamage() {
        return positiveFinite(plugin.getConfigManager().config().getDouble("combat.default-damage", 1.0), 1.0);
    }

    private double maxHitDamage() {
        return Math.max(defaultDamage(), positiveFinite(
                plugin.getConfigManager().config().getDouble("combat.max-hit-damage", 1_000_000.0), 1_000_000.0));
    }

    private static double positiveFinite(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private record Respawn(UUID id, String mobId, String world, double x, double y, double z, float yaw, float pitch, long dueAt) {
        private static Respawn capture(UUID id, String mobId, Location location, long dueAt) {
            return new Respawn(id, mobId, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), dueAt);
        }
    }

    private static Particle configuredParticle(ConfigurationSection config, String key, Particle fallback) {
        if (config == null || !config.isString(key)) return fallback;
        try {
            return Particle.valueOf(config.getString(key).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void playConfiguredSound(Location location, ConfigurationSection config, String key) {
        if (config == null || !config.isString(key) || location.getWorld() == null) return;
        String configured = config.getString(key, "").toLowerCase(Locale.ROOT);
        org.bukkit.NamespacedKey soundKey = org.bukkit.NamespacedKey.fromString(configured.contains(":") ? configured : "minecraft:" + configured);
        Sound sound = soundKey == null ? null : org.bukkit.Registry.SOUNDS.get(soundKey);
        if (sound != null) location.getWorld().playSound(location, sound, 1.0f, 1.0f);
    }

    public enum SpawnFailure { NONE, INVALID_LOCATION, UNKNOWN_MOB, DISABLED, WRONG_ZONE, INVALID_ENTITY_TYPE, CHUNK_LOAD_FAILED, WORLD_REJECTED }
    public record SpawnResult(LivingEntity entity, SpawnFailure failure, String detail) {
        private static SpawnResult success(LivingEntity entity) { return new SpawnResult(entity, SpawnFailure.NONE, ""); }
        private static SpawnResult failed(SpawnFailure failure, String detail) { return new SpawnResult(null, failure, detail); }
        public boolean successful() { return entity != null; }
    }
}
