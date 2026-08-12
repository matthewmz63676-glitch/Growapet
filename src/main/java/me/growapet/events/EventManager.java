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
        return plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("SELECT event_type,ends_at,multiplier FROM event_state WHERE enabled=1 AND ends_at>?")){s.setLong(1,System.currentTimeMillis());try(ResultSet r=s.executeQuery()){while(r.next()){try{EventType type=EventType.valueOf(r.getString(1));active.put(type,new ActiveEvent(r.getLong(2),Math.max(1,r.getDouble(3))));}catch(IllegalArgumentException ignored){}}}}return null;});
    }
    public void start(){cleanupTask=Bukkit.getScheduler().runTaskTimer(plugin,this::expire,20L,20L);}
    public double multiplier(EventType type){ActiveEvent event=active.get(type);return event!=null&&event.endsAt>System.currentTimeMillis()?event.multiplier:1.0;}
    public boolean isActive(EventType type){return multiplier(type)>1.0 || (active.containsKey(type)&&active.get(type).endsAt>System.currentTimeMillis());}
    public CompletableFuture<Void> activate(EventType type,long seconds,double multiplier){long now=System.currentTimeMillis(),end=now+Math.max(1,seconds)*1000L;double safeMultiplier=Math.max(1,multiplier);return plugin.getDatabase().async(c->{try(PreparedStatement s=c.prepareStatement("INSERT INTO event_state(event_id,event_type,starts_at,ends_at,multiplier,enabled) VALUES(?,?,?,?,?,1) ON CONFLICT(event_id) DO UPDATE SET starts_at=excluded.starts_at,ends_at=excluded.ends_at,multiplier=excluded.multiplier,enabled=1")){s.setString(1,type.name());s.setString(2,type.name());s.setLong(3,now);s.setLong(4,end);s.setDouble(5,safeMultiplier);s.executeUpdate();}return null;}).thenRun(()->active.put(type,new ActiveEvent(end,safeMultiplier)));}
    public CompletableFuture<Void> deactivate(EventType type){return plugin.getDatabase().async(c->{try(PreparedStatement s=c.prepareStatement("UPDATE event_state SET enabled=0 WHERE event_id=?")){s.setString(1,type.name());s.executeUpdate();}return null;}).thenRun(()->active.remove(type));}
    private void expire(){long now=System.currentTimeMillis();active.entrySet().removeIf(e->e.getValue().endsAt<=now);if(++pulse%30!=0)return;for(org.bukkit.entity.Player player:Bukkit.getOnlinePlayers()){me.growapet.models.PlayerData data=plugin.getPlayerManager().get(player);if(data==null)continue;if(isActive(EventType.COIN_RAIN)){data.addCoinsRaw(Math.max(0,plugin.getConfigManager().config().getLong("events.coin-rain-coins",25)));player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,player.getLocation().add(0,1,0),12);}if(isActive(EventType.METEOR_SHOWER)){data.addGemsRaw(Math.max(0,plugin.getConfigManager().config().getLong("events.meteor-gems",1)));player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK,player.getLocation().add(0,3,0),20,2,2,2,.05);}}int cap=Math.max(1,plugin.getConfigManager().config().getInt("events.max-tracked-mobs",5000));if(isActive(EventType.TREASURE_GOBLIN)&&plugin.getMobManager().getTrackedCount()<cap){String mobId=plugin.getConfigManager().config().getString("events.treasure-goblin-mob","ZOMBIE");org.bukkit.configuration.ConfigurationSection mob=plugin.getMobManager().getMobConfig(mobId);String zoneId=mob==null?"":mob.getString("zone","");me.growapet.zones.Zone zone=plugin.getZoneManager().getZone(zoneId);org.bukkit.Location location=zone==null?plugin.getSpawnManager().getSpawn():zone.getWarp();if(location!=null)plugin.getMobManager().spawnMob(mobId,location);}}
    public void stop(){if(cleanupTask!=null)cleanupTask.cancel();}
    private record ActiveEvent(long endsAt,double multiplier){}
}
