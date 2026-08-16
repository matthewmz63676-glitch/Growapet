package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.CosmeticsMenu;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens the ownership-aware cosmetics menu. */
public final class CosmeticsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public CosmeticsCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use this command.</red>"); return true; }
        if (!player.hasPermission("growapet.cosmetics")) { Messages.send(player, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
        new CosmeticsMenu(plugin, player).open();
        return true;
    }
}
