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

/** Player-only command allowlist. Console and command blocks are deliberately unaffected. */
public final class CommandLockdownListener implements Listener {
    private static final Component INVALID = Component.text("ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ", NamedTextColor.RED);
    private final GrowAPet plugin;

    public CommandLockdownListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().length() > 1 ? event.getMessage().substring(1).trim() : "";
        if (raw.isEmpty()) { deny(event); return; }
        String[] tokens = raw.split("\\s+");
        String label = tokens[0].toLowerCase(Locale.ROOT);
        boolean administrator = hasBypass(event.getPlayer());
        boolean serverAvailable = plugin.getServer().getPluginCommand("server") != null
                || plugin.getServer().getCommandMap().getCommand("server") != null;
        if (!CommandLockdownPolicy.permitsLabel(label, administrator, serverAvailable)) { deny(event); return; }
        if (administrator || label.equals("server")) return;
        if (!authorized(event.getPlayer(), label, tokens)) deny(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSuggestions(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;
        event.getCommands().removeIf(label -> {
            String normalized = label.toLowerCase(Locale.ROOT);
            boolean serverAvailable = plugin.getServer().getPluginCommand("server") != null
                    || plugin.getServer().getCommandMap().getCommand("server") != null;
            if (!CommandLockdownPolicy.permitsLabel(normalized, false, serverAvailable)) return true;
            if (normalized.equals("server")) return false;
            return !authorized(player, normalized, new String[]{normalized});
        });
    }

    private static boolean hasBypass(Player player) {
        return player.isOp() || player.hasPermission("growapet.admin")
                || player.hasPermission("growapet.admin.bypass")
                || player.hasPermission("growapet.command-blacklist.bypass");
    }

    private static boolean authorized(Player player, String label, String[] tokens) {
        return switch (label) {
            case "getmob" -> player.hasPermission("growapet.admin.mob");
            case "getegg", "getpet" -> player.hasPermission("growapet.admin.give");
            case "setspawn" -> player.hasPermission("growapet.admin.spawn");
            case "unlockzone" -> player.hasPermission("growapet.admin.zones");
            case "autokill" -> player.hasPermission("growapet.autokill");
            case "tutorial" -> player.hasPermission("growapet.tutorial") || player.hasPermission("growapet.admin.tutorial");
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
