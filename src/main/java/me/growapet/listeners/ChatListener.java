/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.md_5.bungee.api.ChatColor
 *  org.bukkit.ChatColor
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 */
package me.growapet.listeners;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.growapet.GrowAPet;
import me.growapet.integration.LuckPermsHook;
import me.growapet.utils.Utils;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener
implements Listener {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public ChatListener(GrowAPet plugin) {
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String rankPrefix = LuckPermsHook.prefix(event.getPlayer());
        Object prefixPart = rankPrefix.isEmpty() ? "" : rankPrefix + " ";
        String message = event.getMessage();
        if (event.getPlayer().hasPermission("growapet.chat.color")) {
            message = this.applyHexColors(message);
            message = ChatColor.translateAlternateColorCodes((char)'&', (String)message);
        } else {
            message = ChatColor.stripColor((String)message);
        }
        event.setMessage(message);
        event.setFormat(Utils.colorize((String)prefixPart + "&f%1$s &8\u00bb &r") + "%2$s");
    }

    private String applyHexColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(net.md_5.bungee.api.ChatColor.of((String)("#" + matcher.group(1))).toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

