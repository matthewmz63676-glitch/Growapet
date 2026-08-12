package me.growapet.hud;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBarManager {
    public static final int PRIORITY_KILL_RECEIPT = 100;

    private final GrowAPet plugin;
    private final Map<UUID, Override> overrides = new HashMap<>();
    private BukkitTask task;

    public ActionBarManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
    }

    public void showTemporary(Player player, Component message, Duration duration, int priority) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Action bars must be changed on the server thread");
        if (player == null || message == null || duration == null || duration.isNegative() || duration.isZero()) return;
        long expiresAt = saturatedAdd(System.currentTimeMillis(), duration.toMillis());
        Override current = overrides.get(player.getUniqueId());
        if (current != null && current.expiresAt > System.currentTimeMillis() && current.priority > priority) return;
        overrides.put(player.getUniqueId(), new Override(message, expiresAt, priority));
        if (enabledFor(player)) player.sendActionBar(message);
    }

    public void clear(UUID playerId) {
        overrides.remove(playerId);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        overrides.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now || Bukkit.getPlayer(entry.getKey()) == null);
        ConfigurationSection section = plugin.getConfigManager().hud().getConfigurationSection("actionbar");
        if (section == null || !section.getBoolean("enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabledFor(player)) continue;
            Override temporary = overrides.get(player.getUniqueId());
            if (temporary != null && temporary.expiresAt > now) {
                player.sendActionBar(temporary.message);
                continue;
            }
            PlayerData data = plugin.getPlayerManager().get(player);
            if (data == null) continue;
            String raw = section.getString("format", "");
            player.sendActionBar(HudComponentBuilder.render(plugin, player, data, raw));
        }
    }

    private boolean enabledFor(Player player) {
        return plugin.getOptionsManager().isLoaded(player.getUniqueId())
                && plugin.getOptionsManager().enabled(player.getUniqueId(), "actionbar", true);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        overrides.clear();
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record Override(Component message, long expiresAt, int priority) {}
}
