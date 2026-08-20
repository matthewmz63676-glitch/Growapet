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
import me.growapet.gui.WarpMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.growapet.utils.Messages;

public class WarpCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public WarpCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("growapet.warp")) { Messages.send(player, "<red>You do not have permission to use warps.</red>"); return true; }
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("list")) { Messages.send(player, "<aqua>Custom warps</aqua> <dark_gray>•</dark_gray> <white>" + String.join(", ", plugin.getWarpService().customIds()) + "</white>"); return true; }
            if (plugin.getWarpService().teleport(player, args[0], false)) return true;
            if (plugin.getZoneManager().getZone(args[0]) != null) { plugin.getZoneManager().teleport(player, args[0]); return true; }
            Messages.send(player, "<red>Unknown warp. Use /warps to browse zones.</red>"); return true;
        }
        new WarpMenu(this.plugin, player).open();
        return true;
    }
}
