package me.growapet.events;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class EventManager {
    private final GrowAPet plugin;
    private final Map<EventType, ActiveEvent> active = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;
    private int pulse;
    public EventManager(GrowAPet plugin) { this.plugin=plugin; }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("SELECT starts_at,ends_at,event_type,multiplier FROM event_state WHERE enabled=1 AND lifecycle_state='ACTIVE' AND starts_at<=? AND ends_at>?")){long now=System.currentTimeMillis();s.setLong(1,now);s.setLong(2,now);try(ResultSet r=s.executeQuery()){while(r.next()){try{EventType type=EventType.valueOf(r.getString(3));active.merge(type,new ActiveEvent(r.getLong(1),r.getLong(2),Math.max(1,r.getDouble(4))), (left,right)->left.multiplier>=right.multiplier?left:right);}catch(IllegalArgumentException ignored){}}}}return null;});
    }
    public void start(){cleanupTask=Bukkit.getScheduler().runTaskTimer(plugin,this::expire,20L,20L);}
    public double multiplier(EventType type){ActiveEvent event=active.get(type);long now=System.currentTimeMillis();return event!=null&&event.startsAt<=now&&event.endsAt>now?event.multiplier:1.0;}
    public boolean isActive(EventType type){ActiveEvent event=active.get(type);long now=System.currentTimeMillis();return event!=null&&event.startsAt<=now&&event.endsAt>now;}
    public CompletableFuture<Void> activate(EventType type,long seconds,double multiplier){long now=System.currentTimeMillis(),end=now+Math.max(1,seconds)*1000L;double safeMultiplier=Math.max(1,multiplier);return plugin.getDatabase().async(c->{try(PreparedStatement s=c.prepareStatement("INSERT INTO event_state(event_id,event_type,starts_at,ends_at,multiplier,enabled,definition_version,definition_checksum,stacking_policy,lifecycle_state,updated_at) VALUES(?,?,?,?,?,1,?,?,?,?,?) ON CONFLICT(event_id) DO UPDATE SET starts_at=excluded.starts_at,ends_at=excluded.ends_at,multiplier=MAX(event_state.multiplier,excluded.multiplier),enabled=1,definition_checksum=excluded.definition_checksum,stacking_policy='HIGHEST',lifecycle_state='ACTIVE',updated_at=excluded.updated_at")){s.setString(1,type.name());s.setString(2,type.name());s.setLong(3,now);s.setLong(4,end);s.setDouble(5,safeMultiplier);s.setString(6,"1");s.setString(7,Integer.toHexString(type.name().hashCode()));s.setString(8,"HIGHEST");s.setString(9,"ACTIVE");s.setLong(10,now);s.executeUpdate();}return null;}).thenRun(()->active.merge(type,new ActiveEvent(now,end,safeMultiplier),(left,right)->left.multiplier>=right.multiplier?left:right));}
    public CompletableFuture<Void> deactivate(EventType type){return plugin.getDatabase().async(c->{try(PreparedStatement s=c.prepareStatement("UPDATE event_state SET enabled=0,lifecycle_state='ENDED',updated_at=? WHERE event_id=?")){s.setLong(1,System.currentTimeMillis());s.setString(2,type.name());s.executeUpdate();}return null;}).thenRun(()->active.remove(type));}
    private void expire(){long now=System.currentTimeMillis();active.entrySet().removeIf(e->{if(e.getValue().endsAt<=now){plugin.getDatabase().async(c->{try(PreparedStatement s=c.prepareStatement("UPDATE event_state SET enabled=0,lifecycle_state='ENDED',updated_at=? WHERE event_id=? AND ends_at<=?")){s.setLong(1,now);s.setString(2,e.getKey().name());s.setLong(3,now);s.executeUpdate();}return null;});return true;}return false;});if(++pulse%30!=0)return;for(org.bukkit.entity.Player player:Bukkit.getOnlinePlayers()){me.growapet.models.PlayerData data=plugin.getPlayerManager().get(player);if(data==null)continue;if(isActive(EventType.COIN_RAIN)){data.addCoinsRaw(Math.max(0,plugin.getConfigManager().config().getLong("events.coin-rain-coins",25)));player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,player.getLocation().add(0,1,0),12);}if(isActive(EventType.METEOR_SHOWER)){data.addGemsRaw(Math.max(0,plugin.getConfigManager().config().getLong("events.meteor-gems",1)));player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK,player.getLocation().add(0,3,0),20,2,2,2,.05);}}int cap=Math.max(1,plugin.getConfigManager().config().getInt("events.max-tracked-mobs",5000));if(isActive(EventType.TREASURE_GOBLIN)&&plugin.getMobManager().getTrackedCount()<cap){String mobId=plugin.getConfigManager().config().getString("events.treasure-goblin-mob","ZOMBIE");org.bukkit.configuration.ConfigurationSection mob=plugin.getMobManager().getMobConfig(mobId);String zoneId=mob==null?"":mob.getString("zone","");me.growapet.zones.Zone zone=plugin.getZoneManager().getZone(zoneId);org.bukkit.Location location=zone==null?plugin.getSpawnManager().getSpawn():zone.getWarp();if(location!=null)plugin.getMobManager().spawnMob(mobId,location);}}
    public void stop(){if(cleanupTask!=null)cleanupTask.cancel();}
    private record ActiveEvent(long startsAt,long endsAt,double multiplier){}
}
