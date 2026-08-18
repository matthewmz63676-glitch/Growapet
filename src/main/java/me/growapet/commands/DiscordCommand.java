package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.discord.DiscordLinkService;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DiscordCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public DiscordCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>This command is player-only.</red>"); return true; }
        if (!player.hasPermission("growapet.discord.link")) { Messages.send(player, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
        switch (label.toLowerCase(java.util.Locale.ROOT)) {
            case "link" -> plugin.getDiscordIntegration().links().issue(player.getUniqueId()).whenComplete((code, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) Messages.send(player, "<red>A link code could not be issued safely.</red>");
                else Messages.send(player, "<aqua><bold>DISCORD LINK CODE</bold></aqua> <dark_gray>•</dark_gray> <white>" + code + "</white> <gray>— enter it with the Discord link button or <white>/link</white>. Expires in five minutes.</gray>");
            }));
            case "checklink" -> plugin.getDiscordIntegration().links().status(player.getUniqueId()).whenComplete((status, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || status == null) Messages.send(player, "<red>Link status is unavailable.</red>");
                else if (status.linked()) Messages.send(player, "<aqua><bold>DISCORD LINK</bold></aqua> <dark_gray>→</dark_gray> <white>" + status.discordName() + "</white> <gray>• lifetime EXP bonus: " + (status.lifetimeReward() ? "<green>active</green>" : "<yellow>pending</yellow>") + "</gray>");
                else Messages.send(player, "<gray>No Discord account is linked.</gray> <white>Use /link to generate a code.</white>");
            }));
            case "unlink" -> plugin.getDiscordIntegration().links().unlink(player.getUniqueId()).whenComplete((ignored, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> Messages.send(player, error == null ? "<yellow>Your Discord account was unlinked. Your earned lifetime bonus remains.</yellow>" : "<red>The account could not be unlinked safely.</red>")));
            default -> Messages.send(sender, "<red>Unknown Discord command.</red>");
        }
        return true;
    }
}
