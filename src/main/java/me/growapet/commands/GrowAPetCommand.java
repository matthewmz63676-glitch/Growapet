/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GrowAPetCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public GrowAPetCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("\u00a77/growapet reload|give|setlevel|setcoins|setgems");
            return true;
        }
        if (!sender.hasPermission("growapet.admin")) {
            sender.sendMessage("\u00a7cYou do not have permission.");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload": {
                this.plugin.getConfigManager().reloadAll();
                sender.sendMessage("\u00a7aGrowAPet configs reloaded.");
                break;
            }
            case "setlevel": 
            case "setcoins": 
            case "setgems": {
                long amount;
                if (args.length < 3) {
                    sender.sendMessage("\u00a7cUsage: /growapet " + args[0] + " <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact((String)args[1]);
                if (target == null) {
                    sender.sendMessage("\u00a7cPlayer not found (must be online).");
                    return true;
                }
                PlayerData data = this.plugin.getPlayerManager().get(target);
                if (data == null) {
                    sender.sendMessage("\u00a7cPlayer data not loaded yet.");
                    return true;
                }
                try {
                    amount = Long.parseLong(args[2]);
                }
                catch (NumberFormatException e) {
                    sender.sendMessage("\u00a7cAmount must be a number.");
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "setlevel": {
                        data.setLevel((int)amount);
                        break;
                    }
                    case "setcoins": {
                        data.setCoins(amount);
                        break;
                    }
                    case "setgems": {
                        data.setGems(amount);
                    }
                }
                this.plugin.getPlayerManager().syncExpBar(target, data);
                sender.sendMessage("\u00a7aUpdated " + target.getName() + "'s " + args[0].substring(3) + " to " + amount);
                break;
            }
            case "give": {
                sender.sendMessage("\u00a7eNot yet implemented \u2014 item/pet giving is planned.");
                break;
            }
            default: {
                sender.sendMessage("\u00a7cUnknown subcommand.");
            }
        }
        return true;
    }
}

