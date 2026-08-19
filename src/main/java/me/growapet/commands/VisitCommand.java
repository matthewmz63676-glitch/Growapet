package me.growapet.commands;

import me.growapet.GrowAPet;
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
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage("§cUsage: /visit <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cUse §e/plot home §cto go to your own plot.");
            return true;
        }
        this.plugin.getPlotVisitManager().visit(player, target.getUniqueId());
        return true;
    }
}
