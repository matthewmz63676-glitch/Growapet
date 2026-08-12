package me.growapet.config;

import me.growapet.GrowAPet;
import me.growapet.quests.QuestType;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads a complete, validated configuration snapshot before publishing it. */
public final class ConfigManager {
    private static final String[] FILES={"config.yml","mobs.yml","pets.yml","eggs.yml","zones.yml","quests.yml","bosses.yml","menus.yml","messages.yml","hud.yml","tutorial.yml"};
    private final GrowAPet plugin;
    private final Map<String,FileConfiguration> configs=new HashMap<>();
    private final Map<String,File> files=new HashMap<>();

    public ConfigManager(GrowAPet plugin){this.plugin=plugin;}

    public void loadAll(){
        try{
            Map<String,FileConfiguration> candidate=readSnapshot(true);
            if(!validate(candidate))throw new IllegalStateException("Bundled/live GrowAPet configuration is invalid");
            configs.clear();configs.putAll(candidate);
        }catch(Exception error){throw new IllegalStateException("Could not load GrowAPet configuration",error);}
    }

    /** Returns false and retains the previous snapshot when any file is invalid. */
    public boolean reloadAll(){
        try{
            Map<String,FileConfiguration> candidate=readSnapshot(false);
            if(!validate(candidate)){plugin.getLogger().severe("Configuration reload rejected; last-known-good values remain active.");return false;}
            configs.clear();configs.putAll(candidate);return true;
        }catch(Exception error){plugin.getLogger().severe("Configuration reload rejected: "+error.getMessage());return false;}
    }

    private Map<String,FileConfiguration> readSnapshot(boolean create) throws Exception{
        Map<String,FileConfiguration> candidate=new HashMap<>();
        for(String name:FILES){
            File file=files.computeIfAbsent(name,key->new File(plugin.getDataFolder(),key));
            if(create&&!file.exists())plugin.saveResource(name,false);
            if(!file.isFile())throw new IllegalStateException("Missing "+name);
            YamlConfiguration cfg=new YamlConfiguration();cfg.load(file);attachDefaults(name,cfg);candidate.put(name,cfg);
        }
        return candidate;
    }

    private void attachDefaults(String name,FileConfiguration cfg)throws Exception{
        try(InputStream resource=plugin.getResource(name)){
            if(resource==null)return;
            YamlConfiguration defaults=YamlConfiguration.loadConfiguration(new InputStreamReader(resource,StandardCharsets.UTF_8));
            cfg.setDefaults((Configuration)defaults);
        }
    }

    private boolean validate(Map<String,FileConfiguration> candidate){
        boolean valid=true;
        FileConfiguration zoneConfig=candidate.get("zones.yml");ConfigurationSection zones=zoneConfig.getConfigurationSection("zones");Set<Integer>orders=new HashSet<>();Set<String>zoneIds=new HashSet<>();
        if(zones==null){problem("zones.yml has no zones section");valid=false;}else for(String id:zones.getKeys(false)){zoneIds.add(id);ConfigurationSection zone=zones.getConfigurationSection(id);if(zone==null){problem("zones.yml: invalid section "+id);valid=false;continue;}int order=zone.getInt("order",-1);if(order<0||!orders.add(order)){problem("zones.yml: invalid/duplicate order for "+id);valid=false;}if(zone.getLong("cost",0)<0||zone.getLong("gem-cost",0)<0||zone.getInt("req-level",0)<0){problem("zones.yml: negative requirement for "+id);valid=false;}}

        ConfigurationSection mobs=candidate.get("mobs.yml").getConfigurationSection("mobs");
        if(mobs!=null)for(String id:mobs.getKeys(false)){ConfigurationSection mob=mobs.getConfigurationSection(id);String entity=mob==null?id:mob.getString("entity",id);valid&=validateEntity("mobs.yml mobs."+id,entity);if(mob!=null&&(mob.getDouble("health",20)<=0||mob.getLong("coins",0)<0||mob.getLong("gems",0)<0||mob.getLong("exp",0)<0||mob.getInt("respawn-seconds",0)<0)){problem("mobs.yml: invalid numeric value for "+id);valid=false;}if(mob!=null&&!mob.getString("zone","").isBlank()&&!zoneIds.contains(mob.getString("zone"))){problem("mobs.yml: unknown zone for "+id);valid=false;}}

        ConfigurationSection bosses=candidate.get("bosses.yml").getConfigurationSection("bosses");Set<String>bossIds=new HashSet<>();
        if(bosses!=null)for(String id:bosses.getKeys(false)){ConfigurationSection boss=bosses.getConfigurationSection(id);if(boss==null){valid=false;continue;}valid&=validateEntity("bosses.yml bosses."+id,boss.getString("entity",""));if(boss.getDouble("health",0)<=0||boss.getDouble("damage",0)<0||boss.getLong("respawn-minutes",0)<1){problem("bosses.yml: invalid numeric value for "+id);valid=false;}String zone=boss.getString("zone","");if(!zone.isBlank()&&!zoneIds.contains(zone)){problem("bosses.yml: unknown zone for "+id);valid=false;}}
        if(bosses!=null)for(String id:bosses.getKeys(false)){bossIds.add(id);ConfigurationSection rewards=bosses.getConfigurationSection(id+".rewards");if(rewards!=null)for(String key:new String[]{"top1-credits","top2-credits","top3-credits","participation-coins","participation-gems","participation-exp"})if(rewards.getLong(key,0)<0){problem("bosses.yml: negative reward "+id+"."+key);valid=false;}}

        ConfigurationSection eggs=candidate.get("eggs.yml").getConfigurationSection("eggs");
        if(eggs!=null)for(String zone:eggs.getKeys(false)){if(!"spawn".equals(zone)&&!zoneIds.contains(zone)){problem("eggs.yml: unknown zone "+zone);valid=false;}for(Map<?,?>entry:eggs.getMapList(zone)){valid&=validateEntity("eggs.yml eggs."+zone,String.valueOf(entry.get("entity")));if(number(entry,"incubate-seconds",1)<=0||number(entry,"price-coins",0)<0||number(entry,"price-gems",0)<0){problem("eggs.yml: invalid numeric value in "+zone);valid=false;}}}

        FileConfiguration questConfig=candidate.get("quests.yml");for(String group:new String[]{"daily","weekly","story"}){ConfigurationSection root=questConfig.getConfigurationSection(group);if(root==null)continue;for(String id:root.getKeys(false)){String path=group+"."+id;String typeName=root.getString(id+".type","");QuestType type=null;try{type=QuestType.valueOf(typeName.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException error){problem("quests.yml: invalid type for "+path);valid=false;}if(root.getLong(id+".amount",1)<1){problem("quests.yml: amount must be positive for "+path);valid=false;}for(String reward:new String[]{"reward-coins","reward-gems","reward-credits","reward-exp"})if(root.getLong(id+"."+reward,0)<0){problem("quests.yml: negative "+reward+" for "+path);valid=false;}if(type==QuestType.ZONE_UNLOCK&&!zoneIds.contains(root.getString(id+".zone",""))){problem("quests.yml: unknown zone for "+path);valid=false;}if(type==QuestType.BOSS_KILL&&!root.getString(id+".boss","").isBlank()&&!bossIds.contains(root.getString(id+".boss"))){problem("quests.yml: unknown boss for "+path);valid=false;}}}

        FileConfiguration main=candidate.get("config.yml");double maxHit=main.getDouble("combat.max-hit-damage",1_000_000);if(!Double.isFinite(maxHit)||maxHit<=0){problem("config.yml: combat.max-hit-damage must be positive and finite");valid=false;}if(main.getLong("trade.request-cooldown-seconds",10)<1){problem("config.yml: trade request cooldown must be at least one second");valid=false;}FileConfiguration tutorial=candidate.get("tutorial.yml");double tutorialSpeed=tutorial.getDouble("tutorial.walk-blocks-per-tick",0.19);if(!Double.isFinite(tutorialSpeed)||tutorialSpeed<0.05||tutorialSpeed>1){problem("tutorial.yml: walk-blocks-per-tick must be 0.05-1.0");valid=false;}for(String point:new String[]{"start","mob","shop","egg"})for(String axis:new String[]{"x","y","z"}){double coordinate=tutorial.getDouble("tutorial.route."+point+"."+axis);if(!Double.isFinite(coordinate)){problem("tutorial.yml: route coordinate must be finite: "+point+"."+axis);valid=false;}}
        return valid;
    }

    private boolean validateEntity(String path,String name){try{EntityType type=EntityType.valueOf(name.toUpperCase(Locale.ROOT));if(!type.isAlive()||!type.isSpawnable()||type==EntityType.ARMOR_STAND||type==EntityType.PLAYER)throw new IllegalArgumentException();return true;}catch(IllegalArgumentException|NullPointerException error){problem(path+": invalid living entity "+name);return false;}}
    private static long number(Map<?,?>map,String key,long fallback){Object value=map.get(key);return value instanceof Number number?number.longValue():fallback;}
    private void problem(String message){plugin.getLogger().warning(message);}

    public void save(String name){FileConfiguration cfg=configs.get(name);File file=files.get(name);if(cfg==null||file==null)return;try{cfg.save(file);}catch(Exception error){plugin.getLogger().warning("Couldn't save "+name+": "+error.getMessage());}}
    public FileConfiguration get(String name){return configs.get(name);}public FileConfiguration config(){return get("config.yml");}public FileConfiguration mobs(){return get("mobs.yml");}public FileConfiguration pets(){return get("pets.yml");}public FileConfiguration eggs(){return get("eggs.yml");}public FileConfiguration zones(){return get("zones.yml");}public FileConfiguration quests(){return get("quests.yml");}public FileConfiguration bosses(){return get("bosses.yml");}public FileConfiguration menus(){return get("menus.yml");}public FileConfiguration messages(){return get("messages.yml");}public FileConfiguration hud(){return get("hud.yml");}public FileConfiguration tutorial(){return get("tutorial.yml");}
}
