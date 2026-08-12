package me.growapet.credits;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/** Receipt-backed progression milestones; safe to evaluate repeatedly and after restart. */
public final class CreditMilestoneManager {
    private static final Map<String,ToLongFunction<PlayerData>> METRICS=Map.of(
            "mob-kills",PlayerData::getMobKills,"boss-kills",PlayerData::getBossKills,
            "eggs-hatched",PlayerData::getEggsHatched,"quests-completed",PlayerData::getQuestsCompleted);
    private final GrowAPet plugin;public CreditMilestoneManager(GrowAPet plugin){this.plugin=plugin;}
    public void evaluate(UUID playerId){PlayerData data=plugin.getPlayerManager().get(playerId);if(data==null)return;ConfigurationSection root=plugin.getConfigManager().config().getConfigurationSection("credit-milestones");if(root==null)return;for(Map.Entry<String,ToLongFunction<PlayerData>>metric:METRICS.entrySet()){ConfigurationSection thresholds=root.getConfigurationSection(metric.getKey());if(thresholds==null)continue;long value=metric.getValue().applyAsLong(data);for(String raw:thresholds.getKeys(false)){long threshold,reward;try{threshold=Long.parseLong(raw);reward=thresholds.getLong(raw);}catch(NumberFormatException ignored){continue;}if(threshold<=0||reward<=0||value<threshold)continue;String receipt="milestone:"+playerId+":"+metric.getKey()+":"+threshold;plugin.getCreditGrantService().grant(receipt,playerId,reward,"MILESTONE").whenComplete((granted,error)->Bukkit.getScheduler().runTask(plugin,()->{if(error!=null){plugin.getLogger().warning("Milestone grant failed: "+error.getMessage());return;}Player player=Bukkit.getPlayer(playerId);if(Boolean.TRUE.equals(granted)&&player!=null)Messages.send(player,"<light_purple><bold>MILESTONE</bold></light_purple> <dark_gray>•</dark_gray> <gray><metric> <white><threshold></white> → <light_purple>+<credits> Credits</light_purple></gray>",Messages.value("metric",label(metric.getKey())),Messages.value("threshold",threshold),Messages.value("credits",reward));}));}}}
    private static String label(String key){return switch(key){case"mob-kills"->"Mob Kills";case"boss-kills"->"Boss Kills";case"eggs-hatched"->"Eggs Hatched";default->"Quests Completed";};}
}
