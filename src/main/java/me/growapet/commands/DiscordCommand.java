package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Minecraft-facing Discord link/check/unlink command surface. */
public final class DiscordCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public DiscordCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player self = sender instanceof Player player ? player : null;
        if (!label.equalsIgnoreCase("unlink") && self == null) { Messages.send(sender, "<red>This command is player-only.</red>"); return true; }
        if (self != null && !self.hasPermission("growapet.discord.link")) { Messages.send(self, "<red>You do not have permission to use Discord linking.</red>"); return true; }
        switch (label.toLowerCase(java.util.Locale.ROOT)) {
            case "discord" -> Messages.send(self, "<aqua><bold>DISCORD</bold></aqua> <dark_gray>•</dark_gray> <gray>Use <white>/link</white> to connect your account. <white>" + plugin.getConfigManager().discord().getString("relay.invite-url", "") + "</white></gray>");
            case "link" -> plugin.getDiscordIntegration().links().issue(self.getUniqueId()).whenComplete((code, error) -> main(() -> { if (error != null) Messages.send(self, "<red>A link code could not be issued safely.</red>"); else Messages.send(self, "<aqua><bold>DISCORD LINK CODE</bold></aqua> <dark_gray>•</dark_gray> <white>" + code + "</white> <gray>Enter it in the Discord link panel. It expires in five minutes.</gray>"); }));
            case "checklink" -> plugin.getDiscordIntegration().links().status(self.getUniqueId()).whenComplete((status, error) -> main(() -> { if (error != null || status == null) Messages.send(self, "<red>Link status is unavailable.</red>"); else if (status.linked()) Messages.send(self, "<aqua><bold>DISCORD LINK</bold></aqua> <dark_gray>→</dark_gray> <white>" + status.discordName() + "</white> <gray>• lifetime EXP bonus: " + (status.lifetimeReward() ? "<green>active</green>" : "<yellow>pending</yellow>") + "</gray>"); else Messages.send(self, "<gray>No Discord account is linked.</gray> <white>Use /link to generate a code.</white>"); }));
            case "unlink" -> {
                Player target = self != null && args.length == 0 ? self : args.length == 1 && sender.hasPermission("growapet.admin.discord") ? Bukkit.getPlayerExact(args[0]) : null;
                if (target == null) { Messages.send(sender, "<yellow>Usage: /unlink [player]</yellow>"); return true; }
                plugin.getDiscordIntegration().links().unlink(target.getUniqueId()).whenComplete((ignored, error) -> main(() -> Messages.send(sender, error == null ? "<yellow>Discord account unlinked safely.</yellow>" : "<red>The account could not be unlinked safely.</red>")));
            }
            default -> Messages.send(sender, "<red>Unknown Discord command.</red>");
        }
        return true;
    }
    private void main(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
}
