/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import me.growapet.GrowAPet;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public SpawnCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        Location spawn = this.plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            player.sendMessage("\u00a7cSpawn hasn't been set yet. An admin needs to run /setspawn first.");
            return true;
        }
        player.teleport(spawn);
        player.sendMessage("\u00a7aTeleported to spawn.");
        return true;
    }
}

