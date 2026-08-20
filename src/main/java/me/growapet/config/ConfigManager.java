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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads a complete, validated configuration snapshot before publishing it. */
public final class ConfigManager {
    private static final String[] FILES={"config.yml","mobs.yml","pets.yml","eggs.yml","zones.yml","quests.yml","bosses.yml","menus.yml","messages.yml","hud.yml","tutorial.yml","discord.yml","commerce.yml","cosmetics.yml","seasons.yml","shop-catalog.yml","announcements.yml","stats.yml","store.yml","warps.yml"};
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

        FileConfiguration main=candidate.get("config.yml");double maxHit=main.getDouble("combat.max-hit-damage",1_000_000);if(!Double.isFinite(maxHit)||maxHit<=0){problem("config.yml: combat.max-hit-damage must be positive and finite");valid=false;}if(main.getLong("trade.request-cooldown-seconds",10)<1){problem("config.yml: trade request cooldown must be at least one second");valid=false;}ConfigurationSection mobSpawns=main.getConfigurationSection("mob-spawns");if(mobSpawns!=null){if(mobSpawns.getLong("tick-period-ticks",100)<20||mobSpawns.getLong("tick-period-ticks",100)>20*60*60){problem("config.yml: mob-spawns.tick-period-ticks must be between 20 and 72000");valid=false;}if(mobSpawns.getInt("spawn-burst-per-tick",2)<1||mobSpawns.getInt("spawn-burst-per-tick",2)>10){problem("config.yml: mob-spawns.spawn-burst-per-tick must be 1-10");valid=false;}if(mobSpawns.getInt("max-tracked-mobs",5000)<1||mobSpawns.getInt("max-respawn-queue",5000)<1){problem("config.yml: mob spawn caps must be positive");valid=false;}}
        ConfigurationSection resourcePack=candidate.get("hud.yml").getConfigurationSection("resource-pack");if(resourcePack!=null&&resourcePack.getBoolean("enabled",false)){String url=resourcePack.getString("url","");String hash=resourcePack.getString("sha1","").replace(" ","");if(!url.startsWith("https://")||!hash.matches("(?i)[0-9a-f]{40}")){problem("hud.yml: enabled resource-pack is invalid; Unicode fallback will remain active");}}
        FileConfiguration tutorial=candidate.get("tutorial.yml");double tutorialSpeed=tutorial.getDouble("tutorial.walk-blocks-per-tick",0.19);if(!Double.isFinite(tutorialSpeed)||tutorialSpeed<0.05||tutorialSpeed>1){problem("tutorial.yml: walk-blocks-per-tick must be 0.05-1.0");valid=false;}for(String point:new String[]{"start","mob","shop","egg"})for(String axis:new String[]{"x","y","z"}){String path="tutorial.route."+point+"."+axis;double coordinate=tutorial.getDouble(path,Double.NaN);if(!tutorial.isSet(path)||!Double.isFinite(coordinate)){problem("tutorial.yml: route coordinate must be finite: "+point+"."+axis);valid=false;}}
        FileConfiguration discord=candidate.get("discord.yml");valid=validateDiscord(discord)&&valid;
        FileConfiguration commerce=candidate.get("commerce.yml");valid=validateCommerce(commerce)&&valid;
        FileConfiguration seasons=candidate.get("seasons.yml");ConfigurationSection seasonRoot=seasons.getConfigurationSection("seasons");if(seasonRoot!=null)for(String id:seasonRoot.getKeys(false)){ConfigurationSection season=seasonRoot.getConfigurationSection(id);if(season==null||season.getString("definition-version","").isBlank()){problem("seasons.yml: missing definition-version for "+id);valid=false;}if(season!=null&&season.getLong("duration-days",14)<1){problem("seasons.yml: duration-days must be positive for "+id);valid=false;}}
        valid=validateSeasons(seasons)&&valid;
        valid=validateShopCatalog(candidate.get("shop-catalog.yml"))&&valid;
        valid=validateStore(candidate.get("store.yml"))&&valid;
        FileConfiguration announcements = candidate.get("announcements.yml");
        long announcementInterval = announcements.getLong("interval-minutes", -1) > 0 ? announcements.getLong("interval-minutes", 45) * 60 : announcements.getLong("interval-seconds", 300);
        long initialDelay = announcements.getLong("initial-delay-minutes", -1) >= 0 ? announcements.getLong("initial-delay-minutes", 2) * 60 : announcements.getLong("initial-delay-seconds", 0);
        if(announcementInterval<10||announcementInterval>86400||initialDelay<0||initialDelay>86400){problem("announcements.yml: announcement interval/delay is outside safe bounds");valid=false;}
        return valid;
    }

    private boolean validateDiscord(FileConfiguration config) {
        if (!config.getBoolean("enabled", false)) return true;
        boolean valid = true;
        String guild = config.getString("guild-id", ""), channel = config.getString("channel-id", "");
        if (!guild.matches("\\d{5,32}") || !channel.matches("\\d{5,32}")) { problem("discord.yml: enabled integration requires numeric guild-id and channel-id"); valid = false; }
        String role = config.getString("linked-role-id", "");
        if (role.isBlank() || !role.matches("\\d{5,32}")) { problem("discord.yml: enabled integration requires a numeric linked-role-id"); valid = false; }
        if (config.getString("token-env", "").isBlank() || config.getLong("token-expiry-seconds", 300) < 60 || config.getLong("token-expiry-seconds", 300) > 3600 || config.getInt("link-attempt-limit", 5) < 1 || config.getInt("link-attempt-limit", 5) > 32) { problem("discord.yml: token env/expiry/attempt limit is invalid"); valid = false; }
        if (config.getInt("message-max-length", 256) < 64 || config.getInt("message-max-length", 256) > 2000 || config.getInt("messages-per-10-seconds", 4) < 1 || config.getInt("messages-per-10-seconds", 4) > 32 || config.getInt("outbound-queue-capacity", 256) < 16) { problem("discord.yml: relay limits are outside safe bounds"); valid = false; }
        if (!valid) plugin.getLogger().warning("discord.yml is invalid; Discord remains unavailable while the core plugin continues.");
        // Discord is an optional network integration. Its configuration must be
        // surfaced by /growapet doctor, but it must never prevent Paper from
        // enabling the core gameplay plugin.
        return true;
    }

    private boolean validateCommerce(FileConfiguration config) {
        boolean valid = true;
        if (!"TEBEX".equalsIgnoreCase(config.getString("provider", "TEBEX"))) { problem("commerce.yml: only the TEBEX provider is supported"); valid = false; }
        try { if (!"https".equalsIgnoreCase(URI.create(config.getString("api-base-url", "https://plugin.tebex.io")).getScheme())) { problem("commerce.yml: api-base-url must use HTTPS"); valid = false; } }
        catch (IllegalArgumentException error) { problem("commerce.yml: api-base-url is invalid"); valid = false; }
        if (config.getString("secret-env", "").isBlank() || config.getInt("max-quantity", 20) < 1 || config.getInt("max-quantity", 20) > 100) { problem("commerce.yml: secret env or quantity cap is invalid"); valid = false; }
        ConfigurationSection root = config.getConfigurationSection("sku-bundles");
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection bundle = root.getConfigurationSection(id);
            if (!id.matches("[A-Za-z0-9._:-]{1,128}") || bundle == null || bundle.getString("version", "").isBlank()) { problem("commerce.yml: invalid SKU " + id); valid = false; continue; }
            for (String currency : new String[]{"coins", "gems", "credits"}) if (bundle.getLong(currency, 0) < 0) { problem("commerce.yml: negative " + id + "." + currency); valid = false; }
            for (String entitlement : bundle.getStringList("entitlements")) if (!entitlement.matches("[A-Za-z0-9._:-]{1,128}")) { problem("commerce.yml: invalid entitlement in " + id); valid = false; }
            ConfigurationSection boosts = bundle.getConfigurationSection("boosts");
            if (boosts != null) for (String boostId : boosts.getKeys(false)) { ConfigurationSection boost = boosts.getConfigurationSection(boostId); String type = boost == null ? "" : boost.getString("type", "").toUpperCase(Locale.ROOT); if (boost == null || !Set.of("COINS", "GEMS", "MOB_EXP", "PET_EXP", "HATCH_SPEED").contains(type) || boost.getDouble("bonus", 0) <= 0 || boost.getDouble("bonus", 0) > 9 || boost.getLong("duration-minutes", 0) < 1 || boost.getLong("duration-minutes", 0) > 1440) { problem("commerce.yml: invalid boost " + id + "." + boostId); valid = false; } }
        }
        return valid;
    }

    private boolean validateSeasons(FileConfiguration config) {
        boolean valid = true; ConfigurationSection root = config.getConfigurationSection("seasons"); if (root == null) return true;
        Set<String> objectiveTypes = Set.of("MOB_KILLS", "QUESTS_COMPLETED", "EGGS_HATCHED", "BOSS_KILLS", "ZONE_UNLOCK");
        Set<String> rewardKinds = Set.of("COINS", "GEMS", "CREDITS", "COSMETIC_TAG", "BOOST");
        for (String id : root.getKeys(false)) { ConfigurationSection season = root.getConfigurationSection(id); if (season == null) { valid = false; continue; }
            if (season.getString("definition-version", "").isBlank() || season.getLong("duration-days", 14) < 1 || season.getLong("duration-days", 14) > 90) { problem("seasons.yml: invalid definition or duration for " + id); valid = false; }
            ConfigurationSection objectives = season.getConfigurationSection("objectives"); if (objectives == null || objectives.getKeys(false).isEmpty()) { problem("seasons.yml: season has no objectives " + id); valid = false; }
            else for (String objective : objectives.getKeys(false)) { String type = objectives.getString(objective + ".type", "").toUpperCase(Locale.ROOT); if (!objectiveTypes.contains(type) || objectives.getLong(objective + ".amount", 0) < 1) { problem("seasons.yml: invalid objective " + id + "." + objective); valid = false; } }
            ConfigurationSection rewards = season.getConfigurationSection("rewards"); if (rewards == null || rewards.getKeys(false).isEmpty()) { problem("seasons.yml: season has no rewards " + id); valid = false; }
            else for (String reward : rewards.getKeys(false)) { String kind = rewards.getString(reward + ".kind", "").toUpperCase(Locale.ROOT); if (!rewardKinds.contains(kind)) { problem("seasons.yml: invalid reward kind " + id + "." + reward); valid = false; } if (Set.of("COINS", "GEMS", "CREDITS").contains(kind) && rewards.getLong(reward + ".amount", 0) < 1) { problem("seasons.yml: reward amount must be positive " + id + "." + reward); valid = false; } }
        }
        return valid;
    }

    private boolean validateShopCatalog(FileConfiguration config) {
        if (config == null) return false;
        String active = config.getString("active-catalog", ""); ConfigurationSection catalogs = config.getConfigurationSection("catalogs");
        if (catalogs == null || !catalogs.isConfigurationSection(active)) { problem("shop-catalog.yml: active catalog is missing"); return false; }
        List<String> expected = List.of("plains", "spruce_forest", "savanna", "desert", "ice_spikes", "cherry_grove", "flower_forest", "cave", "ocean", "mushroom_island");
        List<String> order = config.getStringList("catalogs.zones-v1.order");
        if (order.size() != expected.size() || !order.equals(expected)) { problem("shop-catalog.yml: zones-v1 must contain the approved ten-zone order"); return false; }
        return true;
    }

    private boolean validateStore(FileConfiguration config) {
        ConfigurationSection offers=config.getConfigurationSection("offers");
        if(offers==null)return true;
        boolean valid=true;Set<Integer>slots=new HashSet<>();
        for(String id:offers.getKeys(false)){
            ConfigurationSection offer=offers.getConfigurationSection(id);
            if(!id.matches("[a-z0-9_-]{1,48}")||offer==null){problem("store.yml: invalid offer id "+id);valid=false;continue;}
            long price=offer.getLong("price",-1);int slot=offer.getInt("slot",-1);
            if(price<0||slot<0||slot>=54||!slots.add(slot)){problem("store.yml: invalid price/slot for "+id);valid=false;}
            String type=offer.getString("type","").toUpperCase(Locale.ROOT);
            if(!Set.of("ENTITLEMENT","SETTING","BOOST").contains(type)){problem("store.yml: invalid type for "+id);valid=false;}
            if("ENTITLEMENT".equals(type)&&!offer.getString("entitlement-id","").matches("[A-Za-z0-9._:-]{1,128}")){problem("store.yml: invalid entitlement-id for "+id);valid=false;}
            if("SETTING".equals(type)&&!offer.getString("setting","").matches("[A-Za-z0-9._:-]{1,128}")){problem("store.yml: invalid setting for "+id);valid=false;}
            if("BOOST".equals(type)){
                String boost=offer.getString("boost-type","").toUpperCase(Locale.ROOT);
                double bonus=offer.getDouble("bonus",0);long minutes=offer.getLong("duration-minutes",0);
                if(!Set.of("COINS","GEMS","MOB_EXP","PET_EXP","HATCH_SPEED").contains(boost)||!Double.isFinite(bonus)||bonus<=0||bonus>9||minutes<1||minutes>10080){problem("store.yml: invalid boost for "+id);valid=false;}
            }
        }
        return valid;
    }

    private boolean validateEntity(String path,String name){try{EntityType type=EntityType.valueOf(name.toUpperCase(Locale.ROOT));if(!type.isAlive()||!type.isSpawnable()||type==EntityType.ARMOR_STAND||type==EntityType.PLAYER)throw new IllegalArgumentException();return true;}catch(IllegalArgumentException|NullPointerException error){problem(path+": invalid living entity "+name);return false;}}
    private static long number(Map<?,?>map,String key,long fallback){Object value=map.get(key);return value instanceof Number number?number.longValue():fallback;}
    private void problem(String message){plugin.getLogger().warning(message);}

    public void save(String name){FileConfiguration cfg=configs.get(name);File file=files.get(name);if(cfg==null||file==null)return;try{cfg.save(file);}catch(Exception error){plugin.getLogger().warning("Couldn't save "+name+": "+error.getMessage());}}
    public FileConfiguration get(String name){return configs.get(name);}public FileConfiguration config(){return get("config.yml");}public FileConfiguration mobs(){return get("mobs.yml");}public FileConfiguration pets(){return get("pets.yml");}public FileConfiguration eggs(){return get("eggs.yml");}public FileConfiguration zones(){return get("zones.yml");}public FileConfiguration quests(){return get("quests.yml");}public FileConfiguration bosses(){return get("bosses.yml");}public FileConfiguration menus(){return get("menus.yml");}public FileConfiguration messages(){return get("messages.yml");}public FileConfiguration hud(){return get("hud.yml");}public FileConfiguration tutorial(){return get("tutorial.yml");}public FileConfiguration discord(){return get("discord.yml");}public FileConfiguration commerce(){return get("commerce.yml");}public FileConfiguration cosmetics(){return get("cosmetics.yml");}public FileConfiguration seasons(){return get("seasons.yml");}public FileConfiguration shopCatalog(){return get("shop-catalog.yml");}public FileConfiguration announcements(){return get("announcements.yml");}public FileConfiguration stats(){return get("stats.yml");}public FileConfiguration store(){return get("store.yml");}
}
