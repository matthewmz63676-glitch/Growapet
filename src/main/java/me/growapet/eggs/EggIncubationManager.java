package me.growapet.eggs;

import me.growapet.GrowAPet;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
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

public final class EggIncubationManager implements Listener {
    private final GrowAPet plugin;
    private final Map<BlockKey, IncubatingEgg> active = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> placements = new ConcurrentHashMap<>();
    private final Map<BlockKey, VirtualTextDisplayService.Handle> displays = new ConcurrentHashMap<>();
    private BukkitTask task;

    public EggIncubationManager(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<EggRow> rows=new ArrayList<>();
            try(PreparedStatement s=connection.prepareStatement("SELECT * FROM incubating_eggs");ResultSet r=s.executeQuery()){
                while(r.next()) rows.add(new EggRow(r.getString("world"),r.getInt("x"),r.getInt("y"),r.getInt("z"),UUID.fromString(r.getString("owner")),r.getString("entity_type"),r.getInt("total_seconds"),r.getLong("hatch_at")));
            }
            return rows;
        }).thenCompose(rows -> onMain(() -> {
            active.clear();
            displays.values().forEach(plugin.getVirtualTextDisplays()::remove);
            displays.clear();
            for(EggRow row:rows){ World world=Bukkit.getWorld(row.world()); if(world==null){plugin.getLogger().warning("Incubating egg is waiting for missing world "+row.world());continue;} try{IncubatingEgg egg=row.toEgg(world);active.put(BlockKey.of(egg.getLocation()),egg);ensureEggBlock(egg.getLocation().getBlock());}catch(IllegalArgumentException e){plugin.getLogger().warning("Invalid incubating egg at "+row.world()+":"+row.x()+","+row.y()+","+row.z());}}
            active.values().forEach(this::createDisplay);
            start();
        }));
    }

    public boolean place(Player player, ItemStack item, Block block) {
        if (placements.containsKey(player.getUniqueId())) { player.sendMessage("§cYour previous egg placement is still being saved."); return false; }
        EntityType type=plugin.getEggManager().getEggType(item); if(type==null)return false;
        BlockKey key=BlockKey.of(block.getLocation()); if(active.containsKey(key))return false;
        int configuredSeconds=plugin.getEggManager().getIncubateSeconds(item);double speed=plugin.getPlotBoostManager().multiplier(player.getUniqueId(),me.growapet.boosts.BoostType.HATCH_SPEED);int seconds=Math.max(1,(int)Math.ceil(configuredSeconds/speed)); long hatchAt=System.currentTimeMillis()+seconds*1000L;
        UUID playerId=player.getUniqueId();
        placements.put(playerId,key);
        plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("INSERT INTO incubating_eggs(world,x,y,z,owner,entity_type,total_seconds,hatch_at) VALUES(?,?,?,?,?,?,?,?)")){s.setString(1,key.world);s.setInt(2,key.x);s.setInt(3,key.y);s.setInt(4,key.z);s.setString(5,playerId.toString());s.setString(6,type.name());s.setInt(7,seconds);s.setLong(8,hatchAt);s.executeUpdate();}return null;})
                .whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->finishPlacement(playerId,item,type,seconds,hatchAt,block,key,error)));
        return true;
    }

    private void finishPlacement(UUID owner, ItemStack original, EntityType type, int seconds, long hatchAt, Block block, BlockKey key, Throwable error) {
        placements.remove(owner,key); Player player=Bukkit.getPlayer(owner);
        if(error!=null){if(player!=null)player.sendMessage("§cThe egg could not be saved, so it was not placed.");plugin.getLogger().severe("Egg placement failed: "+error.getMessage());return;}
        if(player==null || !player.isOnline() || block.getType()!=Material.AIR || !plugin.getPlotManager().isWithinOwnPlot(owner,block.getLocation()) || !consumeMatchingEgg(player,original)){
            delete(key); if(player!=null)player.sendMessage("§cEgg placement was cancelled because the location or item changed."); return;
        }
        IncubatingEgg egg=new IncubatingEgg(owner,type,block.getLocation(),seconds,hatchAt);active.put(key,egg);ensureEggBlock(block);createDisplay(egg);me.growapet.utils.Messages.send(player,"<green>Egg placed.</green> <gray>Hatching in <white><time></white>.</gray>",me.growapet.utils.Messages.value("time",formatTime(seconds)));
    }

    private boolean consumeMatchingEgg(Player player, ItemStack original) {
        for(ItemStack candidate:player.getInventory().getContents()){
            if(candidate==null || candidate.getAmount()<1 || !candidate.isSimilar(original))continue;
            candidate.setAmount(candidate.getAmount()-1);return true;
        }
        return false;
    }

    private void start(){if(task==null)task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,20L);}
    private void tick(){for(IncubatingEgg egg:new ArrayList<>(active.values())){Block block=egg.getLocation().getBlock();if(block.getType()!=Material.TURTLE_EGG){plugin.getLogger().warning("Incubating egg block missing at "+BlockKey.of(egg.getLocation()));continue;}if(egg.getSecondsRemaining()<=0){hatch(egg);continue;}updateDisplay(egg);double progress=1.0-(double)egg.getSecondsRemaining()/egg.getTotalSeconds();int stage=progress>=.66?2:progress>=.33?1:0;TurtleEgg data=(TurtleEgg)block.getBlockData();if(data.getHatch()!=stage){data.setHatch(stage);block.setBlockData(data);block.getWorld().playSound(block.getLocation(),Sound.ENTITY_TURTLE_EGG_CRACK,1,1);}}}

    private void hatch(IncubatingEgg egg){BlockKey key=BlockKey.of(egg.getLocation());if(!active.remove(key,egg))return;removeDisplay(key);Pet pet;try{pet=plugin.getPetManager().createHatchCandidate(egg.getOwner(),egg.getEntityType());}catch(Exception error){active.put(key,egg);createDisplay(egg);return;}plugin.getDatabase().transaction(connection->{plugin.getPetManager().insert(connection,pet);try(PreparedStatement s=connection.prepareStatement("DELETE FROM incubating_eggs WHERE world=? AND x=? AND y=? AND z=?")){s.setString(1,key.world);s.setInt(2,key.x);s.setInt(3,key.y);s.setInt(4,key.z);if(s.executeUpdate()!=1)throw new IllegalStateException("Incubation row missing");}return null;}).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{if(error==null)plugin.getPetManager().registerHatched(pet);finishHatch(egg,pet,error);}));}
    private void finishHatch(IncubatingEgg egg, Pet pet, Throwable error){if(error!=null){active.put(BlockKey.of(egg.getLocation()),egg);createDisplay(egg);plugin.getLogger().severe("Hatch failed; incubation retained: "+error.getMessage());return;}Block b=egg.getLocation().getBlock();if(b.getType()==Material.TURTLE_EGG)b.setType(Material.AIR);Location fx=egg.getLocation().clone().add(.5,.5,.5);fx.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,fx,40);fx.getWorld().playSound(fx,Sound.ENTITY_TURTLE_EGG_HATCH,1,1);PlayerData data=plugin.getPlayerManager().get(egg.getOwner());if(data!=null){data.incrementEggsHatched();data.incrementPetsCollected();plugin.getQuestManager().record(egg.getOwner(),me.growapet.quests.QuestType.EGGS_HATCHED,1,null);plugin.getCreditMilestoneManager().evaluate(egg.getOwner());}Player owner=Bukkit.getPlayer(egg.getOwner());if(owner!=null)me.growapet.utils.Messages.send(owner,"<light_purple><bold>PET HATCHED</bold></light_purple> <gray>• <yellow><rarity> <type></yellow></gray>",me.growapet.utils.Messages.value("rarity",pet.getRarity().name()),me.growapet.utils.Messages.value("type",pet.getEntityType().name()));plugin.getTutorialManager().notifyAction(egg.getOwner(),me.growapet.tutorial.TutorialAction.EGG_HATCH);}

    private CompletableFuture<Void> delete(BlockKey key){return plugin.getDatabase().async(connection->{try(PreparedStatement s=connection.prepareStatement("DELETE FROM incubating_eggs WHERE world=? AND x=? AND y=? AND z=?")){s.setString(1,key.world);s.setInt(2,key.x);s.setInt(3,key.y);s.setInt(4,key.z);s.executeUpdate();}return null;});}
    private static void ensureEggBlock(Block block){block.setType(Material.TURTLE_EGG);TurtleEgg data=(TurtleEgg)block.getBlockData();data.setEggs(1);data.setHatch(0);block.setBlockData(data);}
    public int countActive(UUID owner){return(int)active.values().stream().filter(e->e.getOwner().equals(owner)).count();}
    public boolean isIncubating(Location location){return active.containsKey(BlockKey.of(location));}
    public int bypassAll(UUID owner){List<IncubatingEgg> eggs=active.values().stream().filter(e->e.getOwner().equals(owner)).toList();eggs.forEach(this::hatch);return eggs.size();}
    public void stop(){if(task!=null)task.cancel();task=null;placements.clear();displays.values().forEach(plugin.getVirtualTextDisplays()::remove);displays.clear();}
    @EventHandler public void onBreak(BlockBreakEvent e){if(isIncubating(e.getBlock().getLocation())){e.setCancelled(true);e.getPlayer().sendMessage("§cThis egg is incubating.");}}
    @EventHandler public void onNaturalHatch(EntityChangeBlockEvent e){if(isIncubating(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void onTrample(EntityInteractEvent e){if(isIncubating(e.getBlock().getLocation()))e.setCancelled(true);}
    private CompletableFuture<Void> onMain(Runnable r){CompletableFuture<Void>f=new CompletableFuture<>();Bukkit.getScheduler().runTask(plugin,()->{try{r.run();f.complete(null);}catch(Throwable t){f.completeExceptionally(t);}});return f;}
    private void createDisplay(IncubatingEgg egg){BlockKey key=BlockKey.of(egg.getLocation());removeDisplay(key);Location location=egg.getLocation().clone().add(.5,1.35,.5);displays.put(key,plugin.getVirtualTextDisplays().create(location,displayText(egg),viewer->{me.growapet.models.Plot plot=plugin.getPlotManager().getPlot(egg.getOwner());return plot!=null&&plot.contains(viewer.getLocation());}));}
    private void updateDisplay(IncubatingEgg egg){VirtualTextDisplayService.Handle handle=displays.get(BlockKey.of(egg.getLocation()));if(handle==null){createDisplay(egg);return;}plugin.getVirtualTextDisplays().update(handle,displayText(egg));}
    private void removeDisplay(BlockKey key){VirtualTextDisplayService.Handle handle=displays.remove(key);if(handle!=null)plugin.getVirtualTextDisplays().remove(handle);}
    private net.kyori.adventure.text.Component displayText(IncubatingEgg egg){return me.growapet.utils.Messages.parse("<light_purple><bold>HATCHING</bold></light_purple>\n<gray>"+formatTime(egg.getSecondsRemaining())+" remaining</gray>");}
    private static String formatTime(long seconds){long safe=Math.max(0,seconds);return String.format(java.util.Locale.US,"%02d:%02d",safe/60,safe%60);}
    private record BlockKey(String world,int x,int y,int z){static BlockKey of(Location l){return new BlockKey(l.getWorld().getName(),l.getBlockX(),l.getBlockY(),l.getBlockZ());}}
    private record EggRow(String world,int x,int y,int z,UUID owner,String type,int seconds,long hatchAt){IncubatingEgg toEgg(World w){return new IncubatingEgg(owner,EntityType.valueOf(type),new Location(w,x,y,z),seconds,hatchAt);}}
}
