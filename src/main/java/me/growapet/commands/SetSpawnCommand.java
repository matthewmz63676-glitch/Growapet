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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public SetSpawnCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (!sender.hasPermission("growapet.admin")) {
            sender.sendMessage("\u00a7cYou do not have permission.");
            return true;
        }
        this.plugin.getSpawnManager().setSpawn(player.getLocation());
        player.sendMessage("\u00a7aSpawn set to your current location.");
        return true;
    }
}

