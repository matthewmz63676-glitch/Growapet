package me.growapet.credits;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Five server-authoritative Credit relics per valid zone, viewer-culled by zone. */
public final class RelicManager implements Listener {
    private final GrowAPet plugin; private final NamespacedKey zoneKey, receiptKey;
    private final Map<UUID,Item> relics=new ConcurrentHashMap<>();private final java.util.Set<UUID>pending=ConcurrentHashMap.newKeySet();private BukkitTask task;
    public RelicManager(GrowAPet plugin){this.plugin=plugin;zoneKey=new NamespacedKey(plugin,"relic_zone");receiptKey=new NamespacedKey(plugin,"relic_receipt");}
    public void start(){removeOrphans();replenish();task=Bukkit.getScheduler().runTaskTimer(plugin,()->{replenish();cull();},100L,100L);}
    public void stop(){if(task!=null)task.cancel();task=null;for(Item item:new ArrayList<>(relics.values())){for(Player viewer:Bukkit.getOnlinePlayers())viewer.showEntity(plugin,item);item.remove();}relics.clear();pending.clear();}
    private void removeOrphans(){for(org.bukkit.World world:Bukkit.getWorlds())for(Entity entity:new ArrayList<>(world.getEntities()))if(entity instanceof Item item&&item.getPersistentDataContainer().has(zoneKey,PersistentDataType.STRING))item.remove();}
    private void replenish(){int target=Math.max(1,Math.min(20,plugin.getConfigManager().config().getInt("relics.per-zone",5)));for(Zone zone:plugin.getZoneManager().getZonesInOrder()){if(zone.getId().equals("spawn")&&!plugin.getConfigManager().config().getBoolean("relics.include-spawn",false))continue;long count=relics.values().stream().filter(Item::isValid).filter(item->zone.getId().equals(item.getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING))).count();for(long i=count;i<target;i++)spawn(zone);}relics.entrySet().removeIf(entry->!entry.getValue().isValid());}
    private void spawn(Zone zone){ProtectedCuboidRegion region=plugin.getZoneManager().cuboid(zone);if(region==null)return;BlockVector3 min=region.getMinimumPoint(),max=region.getMaximumPoint();org.bukkit.World world=zone.getWarp().getWorld();Location location=null;for(int attempt=0;attempt<8&&location==null;attempt++){int x=random(min.x(),max.x()),z=random(min.z(),max.z());if(!world.isChunkLoaded(x>>4,z>>4))continue;int highest=world.getHighestBlockYAt(x,z)+1;int y=Math.max(min.y()+1,Math.min(max.y(),highest));if(!world.getBlockAt(x,y-1,z).getType().isSolid()||!world.getBlockAt(x,y,z).isPassable())continue;location=new Location(world,x+.5,y+.25,z+.5);}if(location==null)return;ItemStack stack=new me.growapet.gui.ItemBuilder(Material.AMETHYST_SHARD).name(Messages.parse("<light_purple><bold>CREDIT RELIC</bold></light_purple>")).loreComponents(List.of(Messages.parse("<gray>• Pick up → receive <white>2 Credits</white></gray>"))).build();Item item=location.getWorld().dropItem(location,stack);String receipt="relic:"+UUID.randomUUID();item.getPersistentDataContainer().set(zoneKey,PersistentDataType.STRING,zone.getId());item.getPersistentDataContainer().set(receiptKey,PersistentDataType.STRING,receipt);item.setUnlimitedLifetime(true);item.setInvulnerable(true);item.setCanMobPickup(false);item.setPickupDelay(0);item.setGlowing(true);relics.put(item.getUniqueId(),item);}
    private void cull(){for(Player viewer:Bukkit.getOnlinePlayers())refreshViewer(viewer);}
    public void refreshViewer(Player viewer){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Relic visibility must update on the server thread");Zone current=plugin.getZoneManager().getZoneAt(viewer.getLocation());for(Item relic:relics.values()){String zone=relic.getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING);if(current!=null&&current.getId().equals(zone))viewer.showEntity(plugin,relic);else viewer.hideEntity(plugin,relic);}}
    @EventHandler(ignoreCancelled=true)public void onPickup(EntityPickupItemEvent event){if(!(event.getEntity() instanceof Player player))return;Item item=event.getItem();String zone=item.getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING);if(zone==null)return;event.setCancelled(true);if(!plugin.getZoneManager().isPlayerInZone(player,zone)||!pending.add(item.getUniqueId()))return;String receipt=item.getPersistentDataContainer().get(receiptKey,PersistentDataType.STRING);plugin.getCreditGrantService().grant(receipt,player.getUniqueId(),2,"ZONE_RELIC").whenComplete((granted,error)->Bukkit.getScheduler().runTask(plugin,()->{pending.remove(item.getUniqueId());if(error!=null){plugin.getLogger().warning("Relic grant failed: "+error.getMessage());return;}if(Boolean.TRUE.equals(granted)){relics.remove(item.getUniqueId());item.remove();Messages.send(player,"<light_purple><bold>RELIC FOUND</bold></light_purple> <dark_gray>•</dark_gray> <gray>+<white>2 Credits</white></gray>");}}));}
    @EventHandler(ignoreCancelled=true)public void onMerge(ItemMergeEvent event){if(event.getEntity().getPersistentDataContainer().has(zoneKey,PersistentDataType.STRING)||event.getTarget().getPersistentDataContainer().has(zoneKey,PersistentDataType.STRING))event.setCancelled(true);}
    private static int random(int min,int max){return min>=max?min:ThreadLocalRandom.current().nextInt(min,max+1);}
}
