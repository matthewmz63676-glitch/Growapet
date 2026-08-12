package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TutorialCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TutorialCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.tutorial") && !sender.hasPermission("growapet.admin.tutorial")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
            Player target = target(sender, args.length >= 2 ? args[1] : null); if (target != null) { plugin.getTutorialManager().start(target, !(sender instanceof Player player) || !target.equals(player)); acknowledge(sender, target, "start"); } return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            Player target = target(sender, args.length >= 2 ? args[1] : null); if (target != null) { plugin.getTutorialManager().stop(target); acknowledge(sender, target, "stop"); } return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("growapet.admin.tutorial")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
            Player target = target(sender, args.length >= 2 ? args[1] : null); if (target != null) { plugin.getTutorialManager().reset(target); acknowledge(sender, target, "reset"); } return true;
        }
        Messages.send(sender, "<yellow>Usage: /tutorial [start|stop] or /tutorial <start|stop|reset> <player></yellow>"); return true;
    }

    private static Player target(CommandSender sender, String name) {
        if (name == null) { if (sender instanceof Player player) return player; Messages.send(sender, "<yellow>Console usage: /tutorial <start|stop|reset> <player></yellow>"); return null; }
        if (!sender.hasPermission("growapet.admin.tutorial")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return null; }
        Player target = Bukkit.getPlayerExact(name); if (target == null) Messages.send(sender, "<red>That player is not online.</red>"); return target;
    }

    private static void acknowledge(CommandSender sender, Player target, String action) {
        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) return;
        Messages.send(sender, "<green>Tutorial <action> requested for <white><player></white>.</green>",
                Messages.value("action", action), Messages.value("player", target.getName()));
    }
}
