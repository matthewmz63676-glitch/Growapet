package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.DailyMenu;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DailyCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public DailyCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Players only.</red>"); return true; }
        plugin.getDailyManager().status(player.getUniqueId()).whenComplete((status, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) Messages.send(player, "<red>Daily rewards are temporarily unavailable.</red>");
                    else new DailyMenu(plugin, player, status).open();
                }));
        return true;
    }
}
