/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.luckperms.api.LuckPerms
 *  net.luckperms.api.LuckPermsProvider
 *  net.luckperms.api.cacheddata.CachedMetaData
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package me.growapet.integration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LuckPermsHook {
    private static final Pattern HEX_TAG = Pattern.compile("<(#{1,2})([A-Fa-f0-9]{6})>(.*?)</\\1\\2>", 32);
    private static final Pattern LEFTOVER_TAG = Pattern.compile("</?[a-zA-Z#][^<>]*>");
    private static Boolean available;

    private LuckPermsHook() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        }
        return available;
    }

    public static String prefix(Player player) {
        if (!LuckPermsHook.isAvailable()) {
            return "";
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            CachedMetaData meta = luckPerms.getPlayerAdapter(Player.class).getMetaData((Object)player);
            String prefix = meta.getPrefix();
            if (prefix == null) {
                return "";
            }
            prefix = LuckPermsHook.unwrapHexTags(prefix);
            prefix = LEFTOVER_TAG.matcher(prefix).replaceAll("");
            return prefix.replace('\u00a7', '&');
        }
        catch (IllegalStateException e) {
            return "";
        }
    }

    private static String unwrapHexTags(String text) {
        Matcher matcher = HEX_TAG.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(2);
            String inner = matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement("#" + hex + inner));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

