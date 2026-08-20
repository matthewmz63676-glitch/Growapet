package me.growapet.leaderboards;

import me.growapet.GrowAPet;
import me.growapet.database.PlayerDAO;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import me.growapet.utils.LocationSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** GrowAPet adaptation of the supplied persistent leaderboard-board pattern. */
public final class LeaderboardManager {
    private static final NumberFormat NUMBER=NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;private final PlayerDAO dao;private final File file;
    private final Map<String,Board>boards=new LinkedHashMap<>();private final Set<String>refreshing=ConcurrentHashMap.newKeySet();private BukkitTask task;
    private final Map<UUID,VirtualTextDisplayService.Handle>personalBoards=new LinkedHashMap<>();
    public LeaderboardManager(GrowAPet plugin){this.plugin=plugin;this.dao=new PlayerDAO(plugin.getDatabase());this.file=new File(plugin.getDataFolder(),"leaderboards.yml");}

    public void start(){load();task=Bukkit.getScheduler().runTaskTimer(plugin,this::refreshAll,20L,100L);}
    public void stop(){if(task!=null)task.cancel();task=null;refreshing.clear();for(Board board:boards.values())if(board.handle!=null)plugin.getVirtualTextDisplays().remove(board.handle);for(VirtualTextDisplayService.Handle handle:personalBoards.values())plugin.getVirtualTextDisplays().remove(handle);personalBoards.clear();boards.clear();}

    public CompletableFuture<List<Entry>> top(LeaderboardType type,int limit){
        if(type==LeaderboardType.TOPMONEYSPENT)return plugin.getMoneySpentManager().top(limit).thenApply(entries->entries.stream().map(entry->new Entry(entry.uuid(),entry.name(),entry.cents())).toList());
        List<Entry>live=new ArrayList<>();for(Player player:Bukkit.getOnlinePlayers()){PlayerData data=plugin.getPlayerManager().get(player);if(data!=null)live.add(new Entry(player.getUniqueId(),player.getName(),type.live(data)));}
        return dao.topPlayers(type.column(),Math.max(limit*3,limit)).thenApply(database->{Map<UUID,Entry>merged=new LinkedHashMap<>();for(PlayerDAO.LeaderboardEntry entry:database)merged.put(entry.uuid(),new Entry(entry.uuid(),entry.name(),entry.value()));for(Entry entry:live)merged.put(entry.uuid,entry);return merged.values().stream().sorted(Comparator.comparingDouble(Entry::value).reversed().thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER)).limit(limit).toList();});
    }

    public boolean create(String id,LeaderboardType type,Location location){if(id==null||!id.matches("[a-z0-9_-]{1,32}")||type==null||location==null||location.getWorld()==null||boards.containsKey(id)||LocationSafety.problem(location,"leaderboard location",true)!=null)return false;Board board=new Board(id,type,location.clone());boards.put(id,board);spawn(board);save();refresh(board);return true;}
    public boolean remove(String id){Board board=boards.remove(id);if(board==null)return false;if(board.handle!=null)plugin.getVirtualTextDisplays().remove(board.handle);save();return true;}
    public boolean move(String id,Location location){Board board=boards.get(id);if(board==null||location==null||location.getWorld()==null||LocationSafety.problem(location,"leaderboard location",true)!=null)return false;board.location=location.clone();if(board.handle!=null)plugin.getVirtualTextDisplays().teleport(board.handle,board.location);save();return true;}
    public List<String>ids(){return List.copyOf(boards.keySet());}public void refreshAll(){boards.values().forEach(this::refresh);}

    /** Shows a viewer-only packet Text Display; no Bukkit entity or inventory GUI is created. */
    public void showPersonal(Player player,LeaderboardType type){
        UUID viewerId=player.getUniqueId();Messages.send(player,"<gray>Preparing the <white><type></white> hologram…</gray>",Messages.value("type",type.label()));
        top(type,10).whenComplete((entries,error)->onMain(()->{
            Player viewer=Bukkit.getPlayer(viewerId);if(viewer==null||!viewer.isOnline())return;
            if(error!=null){plugin.getLogger().warning("Failed to load personal "+type.key()+" leaderboard for "+viewer.getName()+": "+error.getMessage());Messages.send(viewer,"<red>That leaderboard is temporarily unavailable.</red>");return;}
            VirtualTextDisplayService.Handle old=personalBoards.remove(viewerId);if(old!=null)plugin.getVirtualTextDisplays().remove(old);
            Location location=viewer.getEyeLocation().clone().add(viewer.getEyeLocation().getDirection().normalize().multiply(3.25)).add(0,-0.35,0);
            VirtualTextDisplayService.Handle handle=plugin.getVirtualTextDisplays().create(location,component(type,entries),candidate->candidate.getUniqueId().equals(viewerId));
            personalBoards.put(viewerId,handle);Messages.send(viewer,"<aqua>Leaderboard shown</aqua> <dark_gray>•</dark_gray> <gray>It will close in <white>15 seconds</white>.</gray>");
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(personalBoards.remove(viewerId,handle))plugin.getVirtualTextDisplays().remove(handle);},300L);
        }));
    }

    public void dismissPersonal(UUID viewerId){VirtualTextDisplayService.Handle handle=personalBoards.remove(viewerId);if(handle!=null)plugin.getVirtualTextDisplays().remove(handle);}

    public void reload(){for(Board board:boards.values())if(board.handle!=null)plugin.getVirtualTextDisplays().remove(board.handle);boards.clear();load();}
    private void load(){YamlConfiguration data=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=data.getConfigurationSection("boards");if(root==null)return;for(String id:root.getKeys(false)){LeaderboardType type=LeaderboardType.from(root.getString(id+".type"));World world=Bukkit.getWorld(root.getString(id+".world",""));if(type==null||world==null)continue;Board board=new Board(id,type,new Location(world,root.getDouble(id+".x"),root.getDouble(id+".y"),root.getDouble(id+".z"), (float)root.getDouble(id+".yaw"),0));if(LocationSafety.problem(board.location,"leaderboard "+id,false)!=null){plugin.getLogger().warning("Skipping unsafe leaderboard location '"+id+"'. Move it with /leaderboard movehere.");continue;}boards.put(id,board);spawn(board);refresh(board);}}
    private void save(){YamlConfiguration data=new YamlConfiguration();for(Board board:boards.values()){String base="boards."+board.id;data.set(base+".type",board.type.key());data.set(base+".world",board.location.getWorld().getName());data.set(base+".x",board.location.getX());data.set(base+".y",board.location.getY());data.set(base+".z",board.location.getZ());data.set(base+".yaw",board.location.getYaw());}try{data.save(file);}catch(IOException error){plugin.getLogger().severe("Failed to save leaderboards.yml: "+error.getMessage());}}
    private void spawn(Board board){board.handle=plugin.getVirtualTextDisplays().create(board.location,Messages.parse("<aqua><bold>"+board.type.label().toUpperCase(Locale.ROOT)+"</bold></aqua>\n<gray>Loading rankings…</gray>"),viewer->true);}
    private void refresh(Board board){if(!refreshing.add(board.id))return;top(board.type,10).whenComplete((entries,error)->{if(!plugin.isEnabled()){refreshing.remove(board.id);return;}try{Bukkit.getScheduler().runTask(plugin,()->{try{if(!boards.containsKey(board.id)||board.handle==null)return;if(error!=null){plugin.getVirtualTextDisplays().update(board.handle,Messages.parse("<red>Rankings unavailable</red>"));return;}plugin.getVirtualTextDisplays().update(board.handle,component(board.type,entries));}finally{refreshing.remove(board.id);}});}catch(org.bukkit.plugin.IllegalPluginAccessException ignored){refreshing.remove(board.id);}});}
    private void onMain(Runnable action){if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,action);}
    private static net.kyori.adventure.text.Component component(LeaderboardType type,List<Entry>entries){List<String>lines=new ArrayList<>();lines.add("<aqua><bold>"+type.label().toUpperCase(Locale.ROOT)+"</bold></aqua>");for(int i=0;i<10;i++){if(i>=entries.size()){lines.add("<dark_gray>#"+(i+1)+" • —</dark_gray>");continue;}Entry entry=entries.get(i);String color=switch(i){case 0->"<gold>";case 1->"<gray>";case 2->"<yellow>";default->"<white>";};lines.add(color+"#"+(i+1)+" • "+safe(entry.name)+" → "+format(type,entry.value)+color.replace("<","</"));}return Messages.parse(String.join("\n",lines));}
    public static String format(LeaderboardType type,double value){if(type==LeaderboardType.TOPMONEYSPENT)return MoneySpentManager.format((long)value);if(type==LeaderboardType.PLAYTIME){long seconds=(long)value;return(seconds/3600)+"h "+(seconds%3600/60)+"m";}return type.decimal()?String.format(Locale.US,"%.2fx",value):NUMBER.format((long)value);}
    private static String safe(String value){return value.replace("<","‹").replace(">","›");}
    public record Entry(UUID uuid,String name,double value){}
    private static final class Board{private final String id;private final LeaderboardType type;private Location location;private VirtualTextDisplayService.Handle handle;private Board(String id,LeaderboardType type,Location location){this.id=id;this.type=type;this.location=location;}}
}
