package me.growapet.quests;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import me.growapet.utils.Messages;
import me.growapet.rewards.RewardBundle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestManager {
    private final GrowAPet plugin;
    private final Map<String,QuestDefinition> definitions=new LinkedHashMap<>();
    private final Map<UUID,Map<String,Progress>> progress=new ConcurrentHashMap<>();
    private final Map<UUID,CompletableFuture<Boolean>> pendingClaims=new ConcurrentHashMap<>();
    public QuestManager(GrowAPet plugin){this.plugin=plugin;reload();}

    public void reload(){
        definitions.clear();
        for(String group:List.of("daily","weekly","story")){
            ConfigurationSection root=plugin.getConfigManager().quests().getConfigurationSection(group);if(root==null)continue;
            for(String id:root.getKeys(false)){ConfigurationSection section=root.getConfigurationSection(id);if(section==null)continue;
                try{QuestType type=QuestType.valueOf(section.getString("type","MOB_KILLS").toUpperCase(Locale.ROOT));String target=section.getString(type==QuestType.ZONE_UNLOCK?"zone":"boss","");long amount=type==QuestType.ZONE_UNLOCK?1:Math.max(1,section.getLong("amount",1));String key=group+":"+id;definitions.put(key,new QuestDefinition(key,group,id,section.getString("display-name",id),type,amount,target,Math.max(0,section.getLong("reward-coins")),Math.max(0,section.getLong("reward-gems")),Math.max(0,section.getLong("reward-credits")),Math.max(0,section.getLong("reward-exp"))));}
                catch(IllegalArgumentException error){plugin.getLogger().warning("Invalid quest type at "+group+"."+id);}
            }
        }
    }

    public CompletableFuture<Void> load(UUID player){
        return plugin.getDatabase().async(connection->{Map<String,Progress> loaded=new ConcurrentHashMap<>();try(PreparedStatement statement=connection.prepareStatement("SELECT quest_key,period_key,progress,claimed FROM quest_progress WHERE player_uuid=?")){statement.setString(1,player.toString());try(ResultSet rows=statement.executeQuery()){while(rows.next())loaded.put(rows.getString(1)+"@"+rows.getString(2),new Progress(rows.getLong(3),rows.getInt(4)==1));}}return loaded;})
                .thenAccept(loaded->progress.merge(player,loaded,(existing,fromDatabase)->{existing.forEach((key,value)->fromDatabase.merge(key,value,(a,b)->new Progress(Math.max(a.value,b.value),a.claimed||b.claimed)));return fromDatabase;}));
    }

    public List<QuestView> views(UUID player){List<QuestView>result=new ArrayList<>();for(QuestDefinition definition:definitions.values()){Progress current=current(player,definition);result.add(new QuestView(definition,current.value,current.claimed));}return result;}
    public void record(UUID player,QuestType type,long amount,String target){if(amount<=0)return;for(QuestDefinition definition:definitions.values()){if(definition.type()!=type)continue;if(!definition.target().isBlank()&&!definition.target().equalsIgnoreCase(target))continue;String period=period(definition),mapKey=definition.key()+"@"+period;Map<String,Progress>values=progress.computeIfAbsent(player,ignored->new ConcurrentHashMap<>());Progress old=values.getOrDefault(mapKey,new Progress(0,false));if(old.claimed)continue;Progress next=new Progress(Math.min(definition.amount(),old.value+amount),false);values.put(mapKey,next);persist(player,definition,period,next);}}

    public CompletableFuture<Boolean> claim(Player player,String requested){
        QuestDefinition definition=find(requested);if(definition==null){Messages.send(player,"<red>Unknown quest.</red>");return CompletableFuture.completedFuture(false);}
        UUID playerId=player.getUniqueId();String period=period(definition);Progress current=current(playerId,definition);if(current.claimed||current.value<definition.amount()){Messages.send(player,"<red>That quest is not ready to claim.</red>");return CompletableFuture.completedFuture(false);}
        PlayerData data=plugin.getPlayerManager().get(playerId);if(data==null){Messages.send(player,"<red>Your profile is still loading.</red>");return CompletableFuture.completedFuture(false);}
        CompletableFuture<Boolean> result=new CompletableFuture<>();
        double linkBonus = plugin.getEntitlementService().expMultiplier(playerId);
        long effectiveExp = definition.exp() <= 0 ? 0 : Math.min(Long.MAX_VALUE, Math.round(definition.exp() * linkBonus * data.getExpMultiplier()));
        RewardBundle bundle = RewardBundle.currency(definition.coins(), definition.gems(), definition.credits());
        CompletableFuture<Boolean> transaction = plugin.getRewardFulfilmentService().fulfilGuarded(
                "quest:" + definition.key() + ":" + period + ":" + playerId,
                "QUEST:" + definition.key(), playerId, bundle, connection -> {
                    try (PreparedStatement claim=connection.prepareStatement("UPDATE quest_progress SET claimed=1 WHERE player_uuid=? AND quest_key=? AND period_key=? AND claimed=0 AND progress>=?")) {
                        claim.setString(1,playerId.toString()); claim.setString(2,definition.key()); claim.setString(3,period); claim.setLong(4,definition.amount());
                        if (claim.executeUpdate() != 1) return false;
                    }
                    try (PreparedStatement reward=connection.prepareStatement("UPDATE players SET quests_completed=CASE WHEN quests_completed>? THEN 9223372036854775807 ELSE quests_completed+1 END,exp=CASE WHEN exp>? THEN 9223372036854775807 ELSE exp+? END WHERE uuid=?")) {
                        reward.setLong(1, Long.MAX_VALUE - 1); reward.setLong(2, Long.MAX_VALUE - effectiveExp); reward.setLong(3, effectiveExp); reward.setString(4,playerId.toString());
                        if (reward.executeUpdate() != 1) throw new IllegalStateException("Player missing");
                    }
                    return true;
                });
        pendingClaims.put(playerId,result);
        transaction.whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{
            try{
                if(error!=null){if(player.isOnline())Messages.send(player,"<red>That claim could not be completed. No reward was issued.</red>");result.complete(false);return;}
                if (!Boolean.TRUE.equals(ignored)) { result.complete(false); return; }
                progress.computeIfAbsent(playerId,key->new ConcurrentHashMap<>()).put(definition.key()+"@"+period,new Progress(current.value,true));
                if(plugin.getPlayerManager().get(playerId)==data){data.addExp(Math.min(Long.MAX_VALUE, Math.round(definition.exp() * linkBonus)));data.incrementQuestsCompleted();plugin.getCreditMilestoneManager().evaluate(playerId);if(player.isOnline())plugin.getPlayerManager().syncExpBar(player,data);}
                plugin.getSeasonService().record(playerId, "QUESTS_COMPLETED", 1);
                if(player.isOnline())Messages.send(player,"<aqua><bold>QUEST COMPLETE</bold></aqua> <dark_gray>•</dark_gray> <gray><quest> → reward claimed</gray>",Messages.value("quest",definition.name()));
                result.complete(true);
            }finally{pendingClaims.remove(playerId,result);}
        }));
        return result;
    }

    public CompletableFuture<?> awaitPendingClaim(UUID player){return pendingClaims.getOrDefault(player,CompletableFuture.completedFuture(false));}
    public List<String> keys(){return List.copyOf(definitions.keySet());}
    public String timeUntilReset(String group){
        if(group.equals("story"))return "never";
        ZonedDateTime now=ZonedDateTime.now(ZoneOffset.UTC);ZonedDateTime reset=group.equals("weekly")?now.toLocalDate().plusDays(8-now.getDayOfWeek().getValue()).atStartOfDay(ZoneOffset.UTC):now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        Duration remaining=Duration.between(now,reset);long hours=remaining.toHours(),minutes=remaining.minusHours(hours).toMinutes();return hours+"h "+minutes+"m";
    }

    private Progress current(UUID player,QuestDefinition definition){return progress.getOrDefault(player,Map.of()).getOrDefault(definition.key()+"@"+period(definition),new Progress(0,false));}
    private QuestDefinition find(String requested){return definitions.get(requested.contains(":")?requested:definitions.keySet().stream().filter(key->key.endsWith(":"+requested)).findFirst().orElse(""));}
    private void persist(UUID player,QuestDefinition definition,String period,Progress value){plugin.getDatabase().async(connection->{try(PreparedStatement statement=connection.prepareStatement("INSERT INTO quest_progress(player_uuid,quest_key,period_key,progress,claimed) VALUES(?,?,?,?,?) ON CONFLICT(player_uuid,quest_key,period_key) DO UPDATE SET progress=MAX(progress,excluded.progress),claimed=MAX(claimed,excluded.claimed)")){statement.setString(1,player.toString());statement.setString(2,definition.key());statement.setString(3,period);statement.setLong(4,value.value);statement.setInt(5,value.claimed?1:0);statement.executeUpdate();}return null;}).exceptionally(error->{plugin.getLogger().severe("Failed to persist quest progress for "+player+": "+error.getMessage());return null;});}
    private static String period(QuestDefinition definition){LocalDate today=LocalDate.now(ZoneOffset.UTC);return switch(definition.group()){case"daily"->today.toString();case"weekly"->today.get(WeekFields.ISO.weekBasedYear())+"-W"+today.get(WeekFields.ISO.weekOfWeekBasedYear());default->"story";};}
    private record Progress(long value,boolean claimed){}
    public record QuestView(QuestDefinition definition,long progress,boolean claimed){}
}
