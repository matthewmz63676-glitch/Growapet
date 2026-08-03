/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 */
package me.growapet.hud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class IconResolver {
    private static final Pattern ICON_PATTERN = Pattern.compile("%icon_([a-zA-Z0-9_]+)%");

    private IconResolver() {
    }

    public static String apply(FileConfiguration hud, String template) {
        if (hud == null || !template.contains("%icon_")) {
            return template;
        }
        ConfigurationSection icons = hud.getConfigurationSection("icons");
        if (icons == null) {
            return template;
        }
        Matcher matcher = ICON_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String iconValue = icons.getString(matcher.group(1), "");
            Object wrapped = iconValue.isEmpty() ? "" : "&f" + iconValue + "&r";
            matcher.appendReplacement(result, Matcher.quoteReplacement((String)wrapped));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

