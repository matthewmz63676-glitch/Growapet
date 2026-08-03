/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 */
package me.growapet.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StubCommand
implements CommandExecutor {
    private final String featureName;

    public StubCommand(String featureName) {
        this.featureName = featureName;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("\u00a7e" + this.featureName + " isn't implemented yet \u2014 check back in a future update.");
        return true;
    }
}

