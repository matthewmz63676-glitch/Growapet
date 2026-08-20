package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.StatsMenu;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StatsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public StatsCommand(GrowAPet plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){
        if(!(sender instanceof Player player)){Messages.send(sender,"<red>Only players can use this command.</red>");return true;}
        if (!player.hasPermission("growapet.stats")) { Messages.send(player, "<red>You do not have permission to view stats.</red>"); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("growapet.admin.stats")) { Messages.send(player, "<red>You do not have permission to reload stats.</red>"); return true; }
            if (plugin.getConfigManager().reloadAll()) Messages.send(player, "<green>Stats configuration reloaded.</green>"); else Messages.send(player, "<red>Stats configuration was rejected safely.</red>");
            return true;
        }
        if(plugin.getPlayerManager().get(player)==null){Messages.send(player,"<red>Your profile is still loading. Please try again.</red>");return true;}
        Player target = player;
        if (args.length > 0 && !args[0].isBlank()) {
            if (!player.hasPermission("growapet.stats.others")) { Messages.send(player, "<red>You do not have permission to view another player's stats.</red>"); return true; }
            target = org.bukkit.Bukkit.getPlayerExact(args[0]);
            if (target == null || plugin.getPlayerManager().get(target) == null) { Messages.send(player, "<red>That player is offline or their profile is not loaded.</red>"); return true; }
        }
        new StatsMenu(plugin,player,target).open();return true;
    }
}
