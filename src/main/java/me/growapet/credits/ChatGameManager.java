package me.growapet.credits;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded first-answer arithmetic game used as a small free Credit source. */
public final class ChatGameManager {
    private final GrowAPet plugin;private BukkitTask task;private Game active;
    public ChatGameManager(GrowAPet plugin){this.plugin=plugin;}
    public void start(){long interval=Math.max(60,plugin.getConfigManager().config().getLong("chat-games.interval-seconds",900))*20L;task=Bukkit.getScheduler().runTaskTimer(plugin,this::begin,interval,interval);}
    public void stop(){if(task!=null)task.cancel();task=null;active=null;}
    private void begin(){int left=ThreadLocalRandom.current().nextInt(2,51),right=ThreadLocalRandom.current().nextInt(2,51);active=new Game(UUID.randomUUID().toString(),left+right,System.currentTimeMillis()+60_000);Bukkit.broadcast(Messages.parse("<aqua><bold>CHAT GAME</bold></aqua> <dark_gray>•</dark_gray> <gray>What is <white>"+left+" + "+right+"</white>? First correct answer earns <light_purple>1 Credit</light_purple>.</gray>"));}
    /** Must be called on the main thread. Returns true when the message was a winning answer. */
    public boolean answer(Player player,String message){Game game=active;if(game==null||System.currentTimeMillis()>game.expiresAt){active=null;return false;}int answer;try{answer=Integer.parseInt(message.trim());}catch(NumberFormatException ignored){return false;}if(answer!=game.answer)return false;active=null;plugin.getCreditGrantService().grant("chatgame:"+game.id,player.getUniqueId(),1,"CHAT_GAME").whenComplete((granted,error)->Bukkit.getScheduler().runTask(plugin,()->{if(error!=null){plugin.getLogger().warning("Chat game reward failed: "+error.getMessage());return;}if(Boolean.TRUE.equals(granted))Bukkit.broadcast(Messages.parse("<aqua><bold>CHAT GAME</bold></aqua> <dark_gray>•</dark_gray> <white>"+safe(player.getName())+"</white> <gray>answered correctly and earned <light_purple>1 Credit</light_purple>.</gray>"));}));return true;}
    private static String safe(String value){return value.replace("<","‹").replace(">","›");}
    private record Game(String id,int answer,long expiresAt){}
}
