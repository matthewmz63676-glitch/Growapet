package me.growapet.commands;

import me.growapet.GrowAPet;
import me.growapet.events.EventType;
import me.growapet.diagnostics.ReadinessDoctor;
import me.growapet.economy.EconomySimulator;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GrowAPetCommand implements CommandExecutor {
    private final GrowAPet plugin;
    public GrowAPetCommand(GrowAPet plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){
        if(args.length==0){Messages.send(sender,"<aqua><bold>GROWAPET ADMIN</bold></aqua> <dark_gray>•</dark_gray> <gray>reload, economy, events and receipts</gray>");return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);String permission="growapet.admin."+switch(sub){case"reload"->"reload";case"doctor"->"doctor";case"event"->"events";case"give"->"give";case"season"->"seasons";case"shopmigration"->"shopmigration";case"discord"->"discord";default->"economy";};
        if(!sender.hasPermission(permission)){Messages.send(sender,"<red>ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ</red>");return true;}
        try{
            switch(sub){
                case"reload"->{if(plugin.reloadRuntime()){audit(sender,"reload",null,"");Messages.send(sender,"<green>Configuration reloaded and revalidated.</green>");}else Messages.send(sender,"<red>Reload rejected; last-known-good values remain active.</red>");}
                case"doctor"->{if(args.length!=1)throw new IllegalArgumentException();runDoctor(sender);}
                case"economy-sim"->{if(args.length>2)throw new IllegalArgumentException();long seed=args.length==2?Long.parseLong(args[1]):0xC0FFEE;EconomySimulator.SimulationReport report=EconomySimulator.simulate(EconomySimulator.fromConfig(plugin.getConfigManager().config(),plugin.getConfigManager().mobs(),plugin.getConfigManager().bosses(),plugin.getConfigManager().quests(),plugin.getConfigManager().commerce()),seed);Messages.send(sender,"<aqua><bold>ECONOMY SIMULATION</bold></aqua> <dark_gray>•</dark_gray> <gray>seed <white><seed></white></gray>",Messages.value("seed",seed));for(EconomySimulator.ScenarioReport scenario:report.scenarios()){EconomySimulator.Checkpoint checkpoint=scenario.checkpoints().get(scenario.checkpoints().size()-1);Messages.send(sender,"<gray>• <name> → <white><coins> coins</white>, <white><gems> gems</white>, <white><credits> Credits</white> • first zone: <days> days</gray>",Messages.value("name",scenario.name()),Messages.value("coins",checkpoint.coins()),Messages.value("gems",checkpoint.gems()),Messages.value("credits",checkpoint.credits()),Messages.value("days",scenario.timeToFirstZoneDays()));}}
                case"setlevel"->{if(args.length!=3)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long parsed=Long.parseLong(args[2]);if(parsed<1||parsed>Integer.MAX_VALUE)throw new IllegalArgumentException();int oldLevel=data.getLevel();long oldExp=data.getExp();data.setLevel((int)parsed);data.setExp(0);plugin.getPlayerManager().syncExpBar(target,data);plugin.getPlayerManager().saveNonEconomic(data).whenComplete((ignored,error)->onMain(()->{if(error!=null){data.setLevel(oldLevel);data.setExp(oldExp);plugin.getPlayerManager().syncExpBar(target,data);Messages.send(sender,"<red>Level update failed safely.</red>");return;}audit(sender,"setlevel",target.getUniqueId().toString(),String.valueOf(parsed));Messages.send(sender,"<green>Updated <white><player></white>.</green>",Messages.value("player",target.getName()));}));}
                case"setcoins","setgems","setcredits"->{if(args.length!=3)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long value=Long.parseLong(args[2]);if(value<0)throw new IllegalArgumentException();mutateCurrency(sender,target,data,sub,value,false);}
                case"give"->{if(args.length!=4)throw new IllegalArgumentException();Player target=requirePlayer(args[1]);PlayerData data=requireData(target);long amount=Long.parseLong(args[3]);if(amount<=0)throw new IllegalArgumentException();String currency=args[2].toLowerCase(Locale.ROOT);if(!currency.equals("coins")&&!currency.equals("gems")&&!currency.equals("credits"))throw new IllegalArgumentException();mutateCurrency(sender,target,data,currency,amount,true);}
                case"event"->changeEvent(sender,args);
                case"creditreceipt"->{if(args.length!=4)throw new IllegalArgumentException();String receipt=args[1];UUID target=UUID.fromString(args[2]);long amount=Long.parseLong(args[3]);plugin.getCreditGrantService().grant(receipt,target,amount,"WEBSTORE").whenComplete((granted,error)->onMain(()->{if(error!=null){Messages.send(sender,"<red>Credit receipt failed safely.</red>");return;}if(Boolean.TRUE.equals(granted)){audit(sender,"creditreceipt",target.toString(),receipt+":"+amount);Messages.send(sender,"<green>Credit receipt granted.</green>");}else Messages.send(sender,"<yellow>That receipt was already processed; no Credits were added.</yellow>");}));}
                case"fulfil"->{if(!(sender instanceof org.bukkit.command.ConsoleCommandSender))throw new IllegalArgumentException();if(args.length!=6||!args[1].equalsIgnoreCase("tebex")||!plugin.getCommerceFulfilmentService().providerInstalled())throw new IllegalArgumentException();UUID target=UUID.fromString(args[3]);int quantity=Integer.parseInt(args[5]);plugin.getCommerceFulfilmentService().fulfil(args[2],target,args[4],quantity).whenComplete((granted,error)->onMain(()->{if(error!=null){plugin.getLogger().warning("Tebex fulfilment rejected: "+rootMessage(error));Messages.send(sender,"<red>Tebex receipt was rejected safely.</red>");return;}Messages.send(sender,Boolean.TRUE.equals(granted)?"<green>Tebex reward fulfilled exactly once.</green>":"<yellow>That Tebex receipt was already processed or is pending a debt.</yellow>");}));}
                case"commerce"->commerce(sender,args);
                case"reverse"->{if(!(sender instanceof org.bukkit.command.ConsoleCommandSender))throw new IllegalArgumentException();if(args.length!=6||!args[1].equalsIgnoreCase("tebex"))throw new IllegalArgumentException();UUID target=UUID.fromString(args[4]);plugin.getCommerceFulfilmentService().reverse(args[2],args[3],target,args[5]).whenComplete((reversed,error)->onMain(()->Messages.send(sender,error!=null?"<red>Tebex reversal could not be recorded.</red>":Boolean.TRUE.equals(reversed)?"<green>Tebex reversal recorded for audit.</green>":"<yellow>That reversal was already recorded.</yellow>")));}
                case"season"->{new SeasonCommand(plugin).onCommand(sender,command,label,Arrays.copyOfRange(args,1,args.length));}
                case"shopmigration"->migration(sender,args);
                case"discord"->discord(sender,args);
                default->throw new IllegalArgumentException();
            }
        }catch(Exception error){Messages.send(sender,"<red>Invalid command, value, or target.</red>");}
        return true;
    }

    private void runDoctor(CommandSender sender) {
        Messages.send(sender, "<aqua><bold>GROWAPET READINESS CHECK</bold></aqua> <dark_gray>•</dark_gray> <gray>read-only diagnostics in progress...</gray>");
        new ReadinessDoctor(plugin).run().whenComplete((report, error) -> onMain(() -> {
            if (error != null) {
                Messages.send(sender, "<red>Readiness check failed safely: <white><reason></white></red>", Messages.value("reason", rootMessage(error)));
                return;
            }
            for (ReadinessDoctor.Check check : report.checks()) {
                sender.sendMessage(Component.text(check.symbol() + " ", check.color()).append(Component.text(check.name() + ": ", net.kyori.adventure.text.format.NamedTextColor.WHITE)).append(Component.text(check.detail(), net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            }
            String summary = report.healthy() ? "<green><bold>READY CHECK PASSED</bold></green>" : "<red><bold>READY CHECK FOUND BLOCKERS</bold></red>";
            Messages.send(sender, summary + " <dark_gray>•</dark_gray> <gray><failures> blocker(s), <warnings> warning(s)</gray>", Messages.value("failures", report.failures()), Messages.value("warnings", report.warnings()));
        }));
    }

    private void migration(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "analyze" -> plugin.getShopMigrationService().analyze().whenComplete((entries, error) -> onMain(() -> {
                if (error != null) { Messages.send(sender, "<red>Migration analysis failed safely.</red>"); return; }
                Messages.send(sender, "<aqua><bold>SHOP MIGRATION ANALYSIS</bold></aqua> <dark_gray>•</dark_gray> <gray><count> player(s) evaluated.</gray>", Messages.value("count", entries.size()));
                entries.stream().limit(5).forEach(entry -> Messages.send(sender, "<gray>• <player> → <purchases> purchases, remainder <coins> coins / <gems> gems</gray>", Messages.value("player", entry.name()), Messages.value("purchases", entry.purchases().size()), Messages.value("coins", entry.coinRemainder()), Messages.value("gems", entry.gemRemainder())));
            }));
            case "dryrun" -> plugin.getShopMigrationService().dryRun().whenComplete((batch, error) -> onMain(() -> Messages.send(sender, error == null ? "<green>Migration dry-run created: <white>" + batch + "</white>.</green>" : "<red>Migration dry-run failed safely.</red>")));
            case "apply" -> { if (args.length != 4) throw new IllegalArgumentException(); plugin.getShopMigrationService().apply(args[2], args[3]).whenComplete((ok, error) -> onMain(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<green>Migration applied in a durable transaction.</green>" : "<red>Migration apply rejected safely.</red>"))); }
            case "verify" -> { if (args.length != 3) throw new IllegalArgumentException(); plugin.getShopMigrationService().verify(args[2]).whenComplete((report, error) -> onMain(() -> Messages.send(sender, error == null ? "<green>Batch <white>" + report.batchId() + "</white> → " + report.state() + " (" + report.entries() + " entries).</green>" : "<red>Migration verification failed safely.</red>"))); }
            case "rollback" -> { if (args.length != 3) throw new IllegalArgumentException(); plugin.getShopMigrationService().rollback(args[2]).whenComplete((ok, error) -> onMain(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<yellow>Migration rolled back.</yellow>" : "<red>Migration rollback rejected safely.</red>"))); }
            default -> throw new IllegalArgumentException();
        }
    }

    private void discord(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> Messages.send(sender, plugin.getDiscordIntegration().isAvailable() ? "<green>Discord integration is connected.</green>" : "<yellow>Discord integration is unavailable.</yellow>");
            case "panel" -> plugin.getDiscordIntegration().publishLinkPanel().whenComplete((ok, error) -> onMain(() -> Messages.send(sender, error == null && Boolean.TRUE.equals(ok) ? "<green>Discord link panel published.</green>" : "<red>Discord link panel could not be published.</red>")));
            case "unlink" -> { if (args.length != 3) throw new IllegalArgumentException(); Player target = requirePlayer(args[2]); plugin.getDiscordIntegration().links().unlink(target.getUniqueId()).whenComplete((ignored, error) -> onMain(() -> Messages.send(sender, error == null ? "<green>Discord link removed.</green>" : "<red>Discord unlink failed safely.</red>"))); }
            default -> throw new IllegalArgumentException();
        }
    }

    private void commerce(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (args.length != 3) throw new IllegalArgumentException();
                plugin.getCommerceFulfilmentService().status(args[2]).whenComplete((status, error) -> onMain(() -> {
                    if (error != null || status == null) Messages.send(sender, "<red>Provider status could not be verified.</red>");
                    else Messages.send(sender, status.valid() ? "<green>Provider status → <white>" + status.status() + "</white>.</green>" : "<yellow>Provider status → <white>" + status.status() + "</white>.</yellow>");
                }));
            }
            case "pending" -> plugin.getCommerceFulfilmentService().pendingCount().whenComplete((count, error) -> onMain(() -> Messages.send(sender, error == null ? "<aqua>Pending paid receipts → <white>" + count + "</white>.</aqua>" : "<red>Pending receipt check failed safely.</red>")));
            case "reconcile", "retry" -> {
                if (args.length != 2) throw new IllegalArgumentException();
                plugin.getCommerceFulfilmentService().reconcilePending().whenComplete((count, error) -> onMain(() -> Messages.send(sender, error == null ? "<green>Reconciled <white>" + count + "</white> paid receipt(s).</green>" : "<red>Commerce reconciliation failed safely.</red>")));
            }
            case "resolve-debt" -> {
                if (args.length < 5) throw new IllegalArgumentException();
                UUID target = UUID.fromString(args[2]); String kind = args[3].toUpperCase(Locale.ROOT);
                String reason = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                plugin.getCommerceFulfilmentService().resolveDebt(target, kind, sender.getName(), reason).whenComplete((resolved, error) -> onMain(() -> Messages.send(sender, error != null ? "<red>Debt resolution failed safely.</red>" : Boolean.TRUE.equals(resolved) ? "<green>Commerce debt resolved and audited.</green>" : "<yellow>No open debt matched that player and asset.</yellow>")));
            }
            default -> throw new IllegalArgumentException();
        }
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
    private static String rootMessage(Throwable error){Throwable current=error;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?current.getClass().getSimpleName():current.getMessage();}
    private void audit(CommandSender actor,String action,String target,String details){String actorName=actor.getName();plugin.getDatabase().async(connection->{try(PreparedStatement statement=connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at)VALUES(?,?,?,?,?)")){statement.setString(1,actorName);statement.setString(2,action);statement.setString(3,target);statement.setString(4,details);statement.setLong(5,System.currentTimeMillis());statement.executeUpdate();}return null;}).exceptionally(error->{plugin.getLogger().severe("Failed to write admin audit: "+error.getMessage());return null;});}
}
