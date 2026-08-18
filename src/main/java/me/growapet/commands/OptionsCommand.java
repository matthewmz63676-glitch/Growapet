package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.OptionsMenu;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class OptionsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public OptionsCommand(GrowAPet plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){
        if(!(sender instanceof Player player)){Messages.send(sender,"<red>Only players can use this command.</red>");return true;}
        if(args.length==1&&(args[0].equalsIgnoreCase("actionbar")||args[0].equalsIgnoreCase("trade")||args[0].equalsIgnoreCase("trades")||args[0].equalsIgnoreCase("discord")||args[0].equalsIgnoreCase("relay"))){
            boolean actionbar=args[0].equalsIgnoreCase("actionbar");
            boolean relay=args[0].equalsIgnoreCase("discord")||args[0].equalsIgnoreCase("relay");
            String key=actionbar?"actionbar":relay?"discord_relay":"trade_requests";
            String display=actionbar?"Action bar":relay?"Discord relay":"Trade requests";
            boolean next=!plugin.getOptionsManager().enabled(player.getUniqueId(),key,true);
            if(!actionbar)plugin.getTradeManager().onTradePreferenceChanged(player.getUniqueId(),next);
            plugin.getOptionsManager().set(player.getUniqueId(),key,String.valueOf(next)).whenComplete((ignored,error)->
                    Bukkit.getScheduler().runTask(plugin,()->{
                        if(!player.isOnline())return;
                                if(error!=null){if(!actionbar&&!relay)plugin.getTradeManager().onTradePreferenceChanged(player.getUniqueId(),!next);Messages.send(player,"<red>Could not save that preference. Your previous setting was restored.</red>");return;}
                        Messages.send(player,"<aqua><bold>PLAYER OPTIONS</bold></aqua> <dark_gray>•</dark_gray> <gray><name> →</gray> <state>",
                                Messages.value("name",display),Messages.value("state",next?"enabled":"disabled"));
                    }));
            return true;
        }
        new OptionsMenu(plugin,player).open();
        return true;
    }
}
