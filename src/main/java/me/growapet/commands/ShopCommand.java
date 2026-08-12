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
import me.growapet.gui.ShopMenu;
import me.growapet.gui.EggShopMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public ShopCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("eggs")) new EggShopMenu(this.plugin, player).open();
        else new ShopMenu(this.plugin, player).open();
        return true;
    }
}
