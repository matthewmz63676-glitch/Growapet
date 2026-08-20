package me.growapet.discord;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

/** Optional embedded Discord gateway. Network callbacks never touch Bukkit off-thread. */
public final class DiscordIntegration extends ListenerAdapter {
    private final GrowAPet plugin;
    private final DiscordLinkService links;
    private final ExecutorService outbound;
    private final Map<UUID, DiscordIdentity> identities = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();
    private volatile JDA jda;
    private volatile boolean available;

    public DiscordIntegration(GrowAPet plugin) {
        this.plugin = plugin; this.links = new DiscordLinkService(plugin);
        int capacity = Math.max(16, plugin.getConfigManager().discord().getInt("outbound-queue-capacity", 256));
        this.outbound = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> { Thread t = new Thread(runnable, "GrowAPet-Discord-Outbound"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletableFuture<Void> start() {
        if (!plugin.getConfigManager().discord().getBoolean("enabled", false)) return CompletableFuture.completedFuture(null);
        String guildId = plugin.getConfigManager().discord().getString("guild-id", "");
        String channelId = plugin.getConfigManager().discord().getString("channel-id", "");
        String roleId = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (!guildId.matches("\\d{5,32}") || !channelId.matches("\\d{5,32}") || !roleId.matches("\\d{5,32}")) {
            plugin.getLogger().warning("Discord is enabled but guild-id, channel-id, or linked-role-id is invalid; integration remains unavailable.");
            return CompletableFuture.completedFuture(null);
        }
        String tokenEnv = plugin.getConfigManager().discord().getString("token-env", "GROWAPET_DISCORD_TOKEN");
        String token = System.getenv(tokenEnv);
        if (token == null || token.isBlank()) { plugin.getLogger().warning("Discord is enabled but " + tokenEnv + " is not set; integration remains unavailable."); return CompletableFuture.completedFuture(null); }
        return CompletableFuture.runAsync(() -> {
            try {
                jda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                        .addEventListeners(this)
                        .build();
                jda.awaitReady();
                jda.upsertCommand(Commands.slash("link", "Link a Minecraft account")
                        .addOption(OptionType.STRING, "code", "The code shown in Minecraft", true)).queue();
                jda.upsertCommand(Commands.slash("checklink", "Check your Minecraft link")).queue();
                jda.upsertCommand(Commands.slash("unlink", "Unlink a Minecraft account")).queue();
                available = true;
                plugin.getLogger().info("Discord integration connected.");
            } catch (Exception error) {
                available = false;
                plugin.getLogger().warning("Discord integration unavailable: " + error.getMessage());
            }
        }).orTimeout(25, TimeUnit.SECONDS).exceptionally(error -> { available = false; plugin.getLogger().warning("Discord startup timed out; integration remains unavailable."); return null; });
    }

    public CompletableFuture<Void> loadLinks() {
        return plugin.getDatabase().async(connection -> {
            Map<UUID, DiscordIdentity> loaded = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid,discord_id,discord_name FROM discord_links WHERE unlinked_at IS NULL")) {
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) try { loaded.put(UUID.fromString(rows.getString(1)), new DiscordIdentity(rows.getString(2), rows.getString(3))); } catch (IllegalArgumentException ignored) { }
                }
            }
            return loaded;
        }).thenAccept(loaded -> { identities.clear(); identities.putAll(loaded); });
    }

    public void stop() {
        available = false;
        JDA connected = jda; jda = null;
        if (connected != null) connected.shutdownNow();
        outbound.shutdownNow();
    }

    public DiscordLinkService links() { return links; }
    public boolean isAvailable() { return available && jda != null; }
    public void cacheLink(UUID player, String discordId, String discordName) { identities.put(player, new DiscordIdentity(discordId, discordName)); }
    public void removeLink(UUID player) { identities.remove(player); }

    public void relayMinecraftMessage(Player player, String message) {
        if (!isAvailable() || !plugin.getConfigManager().discord().getBoolean("relay.minecraft-to-discord", true)) return;
        DiscordIdentity identity = identities.get(player.getUniqueId());
        // Relay is intentionally limited to linked members of the configured role. This
        // check is repeated against Discord's member state so removing the role immediately
        // revokes relay access without waiting for a Minecraft relog.
        if (identity == null) return;
        if (!plugin.getOptionsManager().enabled(player.getUniqueId(), "discord_relay", true)) return;
        for (String marker : plugin.getConfigManager().discord().getStringList("chat-bridge.excluded-markers")) {
            if (marker != null && !marker.isBlank() && message.contains(marker)) return;
        }
        String clean = sanitize(message); if (clean.isBlank()) return;
        String channelId = plugin.getConfigManager().discord().getString("channel-id", "");
        if (channelId.isBlank()) return;
        hasConfiguredRole(identity.id()).thenAccept(allowed -> {
            if (!allowed || !allow("mc:" + player.getUniqueId(), plugin.getConfigManager().discord().getInt("messages-per-10-seconds", 4))) return;
            try { outbound.execute(() -> { try { TextChannel channel = jda.getTextChannelById(channelId); if (channel != null) channel.sendMessage("**" + sanitize(player.getName()) + "** → " + clean).queue(); } catch (RuntimeException error) { plugin.getLogger().fine("Discord outbound message dropped: " + error.getMessage()); } }); }
            catch (RejectedExecutionException dropped) { plugin.getLogger().fine("Discord outbound queue is full; message dropped."); }
        });
    }

    public CompletableFuture<Boolean> publishLinkPanel() {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        String channelId = plugin.getConfigManager().discord().getString("channel-id", ""); TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        channel.sendMessage("Link your Minecraft account, then run **/link <code>** in Discord.").addComponents(ActionRow.of(Button.primary("growapet:link", "Link account"))).queue(ignored -> result.complete(true), error -> result.complete(false));
        return result;
    }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!"growapet:link".equals(event.getComponentId())) return;
        TextInput code = TextInput.create("code", TextInputStyle.SHORT).setPlaceholder("Minecraft link code").setRequired(true).setMinLength(8).setMaxLength(16).build();
        event.replyModal(Modal.create("growapet:link", "Link GrowAPet account").addComponents(Label.of("Minecraft link code", code)).build()).queue();
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!"growapet:link".equals(event.getModalId())) return;
        String code = event.getValue("code") == null ? "" : event.getValue("code").getAsString();
        event.deferReply(true).queue(ignored -> completeLink(event, code), error -> plugin.getLogger().fine("Discord modal acknowledgement failed: " + error.getMessage()));
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) { event.reply("Use this command in the configured GrowAPet Discord server.").setEphemeral(true).queue(); return; }
        String guildId = plugin.getConfigManager().discord().getString("guild-id", ""); String channelId = plugin.getConfigManager().discord().getString("channel-id", "");
        if (!guildId.equals(event.getGuild().getId()) || !channelId.equals(event.getChannel().getId())) { event.reply("This command is not enabled in this channel.").setEphemeral(true).queue(); return; }
        switch (event.getName()) {
            case "link" -> event.deferReply(true).queue(ignored -> completeLink(event, event.getOption("code").getAsString()), error -> plugin.getLogger().fine("Discord link acknowledgement failed: " + error.getMessage()));
            case "checklink" -> event.deferReply(true).queue(ignored -> links.playerForDiscord(event.getUser().getId()).thenCompose(playerId -> playerId == null
                    ? CompletableFuture.completedFuture("No Minecraft account is linked.")
                    : links.status(playerId).thenApply(status -> status.linked() ? "Your Minecraft account is linked as " + status.discordName() + "." : "No Minecraft account is linked."))
                    .whenComplete((message, error) -> event.getHook().editOriginal(error == null && message != null ? message : "Link status is temporarily unavailable.").queue()), error -> plugin.getLogger().fine("Discord status acknowledgement failed: " + error.getMessage()));
            case "unlink" -> event.deferReply(true).queue(ignored -> links.playerForDiscord(event.getUser().getId()).thenCompose(playerId -> playerId == null ? CompletableFuture.completedFuture(null) : links.unlink(playerId)).whenComplete((unused, error) -> event.getHook().editOriginal(error == null ? "Your Minecraft account was unlinked." : "The unlink could not be completed safely.").queue()), error -> plugin.getLogger().fine("Discord unlink acknowledgement failed: " + error.getMessage()));
            default -> { }
        }
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        String guildId = plugin.getConfigManager().discord().getString("guild-id", "");
        String channelId = plugin.getConfigManager().discord().getString("channel-id", "");
        if (!guildId.equals(event.getGuild().getId()) || !channelId.equals(event.getChannel().getId())) return;
        if (!plugin.getConfigManager().discord().getBoolean("relay.discord-to-minecraft", true)) return;
        if (!event.getMessage().getAttachments().isEmpty() || !allow("dc:" + event.getAuthor().getId(), plugin.getConfigManager().discord().getInt("messages-per-10-seconds", 4))) return;
        String role = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (role.isBlank() || event.getMember() == null || event.getMember().getRoles().stream().noneMatch(value -> role.equals(value.getId()))) return;
        String content = sanitize(event.getMessage().getContentRaw());
        int max = Math.max(64, plugin.getConfigManager().discord().getInt("message-max-length", 256));
        if (content.isBlank() || content.length() > max) return;
        links.playerForDiscord(event.getAuthor().getId()).whenComplete((playerId, error) -> {
            if (error != null || playerId == null) return;
            runOnMain(() -> {
                Player linked = Bukkit.getPlayer(playerId);
                if (linked == null || !plugin.getOptionsManager().enabled(playerId, "discord_relay", true)) return;
                Bukkit.broadcast(Messages.parse("<dark_aqua><bold>[Discord]</bold></dark_aqua> <white><name></white> <dark_gray>→</dark_gray> <gray><message></gray>", Messages.value("name", event.getAuthor().getName()), Messages.value("message", content)));
            });
        });
    }

    @Override public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String configuredGuild = plugin.getConfigManager().discord().getString("guild-id", "");
        if (!configuredGuild.equals(event.getGuild().getId())) return;
        links.playerForDiscord(event.getUser().getId()).thenAccept(playerId -> {
            if (playerId == null) return;
            links.unlink(playerId).whenComplete((ignored, error) -> {
                if (error != null) return;
                runOnMain(() -> {
                    String template = plugin.getConfigManager().discord().getString("leave-unlink-broadcast", "");
                    if (template == null || template.isBlank() || template.equalsIgnoreCase("false")) return;
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) Bukkit.broadcast(Messages.parse(template, Messages.value("player", player.getName())));
                });
            });
        });
    }

    public void applyLinkedRole(String discordId) {
        String guildId = plugin.getConfigManager().discord().getString("guild-id", ""); String roleId = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (!isAvailable() || guildId.isBlank() || roleId.isBlank()) return;
        var guild = jda.getGuildById(guildId); var role = guild == null ? null : guild.getRoleById(roleId);
        if (guild != null && role != null) guild.addRoleToMember(net.dv8tion.jda.api.entities.UserSnowflake.fromId(discordId), role).queue(null, error -> plugin.getLogger().fine("Could not grant linked Discord role: " + error.getMessage()));
    }

    public void removeLinkedRole(String discordId) {
        String guildId = plugin.getConfigManager().discord().getString("guild-id", ""); String roleId = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (!isAvailable() || guildId.isBlank() || roleId.isBlank()) return;
        var guild = jda.getGuildById(guildId); var role = guild == null ? null : guild.getRoleById(roleId);
        if (guild != null && role != null) guild.removeRoleFromMember(net.dv8tion.jda.api.entities.UserSnowflake.fromId(discordId), role).queue(null, error -> plugin.getLogger().fine("Could not remove linked Discord role: " + error.getMessage()));
    }

    private void completeLink(ModalInteractionEvent event, String code) {
        if (!allow("link:" + event.getUser().getId(), plugin.getConfigManager().discord().getInt("link-attempt-limit", 5))) {
            event.getHook().editOriginal("Too many link attempts. Please wait before trying again.").queue();
            return;
        }
        links.consume(code, event.getUser().getId(), event.getUser().getName()).whenComplete((result, error) -> runOnMain(() -> {
            String message = error == null && result != null ? result.message() : "The link could not be completed safely.";
            if (error == null && result != null && result.success()) message = "Minecraft account linked. Your lifetime EXP bonus is now active.";
            event.getHook().editOriginal(message).queue(); notifyLinked(result);
        }));
    }

    private void completeLink(SlashCommandInteractionEvent event, String code) {
        if (!allow("link:" + event.getUser().getId(), plugin.getConfigManager().discord().getInt("link-attempt-limit", 5))) {
            event.getHook().editOriginal("Too many link attempts. Please wait before trying again.").queue();
            return;
        }
        links.consume(code, event.getUser().getId(), event.getUser().getName()).whenComplete((result, error) -> runOnMain(() -> {
            String message = error == null && result != null ? result.message() : "The link could not be completed safely.";
            if (error == null && result != null && result.success()) message = "Minecraft account linked. Your lifetime EXP bonus is now active.";
            event.getHook().editOriginal(message).queue(); notifyLinked(result);
        }));
    }

    private void notifyLinked(DiscordLinkService.LinkResult result) {
        if (result == null || !result.success()) return;
        Player player = Bukkit.getPlayer(result.playerId());
        if (player != null) {
            Messages.send(player, "<aqua><bold>DISCORD LINKED</bold></aqua> <dark_gray>•</dark_gray> <gray>Your lifetime <white>+5% EXP</white> bonus is active.</gray>");
            if (plugin.getConfigManager().discord().getBoolean("link-broadcast-enabled", true)) {
                String template = plugin.getConfigManager().discord().getString("link-broadcast", "");
                if (template != null && !template.isBlank()) Bukkit.broadcast(Messages.parse(template,
                        Messages.value("player", player.getName()), Messages.value("discord_username", result.discordName()),
                        Messages.value("discord_tag", result.discordName())));
            }
            applyLinkedNickname(result.discordId(), player.getName());
        }
    }

    private void applyLinkedNickname(String discordId, String playerName) {
        if (!isAvailable() || discordId == null || playerName == null || playerName.isBlank()
                || !plugin.getConfigManager().discord().getBoolean("nickname-on-link", false)) return;
        String guildId = plugin.getConfigManager().discord().getString("guild-id", "");
        var guild = jda.getGuildById(guildId);
        if (guild == null) return;
        String format = plugin.getConfigManager().discord().getString("nickname-format", "{name}");
        String nickname = format.replace("{player}", playerName).replace("{name}", playerName).trim();
        if (nickname.isBlank()) return;
        final String requestedNickname = nickname.substring(0, Math.min(32, nickname.length()));
        var member = guild.getMemberById(discordId);
        if (member != null) guild.modifyNickname(member, requestedNickname).queue(null, error -> plugin.getLogger().fine("Could not set linked Discord nickname: " + error.getMessage()));
        else {
            try { guild.retrieveMemberById(discordId).queue(found -> guild.modifyNickname(found, requestedNickname).queue(null, error -> plugin.getLogger().fine("Could not set linked Discord nickname: " + error.getMessage())), error -> plugin.getLogger().fine("Could not retrieve linked Discord member: " + error.getMessage())); }
            catch (RuntimeException error) { plugin.getLogger().fine("Could not retrieve linked Discord member: " + error.getMessage()); }
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cntrl}]", "").replace("@everyone", "@​everyone").replace("@here", "@​here").replaceAll("(?i)https?://", "hxxp://").replace("<", "‹").replace(">", "›").trim();
    }

    private boolean allow(String key, int limit) {
        int bounded = Math.max(1, Math.min(32, limit)); long now = System.currentTimeMillis();
        Deque<Long> window = rateWindows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) { while (!window.isEmpty() && now - window.peekFirst() >= 10_000L) window.removeFirst(); if (window.size() >= bounded) return false; window.addLast(now); return true; }
    }

    private CompletableFuture<Boolean> hasConfiguredRole(String discordId) {
        String guildId = plugin.getConfigManager().discord().getString("guild-id", "");
        String roleId = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (!isAvailable() || guildId.isBlank() || roleId.isBlank()) return CompletableFuture.completedFuture(false);
        var guild = jda.getGuildById(guildId);
        if (guild == null) return CompletableFuture.completedFuture(false);
        var cached = guild.getMemberById(discordId);
        if (cached != null) return CompletableFuture.completedFuture(cached.getRoles().stream().anyMatch(role -> roleId.equals(role.getId())));
        try {
            return guild.retrieveMemberById(discordId).submit().handle((member, error) -> error == null && member != null && member.getRoles().stream().anyMatch(role -> roleId.equals(role.getId())));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private void runOnMain(Runnable action) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }

    private record DiscordIdentity(String id, String name) { }
}
