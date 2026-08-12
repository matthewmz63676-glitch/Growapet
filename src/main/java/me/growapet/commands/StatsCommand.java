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
        if(plugin.getPlayerManager().get(player)==null){Messages.send(player,"<red>Your profile is still loading. Please try again.</red>");return true;}
        new StatsMenu(plugin,player).open();return true;
    }
}
