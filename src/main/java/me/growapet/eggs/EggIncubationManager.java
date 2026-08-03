/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.type.TurtleEgg
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.entity.EntityChangeBlockEvent
 *  org.bukkit.event.entity.EntityInteractEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package me.growapet.eggs;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import me.growapet.eggs.IncubatingEgg;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class EggIncubationManager
implements Listener {
    private final GrowAPet plugin;
    private final Map<Location, IncubatingEgg> activeEggs = new ConcurrentHashMap<Location, IncubatingEgg>();

    public EggIncubationManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public int countActive(UUID owner) {
        int count = 0;
        for (IncubatingEgg egg : this.activeEggs.values()) {
            if (!egg.getOwner().equals(owner)) continue;
            ++count;
        }
        return count;
    }

    public boolean isIncubating(Location blockLocation) {
        return this.activeEggs.containsKey(blockLocation);
    }

    public void startIncubation(Player owner, EntityType entityType, int totalSeconds, Block block) {
        block.setType(Material.TURTLE_EGG);
        TurtleEgg data = (TurtleEgg)block.getBlockData();
        data.setEggs(1);
        data.setHatch(0);
        block.setBlockData((BlockData)data);
        Location location = block.getLocation();
        IncubatingEgg egg = new IncubatingEgg(owner.getUniqueId(), entityType, location, totalSeconds);
        this.activeEggs.put(location, egg);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> this.tick(egg), 20L, 20L);
        egg.setTask(task);
    }

    private void tick(IncubatingEgg egg) {
        egg.setSecondsRemaining(egg.getSecondsRemaining() - 1);
        if (egg.getSecondsRemaining() <= 0) {
            this.hatchNow(egg);
            return;
        }
        Block block = egg.getLocation().getBlock();
        if (block.getType() != Material.TURTLE_EGG) {
            egg.getTask().cancel();
            this.activeEggs.remove(egg.getLocation());
            return;
        }
        double progress = 1.0 - (double)egg.getSecondsRemaining() / (double)egg.getTotalSeconds();
        int stage = progress >= 0.66 ? 2 : (progress >= 0.33 ? 1 : 0);
        TurtleEgg data = (TurtleEgg)block.getBlockData();
        if (data.getHatch() != stage) {
            data.setHatch(stage);
            block.setBlockData((BlockData)data);
            block.getWorld().playSound(egg.getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, 1.0f, 1.0f);
            block.getWorld().spawnParticle(Particle.ITEM, egg.getLocation().clone().add(0.5, 0.4, 0.5), 8, 0.2, 0.2, 0.2, (Object)new ItemStack(Material.TURTLE_EGG));
        }
    }

    public void hatchNow(IncubatingEgg egg) {
        Player owner;
        if (egg.getTask() != null) {
            egg.getTask().cancel();
        }
        this.activeEggs.remove(egg.getLocation());
        Block block = egg.getLocation().getBlock();
        if (block.getType() == Material.TURTLE_EGG) {
            block.setType(Material.AIR);
        }
        Location fx = egg.getLocation().clone().add(0.5, 0.5, 0.5);
        fx.getWorld().playSound(fx, Sound.ENTITY_TURTLE_EGG_HATCH, 1.0f, 1.0f);
        fx.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, fx, 40);
        fx.getWorld().playSound(fx, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        Pet pet = this.plugin.getPetManager().hatch(egg.getOwner(), egg.getEntityType());
        PlayerData data = this.plugin.getPlayerManager().get(egg.getOwner());
        if (data != null) {
            data.setEggsHatched(data.getEggsHatched() + 1L);
            data.setPetsCollected(data.getPetsCollected() + 1L);
        }
        if ((owner = Bukkit.getPlayer((UUID)egg.getOwner())) != null) {
            owner.sendMessage("\u00a7d\u00a7lPET HATCHED! \u00a7r\u00a77You received a \u00a7e" + String.valueOf((Object)pet.getRarity()) + " " + pet.getSize() + " " + egg.getEntityType().name() + "\u00a77!");
        }
    }

    public int bypassAll(UUID owner) {
        ArrayList<IncubatingEgg> matching = new ArrayList<IncubatingEgg>();
        for (IncubatingEgg egg : this.activeEggs.values()) {
            if (!egg.getOwner().equals(owner)) continue;
            matching.add(egg);
        }
        for (IncubatingEgg egg : matching) {
            this.hatchNow(egg);
        }
        return matching.size();
    }

    public void stop() {
        for (IncubatingEgg egg : this.activeEggs.values()) {
            if (egg.getTask() == null) continue;
            egg.getTask().cancel();
        }
        this.activeEggs.clear();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (this.isIncubating(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("\u00a7cThis egg is incubating - only an admin bypass command can skip it.");
        }
    }

    @EventHandler
    public void onNaturalHatch(EntityChangeBlockEvent event) {
        if (this.isIncubating(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTrample(EntityInteractEvent event) {
        if (this.isIncubating(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}

