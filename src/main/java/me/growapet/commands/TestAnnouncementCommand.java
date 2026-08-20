package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Preview the next configured announcement without advancing the cycle. */
public final class TestAnnouncementCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TestAnnouncementCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.announcements")) { Messages.send(sender, "<red>You do not have permission to preview announcements.</red>"); return true; }
        if (!(sender instanceof Player player)) { Messages.send(sender, "<yellow>This preview is player-only.</yellow>"); return true; }
        plugin.getAnnouncementManager().preview(player); return true;
    }
}
