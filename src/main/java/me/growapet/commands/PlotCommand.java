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
import me.growapet.gui.PlotSettingsMenu;
import me.growapet.models.Plot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlotCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public PlotCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("home")) {
            Plot plot = this.plugin.getPlotManager().getPlot(player.getUniqueId());
            if (plot == null) {
                player.sendMessage("\u00a7cYou don't have a plot yet.");
                return true;
            }
            this.plugin.getPlotManager().teleportHome(player, plot).thenAccept(success -> {
                if (success) me.growapet.utils.Messages.send(player, "<green>Teleported to your plot.</green>");
                else me.growapet.utils.Messages.send(player, "<red>Unable to teleport to your plot safely.</red>");
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("settings")) {
            new PlotSettingsMenu(this.plugin, player).open();
            return true;
        }
        player.sendMessage("\u00a77/plot | /plot home | /plot settings");
        return true;
    }
}
