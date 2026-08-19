package me.growapet.plot;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs {@code /plot visit}, {@code /plot exit} and {@code /plot home}. Every player's placed pets
 * and incubating eggs live at the same single shared plot location (see {@link PlotManager}) and are
 * client-sided (visible only to their owner by default). Visiting swaps whose placements a viewer
 * currently sees — ported from Petcore's {@code me.petcore.placedpets.PlotVisitManager}, the same
 * mechanism that lets the shared space read as "walking into someone else's plot" without any real
 * per-player world instancing.
 */
public final class PlotVisitManager implements Listener {
    private final GrowAPet plugin;

    /** Visitor UUID -> host UUID they're currently viewing (absent = viewing their own plot). */
    private final Map<UUID, UUID> visiting = new HashMap<>();

    public PlotVisitManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    /** Whichever owner's placements {@code viewer} currently sees — themself if not visiting anyone. */
    public UUID currentHost(UUID viewer) {
        return visiting.getOrDefault(viewer, viewer);
    }

    public boolean isVisiting(UUID viewer) {
        return visiting.containsKey(viewer);
    }

    /** Every online player currently visiting {@code hostUuid}'s plot. */
    public List<Player> visitorsOf(UUID hostUuid) {
        List<Player> result = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : visiting.entrySet()) {
            if (!hostUuid.equals(entry.getValue())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) result.add(player);
        }
        return result;
    }

    public void visit(Player visitor, UUID hostUuid) {
        UUID visitorId = visitor.getUniqueId();
        if (hostUuid.equals(visitorId)) {
            home(visitor);
            return;
        }
        if (hostUuid.equals(visiting.get(visitorId))) {
            visitor.sendMessage("§cYou're already visiting that plot.");
            return;
        }
        plugin.getPlotManager().teleportHome(visitor).thenAccept(success -> {
            if (!visitor.isOnline()) return;
            if (!success) { visitor.sendMessage("§cCouldn't warp to the plot — visit cancelled."); return; }
            visiting.put(visitorId, hostUuid);
            refresh(visitor);
            visitor.sendMessage("§aVisiting that plot. Use §e/plot exit §ato leave.");
        });
    }

    public void exit(Player visitor) {
        UUID host = visiting.remove(visitor.getUniqueId());
        if (host == null) { visitor.sendMessage("§cYou're not visiting anyone's plot."); return; }
        refresh(visitor);
        visitor.sendMessage("§aLeft the plot — back to your own.");
    }

    public void home(Player player) {
        boolean wasVisiting = visiting.remove(player.getUniqueId()) != null;
        plugin.getPlotManager().teleportHome(player).thenAccept(success -> {
            if (!player.isOnline()) return;
            if (wasVisiting) refresh(player);
            if (success) player.sendMessage("§aTeleported to your plot.");
            else player.sendMessage("§cUnable to teleport to your plot safely.");
        });
    }

    /** Re-syncs which placed pets/eggs {@code viewer} sees right now instead of waiting for the next tick. */
    private void refresh(Player viewer) {
        plugin.getPetManager().refreshViewer(viewer);
        plugin.getVirtualTextDisplays().refreshViewer(viewer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visiting.remove(event.getPlayer().getUniqueId());
    }
}
