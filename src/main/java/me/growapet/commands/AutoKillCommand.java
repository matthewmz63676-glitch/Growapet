package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

public final class AutoKillCommand implements CommandExecutor {
    private static final double REACH = 6.0;
    private final GrowAPet plugin;

    public AutoKillCommand(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>This command can only be used in-game.</red>");
            return true;
        }
        RayTraceResult result = player.getWorld().rayTrace(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                REACH,
                FluidCollisionMode.NEVER,
                true,
                0.2,
                plugin.getMobManager()::isTrackedRegularMob
        );
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)
                || !plugin.getMobManager().killTracked(target, player)) {
            Messages.send(player, "<red>Look directly at a GrowAPet mob within <white>6 blocks</white>.</red>");
            return true;
        }
        Messages.send(player, "<green>Autokill <dark_gray>•</dark_gray> <white>Target defeated.</white></green>");
        return true;
    }
}
