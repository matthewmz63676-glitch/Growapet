package me.growapet.announcements;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Configurable, shuffled, non-repeating server tips. */
public final class AnnouncementManager {
    private final GrowAPet plugin;
    private BukkitTask task;
    private List<String> messages = List.of();
    private int next;

    public AnnouncementManager(GrowAPet plugin) { this.plugin = plugin; reload(); }

    public void start() {
        stop();
        if (!enabled() || messages.isEmpty()) return;
        long interval = intervalTicks();
        long initial = initialDelayTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::announceNext, initial, interval);
    }

    public void stop() { if (task != null) task.cancel(); task = null; }

    public void reload() { 
        var config = plugin.getConfigManager().announcements();
        List<String> loaded = config.getStringList("messages").stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        messages = List.copyOf(loaded);
        next = 0;
        if (config.getBoolean("random-order", true)) reshuffle();
    }

    public void restart() { reload(); start(); }

    public void preview(Player player) { if (player != null && !messages.isEmpty()) { player.sendMessage(render(messages.get(next % messages.size()))); playSound(player); } }

    public void announceNext() {
        if (messages.isEmpty()) return;
        if (next >= messages.size()) { next = 0; if (plugin.getConfigManager().announcements().getBoolean("random-order", true)) reshuffle(); }
        Component message = render(messages.get(next++));
        Bukkit.broadcast(message);
        playSound();
    }

    public List<String> messages() { return messages; }

    private Component render(String body) {
        var config = plugin.getConfigManager().announcements();
        String prefix = config.getString("prefix", "<dark_gray>[</dark_gray><gold><bold>TIP</bold></gold><dark_gray>]</dark_gray> ");
        Component result = Messages.parse(prefix + body).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        String discord = config.getString("links.discord-url", "");
        String store = config.getString("links.store-url", "");
        Component links = Component.empty();
        if (discord != null && !discord.isBlank()) links = links.append(link("[Discord]", discord, NamedTextColor.AQUA));
        if (store != null && !store.isBlank()) links = links.append(links.children().isEmpty() ? Component.empty() : Component.text("  ")).append(link("[Store]", store, NamedTextColor.YELLOW));
        return links.children().isEmpty() ? result : result.append(Component.newline()).append(links);
    }

    private void playSound() {
        String configured = plugin.getConfigManager().announcements().getString("sound", "BLOCK_NOTE_BLOCK_PLING");
        Sound sound = configured == null ? null : Registry.SOUNDS.get(NamespacedKey.minecraft(configured.toLowerCase(Locale.ROOT)));
        if (sound == null) return;
        float volume = (float) Math.max(0, Math.min(2, plugin.getConfigManager().announcements().getDouble("sound-volume", .7)));
        float pitch = (float) Math.max(.5, Math.min(2, plugin.getConfigManager().announcements().getDouble("sound-pitch", 1.2)));
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void playSound(Player player) {
        String configured = plugin.getConfigManager().announcements().getString("sound", "BLOCK_NOTE_BLOCK_PLING");
        Sound sound = configured == null ? null : Registry.SOUNDS.get(NamespacedKey.minecraft(configured.toLowerCase(Locale.ROOT)));
        if (sound == null) return;
        float volume = (float) Math.max(0, Math.min(2, plugin.getConfigManager().announcements().getDouble("sound-volume", .7)));
        float pitch = (float) Math.max(.5, Math.min(2, plugin.getConfigManager().announcements().getDouble("sound-pitch", 1.2)));
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static Component link(String label, String url, NamedTextColor color) {
        return Component.text(label, color).clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text("Open " + label.substring(1, label.length() - 1))));
    }

    private boolean enabled() { return plugin.getConfigManager().announcements().getBoolean("enabled", true); }
    private long intervalTicks() { var config = plugin.getConfigManager().announcements(); long minutes = config.getLong("interval-minutes", -1); long seconds = minutes > 0 ? minutes * 60 : config.getLong("interval-seconds", 45 * 60); return Math.max(10, Math.min(86400, seconds)) * 20L; }
    private long initialDelayTicks() { var config = plugin.getConfigManager().announcements(); long minutes = config.getLong("initial-delay-minutes", -1); long seconds = minutes >= 0 ? minutes * 60 : config.getLong("initial-delay-seconds", 2 * 60); return Math.max(0, Math.min(86400, seconds)) * 20L; }
    private void reshuffle() { List<String> shuffled = new ArrayList<>(messages); Collections.shuffle(shuffled); messages = List.copyOf(shuffled); }
}
