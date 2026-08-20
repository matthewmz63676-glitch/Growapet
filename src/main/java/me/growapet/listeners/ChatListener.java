package me.growapet.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.growapet.GrowAPet;
import me.growapet.integration.LuckPermsHook;
import me.growapet.utils.Utils;
import me.growapet.utils.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
        String legacyTag = plugin.getOptionsManager().get(player.getUniqueId(), "chat_tag", "");
        String selectedTag = plugin.getTagService().selected(player.getUniqueId());
        String tag = selectedTag.isBlank() ? legacyTag : plugin.getTagService().render(selectedTag, legacyTag);
        String legacyColor = plugin.getOptionsManager().get(player.getUniqueId(), "chat_color", "&f");
        String selectedColor = plugin.getCosmeticService().selected(player.getUniqueId(), "color");
        String color = selectedColor.isBlank() ? legacyColor : plugin.getCosmeticService().render("color", selectedColor, legacyColor);
        String safeMessage = player.hasPermission("growapet.chat.color")
                ? Utils.colorize(raw)
                : PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(Utils.colorize(raw)));

        Component line = Component.empty();
        if (!rank.isBlank()) line = line.append(legacy(rank)).append(Component.space());
        if (!tag.isBlank()) line = line.append(selectedTag.isBlank() ? legacy(tag) : Messages.parse(tag).decoration(TextDecoration.ITALIC, false)).append(Component.space());
        line = line.append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                .append(selectedColor.isBlank() ? legacy(color + safeMessage) : Messages.parse(color + MiniMessage.miniMessage().escapeTags(safeMessage)).decoration(TextDecoration.ITALIC, false));
        Bukkit.broadcast(line);
        plugin.getDiscordIntegration().relayMinecraftMessage(player, raw);
    }

    private static Component legacy(String value) {
        return LEGACY.deserialize(Utils.colorize(value)).decoration(TextDecoration.ITALIC, false);
    }
}
