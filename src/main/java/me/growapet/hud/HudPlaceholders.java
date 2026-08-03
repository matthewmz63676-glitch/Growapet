/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package me.growapet.hud;

import me.growapet.GrowAPet;
import me.growapet.integration.LuckPermsHook;
import me.growapet.models.PlayerData;
import me.growapet.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class HudPlaceholders {
    private HudPlaceholders() {
    }

    public static String resolve(GrowAPet plugin, Player player, PlayerData data, String template) {
        Plot plot = plugin.getPlotManager().getPlot(player.getUniqueId());
        String rank = LuckPermsHook.prefix(player);
        return template.replace("%player%", player.getName()).replace("%rank%", rank).replace("%coins%", String.valueOf(data.getCoins())).replace("%gems%", String.valueOf(data.getGems())).replace("%credits%", String.valueOf(data.getCredits())).replace("%level%", String.valueOf(data.getLevel())).replace("%exp_bar%", String.format("%.1f%%", data.getExpProgress() * 100.0)).replace("%damage%", String.format("%.2f", data.getDamageMultiplier())).replace("%coinmulti%", String.format("%.2f", data.getCoinMultiplier())).replace("%gemsmulti%", String.format("%.2f", data.getGemMultiplier())).replace("%expmulti%", String.format("%.2f", data.getExpMultiplier())).replace("%mob_kills%", String.valueOf(data.getMobKills())).replace("%boss_kills%", String.valueOf(data.getBossKills())).replace("%plot_id%", plot != null ? String.valueOf(plot.getId()) : "-").replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%max_online%", String.valueOf(Bukkit.getMaxPlayers())).replace("%ping%", String.valueOf(player.getPing()));
    }
}

