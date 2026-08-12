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
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class UnlockZoneCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public UnlockZoneCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.zones")) {
            sender.sendMessage("\u00a7cYou do not have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("\u00a7cUsage: /unlockzone <player> <zoneId>");
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[0]);
        if (target == null) {
            sender.sendMessage("\u00a7cPlayer not found (must be online).");
            return true;
        }
        Zone zone = this.plugin.getZoneManager().getZone(args[1]);
        if (zone == null) {
            sender.sendMessage("\u00a7cUnknown zone: " + args[1]);
            return true;
        }
        PlayerData data = this.plugin.getPlayerManager().get(target);
        if (data == null) {
            sender.sendMessage("\u00a7cThat player's data hasn't loaded yet.");
            return true;
        }
        if (data.hasUnlockedZone(zone.getId())) { sender.sendMessage("§eThat zone is already unlocked."); return true; }
        if (!data.tryLockEconomy()) { sender.sendMessage("§cThat player has another transaction in progress."); return true; }
        data.unlockZone(zone.getId());
        this.plugin.getPlayerManager().saveTransaction(data, UUID.randomUUID().toString(), "ADMIN:ZONE:" + zone.getId(), 0, 0, 0)
                .whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{
                    data.unlockEconomy();
                    if(error!=null){data.revokeZone(zone.getId());sender.sendMessage("§cZone unlock failed safely.");return;}
                    sender.sendMessage("§aUnlocked " + zone.getDisplayName() + " for " + target.getName() + ".");
                    if (zone.hasWall()) plugin.getWallManager().playBreakCutscene(target, zone);
                    else target.sendMessage("§aAn admin unlocked §e" + zone.getDisplayName() + " §afor you!");
                }));
        return true;
    }
}
