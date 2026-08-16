package me.growapet.bosses;

import me.growapet.GrowAPet;
import me.growapet.events.EventType;
import me.growapet.utils.Messages;
import me.growapet.utils.LocationSafety;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Timed, zone-centred bosses with bounded skills and restart-continuous combat state. */
public final class BossManager {
    private final GrowAPet plugin;
    private final Map<UUID, ActiveBoss> active = new ConcurrentHashMap<>();
    private final Map<String, Long> next = new ConcurrentHashMap<>();
    private final Map<String, RestoredState> restored = new LinkedHashMap<>();
    private final NamespacedKey bossKey;
    private BukkitTask timer;
    private BukkitTask skillTimer;

    public BossManager(GrowAPet plugin) {
        this.plugin = plugin;
        bossKey = new NamespacedKey(plugin, "boss_id");
    }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            Map<String, RestoredState> loaded = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT boss_id,next_spawn_at,active,run_id,health,damage_data FROM boss_state");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) loaded.put(rows.getString("boss_id"), new RestoredState(
                        rows.getLong("next_spawn_at"), rows.getInt("active") == 1,
                        parseUuid(rows.getString("run_id")), rows.getDouble("health"),
                        parseDamage(rows.getString("damage_data"))));
            }
            return loaded;
        }).thenAccept(loaded -> {
            restored.clear();
            restored.putAll(loaded);
            loaded.forEach((id, state) -> next.put(id, state.nextSpawnAt));
        });
    }

    public void start() {
        removeOrphanedBosses();
        for (Map.Entry<String, RestoredState> entry : new ArrayList<>(restored.entrySet())) {
            if (entry.getValue().active) spawn(entry.getKey(), entry.getValue(), false);
        }
        restored.clear();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        skillTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { skills(); persistActiveStates(); }, 100, 100);
    }

    private void removeOrphanedBosses() {
        for (World world : Bukkit.getWorlds()) for (Entity entity : new ArrayList<>(world.getEntities()))
            if (entity.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)) entity.remove();
    }

    private void tick() {
        ConfigurationSection root = plugin.getConfigManager().bosses().getConfigurationSection("bosses");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String id : root.getKeys(false)) {
            if (isBossAlreadyActive(id)) continue;
            long due = next.computeIfAbsent(id, ignored -> now + 60_000);
            if (now >= due) spawn(id);
        }
        cullForViewers();
    }

    private void skills() {
        for (ActiveBoss boss : active.values()) {
            LivingEntity entity = boss.getEntity();
            if (!entity.isValid()) continue;
            double radius = Math.max(0, boss.getConfig().getDouble("skills.area-radius", 0));
            double damage = Math.max(0, boss.getConfig().getDouble("skills.area-damage", 0));
            if (radius <= 0 || damage <= 0) continue;
            entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, entity.getLocation(), 20);
            for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) if (nearby instanceof Player player) player.damage(damage, entity);
        }
    }

    public ConfigurationSection getBossConfig(String id) { return plugin.getConfigManager().bosses().getConfigurationSection("bosses." + id); }
    public ActiveBoss getActive(UUID id) { return active.get(id); }
    public ActiveBoss getActiveByBossId(String id) { return active.values().stream().filter(boss -> boss.getBossId().equals(id)).findFirst().orElse(null); }
    public Map<UUID, ActiveBoss> getActiveBosses() { return Collections.unmodifiableMap(active); }
    public boolean isBossAlreadyActive(String id) { return getActiveByBossId(id) != null; }
    public ActiveBoss spawn(String id) { return spawn(id, null, true); }

    private ActiveBoss spawn(String id, RestoredState state, boolean announce) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Boss spawn off server thread");
        ConfigurationSection config = getBossConfig(id);
        if (config == null || isBossAlreadyActive(id)) return null;
        Location location = resolve(config);
        if (location == null || location.getWorld() == null) return null;
        EntityType type;
        try { type = EntityType.valueOf(config.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT)); }
        catch (Exception error) { return null; }
        Entity raw = location.getWorld().spawnEntity(location, type);
        if (!(raw instanceof LivingEntity entity)) { raw.remove(); return null; }
        double configuredHealth = Math.max(1, config.getDouble("health", 1000));
        double visibleHealth = Math.min(1024, configuredHealth);
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) max.setBaseValue(visibleHealth);
        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(Math.max(0, config.getDouble("damage", 10)));
        entity.setMaximumNoDamageTicks(0);
        entity.setNoDamageTicks(0);
        entity.customName(Messages.parse("<red><bold><name></bold></red>", Messages.value("name", config.getString("display-name", id))));
        entity.setCustomNameVisible(true);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, id);
        ActiveBoss boss = state == null
                ? new ActiveBoss(id, entity, config)
                : new ActiveBoss(id, entity, config, state.runId, state.health <= 0 ? configuredHealth : state.health, state.damage);
        entity.setHealth(Math.max(1, visibleHealth * (boss.getHealth() / boss.getMaxHealth())));
        active.put(entity.getUniqueId(), boss);
        persistActiveState(boss);
        cullForViewers();
        if (announce) Bukkit.broadcast(Messages.parse("<red><bold>BOSS SPAWNED</bold></red> <dark_gray>•</dark_gray> <yellow><name></yellow>", Messages.value("name", config.getString("display-name", id))));
        else plugin.getLogger().info("Restored active boss " + id + " at " + Math.round(boss.getHealth()) + " HP.");
        return boss;
    }

    public void defeated(ActiveBoss boss) {
        active.remove(boss.getEntity().getUniqueId());
        long delay = Math.max(1, boss.getConfig().getLong("respawn-minutes", 60)) * 60_000L;
        if (plugin.getEventManager().isActive(EventType.BOSS_RUSH)) delay = Math.max(30_000L, delay / 4);
        long due = System.currentTimeMillis() + delay;
        next.put(boss.getBossId(), due);
        persistNext(boss.getBossId(), due);
    }

    private void persistNext(String id, long due) {
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO boss_state(boss_id,next_spawn_at,active,run_id,health,damage_data,updated_at) VALUES(?,?,0,NULL,NULL,NULL,?) " +
                            "ON CONFLICT(boss_id) DO UPDATE SET next_spawn_at=excluded.next_spawn_at,active=0,run_id=NULL,health=NULL,damage_data=NULL,updated_at=excluded.updated_at")) {
                statement.setString(1, id); statement.setLong(2, due); statement.setLong(3, System.currentTimeMillis()); statement.executeUpdate();
            }
            return null;
        }).exceptionally(error -> { plugin.getLogger().severe("Failed to persist boss timer for " + id + ": " + error.getMessage()); return null; });
    }

    private void persistActiveStates() {
        for (ActiveBoss boss : new ArrayList<>(active.values())) persistActiveState(boss);
    }

    private void persistActiveState(ActiveBoss boss) {
        StateSnapshot snapshot = StateSnapshot.capture(boss);
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO boss_state(boss_id,next_spawn_at,active,run_id,health,damage_data,updated_at) VALUES(?,0,1,?,?,?,?) " +
                            "ON CONFLICT(boss_id) DO UPDATE SET active=1,run_id=excluded.run_id,health=excluded.health,damage_data=excluded.damage_data,updated_at=excluded.updated_at")) {
                statement.setString(1, snapshot.id); statement.setString(2, snapshot.runId.toString()); statement.setDouble(3, snapshot.health);
                statement.setString(4, serializeDamage(snapshot.damage)); statement.setLong(5, System.currentTimeMillis()); statement.executeUpdate();
            }
            return null;
        }).exceptionally(error -> { plugin.getLogger().severe("Failed to persist active boss " + snapshot.id + ": " + error.getMessage()); return null; });
    }

    private Location resolve(ConfigurationSection config) {
        Zone zone = plugin.getZoneManager().getZone(config.getString("zone", ""));
        if (zone != null) {
            Location center = plugin.getZoneManager().center(zone);
            return center == null ? null : LocationSafety.prepareForUse(center, "boss zone center");
        }
        String worldName = config.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        Location configured = new Location(world, config.getDouble("x"), config.getDouble("y"), config.getDouble("z"));
        return LocationSafety.prepareForUse(configured, "boss location");
    }

    private void cullForViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) refreshViewer(viewer);
    }

    public void refreshViewer(Player viewer) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Boss visibility must update on the server thread");
        Zone viewerZone = plugin.getZoneManager().getZoneAt(viewer.getLocation());
        for (ActiveBoss boss : active.values()) {
            String bossZone = boss.getConfig().getString("zone", "");
            if (viewerZone != null && viewerZone.getId().equals(bossZone)) viewer.showEntity(plugin, boss.getEntity());
            else viewer.hideEntity(plugin, boss.getEntity());
        }
    }

    public void stop() {
        if (timer != null) timer.cancel();
        if (skillTimer != null) skillTimer.cancel();
        persistActiveStates();
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showEntity(plugin, boss.getEntity());
            boss.getEntity().remove();
        }
        active.clear();
    }

    static String serializeDamage(Map<UUID, Double> damage) {
        StringBuilder result = new StringBuilder();
        damage.forEach((uuid, amount) -> {
            if (uuid != null && amount != null && Double.isFinite(amount) && amount > 0) {
                if (!result.isEmpty()) result.append(';');
                result.append(uuid).append('=').append(amount);
            }
        });
        return result.toString();
    }

    static Map<UUID, Double> parseDamage(String encoded) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String entry : encoded.split(";")) {
            int split = entry.indexOf('=');
            if (split < 1) continue;
            try {
                UUID uuid = UUID.fromString(entry.substring(0, split));
                double amount = Double.parseDouble(entry.substring(split + 1));
                if (Double.isFinite(amount) && amount > 0) result.put(uuid, amount);
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    private static UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private record RestoredState(long nextSpawnAt, boolean active, UUID runId, double health, Map<UUID, Double> damage) {}
    private record StateSnapshot(String id, UUID runId, double health, Map<UUID, Double> damage) {
        private static StateSnapshot capture(ActiveBoss boss) { return new StateSnapshot(boss.getBossId(), boss.getRunId(), boss.getHealth(), new HashMap<>(boss.getDamageByPlayer())); }
    }
}
