package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.PetMenu;
import me.growapet.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PetsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public PetsCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can open the pet collection.</red>"); return true; }
        // Keep the documented legacy subcommands working while making the normal
        // /pets path safe and discoverable through the protected inventory menu.
        if (args.length == 2 && (args[0].equalsIgnoreCase("equip") || args[0].equalsIgnoreCase("unequip"))) {
            try {
                UUID petId = UUID.fromString(args[1]);
                boolean changed = args[0].equalsIgnoreCase("equip") ? plugin.getPetManager().equip(player.getUniqueId(), petId) : plugin.getPetManager().unequip(player.getUniqueId(), petId);
                Messages.send(player, changed ? "<green>Pet state updated.</green>" : "<red>That pet could not be updated.</red>");
            } catch (IllegalArgumentException error) { Messages.send(player, "<red>Invalid pet identifier.</red>"); }
            return true;
        }
        new PetMenu(plugin, player).open();
        return true;
    }
}
