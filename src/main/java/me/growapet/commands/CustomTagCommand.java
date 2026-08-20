package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class CustomTagCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public CustomTagCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.tags")) { Messages.send(sender, "<red>You do not have permission to grant custom tags.</red>"); return true; }
        if (args.length != 2) { Messages.send(sender, "<yellow>Usage: /customtag <player> <tag></yellow>"); return true; }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (target == null || plugin.getTagService().find(args[1]) == null) { Messages.send(sender, "<red>Target must be online and the tag must exist.</red>"); return true; }
        plugin.getTagService().issueToken(target.getUniqueId(), args[1]).whenComplete((token, error) -> main(() -> { if (error != null) { Messages.send(sender, "<red>Token issuance failed safely.</red>"); return; } Messages.send(sender, "<green>Custom tag token issued.</green>"); Messages.send(target, "<aqua>Your custom tag token is <white>" + token + "</white>. Use <white>/tag redeem " + token + "</white>.</aqua>"); }));
        return true;
    }
    private void main(Runnable action) { if (plugin.isEnabled()) org.bukkit.Bukkit.getScheduler().runTask(plugin, action); }
}
