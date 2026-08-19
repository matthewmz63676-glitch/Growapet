package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.PlotSettingsMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("home")) {
            this.plugin.getPlotVisitManager().home(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("visit")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /plot visit <player>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            this.plugin.getPlotVisitManager().visit(player, target.getUniqueId());
            return true;
        }
        if (args[0].equalsIgnoreCase("exit")) {
            this.plugin.getPlotVisitManager().exit(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("settings")) {
            new PlotSettingsMenu(this.plugin, player).open();
            return true;
        }
        player.sendMessage("§7/plot | /plot home | /plot visit <player> | /plot exit | /plot settings");
        return true;
    }
}
