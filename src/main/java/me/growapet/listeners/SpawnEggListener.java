/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.block.Block
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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import me.growapet.mobs.MobManager;
import me.growapet.utils.Messages;

public class SpawnEggListener
implements Listener {
    private final GrowAPet plugin;

    public SpawnEggListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUseSpawnEgg(PlayerInteractEvent event) {
        Location spawnAt;
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String mobId = this.plugin.getSpawnEggManager().getMobId(item);
        if (mobId == null) {
            return;
        }
        if (!player.hasPermission("growapet.admin.mob")) { event.setCancelled(true); player.sendMessage("§cYou cannot use admin mob spawners."); return; }
        event.setCancelled(true);
        if (event.getClickedBlock() != null) {
            Block relative = event.getClickedBlock().getRelative(event.getBlockFace());
            spawnAt = relative.getLocation().add(0.5, 0.0, 0.5);
        } else {
            spawnAt = player.getLocation();
        }
        MobManager.SpawnResult result = this.plugin.getMobManager().spawnAdminMob(mobId, spawnAt);
        if (!result.successful()) {
            Messages.send(player, "<red>Could not spawn that mob.</red> <gray><reason></gray>", Messages.value("reason", result.detail()));
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }
}
