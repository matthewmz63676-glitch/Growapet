package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.events.EventType;
import me.growapet.models.Pet;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Context- and permission-aware completion for every command declared by GrowAPet. */
public final class GrowAPetTabCompleter implements TabCompleter {
    private static final List<String> LEADERBOARDS=List.of("coins","gems","credits","level","mobkills","bosskills","damage","coinmulti","gemsmulti","power","playtime","eggs","pets","trades","highestpet");
    private final GrowAPet plugin;
    public GrowAPetTabCompleter(GrowAPet plugin){this.plugin=plugin;}

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[]args){
        String name=command.getName().toLowerCase(Locale.ROOT);
        List<String> choices=switch(name){
            case"growapet"->admin(sender,args);
            case"plot"->at(args,1,List.of("home","settings"));
            case"pets"->pets(sender,args);
            case"zones"->zones(args);
            case"quests"->quests(args);
            case"trade"->trade(sender,args);
            case"leaderboard"->leaderboards(sender,args);
            case"boss"->boss(sender,args);
            case"options"->at(args,1,List.of("actionbar","trade"));
            case"visit"->at(args,1,onlinePlayers(player->sender instanceof Player viewer&&!player.getUniqueId().equals(viewer.getUniqueId())));
            case"getmob"->configured(sender,args,"growapet.admin.mob","mobs");
            case"getpet"->configured(sender,args,"growapet.admin.give","mobs");
            case"getegg"->getEgg(sender,args);
            case"shop"->at(args,1,List.of("eggs"));
            case"unlockzone"->unlockZone(sender,args);
            case"setspawn"->sender.hasPermission("growapet.admin.spawn")?List.of():List.of();
            default->List.of();
        };
        return filter(choices,args.length==0?"":args[args.length-1]);
    }

    private List<String> admin(CommandSender sender,String[]args){
        if(args.length==1){List<String>result=new ArrayList<>();addIf(sender,result,"growapet.admin.reload","reload");addIf(sender,result,"growapet.admin.economy","setlevel","setcoins","setgems","setcredits","creditreceipt");addIf(sender,result,"growapet.admin.give","give");addIf(sender,result,"growapet.admin.events","event");return result;}
        if(args.length<2)return List.of();String sub=args[0].toLowerCase(Locale.ROOT);
        if((sub.startsWith("set")&&sender.hasPermission("growapet.admin.economy"))||(sub.equals("give")&&sender.hasPermission("growapet.admin.give"))) {
            if(args.length==2)return onlinePlayers(player->true);
            if(sub.equals("give")&&args.length==3)return List.of("coins","gems","credits");
            if((sub.startsWith("set")&&args.length==3)||(sub.equals("give")&&args.length==4))return List.of("1","100","1000");
        }
        if(sub.equals("event")&&sender.hasPermission("growapet.admin.events")){
            if(args.length==2)return List.of("start","stop");
            if(args.length==3)return java.util.Arrays.stream(EventType.values()).map(value->value.name().toLowerCase(Locale.ROOT)).toList();
            if(args[1].equalsIgnoreCase("start")&&args.length==4)return List.of("300","1800","3600");
            if(args[1].equalsIgnoreCase("start")&&args.length==5)return List.of("1.5","2.0","3.0");
        }
        if(sub.equals("creditreceipt")&&sender.hasPermission("growapet.admin.economy")) {
            if (args.length == 2) return List.of("receipt-id");
            if (args.length == 3) return onlinePlayers(player -> true).stream().map(name -> {
                Player player = Bukkit.getPlayerExact(name);
                return player == null ? "" : player.getUniqueId().toString();
            }).filter(value -> !value.isBlank()).toList();
            if (args.length == 4) return List.of("1","10","100");
        }
        return List.of();
    }

    private List<String> pets(CommandSender sender,String[]args){
        if(!(sender instanceof Player player))return List.of();
        if(args.length==1)return List.of("equip","unequip");
        if(args.length==2&&(args[0].equalsIgnoreCase("equip")||args[0].equalsIgnoreCase("unequip"))){boolean equip=args[0].equalsIgnoreCase("equip");return plugin.getPetManager().getPets(player.getUniqueId()).stream().filter(pet->pet.isEquipped()!=equip).map(Pet::getUuid).map(Object::toString).toList();}
        return List.of();
    }

    private List<String> zones(String[]args){if(args.length==1)return List.of("unlock","warp");if(args.length==2&&(args[0].equalsIgnoreCase("unlock")||args[0].equalsIgnoreCase("warp")))return zoneIds();return List.of();}
    private List<String> quests(String[]args){if(args.length==1)return List.of("claim");if(args.length==2&&args[0].equalsIgnoreCase("claim"))return plugin.getQuestManager().keys();return List.of();}
    private List<String> trade(CommandSender sender,String[]args){
        if(!(sender instanceof Player player))return List.of();
        if(args.length==1){List<String>result=new ArrayList<>(List.of("accept","offer","confirm","cancel"));result.addAll(onlinePlayers(target->!target.getUniqueId().equals(player.getUniqueId())&&plugin.getOptionsManager().isLoaded(target.getUniqueId())&&plugin.getOptionsManager().enabled(target.getUniqueId(),"trade_requests",true)));return result;}
        if(args.length==2&&args[0].equalsIgnoreCase("offer"))return List.of("coins","gems","pet","egg");
        if(args.length==3&&args[0].equalsIgnoreCase("offer")){
            if(args[1].equalsIgnoreCase("pet"))return plugin.getPetManager().getPets(player.getUniqueId()).stream().map(Pet::getUuid).map(Object::toString).toList();
            if(args[1].equalsIgnoreCase("coins")||args[1].equalsIgnoreCase("gems"))return List.of("1","100","1000");
        }
        return List.of();
    }

    private List<String> boss(CommandSender sender,String[]args){if(args.length==1&&sender.hasPermission("growapet.admin.boss"))return List.of("spawn");if(args.length==2&&args[0].equalsIgnoreCase("spawn")&&sender.hasPermission("growapet.admin.boss"))return keys(plugin.getConfigManager().bosses().getConfigurationSection("bosses"));return List.of();}
    private List<String> leaderboards(CommandSender sender,String[]args){if(args.length==1){List<String>result=new ArrayList<>(LEADERBOARDS);if(sender.hasPermission("growapet.admin.leaderboards"))result.addAll(List.of("create","remove","movehere","refresh","reload","list"));return result;}if(!sender.hasPermission("growapet.admin.leaderboards"))return List.of();if(args.length==2&&args[0].equalsIgnoreCase("create"))return LEADERBOARDS;if(args.length==2&&(args[0].equalsIgnoreCase("remove")||args[0].equalsIgnoreCase("movehere")))return plugin.getLeaderboardManager().ids();return List.of();}
    private List<String> configured(CommandSender sender,String[]args,String permission,String root){if(!sender.hasPermission(permission))return List.of();if(args.length==1)return onlinePlayers(player->true);if(args.length==2)return keys(plugin.getConfigManager().mobs().getConfigurationSection(root));return List.of();}
    private List<String> getEgg(CommandSender sender,String[]args){if(!sender.hasPermission("growapet.admin.give"))return List.of();if(args.length==1)return onlinePlayers(player->true);if(args.length==2)return java.util.Arrays.stream(EntityType.values()).filter(type->type.isAlive()&&type.isSpawnable()&&type!=EntityType.PLAYER&&type!=EntityType.ARMOR_STAND).map(type->type.name().toLowerCase(Locale.ROOT)).toList();if(args.length==3)return List.of("60","300","3600");return List.of();}
    private List<String> unlockZone(CommandSender sender,String[]args){if(!sender.hasPermission("growapet.admin.zones"))return List.of();if(args.length==1)return onlinePlayers(player->true);if(args.length==2)return zoneIds();return List.of();}
    private List<String> zoneIds(){return plugin.getZoneManager().getZonesInOrder().stream().map(zone->zone.getId()).toList();}
    private static List<String> keys(ConfigurationSection section){return section==null?List.of():List.copyOf(section.getKeys(false));}
    private static List<String> at(String[]args,int position,List<String>values){return args.length==position?values:List.of();}
    private static List<String> onlinePlayers(Predicate<Player> predicate){return Bukkit.getOnlinePlayers().stream().filter(predicate).map(Player::getName).toList();}
    private static void addIf(CommandSender sender,Collection<String>target,String permission,String...values){if(sender.hasPermission(permission))target.addAll(List.of(values));}

    static List<String> filter(Collection<String>choices,String prefix){String lower=prefix.toLowerCase(Locale.ROOT);return choices.stream().filter(value->!value.equalsIgnoreCase("%PLAYER%")&&value.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
}
