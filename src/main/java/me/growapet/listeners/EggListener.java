/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EggListener
implements Listener {
    private final GrowAPet plugin;

    public EggListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlaceEgg(PlayerInteractEvent event) {
        int limit;
        Block placeAt;
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EntityType entityType = this.plugin.getEggManager().getEggType(item);
        if (entityType == null) {
            return;
        }
        event.setCancelled(true);
        Block block = placeAt = event.getClickedBlock() != null ? event.getClickedBlock().getRelative(event.getBlockFace()) : player.getLocation().getBlock();
        if (placeAt.getType() != Material.AIR) {
            player.sendMessage("\u00a7cThere's no room to place an egg there.");
            return;
        }
        Location target = placeAt.getLocation();
        if (!this.plugin.getPlotManager().isPlotRegion(target)) {
            player.sendMessage("\u00a7cYou can only place eggs inside the plot!");
            return;
        }
        Plot plot = this.plugin.getPlotManager().getPlot(player.getUniqueId());
        int n = limit = plot != null ? plot.getEggLimit() : 3;
        if (this.plugin.getEggIncubationManager().countActive(player.getUniqueId()) >= limit) {
            player.sendMessage("\u00a7cYou already have the maximum of \u00a7e" + limit + " \u00a7ceggs incubating.");
            return;
        }
        this.plugin.getEggIncubationManager().place(player, item.clone(), placeAt);
    }
}
