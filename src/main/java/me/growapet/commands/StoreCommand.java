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
import me.growapet.gui.StoreMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.growapet.utils.Messages;

public class StoreCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public StoreCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("check")) { Messages.send(sender, "<yellow>Use /credits show <player> for balance checks.</yellow>"); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) { if (!sender.hasPermission("growapet.admin.store")) Messages.send(sender, "<red>You do not have permission to administer the store.</red>"); else Messages.send(sender, "<yellow>Use /credits and /tagadmin for audited store administration.</yellow>"); return true; }
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("growapet.store")) { Messages.send(player, "<red>You do not have permission to use the store.</red>"); return true; }
        new StoreMenu(this.plugin, player).open();
        return true;
    }
}
