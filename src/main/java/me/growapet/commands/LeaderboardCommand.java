/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.commands;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import me.growapet.GrowAPet;
import me.growapet.database.PlayerDAO;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class LeaderboardCommand
implements CommandExecutor {
    private static final Map<String, Stat> STATS = new LinkedHashMap<String, Stat>();
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final String[] MEDALS;
    private final GrowAPet plugin;

    public LeaderboardCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String key = args.length >= 1 ? args[0].toLowerCase() : "coins";
        Stat stat = STATS.get(key);
        if (stat == null) {
            sender.sendMessage("\u00a7cUnknown stat. Choose one of: " + String.join((CharSequence)", ", STATS.keySet()));
            return true;
        }
        int limit = 10;
        PlayerDAO dao = new PlayerDAO(this.plugin.getDatabase());
        dao.topPlayers(stat.column(), limit * 3).thenAccept(dbTop -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.send(sender, key, stat, (List<PlayerDAO.LeaderboardEntry>)dbTop, limit)));
        return true;
    }

    private void send(CommandSender sender, String key, Stat stat, List<PlayerDAO.LeaderboardEntry> dbTop, int limit) {
        LinkedHashMap<UUID, PlayerDAO.LeaderboardEntry> merged = new LinkedHashMap<UUID, PlayerDAO.LeaderboardEntry>();
        for (PlayerDAO.LeaderboardEntry entry : dbTop) {
            merged.put(entry.uuid(), entry);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = this.plugin.getPlayerManager().get(player);
            if (data == null) continue;
            merged.put(player.getUniqueId(), new PlayerDAO.LeaderboardEntry(player.getUniqueId(), player.getName(), stat.live().applyAsDouble(data)));
        }
        List<Object> ranked = new ArrayList(merged.values());
        ranked.sort(Comparator.comparingDouble(PlayerDAO.LeaderboardEntry::value).reversed());
        if (ranked.size() > limit) {
            ranked = ranked.subList(0, limit);
        }
        if (ranked.isEmpty()) {
            sender.sendMessage("\u00a77No players ranked yet for \u00a7e" + key + "\u00a77.");
            return;
        }
        sender.sendMessage(this.color(stat.label() + " &7- Top " + ranked.size()));
        for (int i = 0; i < ranked.size(); ++i) {
            PlayerDAO.LeaderboardEntry entry = (PlayerDAO.LeaderboardEntry)ranked.get(i);
            Object rank = i < 3 ? MEDALS[i] : "&7#" + (i + 1);
            String value = stat.decimal() ? String.format(Locale.US, "%.2fx", entry.value()) : FORMAT.format((long)entry.value());
            sender.sendMessage(this.color((String)rank + " &f" + entry.name() + " &8- &e" + value));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes((char)'&', (String)text);
    }

    static {
        STATS.put("coins", new Stat("coins", PlayerData::getCoins, false, "&6Coins"));
        STATS.put("gems", new Stat("gems", PlayerData::getGems, false, "&aGems"));
        STATS.put("credits", new Stat("credits", PlayerData::getCredits, false, "&bCredits"));
        STATS.put("level", new Stat("level", PlayerData::getLevel, false, "&dLevel"));
        STATS.put("mobkills", new Stat("mob_kills", PlayerData::getMobKills, false, "&cMob Kills"));
        STATS.put("bosskills", new Stat("boss_kills", PlayerData::getBossKills, false, "&4Boss Kills"));
        STATS.put("damage", new Stat("damage_multiplier", PlayerData::getDamageMultiplier, true, "&cDamage Multiplier"));
        STATS.put("coinmulti", new Stat("coin_multiplier", PlayerData::getCoinMultiplier, true, "&6Coin Multiplier"));
        STATS.put("gemsmulti", new Stat("gem_multiplier", PlayerData::getGemMultiplier, true, "&aGem Multiplier"));
        MEDALS = new String[]{"&6#1", "&7#2", "&c#3"};
    }

    private record Stat(String column, ToDoubleFunction<PlayerData> live, boolean decimal, String label) {
    }
}

