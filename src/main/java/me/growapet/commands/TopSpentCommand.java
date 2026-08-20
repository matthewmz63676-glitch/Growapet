package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.leaderboards.LeaderboardType;
import me.growapet.leaderboards.MoneySpentManager;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Locale;

public final class TopSpentCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public TopSpentCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { if (sender instanceof Player player) plugin.getLeaderboardManager().showPersonal(player, LeaderboardType.TOPMONEYSPENT); else printTop(sender); return true; }
        if (!sender.hasPermission("growapet.admin.topspent")) { Messages.send(sender, "<red>You do not have permission to administer TOPMONEYSPENT.</red>"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("clearall")) {
            if (args.length != 2 || !args[1].equalsIgnoreCase("confirm")) { Messages.send(sender, "<yellow>Usage: /topspent clearall confirm</yellow>"); return true; }
            plugin.getMoneySpentManager().clearAll().whenComplete((count, error) -> main(() -> Messages.send(sender, error == null ? "<green>Cleared <count> spending records.</green>" : "<red>Could not clear spending records.</red>", Messages.value("count", count == null ? 0 : count)))); return true;
        }
        if (sub.equals("check")) {
            if (args.length != 2) { Messages.send(sender, "<yellow>Usage: /topspent check <player></yellow>"); return true; }
            resolve(sender, args[1], target -> plugin.getMoneySpentManager().find(target).whenComplete((found, error) -> main(() -> Messages.send(sender, error != null || found == null ? "<gray>No TOPMONEYSPENT record exists.</gray>" : "<gold>" + found.name() + "</gold> <dark_gray>→</dark_gray> <white>" + MoneySpentManager.format(found.cents()) + "</white>")))); return true;
        }
        if (sub.equals("clear")) {
            if (args.length != 2) { Messages.send(sender, "<yellow>Usage: /topspent clear <player></yellow>"); return true; }
            resolve(sender, args[1], target -> plugin.getMoneySpentManager().clear(target).whenComplete((removed, error) -> main(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(removed) ? "<green>Spending record cleared.</green>" : "<red>No spending record was cleared.</red>")))); return true;
        }
        if (args.length != 2) { Messages.send(sender, "<yellow>Usage: /topspent <player> <amount></yellow>"); return true; }
        BigDecimal amount;
        try { amount = new BigDecimal(args[1]); MoneySpentManager.toCents(amount); } catch (Exception error) { Messages.send(sender, "<red>Amount must be positive with at most two decimal places.</red>"); return true; }
        resolve(sender, args[0], target -> plugin.getMoneySpentManager().add(target, amount).whenComplete((total, error) -> main(() -> Messages.send(sender, error == null ? "<green>Recorded purchase. New lifetime total: <yellow><total></yellow>.</green>" : "<red>The purchase was not recorded safely.</red>", Messages.value("total", error == null ? MoneySpentManager.format(total) : "")))));
        return true;
    }

    private void printTop(CommandSender sender) { plugin.getMoneySpentManager().top(3).whenComplete((entries, error) -> main(() -> { if (error != null) { Messages.send(sender, "<red>TOPMONEYSPENT is unavailable.</red>"); return; } Messages.send(sender, "<gold><bold>TOP MONEY SPENT</bold></gold>"); for (int i = 0; i < entries.size(); i++) { MoneySpentManager.Entry entry = entries.get(i); Messages.send(sender, "<gray>#" + (i + 1) + "</gray> <white>" + entry.name() + "</white> <dark_gray>→</dark_gray> <gold>" + MoneySpentManager.format(entry.cents()) + "</gold>"); } })); }
    private void resolve(CommandSender sender, String input, java.util.function.Consumer<MoneySpentManager.Target> action) { plugin.getMoneySpentManager().resolve(input).whenComplete((target, error) -> main(() -> { if (error != null || target == null) Messages.send(sender, "<red>That player has no GrowAPet profile.</red>"); else action.accept(target); })); }
    private void main(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
}
