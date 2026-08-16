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
import me.growapet.utils.LocationSafety;

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
        Location destination = LocationSafety.prepareForUse(spawn, "Server spawn");
        if (destination == null) {
            player.sendMessage("\u00a7cThe configured spawn is unavailable or unsafe. An admin must run /setspawn again.");
            return true;
        }
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) player.sendMessage("\u00a7aTeleported to spawn.");
            else player.sendMessage("\u00a7cThe server could not complete that teleport safely.");
        });
        return true;
    }
}
