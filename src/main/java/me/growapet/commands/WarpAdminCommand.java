package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WarpAdminCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public WarpAdminCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.warp")) { Messages.send(sender, "<red>You do not have permission to administer warps.</red>"); return true; }
        if (label.equalsIgnoreCase("setwarp")) {
            if (!(sender instanceof Player player) || args.length != 1) { Messages.send(sender, "<yellow>Usage: /setwarp <name></yellow>"); return true; }
            Messages.send(sender, plugin.getWarpService().set(args[0], player.getLocation()) ? "<green>Warp saved.</green>" : "<red>Warp location is unsafe or invalid.</red>"); return true;
        }
        if (label.equalsIgnoreCase("warpplayer")) {
            if (args.length != 2) { Messages.send(sender, "<yellow>Usage: /warpplayer <player> <warp></yellow>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !plugin.getWarpService().teleport(target, args[1], true)) Messages.send(sender, "<red>Target or warp was not found.</red>"); else Messages.send(sender, "<green>Forced warp queued.</green>");
            return true;
        }
        Messages.send(sender, "<yellow>Usage: /setwarp <name> or /warpplayer <player> <warp></yellow>"); return true;
    }
}
