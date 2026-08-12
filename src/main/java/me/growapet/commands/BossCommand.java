/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import java.util.Map;
import java.util.UUID;
import me.growapet.GrowAPet;
import me.growapet.bosses.ActiveBoss;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BossCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public BossCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
            if (!sender.hasPermission("growapet.admin.boss")) {
                sender.sendMessage("\u00a7cYou do not have permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("\u00a7cUsage: /boss spawn <bossId>");
                return true;
            }
            ActiveBoss active = this.plugin.getBossManager().spawn(args[1]);
            if (active == null) {
                sender.sendMessage("\u00a7cCouldn't spawn that boss (unknown id, already active, or zone/warp not configured).");
            } else {
                sender.sendMessage("\u00a7aSpawned boss: \u00a7e" + args[1]);
            }
            return true;
        }
        Map<UUID, ActiveBoss> active = this.plugin.getBossManager().getActiveBosses();
        if (active.isEmpty()) {
            sender.sendMessage("\u00a77No bosses are currently active.");
            return true;
        }
        sender.sendMessage("\u00a7c\u00a7lActive Bosses");
        for (ActiveBoss boss : active.values()) {
            double hp = boss.getHealth();
            double max = boss.getMaxHealth();
            sender.sendMessage(" \u00a77- \u00a7e" + boss.getBossId() + " \u00a77HP: \u00a7c" + Math.round(hp) + "/" + Math.round(max));
            boss.getDamageByPlayer().entrySet().stream().sorted((a, b) -> Double.compare((Double)b.getValue(), (Double)a.getValue())).limit(3L).forEach(entry -> {
                Player p = this.plugin.getServer().getPlayer((UUID)entry.getKey());
                String name = p != null ? p.getName() : ((UUID)entry.getKey()).toString();
                sender.sendMessage("    \u00a77" + name + ": \u00a7e" + Math.round((Double)entry.getValue()) + " dmg");
            });
        }
        return true;
    }
}
