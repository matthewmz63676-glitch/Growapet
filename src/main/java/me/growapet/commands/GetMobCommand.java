/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import java.util.ArrayList;
import java.util.List;
import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import me.growapet.mobs.MobManager;
import me.growapet.utils.Messages;

public class GetMobCommand
implements CommandExecutor,
TabCompleter {
    private final GrowAPet plugin;

    public GetMobCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.mob")) {
            sender.sendMessage("\u00a7cYou do not have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("\u00a7cUsage: /getmob <player> <mob_name>");
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[0]);
        if (target == null) {
            sender.sendMessage("\u00a7cPlayer not found (must be online).");
            return true;
        }
        MobManager.SpawnResult result = this.plugin.getMobManager().spawnAdminMob(args[1], target.getLocation());
        if (!result.successful()) {
            Messages.send(sender, "<red>Could not spawn <white><mob></white>.</red> <gray><reason></gray>",
                    Messages.value("mob", args[1]), Messages.value("reason", result.detail()));
            return true;
        }
        Messages.send(sender, "<green>Spawned <yellow><mob></yellow> at <white><player></white>'s location.</green>",
                Messages.value("mob", args[1]), Messages.value("player", target.getName()));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> suggestions;
        block4: {
            block3: {
                suggestions = new ArrayList<String>();
                if (args.length != 1) break block3;
                String partial = args[0].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getName().toLowerCase().startsWith(partial)) continue;
                    suggestions.add(player.getName());
                }
                break block4;
            }
            if (args.length != 2) break block4;
            String partial = args[1].toUpperCase();
            ConfigurationSection mobs = this.plugin.getConfigManager().mobs().getConfigurationSection("mobs");
            if (mobs != null) {
                for (String mobId : mobs.getKeys(false)) {
                    if (!mobId.startsWith(partial)) continue;
                    suggestions.add(mobId);
                }
            }
        }
        return suggestions;
    }
}
