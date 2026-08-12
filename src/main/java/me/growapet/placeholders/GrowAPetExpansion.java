/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 *  org.jetbrains.annotations.NotNull
 */
package me.growapet.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class GrowAPetExpansion
extends PlaceholderExpansion {
    private final GrowAPet plugin;

    public GrowAPetExpansion(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String getIdentifier() {
        return "growapet";
    }

    @NotNull
    public String getAuthor() {
        return "StringClient";
    }

    @NotNull
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerData data = this.plugin.getPlayerManager().get(player.getUniqueId());
        if (data == null) {
            return "";
        }
        return switch (params) {
            case "level" -> String.valueOf(data.getLevel());
            case "exp" -> String.valueOf(data.getExp());
            case "exp_bar" -> String.format("%.1f%%", data.getExpProgress() * 100.0);
            case "coins" -> String.valueOf(data.getCoins());
            case "gems" -> String.valueOf(data.getGems());
            case "credits" -> String.valueOf(data.getCredits());
            case "damage", "damage_multiplier" -> String.format(java.util.Locale.US, "%.2f", data.getDamageMultiplier());
            case "coinmulti", "coin_multiplier" -> String.format(java.util.Locale.US, "%.2f", data.getCoinMultiplier());
            case "gemsmulti", "gem_multiplier" -> String.format(java.util.Locale.US, "%.2f", data.getGemMultiplier());
            case "expmulti", "exp_multiplier" -> String.format(java.util.Locale.US, "%.2f", data.getExpMultiplier());
            case "power", "pet_power" -> String.format(java.util.Locale.US, "%.2f", data.getPetPower());
            case "mob_kills" -> String.valueOf(data.getMobKills());
            case "boss_kills" -> String.valueOf(data.getBossKills());
            case "playtime" -> String.valueOf(data.getPlaytimeSeconds());
            default -> null;
        };
    }
}
