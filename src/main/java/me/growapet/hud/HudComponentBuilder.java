package me.growapet.hud;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Utils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds an action bar containing native 1.21 sprite object components. */
final class HudComponentBuilder {
    private static final Pattern SPRITE = Pattern.compile("%sprite_([a-z0-9_]+)%");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private HudComponentBuilder() {}

    static Component render(GrowAPet plugin, Player player, PlayerData data, String template) {
        String resolved = Utils.colorize(HudPlaceholders.resolve(plugin, player, data, template));
        Matcher matcher = SPRITE.matcher(resolved);
        Component result = Component.empty();
        int end = 0;
        while (matcher.find()) {
            result = result.append(LEGACY.deserialize(resolved.substring(end, matcher.start())));
            result = result.append(icon(plugin, player, matcher.group(1)));
            end = matcher.end();
        }
        return result.append(LEGACY.deserialize(resolved.substring(end)));
    }

    private static Component icon(GrowAPet plugin, Player player, String id) {
        boolean enabled = plugin.getConfigManager().hud().getBoolean("sprites.enabled", true);
        boolean packReady = plugin.getActionBarManager() != null && plugin.getActionBarManager().spritesReady(player);
        if (!useSprites(enabled, packReady)) {
            String fallback = plugin.getConfigManager().hud().getString("icons." + id, "•");
            return Component.text(fallback == null ? "•" : fallback);
        }
        String atlas = plugin.getConfigManager().hud().getString("sprites.atlas", "growapet:hud");
        String sprite = plugin.getConfigManager().hud().getString("sprites." + id, "growapet:" + id.toLowerCase(Locale.ROOT));
        try { return Component.object(ObjectContents.sprite(Key.key(atlas), Key.key(sprite))); }
        catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Invalid HUD sprite key for '" + id + "': " + error.getMessage());
            return Component.text(plugin.getConfigManager().hud().getString("icons." + id, "•"));
        }
    }

    static boolean useSprites(boolean configured, boolean packReady) {
        return configured && packReady;
    }
}
