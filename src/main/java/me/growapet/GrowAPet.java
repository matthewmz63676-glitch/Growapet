package me.growapet;

import me.growapet.bosses.BossManager;
import me.growapet.boosts.PlotBoostManager;
import me.growapet.commands.*;
import me.growapet.config.ConfigManager;
import me.growapet.database.Database;
import me.growapet.credits.CreditGrantService;
import me.growapet.credits.RelicManager;
import me.growapet.credits.ChatGameManager;
import me.growapet.credits.CreditMilestoneManager;
import me.growapet.daily.DailyManager;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.eggs.EggIncubationManager;
import me.growapet.eggs.EggManager;
import me.growapet.events.EventManager;
import me.growapet.gui.MenuListener;
import me.growapet.hud.ActionBarManager;
import me.growapet.listeners.*;
import me.growapet.leaderboards.LeaderboardManager;
import me.growapet.managers.PlayerManager;
import me.growapet.managers.SpawnManager;
import me.growapet.mobs.MobManager;
import me.growapet.mobs.SpawnEggManager;
import me.growapet.pets.PetManager;
import me.growapet.placeholders.GrowAPetExpansion;
import me.growapet.plot.PlotManager;
import me.growapet.quests.QuestManager;
import me.growapet.settings.OptionsManager;
import me.growapet.shop.ShopManager;
import me.growapet.store.StoreManager;
import me.growapet.trade.TradeManager;
import me.growapet.zones.WallManager;
import me.growapet.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GrowAPet extends JavaPlugin {
    private ConfigManager configManager; private Database database; private PlayerManager playerManager;
    private PlotManager plotManager; private PetManager petManager; private EggManager eggManager;
    private EggIncubationManager eggIncubationManager; private ZoneManager zoneManager; private BossManager bossManager;
    private MobManager mobManager; private ShopManager shopManager; private SpawnEggManager spawnEggManager;
    private SpawnManager spawnManager; private StoreManager storeManager; private WallManager wallManager;
    private EventManager eventManager; private QuestManager questManager; private TradeManager tradeManager;
    private OptionsManager optionsManager; private ActionBarManager actionBarManager;
    private DailyManager dailyManager;
    private PlotBoostManager plotBoostManager;
    private LeaderboardManager leaderboardManager;
    private CreditGrantService creditGrantService;
    private RelicManager relicManager;
    private ChatGameManager chatGameManager;
    private CreditMilestoneManager creditMilestoneManager;
    private VirtualTextDisplayService virtualTextDisplays;
    private volatile boolean ready;

    @Override public void onEnable() {
        configManager=new ConfigManager(this);configManager.loadAll();database=new Database(this);
        database.initialize().whenComplete((ignored,error)->{if(!isEnabled()){database.closeAsync();return;}Bukkit.getScheduler().runTask(this,()->{
            if(error!=null){getLogger().severe("GrowAPet is disabling because its data store is unavailable.");Bukkit.getPluginManager().disablePlugin(this);return;}
            initializeManagers();
        });});
    }

    private void initializeManagers(){
        eventManager=new EventManager(this);optionsManager=new OptionsManager(this);playerManager=new PlayerManager(this);creditGrantService=new CreditGrantService(this);creditMilestoneManager=new CreditMilestoneManager(this);
        virtualTextDisplays=new VirtualTextDisplayService(this);
        plotManager=new PlotManager(this);petManager=new PetManager(this);eggManager=new EggManager(this);eggIncubationManager=new EggIncubationManager(this);
        zoneManager=new ZoneManager(this);bossManager=new BossManager(this);mobManager=new MobManager(this);shopManager=new ShopManager(this);
        spawnEggManager=new SpawnEggManager(this);spawnManager=new SpawnManager(this);storeManager=new StoreManager(this);wallManager=new WallManager(this);
        questManager=new QuestManager(this);tradeManager=new TradeManager(this);actionBarManager=new ActionBarManager(this);dailyManager=new DailyManager(this);plotBoostManager=new PlotBoostManager(this);leaderboardManager=new LeaderboardManager(this);relicManager=new RelicManager(this);chatGameManager=new ChatGameManager(this);
        CompletableFuture.allOf(plotManager.loadAll(),petManager.loadAll(),eggIncubationManager.loadAll(),eventManager.loadAll(),bossManager.loadAll(),tradeManager.initialize(),plotBoostManager.loadAll())
                .whenComplete((ignored,error)->{if(!isEnabled())return;Bukkit.getScheduler().runTask(this,()->{if(error!=null){getLogger().severe("GrowAPet startup load failed: "+rootMessage(error));Bukkit.getPluginManager().disablePlugin(this);return;}finishEnable();});});
    }

    private void finishEnable(){
        me.growapet.integration.LuckPermsHook.initialize();wallManager.loadAll();registerListeners();registerCommands();virtualTextDisplays.start();mobManager.start();petManager.start();bossManager.start();eventManager.start();actionBarManager.start();plotBoostManager.start();leaderboardManager.start();relicManager.start();chatGameManager.start();
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI")!=null){new GrowAPetExpansion(this).register();getLogger().info("Hooked into PlaceholderAPI.");}
        long autosave=Math.max(30,configManager.config().getLong("autosave-interval-seconds",300))*20L;
        Bukkit.getScheduler().runTaskTimer(this,()->playerManager.saveAll().exceptionally(error->{getLogger().severe("Autosave failed: "+rootMessage(error));return null;}),autosave,autosave);
        ready=true;for(Player player:Bukkit.getOnlinePlayers())playerManager.load(player);getLogger().info("GrowAPet is ready.");
    }

    private void registerListeners(){for(Listener listener:List.of(new CommandLockdownListener(this),new PlayerListener(this),new ZoneVisibilityListener(this),new MobDamageListener(this),new EggListener(this),eggIncubationManager,new BossListener(this),new PlotProtectionListener(this),new SpawnEggListener(this),new ChatListener(this),new WallListener(this),new MenuListener(this),new PetListener(this),relicManager))getServer().getPluginManager().registerEvents(listener,this);}
    private void registerCommands(){command("growapet",new GrowAPetCommand(this));command("plot",new PlotCommand(this));command("pets",new PetsCommand(this));command("stats",new StatsCommand(this));command("boss",new BossCommand(this));command("warp",new WarpCommand(this));command("zones",new ZonesCommand(this));command("visit",new VisitCommand(this));command("leaderboard",new LeaderboardCommand(this));command("getegg",new GetEggCommand(this));command("shop",new ShopCommand(this));command("spawn",new SpawnCommand(this));command("setspawn",new SetSpawnCommand(this));command("store",new StoreCommand(this));command("unlockzone",new UnlockZoneCommand(this));command("quests",new QuestsCommand(this));command("trade",new TradeCommand(this));command("options",new OptionsCommand(this));command("getmob",new GetMobCommand(this));command("getpet",new GetPetCommand(this));command("autokill",new AutoKillCommand(this));command("daily",new DailyCommand(this));GrowAPetTabCompleter completer=new GrowAPetTabCompleter(this);for(String name:List.of("growapet","plot","pets","stats","boss","warp","zones","visit","leaderboard","getegg","shop","spawn","setspawn","store","unlockzone","quests","trade","options","getmob","getpet","autokill","daily"))getCommand(name).setTabCompleter(completer);}
    private void command(String name,org.bukkit.command.CommandExecutor executor){PluginCommand command=getCommand(name);if(command==null)throw new IllegalStateException("Missing command "+name+" in plugin.yml");command.setExecutor(executor);}

    public void onPlayerReady(Player player){if(!plotManager.hasPlot(player.getUniqueId())){plotManager.createPlot(player.getUniqueId());player.sendMessage("§aA plot has been created for you. Use §e/plot home§a.");}optionsManager.load(player.getUniqueId());questManager.load(player.getUniqueId());tradeManager.deliverPending(player.getUniqueId());petManager.restoreEquipped(player.getUniqueId());plotBoostManager.refreshDisplay(player.getUniqueId());creditMilestoneManager.evaluate(player.getUniqueId());wallManager.sendWalls(player);}
    public boolean reloadRuntime(){if(!configManager.reloadAll())return false;zoneManager.load();wallManager.loadAll();questManager.reload();return true;}

    @Override public void onDisable(){ready=false;if(tradeManager!=null)tradeManager.stop();if(actionBarManager!=null)actionBarManager.stop();if(chatGameManager!=null)chatGameManager.stop();if(eventManager!=null)eventManager.stop();if(bossManager!=null)bossManager.stop();if(mobManager!=null)mobManager.stop();if(relicManager!=null)relicManager.stop();if(leaderboardManager!=null)leaderboardManager.stop();if(plotBoostManager!=null)plotBoostManager.stop();if(eggIncubationManager!=null)eggIncubationManager.stop();if(petManager!=null)petManager.stop();if(virtualTextDisplays!=null)virtualTextDisplays.stop();CompletableFuture<Void>saves=playerManager==null?CompletableFuture.completedFuture(null):playerManager.saveAll();saves.whenComplete((v,e)->{if(e!=null)getLogger().severe("Shutdown save failed: "+rootMessage(e));if(database!=null)database.closeAsync();});}
    private static String rootMessage(Throwable error){Throwable current=error;while(current.getCause()!=null)current=current.getCause();return String.valueOf(current.getMessage());}

    public boolean isReady(){return ready;}public ConfigManager getConfigManager(){return configManager;}public Database getDatabase(){return database;}public PlayerManager getPlayerManager(){return playerManager;}public PlotManager getPlotManager(){return plotManager;}public PetManager getPetManager(){return petManager;}public EggManager getEggManager(){return eggManager;}public EggIncubationManager getEggIncubationManager(){return eggIncubationManager;}public ZoneManager getZoneManager(){return zoneManager;}public BossManager getBossManager(){return bossManager;}public MobManager getMobManager(){return mobManager;}public ShopManager getShopManager(){return shopManager;}public SpawnEggManager getSpawnEggManager(){return spawnEggManager;}public SpawnManager getSpawnManager(){return spawnManager;}public StoreManager getStoreManager(){return storeManager;}public WallManager getWallManager(){return wallManager;}public EventManager getEventManager(){return eventManager;}public QuestManager getQuestManager(){return questManager;}public TradeManager getTradeManager(){return tradeManager;}public OptionsManager getOptionsManager(){return optionsManager;}public VirtualTextDisplayService getVirtualTextDisplays(){return virtualTextDisplays;}public ActionBarManager getActionBarManager(){return actionBarManager;}public DailyManager getDailyManager(){return dailyManager;}public PlotBoostManager getPlotBoostManager(){return plotBoostManager;}public LeaderboardManager getLeaderboardManager(){return leaderboardManager;}public CreditGrantService getCreditGrantService(){return creditGrantService;}public ChatGameManager getChatGameManager(){return chatGameManager;}public CreditMilestoneManager getCreditMilestoneManager(){return creditMilestoneManager;}public RelicManager getRelicManager(){return relicManager;}
}
