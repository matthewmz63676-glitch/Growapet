package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.QuestMenu;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class QuestsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public QuestsCommand(GrowAPet plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){
        if(!(sender instanceof Player player)){Messages.send(sender,"<red>Only players can use this command.</red>");return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("claim")){plugin.getQuestManager().claim(player,args[1]);return true;}
        new QuestMenu(plugin,player).open();return true;
    }
}
