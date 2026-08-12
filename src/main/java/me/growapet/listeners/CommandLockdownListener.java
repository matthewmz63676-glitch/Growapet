package me.growapet.listeners;

import me.growapet.GrowAPet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;

/** Player-only command allowlist. Console and command blocks are deliberately unaffected. */
public final class CommandLockdownListener implements Listener {
    private static final Component INVALID = Component.text("ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ", NamedTextColor.RED);
    private static final Set<String> PLAYER_COMMANDS = Set.of(
            "plot", "pets", "stats", "warp", "zones", "visit", "leaderboard", "quests", "trade",
            "options", "shop", "spawn", "store", "autokill", "daily", "boss", "growapet", "leaderboards"
    );
    private static final Set<String> ADMIN_COMMANDS = Set.of("getmob", "getegg", "getpet", "setspawn", "unlockzone");
    private final GrowAPet plugin;

    public CommandLockdownListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().length() > 1 ? event.getMessage().substring(1).trim() : "";
        if (raw.isEmpty()) { deny(event); return; }
        String[] tokens = raw.split("\\s+");
        String label = tokens[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) { deny(event); return; }
        if (label.equals("server")) {
            if (plugin.getServer().getPluginCommand("server") == null && plugin.getServer().getCommandMap().getCommand("server") == null) deny(event);
            return;
        }
        if (!PLAYER_COMMANDS.contains(label) && !ADMIN_COMMANDS.contains(label)) { deny(event); return; }
        if (!authorized(event.getPlayer(), label, tokens)) deny(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSuggestions(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        event.getCommands().removeIf(label -> {
            String normalized = label.toLowerCase(Locale.ROOT);
            if (normalized.contains(":")) return true;
            if (normalized.equals("server")) return false;
            if (!PLAYER_COMMANDS.contains(normalized) && !ADMIN_COMMANDS.contains(normalized)) return true;
            return !authorized(player, normalized, new String[]{normalized});
        });
    }

    private static boolean authorized(Player player, String label, String[] tokens) {
        return switch (label) {
            case "getmob" -> player.hasPermission("growapet.admin.mob");
            case "getegg", "getpet" -> player.hasPermission("growapet.admin.give");
            case "setspawn" -> player.hasPermission("growapet.admin.spawn");
            case "unlockzone" -> player.hasPermission("growapet.admin.zones");
            case "autokill" -> player.hasPermission("growapet.autokill");
            case "boss" -> tokens.length < 2 || !tokens[1].equalsIgnoreCase("spawn") || player.hasPermission("growapet.admin.boss");
            case "growapet" -> authorizedGrowAPet(player, tokens);
            default -> true;
        };
    }

    private static boolean authorizedGrowAPet(Player player, String[] tokens) {
        if (tokens.length < 2) return player.hasPermission("growapet.admin");
        return switch (tokens[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> player.hasPermission("growapet.admin.reload");
            case "setlevel", "setcoins", "setgems", "setcredits", "creditreceipt" -> player.hasPermission("growapet.admin.economy");
            case "give" -> player.hasPermission("growapet.admin.give");
            case "event" -> player.hasPermission("growapet.admin.events");
            default -> player.hasPermission("growapet.admin");
        };
    }

    private static void deny(PlayerCommandPreprocessEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(INVALID);
    }
}
