package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.TagMenu;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TagCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TagCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use tags.</red>"); return true; }
        if (!player.hasPermission("growapet.tags")) { Messages.send(player, "<red>You do not have permission to use tags.</red>"); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("redeem")) { plugin.getTagService().redeemToken(player.getUniqueId(), args[1].toUpperCase(java.util.Locale.ROOT)).whenComplete((ok, error) -> main(() -> Messages.send(player, error == null && Boolean.TRUE.equals(ok) ? "<green>Custom tag redeemed.</green>" : "<red>That token is invalid, expired, or belongs to another player.</red>"))); return true; }
        new TagMenu(plugin, player).open(); return true;
    }
    private void main(Runnable action) { if (plugin.isEnabled()) org.bukkit.Bukkit.getScheduler().runTask(plugin, action); }
}
