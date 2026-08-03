/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.commands;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetEggCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public GetEggCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EntityType entityType;
        if (!sender.hasPermission("growapet.admin")) {
            sender.sendMessage("\u00a7cYou do not have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("\u00a7cUsage: /getegg <player> <entityType> [incubateSeconds]");
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[0]);
        if (target == null) {
            sender.sendMessage("\u00a7cPlayer not found (must be online).");
            return true;
        }
        try {
            entityType = EntityType.valueOf((String)args[1].toUpperCase());
        }
        catch (IllegalArgumentException e) {
            sender.sendMessage("\u00a7cUnknown entity type: " + args[1]);
            return true;
        }
        int seconds = 60;
        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
            }
            catch (NumberFormatException e) {
                sender.sendMessage("\u00a7cIncubate seconds must be a number.");
                return true;
            }
        }
        ItemStack egg = this.plugin.getEggManager().createEgg(entityType, seconds);
        target.getInventory().addItem(new ItemStack[]{egg});
        sender.sendMessage("\u00a7aGave " + target.getName() + " a " + entityType.name() + " egg.");
        return true;
    }
}

