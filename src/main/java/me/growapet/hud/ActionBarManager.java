package me.growapet.hud;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBarManager implements Listener {
    public static final int PRIORITY_KILL_RECEIPT = 100;

    private final GrowAPet plugin;
    private final Map<UUID, Override> overrides = new HashMap<>();
    private final Set<UUID> spritePackReady = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public ActionBarManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
    }

    /** Sends the configured hosted HUD pack once a player profile is ready. */
    public void requestResourcePack(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Resource-pack requests must run on the server thread");
        if (player == null) return;
        ConfigurationSection section = plugin.getConfigManager().hud().getConfigurationSection("resource-pack");
        if (section == null || !section.getBoolean("enabled", false)) return;
        String url = section.getString("url", "").trim();
        byte[] hash = decodeSha1(section.getString("sha1", ""));
        if (!url.startsWith("https://") || hash == null) {
            plugin.getLogger().warning("HUD resource pack is enabled but its HTTPS URL or SHA-1 is invalid; Unicode fallback remains active.");
            return;
        }
        Component prompt = Messages.parse(section.getString("prompt", "<aqua>Load the GrowAPet HUD pack?</aqua>"));
        try {
            player.setResourcePack(url, hash, prompt, section.getBoolean("required", false));
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not send the HUD resource pack to " + player.getName() + ": " + error.getMessage());
        }
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
        spritePackReady.remove(playerId);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            spritePackReady.add(event.getPlayer().getUniqueId());
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.INVALID_URL
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED) {
            spritePackReady.remove(event.getPlayer().getUniqueId());
        }
    }

    boolean spritesReady(Player player) {
        return player != null && spritePackReady.contains(player.getUniqueId());
    }

    public int readySpritePlayers() { return spritePackReady.size(); }

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
        spritePackReady.clear();
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static byte[] decodeSha1(String value) {
        String normalized = value == null ? "" : value.replace(" ", "").trim();
        if (!normalized.matches("(?i)[0-9a-f]{40}")) return null;
        byte[] result = new byte[20];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private record Override(Component message, long expiresAt, int priority) {}
}
