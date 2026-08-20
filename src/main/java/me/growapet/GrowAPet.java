package me.growapet;

import me.growapet.bosses.BossManager;
import me.growapet.announcements.AnnouncementManager;
import me.growapet.tags.TagService;
import me.growapet.warps.WarpService;
import me.growapet.boosts.PlotBoostManager;
import me.growapet.commands.*;
import me.growapet.config.ConfigManager;
import me.growapet.commerce.CommerceFulfilmentService;
import me.growapet.cosmetics.CosmeticService;
import me.growapet.database.Database;
import me.growapet.credits.CreditGrantService;
import me.growapet.credits.RelicManager;
import me.growapet.credits.ChatGameManager;
import me.growapet.credits.CreditMilestoneManager;
import me.growapet.daily.DailyManager;
import me.growapet.discord.DiscordIntegration;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.eggs.EggIncubationManager;
import me.growapet.eggs.EggManager;
import me.growapet.events.EventManager;
import me.growapet.gui.MenuListener;
import me.growapet.hud.ActionBarManager;
import me.growapet.listeners.*;
import me.growapet.lifecycle.LifecycleCoordinator;
import me.growapet.leaderboards.LeaderboardManager;
import me.growapet.leaderboards.MoneySpentManager;
import me.growapet.managers.PlayerManager;
import me.growapet.managers.SpawnManager;
import me.growapet.mobs.MobManager;
import me.growapet.mobs.SpawnEggManager;
import me.growapet.pets.PetManager;
import me.growapet.placeholders.GrowAPetExpansion;
import me.growapet.plot.PlotManager;
import me.growapet.quests.QuestManager;
import me.growapet.rewards.EntitlementService;
import me.growapet.rewards.RewardFulfilmentService;
import me.growapet.rewards.BoostItemManager;
import me.growapet.seasons.SeasonService;
import me.growapet.settings.OptionsManager;
import me.growapet.shop.ShopManager;
import me.growapet.shop.ShopMigrationService;
import me.growapet.store.StoreManager;
import me.growapet.trade.TradeManager;
import me.growapet.tutorial.TutorialManager;
import me.growapet.zones.WallManager;
import me.growapet.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class GrowAPet extends JavaPlugin {
    private ConfigManager configManager; private Database database; private PlayerManager playerManager;
    private PlotManager plotManager; private PetManager petManager; private EggManager eggManager;
    private EggIncubationManager eggIncubationManager; private ZoneManager zoneManager; private BossManager bossManager;
    private MobManager mobManager; private me.growapet.mobs.MobSpawnPointManager mobSpawnPointManager; private ShopManager shopManager; private SpawnEggManager spawnEggManager;
    private SpawnManager spawnManager; private StoreManager storeManager; private WallManager wallManager;
    private EventManager eventManager; private QuestManager questManager; private TradeManager tradeManager;
    private OptionsManager optionsManager; private ActionBarManager actionBarManager;
    private DailyManager dailyManager;
    private PlotBoostManager plotBoostManager;
    private LeaderboardManager leaderboardManager;
    private MoneySpentManager moneySpentManager;
    private AnnouncementManager announcementManager;
    private TagService tagService;
    private WarpService warpService;
    private CreditGrantService creditGrantService;
    private EntitlementService entitlementService;
    private RewardFulfilmentService rewardFulfilmentService;
    private BoostItemManager boostItemManager;
    private DiscordIntegration discordIntegration;
    private CommerceFulfilmentService commerceFulfilmentService;
    private SeasonService seasonService;
    private CosmeticService cosmeticService;
    private ShopMigrationService shopMigrationService;
    private RelicManager relicManager;
    private ChatGameManager chatGameManager;
    private CreditMilestoneManager creditMilestoneManager;
    private VirtualTextDisplayService virtualTextDisplays;
    private TutorialManager tutorialManager;
    private final LifecycleCoordinator lifecycle = new LifecycleCoordinator();

    @Override public void onEnable() {
        if(!lifecycle.beginStartup())throw new IllegalStateException("GrowAPet lifecycle has already started");
        configManager=new ConfigManager(this);configManager.loadAll();registerCommands();database=new Database(this);
        database.initialize().whenComplete((ignored,error)->{if(!isEnabled()){lifecycle.beginShutdown();database.closeAsync().whenComplete((closed,closeError)->lifecycle.markStopped());return;}Bukkit.getScheduler().runTask(this,()->{
            if(error!=null){lifecycle.fail(error);getLogger().severe("GrowAPet is disabling because its data store is unavailable.");Bukkit.getPluginManager().disablePlugin(this);return;}
            initializeManagers();
        });});
    }

    private void initializeManagers(){
        eventManager=new EventManager(this);optionsManager=new OptionsManager(this);entitlementService=new EntitlementService(this);tagService=new TagService(this);warpService=new WarpService(this);rewardFulfilmentService=new RewardFulfilmentService(this);boostItemManager=new BoostItemManager(this);discordIntegration=new DiscordIntegration(this);commerceFulfilmentService=new CommerceFulfilmentService(this);cosmeticService=new CosmeticService(this);shopMigrationService=new ShopMigrationService(this);seasonService=new SeasonService(this);playerManager=new PlayerManager(this);creditGrantService=new CreditGrantService(this);creditMilestoneManager=new CreditMilestoneManager(this);
        virtualTextDisplays=new VirtualTextDisplayService(this);
        plotManager=new PlotManager(this);petManager=new PetManager(this);eggManager=new EggManager(this);eggIncubationManager=new EggIncubationManager(this);
        zoneManager=new ZoneManager(this);bossManager=new BossManager(this);mobManager=new MobManager(this);mobSpawnPointManager=new me.growapet.mobs.MobSpawnPointManager(this);shopManager=new ShopManager(this);
        spawnEggManager=new SpawnEggManager(this);spawnManager=new SpawnManager(this);storeManager=new StoreManager(this);wallManager=new WallManager(this);
        questManager=new QuestManager(this);tradeManager=new TradeManager(this);actionBarManager=new ActionBarManager(this);dailyManager=new DailyManager(this);plotBoostManager=new PlotBoostManager(this);moneySpentManager=new MoneySpentManager(this);announcementManager=new AnnouncementManager(this);leaderboardManager=new LeaderboardManager(this);tutorialManager=new TutorialManager(this);relicManager=new RelicManager(this);chatGameManager=new ChatGameManager(this);
        CompletableFuture.allOf(plotManager.loadAll(),petManager.loadAll(),eggIncubationManager.loadAll(),eventManager.loadAll(),bossManager.loadAll(),tradeManager.initialize(),plotBoostManager.loadAll(),mobSpawnPointManager.loadAll(),seasonService.load(),tagService.load())
                .whenComplete((ignored,error)->{if(!isEnabled())return;Bukkit.getScheduler().runTask(this,()->{if(error!=null){lifecycle.fail(error);getLogger().severe("GrowAPet startup load failed: "+rootMessage(error));Bukkit.getPluginManager().disablePlugin(this);return;}finishEnable();});});
    }

    private void finishEnable(){
        me.growapet.integration.LuckPermsHook.initialize();wallManager.loadAll();registerListeners();virtualTextDisplays.start();mobManager.start();mobSpawnPointManager.start();petManager.start();bossManager.start();eventManager.start();actionBarManager.start();plotBoostManager.start();moneySpentManager.start();announcementManager.start();leaderboardManager.start();relicManager.start();chatGameManager.start();discordIntegration.loadLinks().exceptionally(error->{getLogger().warning("Discord link cache could not load: "+error.getMessage());return null;});discordIntegration.start();
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI")!=null){new GrowAPetExpansion(this).register();new me.growapet.placeholders.TopSpentExpansion(this).register();getLogger().info("Hooked into PlaceholderAPI.");}
        long autosave=Math.max(30,configManager.config().getLong("autosave-interval-seconds",300))*20L;
        Bukkit.getScheduler().runTaskTimer(this,()->playerManager.saveAll().exceptionally(error->{getLogger().severe("Autosave failed: "+rootMessage(error));return null;}),autosave,autosave);
        if(!lifecycle.markReady())return;for(Player player:Bukkit.getOnlinePlayers())playerManager.load(player);getLogger().info("GrowAPet is ready.");
    }

    private void registerListeners(){for(Listener listener:List.of(new CommandLockdownListener(this),new PlayerListener(this),new StatsInteractionListener(this),new ZoneVisibilityListener(this),new ZoneEntityCullingListener(this),new MobDamageListener(this),new EggListener(this),eggIncubationManager,new BossListener(this),new PlotProtectionListener(this),new SpawnEggListener(this),new BoostItemListener(this),new ChatListener(this),new WallListener(this),new MenuListener(this),new PetListener(this),actionBarManager,relicManager))getServer().getPluginManager().registerEvents(listener,this);}
    private void registerCommands(){command("growapet",new GrowAPetCommand(this));command("plot",new PlotCommand(this));command("pets",new PetsCommand(this));command("stats",new StatsCommand(this));command("boss",new BossCommand(this));command("warp",new WarpCommand(this));command("setwarp",new WarpAdminCommand(this));command("warpplayer",new WarpAdminCommand(this));command("zones",new ZonesCommand(this));command("visit",new VisitCommand(this));command("leaderboard",new LeaderboardCommand(this));command("getegg",new GetEggCommand(this));command("shop",new ShopCommand(this));command("spawn",new SpawnCommand(this));command("setspawn",new SetSpawnCommand(this));command("store",new StoreCommand(this));command("credits",new CreditsCommand(this));command("unlockzone",new UnlockZoneCommand(this));command("quests",new QuestsCommand(this));command("trade",new TradeCommand(this));command("options",new OptionsCommand(this));command("getmob",new GetMobCommand(this));command("mobspawn",new MobSpawnCommand(this));command("getpet",new GetPetCommand(this));command("autokill",new AutoKillCommand(this));command("daily",new DailyCommand(this));command("tutorial",new TutorialCommand(this));command("link",new DiscordCommand(this));command("checklink",new DiscordCommand(this));command("unlink",new DiscordCommand(this));command("season",new SeasonCommand(this));command("cosmetics",new CosmeticsCommand(this));command("tag",new TagCommand(this));command("tagadmin",new TagAdminCommand(this));command("customtag",new CustomTagCommand(this));command("testannouncement",new TestAnnouncementCommand(this));command("topspent",new TopSpentCommand(this));GrowAPetTabCompleter completer=new GrowAPetTabCompleter(this);for(String name:List.of("growapet","plot","pets","stats","boss","warp","setwarp","warpplayer","zones","visit","leaderboard","getegg","shop","spawn","setspawn","store","credits","unlockzone","quests","trade","options","getmob","mobspawn","getpet","autokill","daily","tutorial","link","checklink","unlink","season","cosmetics","tag","tagadmin","customtag","testannouncement","topspent")){PluginCommand registered=getCommand(name);if(registered!=null)registered.setTabCompleter(safeCompleter(name,completer));}}
    private void command(String name,CommandExecutor executor){PluginCommand command=getCommand(name);if(command==null)throw new IllegalStateException("Missing command "+name+" in plugin.yml");command.setExecutor((sender,registered,label,args)->{
        if(!isReady()){sender.sendMessage(net.kyori.adventure.text.Component.text("GrowAPet is still starting. Please try again shortly.",net.kyori.adventure.text.format.NamedTextColor.YELLOW));return true;}
        if(sender instanceof Player player&&!playerManager.isLoaded(player.getUniqueId())){sender.sendMessage(net.kyori.adventure.text.Component.text("Your GrowAPet profile is still loading. Please try again shortly.",net.kyori.adventure.text.format.NamedTextColor.YELLOW));return true;}
        try{return executor.onCommand(sender,registered,label,args);}catch(Exception error){String incident=UUID.randomUUID().toString().substring(0,8);getLogger().log(Level.SEVERE,"Command /"+label+" failed ["+incident+"]",error);sender.sendMessage(net.kyori.adventure.text.Component.text("That command could not be completed. Reference: "+incident,net.kyori.adventure.text.format.NamedTextColor.RED));return true;}
    });}
    private TabCompleter safeCompleter(String name,TabCompleter delegate){return(sender,command,alias,args)->{if(!isReady())return List.of();try{return delegate.onTabComplete(sender,command,alias,args);}catch(Exception error){getLogger().log(Level.WARNING,"Tab completion failed for /"+name,error);return List.of();}};}

    public void onPlayerReady(Player player){if(!plotManager.hasPlot(player.getUniqueId())){plotManager.createPlot(player.getUniqueId());player.sendMessage("§aA plot has been created for you. Use §e/plot home§a.");}optionsManager.load(player.getUniqueId());questManager.load(player.getUniqueId());tradeManager.deliverPending(player.getUniqueId());petManager.restoreEquipped(player.getUniqueId());plotBoostManager.refreshDisplay(player.getUniqueId());creditMilestoneManager.evaluate(player.getUniqueId());wallManager.sendWalls(player);tutorialManager.onPlayerReady(player);actionBarManager.requestResourcePack(player);}
    public boolean reloadRuntime(){if(!configManager.reloadAll())return false;zoneManager.load();wallManager.loadAll();questManager.reload();seasonService.reload();storeManager.reload();tagService.load();warpService.reload();announcementManager.restart();return true;}

    @Override public void onDisable(){lifecycle.beginShutdown();if(announcementManager!=null)announcementManager.stop();if(discordIntegration!=null)discordIntegration.stop();if(tutorialManager!=null)tutorialManager.shutdown();if(tradeManager!=null)tradeManager.stop();if(actionBarManager!=null)actionBarManager.stop();if(chatGameManager!=null)chatGameManager.stop();if(eventManager!=null)eventManager.stop();if(bossManager!=null)bossManager.stop();if(mobSpawnPointManager!=null)mobSpawnPointManager.stop();if(mobManager!=null)mobManager.stop();if(relicManager!=null)relicManager.stop();if(leaderboardManager!=null)leaderboardManager.stop();if(moneySpentManager!=null)moneySpentManager.stop();if(plotBoostManager!=null)plotBoostManager.stop();if(eggIncubationManager!=null)eggIncubationManager.stop();if(petManager!=null)petManager.stop();if(virtualTextDisplays!=null)virtualTextDisplays.stop();CompletableFuture<Void>saves=playerManager==null?CompletableFuture.completedFuture(null):playerManager.saveAll();saves.whenComplete((v,e)->{if(e!=null)getLogger().severe("Shutdown save failed: "+rootMessage(e));if(database==null){lifecycle.markStopped();return;}database.closeAsync().whenComplete((closed,closeError)->lifecycle.markStopped());});}
    private static String rootMessage(Throwable error){Throwable current=error;while(current.getCause()!=null)current=current.getCause();return String.valueOf(current.getMessage());}

    public boolean isReady(){return lifecycle.isReady();}public ConfigManager getConfigManager(){return configManager;}public Database getDatabase(){return database;}public PlayerManager getPlayerManager(){return playerManager;}public PlotManager getPlotManager(){return plotManager;}public PetManager getPetManager(){return petManager;}public EggManager getEggManager(){return eggManager;}public EggIncubationManager getEggIncubationManager(){return eggIncubationManager;}public ZoneManager getZoneManager(){return zoneManager;}public BossManager getBossManager(){return bossManager;}public MobManager getMobManager(){return mobManager;}public me.growapet.mobs.MobSpawnPointManager getMobSpawnPointManager(){return mobSpawnPointManager;}public ShopManager getShopManager(){return shopManager;}public SpawnEggManager getSpawnEggManager(){return spawnEggManager;}public SpawnManager getSpawnManager(){return spawnManager;}public StoreManager getStoreManager(){return storeManager;}public WallManager getWallManager(){return wallManager;}public EventManager getEventManager(){return eventManager;}public QuestManager getQuestManager(){return questManager;}public TradeManager getTradeManager(){return tradeManager;}public OptionsManager getOptionsManager(){return optionsManager;}public VirtualTextDisplayService getVirtualTextDisplays(){return virtualTextDisplays;}public ActionBarManager getActionBarManager(){return actionBarManager;}public DailyManager getDailyManager(){return dailyManager;}public PlotBoostManager getPlotBoostManager(){return plotBoostManager;}public LeaderboardManager getLeaderboardManager(){return leaderboardManager;}public MoneySpentManager getMoneySpentManager(){return moneySpentManager;}public AnnouncementManager getAnnouncementManager(){return announcementManager;}public TagService getTagService(){return tagService;}public WarpService getWarpService(){return warpService;}public TutorialManager getTutorialManager(){return tutorialManager;}public CreditGrantService getCreditGrantService(){return creditGrantService;}public ChatGameManager getChatGameManager(){return chatGameManager;}public CreditMilestoneManager getCreditMilestoneManager(){return creditMilestoneManager;}public RelicManager getRelicManager(){return relicManager;}public EntitlementService getEntitlementService(){return entitlementService;}public RewardFulfilmentService getRewardFulfilmentService(){return rewardFulfilmentService;}public BoostItemManager getBoostItemManager(){return boostItemManager;}public DiscordIntegration getDiscordIntegration(){return discordIntegration;}public CommerceFulfilmentService getCommerceFulfilmentService(){return commerceFulfilmentService;}public SeasonService getSeasonService(){return seasonService;}public CosmeticService getCosmeticService(){return cosmeticService;}public ShopMigrationService getShopMigrationService(){return shopMigrationService;}
}
