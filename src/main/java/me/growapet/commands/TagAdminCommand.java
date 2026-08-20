package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.tags.TagService;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

public final class TagAdminCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TagAdminCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("growapet.admin.tags")) { Messages.send(sender, "<red>You do not have permission to administer tags.</red>"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { Messages.send(sender, "<aqua>Tags</aqua> <dark_gray>•</dark_gray> <white>" + String.join(", ", plugin.getTagService().all().stream().map(TagService.Definition::id).toList()) + "</white>"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) { plugin.getTagService().load().whenComplete((ignored, error) -> main(() -> Messages.send(sender, error == null ? "<green>Custom tags reloaded.</green>" : "<red>Custom tags could not be reloaded safely.</red>"))); return true; }
        if (sub.equals("create") && args.length >= 4) {
            String id = args[1]; String display = args[2]; String markup = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            plugin.getTagService().create(id, display, markup, sender.getName()).whenComplete((ok, error) -> main(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<green>Custom tag created.</green>" : "<red>Tag creation failed; check its id and markup.</red>")));
            return true;
        }
        if ((sub.equals("give") || sub.equals("take")) && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]); TagService.Definition definition = plugin.getTagService().find(args[2]);
            if (target == null || definition == null) { Messages.send(sender, "<red>Target must be online and the tag must exist.</red>"); return true; }
            var future = sub.equals("give") ? plugin.getTagService().grant(target.getUniqueId(), definition.id()) : plugin.getTagService().revoke(target.getUniqueId(), definition.id());
            future.whenComplete((ok, error) -> main(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<green>Tag ownership updated.</green>" : "<red>Tag ownership was not changed.</red>")));
            return true;
        }
        if (sub.equals("delete") && args.length == 2) { plugin.getTagService().delete(args[1].toLowerCase(Locale.ROOT)).whenComplete((ok, error) -> main(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<green>Custom tag deleted.</green>" : "<red>Only custom tags can be deleted.</red>"))); return true; }
        Messages.send(sender, "<yellow>Usage: /tagadmin <list|reload|create|delete|give|take> ...</yellow>"); return true;
    }
    private void main(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
}
