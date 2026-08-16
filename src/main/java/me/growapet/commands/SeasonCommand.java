package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.gui.SeasonMenu;
import me.growapet.seasons.SeasonService;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Player season journal plus granular admin lifecycle controls. */
public final class SeasonCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public SeasonCommand(GrowAPet plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || isSeasonId(args[0])) {
            if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Specify a season id from console.</red>"); return true; }
            String id = args.length == 0 ? firstId() : args[0];
            if (id == null) { Messages.send(player, "<yellow>No season is currently configured.</yellow>"); return true; }
            new SeasonMenu(plugin, player, id).open();
            return true;
        }
        if (!sender.hasPermission("growapet.admin.seasons")) { Messages.send(sender, "<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>"); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1] : firstId();
        if (id == null) { Messages.send(sender, "<red>No season definition is available.</red>"); return true; }
        switch (action) {
            case "start" -> {
                long days = args.length > 2 ? Long.parseLong(args[2]) : plugin.getSeasonService().definition(id).durationDays();
                plugin.getSeasonService().start(id, days).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> Messages.send(sender, error == null ? "<green>Season started.</green>" : "<red>Season start rejected safely.</red>")));
            }
            case "stop" -> plugin.getSeasonService().stop(id).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> Messages.send(sender, error == null ? "<yellow>Season stopped.</yellow>" : "<red>Season stop rejected safely.</red>")));
            case "status" -> Messages.send(sender, plugin.getSeasonService().isActive(id) ? "<green>Season is active.</green>" : "<gray>Season is inactive.</gray>");
            case "validate" -> Messages.send(sender, plugin.getSeasonService().validate(id) ? "<green>Season definition is valid.</green>" : "<red>Season definition is invalid; last-known-good remains active.</red>");
            case "preview" -> Messages.send(sender, plugin.getSeasonService().preview(id));
            case "reconcile" -> plugin.getSeasonService().reconcile(id).whenComplete((count, error) -> Bukkit.getScheduler().runTask(plugin, () -> Messages.send(sender, error == null ? "<green>Reconciled <white><count></white> pending claim(s).</green>" : "<red>Season reconciliation failed safely.</red>", Messages.value("count", count == null ? 0 : count))));
            default -> Messages.send(sender, "<red>Usage: /season <start|stop|status|validate|preview|reconcile> [id]</red>");
        }
        return true;
    }

    private boolean isSeasonId(String value) { return plugin.getSeasonService().definition(value) != null; }
    private String firstId() { return plugin.getSeasonService().ids().stream().sorted().findFirst().orElse(null); }
}
