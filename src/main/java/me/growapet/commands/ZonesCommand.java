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
import me.growapet.zones.Zone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ZonesCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public ZonesCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length >= 1 && args[0].equalsIgnoreCase("unlock")) {
            if (args.length < 2) {
                player.sendMessage("\u00a7cUsage: /zones unlock <zoneId>");
                return true;
            }
            Zone zone = this.plugin.getZoneManager().getZone(args[1]);
            if (zone == null) {
                player.sendMessage("\u00a7cUnknown zone: " + args[1]);
                return true;
            }
            this.plugin.getZoneManager().unlock(player, zone.getId());
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("warp")) {
            if (args.length < 2) {
                player.sendMessage("\u00a7cUsage: /zones warp <zoneId>");
                return true;
            }
            Zone zone = this.plugin.getZoneManager().getZone(args[1]);
            if (zone == null) {
                player.sendMessage("\u00a7cUnknown zone: " + args[1]);
                return true;
            }
            if (!this.plugin.getZoneManager().isUnlocked(player, zone.getId())) {
                player.sendMessage("\u00a7cYou haven't unlocked \u00a7e" + zone.getDisplayName() + "\u00a7c yet.");
                return true;
            }
            this.plugin.getZoneManager().teleport(player, zone.getId());
            return true;
        }
        player.sendMessage("\u00a7b\u00a7lZones");
        for (Zone zone : this.plugin.getZoneManager().getZonesInOrder()) {
            boolean unlocked = this.plugin.getZoneManager().isUnlocked(player, zone.getId());
            if (unlocked) {
                player.sendMessage(" \u00a7a\u2714 \u00a7e" + zone.getDisplayName());
                continue;
            }
            player.sendMessage(" \u00a7c\u2718 \u00a7e" + zone.getDisplayName() + " \u00a77- \u00a7e" + zone.getCost() + " coins");
        }
        player.sendMessage("\u00a77/zones unlock <zoneId> | /zones warp <zoneId>");
        return true;
    }
}

