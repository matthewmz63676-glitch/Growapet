/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VisitCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public VisitCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 1) {
            player.sendMessage("\u00a7cUsage: /visit <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer((String)args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("\u00a7cUse \u00a7e/plot home \u00a7cto go to your own plot.");
            return true;
        }
        Plot plot = this.plugin.getPlotManager().getPlot(target.getUniqueId());
        if (plot == null) {
            player.sendMessage("\u00a7cThat player doesn't have a plot.");
            return true;
        }
        String targetName = target.getName() != null ? target.getName() : args[0];
        this.plugin.getPlotManager().teleportHome(player, plot).thenAccept(success -> {
            if (success) me.growapet.utils.Messages.send(player, "<green>Teleported to <yellow><player></yellow>'s plot.</green>", me.growapet.utils.Messages.value("player", targetName));
            else me.growapet.utils.Messages.send(player, "<red>Unable to teleport to that plot safely.</red>");
        });
        return true;
    }
}
