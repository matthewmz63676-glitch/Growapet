package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.events.EventType;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GrowAPetCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public GrowAPetCommand(GrowAPet plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){
        if(args.length==0){Messages.send(sender,"<aqua><bold>GROWAPET ADMIN</bold></aqua> <dark_gray>•</dark_gray> <gray>reload, economy, events and receipts</gray>");return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);String permission="growapet.admin."+switch(sub){case"reload"->"reload";case"event"->"events";case"give"->"give";default->"economy";};
        if(!sender.hasPermission(permission)){Messages.send(sender,"<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>");return true;}
        try{
            switch(sub){
                case"reload"->{if(plugin.reloadRuntime()){audit(sender,"reload",null,"");Messages.send(sender,"<green>Configuration reloaded and revalidated.</green>");}else Messages.send(sender,"<red>Reload rejected; last-known-good values remain active.</red>");}
                case"setlevel"->{if(args.length!=3)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long parsed=Long.parseLong(args[2]);if(parsed<1||parsed>Integer.MAX_VALUE)throw new IllegalArgumentException();int oldLevel=data.getLevel();long oldExp=data.getExp();data.setLevel((int)parsed);data.setExp(0);plugin.getPlayerManager().syncExpBar(target,data);plugin.getPlayerManager().saveNonEconomic(data).whenComplete((ignored,error)->onMain(()->{if(error!=null){data.setLevel(oldLevel);data.setExp(oldExp);plugin.getPlayerManager().syncExpBar(target,data);Messages.send(sender,"<red>Level update failed safely.</red>");return;}audit(sender,"setlevel",target.getUniqueId().toString(),String.valueOf(parsed));Messages.send(sender,"<green>Updated <white><player></white>.</green>",Messages.value("player",target.getName()));}));}
                case"setcoins","setgems","setcredits"->{if(args.length!=3)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long value=Long.parseLong(args[2]);if(value<0)throw new IllegalArgumentException();mutateCurrency(sender,target,data,sub,value,false);}
                case"give"->{if(args.length!=4)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long amount=Long.parseLong(args[3]);if(amount<=0)throw new IllegalArgumentException();String currency=args[2].toLowerCase(Locale.ROOT);if(!currency.equals("coins")&&!currency.equals("gems")&&!currency.equals("credits"))throw new IllegalArgumentException();mutateCurrency(sender,target,data,currency,amount,true);}
                case"event"->changeEvent(sender,args);
                case"creditreceipt"->{if(args.length!=4)throw new IllegalArgumentException();String receipt=args[1];UUID target=UUID.fromString(args[2]);long amount=Long.parseLong(args[3]);plugin.getCreditGrantService().grant(receipt,target,amount,"WEBSTORE").whenComplete((granted,error)->onMain(()->{if(error!=null){Messages.send(sender,"<red>Credit receipt failed safely.</red>");return;}if(Boolean.TRUE.equals(granted)){audit(sender,"creditreceipt",target.toString(),receipt+":"+amount);Messages.send(sender,"<green>Credit receipt granted.</green>");}else Messages.send(sender,"<yellow>That receipt was already processed; no Credits were added.</yellow>");}));}
                default->throw new IllegalArgumentException();
            }
        }catch(Exception error){Messages.send(sender,"<red>Invalid command, value, or target.</red>");}
        return true;
    }

    private void mutateCurrency(CommandSender sender,Player target,PlayerData data,String operation,long value,boolean add){
        if(!data.tryLockEconomy()){Messages.send(sender,"<red>That player has another transaction in progress.</red>");return;}
        long oldCoins=data.getCoins(),oldGems=data.getGems(),oldCredits=data.getCredits();
        String currency=operation.startsWith("set")?operation.substring(3):operation;
        switch(currency){case"coins"->{if(add)data.addCoinsRaw(value);else data.setCoins(value);}case"gems"->{if(add)data.addGemsRaw(value);else data.setGems(value);}case"credits"->{if(add)data.addCredits(value);else data.setCredits(value);}default->{data.unlockEconomy();throw new IllegalArgumentException();}}
        long coinDelta=data.getCoins()-oldCoins,gemDelta=data.getGems()-oldGems,creditDelta=data.getCredits()-oldCredits;String action=add?"give_"+currency:"set"+currency;
        plugin.getPlayerManager().saveTransaction(data,UUID.randomUUID().toString(),"ADMIN:"+action,coinDelta,gemDelta,creditDelta).whenComplete((ignored,error)->onMain(()->{
            data.unlockEconomy();if(error!=null){data.setCoins(oldCoins);data.setGems(oldGems);data.setCredits(oldCredits);Messages.send(sender,"<red>Currency update failed safely.</red>");return;}
            audit(sender,action,target.getUniqueId().toString(),String.valueOf(value));Messages.send(sender,"<green>Updated <white><player></white>.</green>",Messages.value("player",target.getName()));
        }));
    }

    private void changeEvent(CommandSender sender,String[]args){
        if(args.length<3)throw new IllegalArgumentException();EventType type=EventType.valueOf(args[2].toUpperCase(Locale.ROOT));CompletableFuture<Void> operation;Component success;
        if(args[1].equalsIgnoreCase("stop")){operation=plugin.getEventManager().deactivate(type);success=Messages.parse("<yellow><bold>EVENT ENDED</bold></yellow> <dark_gray>•</dark_gray> <white><type></white>",Messages.value("type",type));}
        else if(args[1].equalsIgnoreCase("start")&&args.length>=4){long seconds=Long.parseLong(args[3]);double multiplier=args.length>=5?Double.parseDouble(args[4]):2;if(seconds<1||!Double.isFinite(multiplier)||multiplier<1)throw new IllegalArgumentException();operation=plugin.getEventManager().activate(type,seconds,multiplier);success=Messages.parse("<light_purple><bold>EVENT STARTED</bold></light_purple> <dark_gray>•</dark_gray> <white><type></white>",Messages.value("type",type));}
        else throw new IllegalArgumentException();
        operation.whenComplete((ignored,error)->onMain(()->{if(error!=null){Messages.send(sender,"<red>Event change failed safely.</red>");return;}Bukkit.broadcast(success);audit(sender,"event",type.name(),String.join(" ",args));}));
    }

    private Player requirePlayer(String name){Player player=Bukkit.getPlayerExact(name);if(player==null)throw new IllegalArgumentException();return player;}
    private PlayerData requireData(Player player){PlayerData data=plugin.getPlayerManager().get(player);if(data==null)throw new IllegalStateException();return data;}
    private void onMain(Runnable action){Bukkit.getScheduler().runTask(plugin,action);}
    private void audit(CommandSender actor,String action,String target,String details){String actorName=actor.getName();plugin.getDatabase().async(connection->{try(PreparedStatement statement=connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at)VALUES(?,?,?,?,?)")){statement.setString(1,actorName);statement.setString(2,action);statement.setString(3,target);statement.setString(4,details);statement.setLong(5,System.currentTimeMillis());statement.executeUpdate();}return null;}).exceptionally(error->{plugin.getLogger().severe("Failed to write admin audit: "+error.getMessage());return null;});}
}
