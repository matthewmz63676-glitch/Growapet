/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 *  org.bukkit.scoreboard.DisplaySlot
 *  org.bukkit.scoreboard.Objective
 *  org.bukkit.scoreboard.Scoreboard
 *  org.bukkit.scoreboard.ScoreboardManager
 *  org.bukkit.scoreboard.Team
 */
package me.growapet.hud;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import me.growapet.hud.GradientText;
import me.growapet.hud.HudPlaceholders;
import me.growapet.hud.IconResolver;
import me.growapet.integration.LuckPermsHook;
import me.growapet.models.PlayerData;
import me.growapet.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public class HudManager {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final long ACTIONBAR_PERIOD_TICKS = 10L;
    private static final long SLOW_PERIOD_TICKS = 20L;
    private final GrowAPet plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<UUID, Scoreboard>();
    private BukkitTask slowTask;
    private BukkitTask actionBarTask;

    public HudManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.slowTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::tickScoreboardAndTab, 20L, 20L);
        this.actionBarTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::tickActionBar, 10L, 10L);
    }

    public void stop() {
        if (this.slowTask != null) {
            this.slowTask.cancel();
        }
        if (this.actionBarTask != null) {
            this.actionBarTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
            player.setPlayerListHeaderFooter("", "");
            player.setPlayerListName(player.getName());
        }
        this.boards.clear();
    }

    private void tickScoreboardAndTab() {
        FileConfiguration hud = this.plugin.getConfigManager().hud();
        if (hud == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = this.plugin.getPlayerManager().get(player);
            if (data == null) continue;
            this.updateScoreboard(player, data, hud);
            this.updateTab(player, data, hud);
        }
    }

    private void tickActionBar() {
        FileConfiguration hud = this.plugin.getConfigManager().hud();
        if (hud == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = this.plugin.getPlayerManager().get(player);
            if (data == null) continue;
            this.updateActionBar(player, data, hud);
        }
    }

    public void update(Player player) {
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return;
        }
        FileConfiguration hud = this.plugin.getConfigManager().hud();
        if (hud == null) {
            return;
        }
        this.updateScoreboard(player, data, hud);
        this.updateActionBar(player, data, hud);
        this.updateTab(player, data, hud);
    }

    private String resolve(GrowAPet plugin, Player player, PlayerData data, FileConfiguration hud, String template) {
        String withIcons = IconResolver.apply(hud, template);
        return Utils.colorize(HudPlaceholders.resolve(plugin, player, data, withIcons));
    }

    private String buildTitle(Player player, PlayerData data, FileConfiguration hud, ConfigurationSection section) {
        List gradient = section.getStringList("title-gradient");
        if (gradient.size() >= 2) {
            String text = this.resolve(this.plugin, player, data, hud, section.getString("title-text", "GrowAPet"));
            double phase = (double)(System.currentTimeMillis() % 4000L) / 4000.0 * 2.0;
            String bold = section.getBoolean("title-bold", true) ? "\u00a7l" : "";
            return bold + GradientText.apply(text, (String)gradient.get(0), (String)gradient.get(1), phase);
        }
        return this.resolve(this.plugin, player, data, hud, section.getString("title", "&dGrowAPet"));
    }

    private void updateScoreboard(Player player, PlayerData data, FileConfiguration hud) {
        int i;
        ConfigurationSection section = hud.getConfigurationSection("scoreboard");
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        Scoreboard board = this.boards.computeIfAbsent(player.getUniqueId(), id -> {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            return manager != null ? manager.getNewScoreboard() : null;
        });
        if (board == null) {
            return;
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        Objective objective = board.getObjective("growapet");
        String title = this.buildTitle(player, data, hud, section);
        if (title.length() > 128) {
            title = title.substring(0, 128);
        }
        if (objective == null) {
            objective = board.registerNewObjective("growapet", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!objective.getDisplayName().equals(title)) {
            objective.setDisplayName(title);
        }
        List rawLines = section.getStringList("lines");
        int score = rawLines.size();
        for (i = 0; i < rawLines.size(); ++i) {
            String line = this.resolve(this.plugin, player, data, hud, (String)rawLines.get(i));
            String entry = "\u00a7" + HEX[i / 16 % 16] + "\u00a7" + HEX[i % 16] + "\u00a7r";
            String teamName = "gapet_" + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(entry);
                objective.getScore(entry).setScore(score);
            }
            team.prefix((Component)LegacyComponentSerializer.legacySection().deserialize(line));
            --score;
        }
        for (i = rawLines.size(); i < 15; ++i) {
            Team stale = board.getTeam("gapet_" + i);
            if (stale == null) continue;
            stale.unregister();
        }
    }

    private void updateActionBar(Player player, PlayerData data, FileConfiguration hud) {
        ConfigurationSection section = hud.getConfigurationSection("actionbar");
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        String format = section.getString("format", "");
        if (format.isEmpty()) {
            return;
        }
        String message = this.resolve(this.plugin, player, data, hud, format);
        TextComponent component = LegacyComponentSerializer.legacySection().deserialize(message);
        player.sendActionBar((Component)component);
    }

    private void updateTab(Player player, PlayerData data, FileConfiguration hud) {
        ConfigurationSection section = hud.getConfigurationSection("tab");
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        Object header = this.resolve(this.plugin, player, data, hud, section.getString("header", ""));
        List gradient = section.getStringList("header-gradient");
        if (gradient.size() >= 2) {
            String text = this.resolve(this.plugin, player, data, hud, section.getString("header-gradient-text", "GrowAPet"));
            double phase = (double)(System.currentTimeMillis() % 4000L) / 4000.0 * 2.0;
            header = "\u00a7l" + GradientText.apply(text, (String)gradient.get(0), (String)gradient.get(1), phase) + "\n" + (String)header;
        }
        String footer = this.resolve(this.plugin, player, data, hud, section.getString("footer", ""));
        player.setPlayerListHeaderFooter((String)header, footer);
        String rankPrefix = LuckPermsHook.prefix(player);
        String listName = Utils.colorize((String)(rankPrefix.isEmpty() ? "" : rankPrefix + " ") + "&f" + player.getName());
        if (listName.length() > 128) {
            listName = listName.substring(0, 128);
        }
        player.setPlayerListName(listName);
    }
}

