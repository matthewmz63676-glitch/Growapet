package me.growapet.cosmetics;

import me.growapet.GrowAPet;
import me.growapet.rewards.EntitlementService;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Data-driven ownership and selection boundary for chat cosmetics. */
public final class CosmeticService {
    private final GrowAPet plugin;
    private final EntitlementService entitlements;

    public CosmeticService(GrowAPet plugin) {
        this.plugin = plugin;
        this.entitlements = plugin.getEntitlementService();
    }

    public boolean valid(String kind, String value) {
        if (kind == null || value == null || value.isBlank()) return false;
        String normalized = kind.toLowerCase(Locale.ROOT);
        ConfigurationSection root = plugin.getConfigManager().cosmetics().getConfigurationSection(normalized + "s");
        if (root == null) return false;
        return root.isConfigurationSection(value) || root.contains(value);
    }

    public boolean owns(UUID playerId, String kind, String value) {
        if (!valid(kind, value)) return false;
        String id = entitlementId(kind, value);
        return entitlements.has(playerId, id);
    }

    public CompletableFuture<Boolean> select(UUID playerId, String kind, String value) {
        if (!owns(playerId, kind, value)) return CompletableFuture.completedFuture(false);
        String normalized = kind.toLowerCase(Locale.ROOT);
        return plugin.getOptionsManager().set(playerId, "cosmetic.selected." + normalized, value).thenApply(ignored -> true);
    }

    public CompletableFuture<Boolean> clear(UUID playerId, String kind) {
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (!normalized.equals("tag") && !normalized.equals("color")) return CompletableFuture.completedFuture(false);
        return plugin.getOptionsManager().set(playerId, "cosmetic.selected." + normalized, "").thenApply(ignored -> true);
    }

    public String selected(UUID playerId, String kind) {
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String value = plugin.getOptionsManager().get(playerId, "cosmetic.selected." + normalized, "");
        if (value.isBlank() || !owns(playerId, normalized, value)) return "";
        return value;
    }

    public List<String> available(UUID playerId, String kind) {
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        ConfigurationSection root = plugin.getConfigManager().cosmetics().getConfigurationSection(normalized + "s");
        if (root == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String key : root.getKeys(false)) if (owns(playerId, normalized, key)) result.add(key);
        return result.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public String render(String kind, String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String path = kind.toLowerCase(Locale.ROOT) + "s." + value + ".value";
        String configured = plugin.getConfigManager().cosmetics().getString(path, "");
        return configured.isBlank() ? fallback : configured;
    }

    public String entitlementId(String kind, String value) {
        return "cosmetic." + kind.toLowerCase(Locale.ROOT) + "." + value.toLowerCase(Locale.ROOT);
    }
}
