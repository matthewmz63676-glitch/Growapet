package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.UUID;

/** Admin-safe Credit balance inspection and mutation boundary. */
public final class CreditsCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public CreditsCommand(GrowAPet plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "show" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("show")) {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null;
            if (target == null || (!target.equals(sender) && !sender.hasPermission("growapet.admin.credits"))) { Messages.send(sender, "<red>Use /credits show <online-player> as an administrator.</red>"); return true; }
            PlayerData data = plugin.getPlayerManager().get(target); Messages.send(sender, data == null ? "<red>That profile is still loading.</red>" : "<light_purple>Credits</light_purple> <dark_gray>•</dark_gray> <white>" + data.getCredits() + "</white>"); return true;
        }
        if (!sender.hasPermission("growapet.admin.credits") || args.length != 3 || !java.util.Set.of("give", "take", "set").contains(sub)) { Messages.send(sender, "<yellow>Usage: /credits show [player] or /credits <give|take|set> <player> <amount></yellow>"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]); long amount;
        try { amount = Long.parseLong(args[2]); } catch (NumberFormatException error) { amount = -1; }
        if (target == null || amount < 0 || amount > 1_000_000_000L) { Messages.send(sender, "<red>Target must be online and amount must be 0-1,000,000,000.</red>"); return true; }
        PlayerData data = plugin.getPlayerManager().get(target); if (data == null || !data.tryLockEconomy()) { Messages.send(sender, "<yellow>That player has another economy transaction in progress.</yellow>"); return true; }
        final long value = amount;
        plugin.getDatabase().<Long>transaction(connection -> {
            String sql = switch (sub) { case "give" -> "UPDATE players SET credits=CASE WHEN credits>? THEN 9223372036854775807 ELSE credits+? END WHERE uuid=?"; case "take" -> "UPDATE players SET credits=credits-? WHERE uuid=? AND credits>=?"; default -> "UPDATE players SET credits=? WHERE uuid=?"; };
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (sub.equals("give")) { statement.setLong(1, Long.MAX_VALUE - value); statement.setLong(2, value); statement.setString(3, target.getUniqueId().toString()); }
                else if (sub.equals("take")) { statement.setLong(1, value); statement.setString(2, target.getUniqueId().toString()); statement.setLong(3, value); }
                else { statement.setLong(1, value); statement.setString(2, target.getUniqueId().toString()); }
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Balance update failed");
            }
            try (PreparedStatement audit = connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at) VALUES(?,?,?,?,?)")) { audit.setString(1, sender.getName()); audit.setString(2, "CREDITS_" + sub.toUpperCase(Locale.ROOT)); audit.setString(3, target.getUniqueId().toString()); audit.setString(4, String.valueOf(value)); audit.setLong(5, System.currentTimeMillis()); audit.executeUpdate(); }
            try (PreparedStatement balance = connection.prepareStatement("SELECT credits FROM players WHERE uuid=?")) { balance.setString(1, target.getUniqueId().toString()); try (var row = balance.executeQuery()) { if (!row.next()) throw new IllegalStateException("Balance row missing"); return row.getLong(1); } }
        }).whenComplete((balance, error) -> main(() -> {
            data.unlockEconomy();
            if (error != null) { Messages.send(sender, "<red>Credit balance was not changed.</red>"); return; }
            data.setCredits(balance == null ? data.getCredits() : Math.max(0, balance));
            Messages.send(sender, "<green>Credits updated safely.</green>"); if (target != sender) Messages.send(target, "<light_purple>Your Credits balance was updated by an administrator.</light_purple>");
        }));
        return true;
    }
    private void main(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
}
