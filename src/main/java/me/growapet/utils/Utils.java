/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.md_5.bungee.api.ChatColor
 */
package me.growapet.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;

public class Utils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#[a-fA-F0-9]{6}");

    public static String colorize(String msg) {
        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group().substring(matcher.group().length() - 7);
            matcher.appendReplacement(result, Matcher.quoteReplacement(ChatColor.of((String)hex).toString()));
        }
        matcher.appendTail(result);
        return ChatColor.translateAlternateColorCodes((char)'&', (String)result.toString());
    }
}

