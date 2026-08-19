package me.growapet.eggs;

import me.growapet.GrowAPet;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Incubating eggs are packet-only: nothing real is ever written to the world (no {@code TURTLE_EGG}
 * block, no crack-stage block data). Placement is confined to the single shared "plot" WorldGuard
 * region (see PlotManager) and the countdown hologram is only shown to the egg's owner and whoever is
 * currently visiting their plot (see PlotVisitManager) — several owners can anchor an egg at the exact
 * same visual coordinate since nothing physical collides any more.
 */
public final class EggIncubationManager implements Listener {
    private final GrowAPet plugin;
    private final Map<EggKey, IncubatingEgg> active = new ConcurrentHashMap<>();
    private final Map<UUID, EggKey> placements = new ConcurrentHashMap<>();
    private final Map<EggKey, VirtualTextDisplayService.Handle> displays = new ConcurrentHashMap<>();
    private final Map<EggKey, Integer> crackStage = new ConcurrentHashMap<>();
    private BukkitTask task;

    public EggIncubationManager(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<EggRow> rows=new ArrayList<>();
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM incubating_eggs");ResultSet r=s.executeQuery()){
                while(r.next()) rows.add(new EggRow(UUID.fromString(r.getString("owner")),r.getString("world"),r.getInt("x"),r.getInt("y"),r.getInt("z"),r.getString("entity_type"),r.getInt("total_seconds"),r.getLong("hatch_at")));
            }
            return rows;
        }).thenCompose(rows -> onMain(() -> {
            active.clear();
            displays.values().forEach(plugin.getVirtualTextDisplays()::remove);
            displays.clear();
            for(EggRow row:rows){ World world=Bukkit.getWorld(row.world()); if(world==null){plugin.getLogger().warning("Incubating egg is waiting for missing world "+row.world());continue;} try{IncubatingEgg egg=row.toEgg(world);active.put(EggKey.of(row.owner(),egg.getLocation()),egg);}catch(IllegalArgumentException e){plugin.getLogger().warning("Invalid incubating egg at "+row.world()+":"+row.x()+","+row.y()+","+row.z());}}
            active.forEach((key,egg)->createDisplay(key,egg));
            start();
        }));
    }

    public boolean place(Player player, ItemStack item, Block block) {
        if (placements.containsKey(player.getUniqueId())) { player.sendMessage("§cYour previous egg placement is still being saved."); return false; }
        EntityType type=plugin.getEggManager().getEggType(item); if(type==null)return false;
        UUID playerId=player.getUniqueId();
        EggKey key=EggKey.of(playerId,block.getLocation()); if(active.containsKey(key))return false;
        int configuredSeconds=plugin.getEggManager().getIncubateSeconds(item);double speed=plugin.getPlotBoostManager().multiplier(playerId,me.growapet.boosts.BoostType.HATCH_SPEED);int seconds=Math.max(1,(int)Math.ceil(configuredSeconds/speed)); long hatchAt=System.currentTimeMillis()+seconds*1000L;
        placements.put(playerId,key);
        plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("INSERT INTO incubating_eggs(owner,world,x,y,z,entity_type,total_seconds,hatch_at) VALUES(?,?,?,?,?,?,?,?)")){s.setString(1,key.owner().toString());s.setString(2,key.world());s.setInt(3,key.x());s.setInt(4,key.y());s.setInt(5,key.z());s.setString(6,type.name());s.setInt(7,seconds);s.setLong(8,hatchAt);s.executeUpdate();}return null;})
                .whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->finishPlacement(playerId,item,type,seconds,hatchAt,block,key,error)));
        return true;
    }

    private void finishPlacement(UUID owner, ItemStack original, EntityType type, int seconds, long hatchAt, Block block, EggKey key, Throwable error) {
        placements.remove(owner,key); Player player=Bukkit.getPlayer(owner);
        if(error!=null){if(player!=null)player.sendMessage("§cThe egg could not be saved, so it was not placed.");plugin.getLogger().severe("Egg placement failed: "+error.getMessage());return;}
        if(player==null || !player.isOnline() || !plugin.getPlotManager().isPlotRegion(block.getLocation()) || !consumeMatchingEgg(player,original)){
            delete(key); if(player!=null)player.sendMessage("§cEgg placement was cancelled because the location or item changed."); return;
        }
        IncubatingEgg egg=new IncubatingEgg(owner,type,block.getLocation(),seconds,hatchAt);active.put(key,egg);createDisplay(key,egg);me.growapet.utils.Messages.send(player,"<green>Egg placed.</green> <gray>Hatching in <white><time></white>.</gray>",me.growapet.utils.Messages.value("time",formatTime(seconds)));
    }

    private boolean consumeMatchingEgg(Player player, ItemStack original) {
        for(ItemStack candidate:player.getInventory().getContents()){
            if(candidate==null || candidate.getAmount()<1 || !candidate.isSimilar(original))continue;
            candidate.setAmount(candidate.getAmount()-1);return true;
        }
        return false;
    }

    private void start(){if(task==null)task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,20L);}
    private void tick(){for(Map.Entry<EggKey,IncubatingEgg> entry:new ArrayList<>(active.entrySet())){EggKey key=entry.getKey();IncubatingEgg egg=entry.getValue();if(egg.getSecondsRemaining()<=0){hatch(key,egg);continue;}updateDisplay(key,egg);double progress=1.0-(double)egg.getSecondsRemaining()/egg.getTotalSeconds();int stage=progress>=.66?2:progress>=.33?1:0;Integer previous=crackStage.put(key,stage);if(previous!=null&&previous!=stage){notifyOwnerAndVisitors(egg.getOwner(),p->p.playSound(egg.getLocation(),Sound.ENTITY_TURTLE_EGG_CRACK,1,1));}}}

    private void hatch(EggKey key, IncubatingEgg egg){if(!active.remove(key,egg))return;removeDisplay(key);crackStage.remove(key);Pet pet;try{pet=plugin.getPetManager().createHatchCandidate(egg.getOwner(),egg.getEntityType());}catch(Exception error){active.put(key,egg);createDisplay(key,egg);return;}plugin.getDatabase().transaction(connection->{plugin.getPetManager().insert(connection,pet);try(PreparedStatement s=connection.prepareStatement("DELETE FROM incubating_eggs WHERE owner=? AND world=? AND x=? AND y=? AND z=?")){s.setString(1,key.owner().toString());s.setString(2,key.world());s.setInt(3,key.x());s.setInt(4,key.y());s.setInt(5,key.z());if(s.executeUpdate()!=1)throw new IllegalStateException("Incubation row missing");}return null;}).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{if(error==null)plugin.getPetManager().registerHatched(pet);finishHatch(key,egg,pet,error);}));}
    private void finishHatch(EggKey key, IncubatingEgg egg, Pet pet, Throwable error){if(error!=null){active.put(key,egg);createDisplay(key,egg);plugin.getLogger().severe("Hatch failed; incubation retained: "+error.getMessage());return;}Location fx=egg.getLocation().clone().add(.5,.5,.5);notifyOwnerAndVisitors(egg.getOwner(),p->{p.spawnParticle(Particle.TOTEM_OF_UNDYING,fx,40);p.playSound(fx,Sound.ENTITY_TURTLE_EGG_HATCH,1,1);});PlayerData data=plugin.getPlayerManager().get(egg.getOwner());if(data!=null){data.incrementEggsHatched();data.incrementPetsCollected();plugin.getQuestManager().record(egg.getOwner(),me.growapet.quests.QuestType.EGGS_HATCHED,1,null);plugin.getSeasonService().record(egg.getOwner(),"EGGS_HATCHED",1);plugin.getCreditMilestoneManager().evaluate(egg.getOwner());}Player owner=Bukkit.getPlayer(egg.getOwner());if(owner!=null)me.growapet.utils.Messages.send(owner,"<light_purple><bold>PET HATCHED</bold></light_purple> <gray>• <yellow><rarity> <type></yellow></gray>",me.growapet.utils.Messages.value("rarity",pet.getRarity().name()),me.growapet.utils.Messages.value("type",pet.getEntityType().name()));plugin.getTutorialManager().notifyAction(egg.getOwner(),me.growapet.tutorial.TutorialAction.EGG_HATCH);}

    private CompletableFuture<Void> delete(EggKey key){crackStage.remove(key);return plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("DELETE FROM incubating_eggs WHERE owner=? AND world=? AND x=? AND y=? AND z=?")){s.setString(1,key.owner().toString());s.setString(2,key.world());s.setInt(3,key.x());s.setInt(4,key.y());s.setInt(5,key.z());s.executeUpdate();}return null;});}
    public int countActive(UUID owner){return(int)active.values().stream().filter(e->e.getOwner().equals(owner)).count();}
    public int bypassAll(UUID owner){List<Map.Entry<EggKey,IncubatingEgg>> eggs=active.entrySet().stream().filter(e->e.getValue().getOwner().equals(owner)).toList();for(Map.Entry<EggKey,IncubatingEgg> entry:eggs)hatch(entry.getKey(),entry.getValue());return eggs.size();}
    public void stop(){if(task!=null)task.cancel();task=null;placements.clear();displays.values().forEach(plugin.getVirtualTextDisplays()::remove);displays.clear();}

    /** Runs {@code action} for the egg's owner (if online) and everyone currently visiting their plot,
     *  so incubation sound/particle feedback stays as client-sided as the egg itself. */
    private void notifyOwnerAndVisitors(UUID owner, Consumer<Player> action){Player ownerPlayer=Bukkit.getPlayer(owner);if(ownerPlayer!=null)action.accept(ownerPlayer);for(Player visitor:plugin.getPlotVisitManager().visitorsOf(owner))action.accept(visitor);}

    private CompletableFuture<Void> onMain(Runnable r){CompletableFuture<Void>f=new CompletableFuture<>();Bukkit.getScheduler().runTask(plugin,()->{try{r.run();f.complete(null);}catch(Throwable t){f.completeExceptionally(t);}});return f;}
    private void createDisplay(EggKey key, IncubatingEgg egg){removeDisplay(key);Location location=egg.getLocation().clone().add(.5,1.35,.5);displays.put(key,plugin.getVirtualTextDisplays().create(location,displayText(egg),viewer->plugin.getPlotManager().isPlotRegion(viewer.getLocation())&&plugin.getPlotVisitManager().currentHost(viewer.getUniqueId()).equals(egg.getOwner())));}
    private void updateDisplay(EggKey key, IncubatingEgg egg){VirtualTextDisplayService.Handle handle=displays.get(key);if(handle==null){createDisplay(key,egg);return;}plugin.getVirtualTextDisplays().update(handle,displayText(egg));}
    private void removeDisplay(EggKey key){VirtualTextDisplayService.Handle handle=displays.remove(key);if(handle!=null)plugin.getVirtualTextDisplays().remove(handle);}
    private net.kyori.adventure.text.Component displayText(IncubatingEgg egg){return me.growapet.utils.Messages.parse("<light_purple><bold>HATCHING</bold></light_purple>\n<gray>"+formatTime(egg.getSecondsRemaining())+" remaining</gray>");}
    private static String formatTime(long seconds){long safe=Math.max(0,seconds);return String.format(java.util.Locale.US,"%02d:%02d",safe/60,safe%60);}

    /** Multiple owners can legitimately anchor an egg at the same visual (world,x,y,z) now that placement
     *  is client-sided, so the owner is part of the identity. */
    private record EggKey(UUID owner,String world,int x,int y,int z){
        static EggKey of(UUID owner, Location l){return new EggKey(owner,l.getWorld().getName(),l.getBlockX(),l.getBlockY(),l.getBlockZ());}
    }
    private record EggRow(UUID owner,String world,int x,int y,int z,String type,int seconds,long hatchAt){IncubatingEgg toEgg(World w){return new IncubatingEgg(owner,EntityType.valueOf(type),new Location(w,x,y,z),seconds,hatchAt);}}
}
