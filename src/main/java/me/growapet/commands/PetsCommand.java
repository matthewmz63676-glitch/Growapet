/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.growapet.commands;

import java.util.List;
import me.growapet.GrowAPet;
import me.growapet.models.Pet;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PetsCommand
implements CommandExecutor {
    private final GrowAPet plugin;

    public PetsCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a7cOnly players can use this command.");
            return true;
        }
        Player player = (Player)sender;
        List<Pet> pets = this.plugin.getPetManager().getPets(player.getUniqueId());
        if (pets.isEmpty()) {
            player.sendMessage("\u00a77You don't have any pets yet. Hatch an egg in your plot!");
            return true;
        }
        player.sendMessage("\u00a7d\u00a7lYour Pets \u00a77(" + pets.size() + ")");
        for (Pet pet : pets) {
            player.sendMessage(" \u00a77- \u00a7e" + pet.getDisplayName() + " \u00a77[" + pet.getSize() + "] Lv." + pet.getLevel() + (pet.isEquipped() ? " \u00a7a(equipped)" : ""));
        }
        return true;
    }
}

