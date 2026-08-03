/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public StatsCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            player.sendMessage("\u00a7cYour data hasn't finished loading yet.");
            return true;
        }
        player.sendMessage("\u00a7b\u00a7lYour Stats\n\u00a77Level: \u00a7e%d \u00a77(%.1f%% to next)\n\u00a77Coins: \u00a7e%d \u00a77| Gems: \u00a7e%d \u00a77| Credits: \u00a7e%d\n\u00a77Mob Kills: \u00a7e%d \u00a77| Boss Kills: \u00a7e%d\n\u00a77Eggs Hatched: \u00a7e%d \u00a77| Pets Collected: \u00a7e%d\n\u00a77Trades: \u00a7e%d \u00a77| Quests Completed: \u00a7e%d\n".formatted(data.getLevel(), data.getExpProgress() * 100.0, data.getCoins(), data.getGems(), data.getCredits(), data.getMobKills(), data.getBossKills(), data.getEggsHatched(), data.getPetsCollected(), data.getTrades(), data.getQuestsCompleted()));
        return true;
    }
}

