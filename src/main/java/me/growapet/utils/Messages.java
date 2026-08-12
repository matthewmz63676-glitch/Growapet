package me.growapet.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Central MiniMessage boundary for player-facing text added by the v2 polish pass. */
public final class Messages {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Messages() {}

    public static Component parse(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, resolvers);
    }

    public static void send(CommandSender recipient, String message, TagResolver... resolvers) {
        recipient.sendMessage(parse(message, resolvers));
    }

    public static TagResolver value(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
