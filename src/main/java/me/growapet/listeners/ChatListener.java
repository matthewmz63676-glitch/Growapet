package me.growapet.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.growapet.GrowAPet;
import me.growapet.integration.LuckPermsHook;
import me.growapet.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final GrowAPet plugin;
    public ChatListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> broadcast(player, raw));
    }

    private void broadcast(Player player, String raw) {
        if (!player.isOnline()) return;
        if (plugin.getChatGameManager().answer(player, raw)) return;
        String rank = LuckPermsHook.prefix(player);
        String tag = plugin.getOptionsManager().get(player.getUniqueId(), "chat_tag", "");
        String color = plugin.getOptionsManager().get(player.getUniqueId(), "chat_color", "&f");
        String safeMessage = player.hasPermission("growapet.chat.color")
                ? Utils.colorize(raw)
                : PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(Utils.colorize(raw)));

        Component line = Component.empty();
        if (!rank.isBlank()) line = line.append(legacy(rank)).append(Component.space());
        if (!tag.isBlank()) line = line.append(legacy(tag)).append(Component.space());
        line = line.append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(legacy(color + safeMessage));
        Bukkit.broadcast(line);
    }

    private static Component legacy(String value) {
        return LEGACY.deserialize(Utils.colorize(value)).decoration(TextDecoration.ITALIC, false);
    }
}
