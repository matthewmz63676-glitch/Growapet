package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class TutorialCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TutorialCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.tutorial") && !sender.hasPermission("growapet.admin.tutorial")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("validate")) {
            if (!sender.hasPermission("growapet.admin.tutorial")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
            List<String> problems = plugin.getTutorialManager().validateRoute();
            if (problems.isEmpty()) Messages.send(sender, "<green>Tutorial route is valid and all four points are usable.</green>");
            else for (String problem : problems) Messages.send(sender, "<red>Route issue:</red> <gray><problem></gray>", Messages.value("problem", problem));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("setpoint")) {
            if (!(sender instanceof Player player) || !sender.hasPermission("growapet.admin.tutorial") || args.length != 2 || !List.of("start", "mob", "shop", "egg").contains(args[1].toLowerCase())) { Messages.send(sender, "<red>Usage:</red> <gray>/tutorial setpoint <start|mob|shop|egg></gray>"); return true; }
            boolean saved = plugin.getTutorialManager().setPoint(args[1].toLowerCase(), player.getLocation());
            Messages.send(sender, saved ? "<green>Saved the <yellow><point></yellow> tutorial point here.</green>" : "<red>That location is not safe or could not be saved.</red>", Messages.value("point", args[1].toLowerCase()));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("preview")) {
            if (!(sender instanceof Player player) || !sender.hasPermission("growapet.admin.tutorial") || args.length != 1) { Messages.send(sender, "<red>Usage:</red> <gray>/tutorial preview</gray>"); return true; }
            if (!plugin.getTutorialManager().preview(player)) Messages.send(sender, "<red>Preview unavailable; run <white>/tutorial validate</white> and fix the route first.</red>");
            return true;
        }
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
        Messages.send(sender, "<yellow>Usage: /tutorial [start|stop] or /tutorial <start|stop|reset|setpoint|preview|validate> ...</yellow>"); return true;
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
