package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.mobs.MobSpawnPointManager;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Admin placement/removal command for durable mob populations. */
public final class MobSpawnCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public MobSpawnCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.mob")) {
            Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        String operation = args[0].toLowerCase(Locale.ROOT);
        if (operation.equals("set")) {
            if (!(sender instanceof Player player) || (args.length != 3 && args.length != 4)) {
                Messages.send(sender, "<red>Usage:</red> <gray>/mobspawn set <id> <mob> [max]</gray>");
                return true;
            }
            int max;
            try { max = args.length == 4 ? Integer.parseInt(args[3]) : 1; }
            catch (NumberFormatException error) { Messages.send(sender, "<red>Maximum population must be a whole number from 1 to 100.</red>"); return true; }
            plugin.getMobSpawnPointManager().upsert(args[1], args[2], player.getLocation(), max).whenComplete((ignored, error) -> onMain(() -> {
                if (error != null) Messages.send(sender, "<red>Spawn point was not saved: <white><reason></white></red>", Messages.value("reason", rootMessage(error)));
                else Messages.send(sender, "<green>Spawn point <yellow><id></yellow> saved for <white><mob></white>.</green>", Messages.value("id", args[1]), Messages.value("mob", args[2].toUpperCase(Locale.ROOT)));
            }));
            return true;
        }
        if (operation.equals("remove")) {
            if (args.length != 2) { Messages.send(sender, "<red>Usage:</red> <gray>/mobspawn remove <id></gray>"); return true; }
            plugin.getMobSpawnPointManager().remove(args[1]).whenComplete((ignored, error) -> onMain(() -> {
                if (error != null) Messages.send(sender, "<red>Spawn point could not be removed: <white><reason></white></red>", Messages.value("reason", rootMessage(error)));
                else Messages.send(sender, "<green>Spawn point <yellow><id></yellow> removed and its active mobs were cleared.</green>", Messages.value("id", args[1]));
            }));
            return true;
        }
        Messages.send(sender, "<red>Usage:</red> <gray>/mobspawn <set|remove|list> ...</gray>");
        return true;
    }

    private void list(CommandSender sender) {
        Messages.send(sender, "<aqua><bold>MOB SPAWN POINTS</bold></aqua> <dark_gray>•</dark_gray> <gray>persistent populations</gray>");
        if (plugin.getMobSpawnPointManager().points().isEmpty()) {
            Messages.send(sender, "<gray>No spawn points are configured. Use <white>/mobspawn set <id> <mob> [max]</white>.</gray>");
            return;
        }
        for (MobSpawnPointManager.MobSpawnPoint point : plugin.getMobSpawnPointManager().points()) {
            Messages.send(sender, "<dark_gray>•</dark_gray> <yellow><id></yellow> <gray>→</gray> <white><mob></white> <gray>• zone <zone> • cap <max> • <state></gray>",
                    Messages.value("id", point.id()), Messages.value("mob", point.mobId()), Messages.value("zone", point.zoneId().isBlank() ? "any" : point.zoneId()), Messages.value("max", point.maxCount()), Messages.value("state", point.enabled() ? "enabled" : "disabled"));
        }
    }

    private void onMain(Runnable action) { if (plugin.isEnabled()) org.bukkit.Bukkit.getScheduler().runTask(plugin, action); }
    private static String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage(); }
}
