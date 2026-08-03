/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package me.growapet;

import lombok.Generated;
import me.growapet.bosses.BossManager;
import me.growapet.commands.BossCommand;
import me.growapet.commands.GetEggCommand;
import me.growapet.commands.GetMobCommand;
import me.growapet.commands.GetPetCommand;
import me.growapet.commands.GrowAPetCommand;
import me.growapet.commands.LeaderboardCommand;
import me.growapet.commands.PetsCommand;
import me.growapet.commands.PlotCommand;
import me.growapet.commands.SetSpawnCommand;
import me.growapet.commands.ShopCommand;
import me.growapet.commands.SpawnCommand;
import me.growapet.commands.StatsCommand;
import me.growapet.commands.StoreCommand;
import me.growapet.commands.StubCommand;
import me.growapet.commands.UnlockZoneCommand;
import me.growapet.commands.VisitCommand;
import me.growapet.commands.WarpCommand;
import me.growapet.commands.ZonesCommand;
import me.growapet.config.ConfigManager;
import me.growapet.database.Database;
import me.growapet.eggs.EggIncubationManager;
import me.growapet.eggs.EggManager;
import me.growapet.hud.HudManager;
import me.growapet.listeners.BossListener;
import me.growapet.listeners.ChatListener;
import me.growapet.listeners.EggListener;
import me.growapet.listeners.MobDamageListener;
import me.growapet.listeners.MobKillListener;
import me.growapet.listeners.PlayerListener;
import me.growapet.listeners.PlotProtectionListener;
import me.growapet.listeners.SpawnEggListener;
import me.growapet.listeners.WallListener;
import me.growapet.managers.PlayerManager;
import me.growapet.managers.SpawnManager;
import me.growapet.mobs.MobManager;
import me.growapet.mobs.SpawnEggManager;
import me.growapet.pets.PetManager;
import me.growapet.placeholders.GrowAPetExpansion;
import me.growapet.plot.PlotManager;
import me.growapet.shop.ShopManager;
import me.growapet.store.StoreManager;
import me.growapet.zones.WallManager;
import me.growapet.zones.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class GrowAPet
extends JavaPlugin {
    private ConfigManager configManager;
    private Database database;
    private PlayerManager playerManager;
    private PlotManager plotManager;
    private PetManager petManager;
    private EggManager eggManager;
    private EggIncubationManager eggIncubationManager;
    private ZoneManager zoneManager;
    private BossManager bossManager;
    private MobManager mobManager;
    private ShopManager shopManager;
    private HudManager hudManager;
    private SpawnEggManager spawnEggManager;
    private SpawnManager spawnManager;
    private StoreManager storeManager;
    private WallManager wallManager;

    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();
        this.database = new Database(this);
        this.database.connect();
        this.playerManager = new PlayerManager(this);
        this.plotManager = new PlotManager(this);
        this.petManager = new PetManager(this);
        this.eggManager = new EggManager(this);
        this.eggIncubationManager = new EggIncubationManager(this);
        this.zoneManager = new ZoneManager(this);
        this.bossManager = new BossManager(this);
        this.mobManager = new MobManager(this);
        this.shopManager = new ShopManager(this);
        this.hudManager = new HudManager(this);
        this.spawnEggManager = new SpawnEggManager(this);
        this.spawnManager = new SpawnManager(this);
        this.storeManager = new StoreManager(this);
        this.wallManager = new WallManager(this);
        this.mobManager.start();
        this.hudManager.start();
        this.wallManager.loadAll();
        this.plotManager.loadAll();
        this.petManager.loadAll();
        this.getServer().getPluginManager().registerEvents((Listener)new PlayerListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new MobKillListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new MobDamageListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new EggListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)this.eggIncubationManager, (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new BossListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new PlotProtectionListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new SpawnEggListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new ChatListener(this), (Plugin)this);
        this.getServer().getPluginManager().registerEvents((Listener)new WallListener(this), (Plugin)this);
        this.getCommand("growapet").setExecutor((CommandExecutor)new GrowAPetCommand(this));
        this.getCommand("plot").setExecutor((CommandExecutor)new PlotCommand(this));
        this.getCommand("pets").setExecutor((CommandExecutor)new PetsCommand(this));
        this.getCommand("stats").setExecutor((CommandExecutor)new StatsCommand(this));
        this.getCommand("boss").setExecutor((CommandExecutor)new BossCommand(this));
        this.getCommand("warp").setExecutor((CommandExecutor)new WarpCommand(this));
        this.getCommand("zones").setExecutor((CommandExecutor)new ZonesCommand(this));
        this.getCommand("visit").setExecutor((CommandExecutor)new VisitCommand(this));
        this.getCommand("leaderboard").setExecutor((CommandExecutor)new LeaderboardCommand(this));
        this.getCommand("getegg").setExecutor((CommandExecutor)new GetEggCommand(this));
        this.getCommand("shop").setExecutor((CommandExecutor)new ShopCommand(this));
        this.getCommand("spawn").setExecutor((CommandExecutor)new SpawnCommand(this));
        this.getCommand("setspawn").setExecutor((CommandExecutor)new SetSpawnCommand(this));
        this.getCommand("store").setExecutor((CommandExecutor)new StoreCommand(this));
        this.getCommand("unlockzone").setExecutor((CommandExecutor)new UnlockZoneCommand(this));
        GetMobCommand getMobCommand = new GetMobCommand(this);
        this.getCommand("getmob").setExecutor((CommandExecutor)getMobCommand);
        this.getCommand("getmob").setTabCompleter((TabCompleter)getMobCommand);
        GetPetCommand getPetCommand = new GetPetCommand(this);
        this.getCommand("getpet").setExecutor((CommandExecutor)getPetCommand);
        this.getCommand("getpet").setTabCompleter((TabCompleter)getPetCommand);
        this.getCommand("quests").setExecutor((CommandExecutor)new StubCommand("Quests"));
        this.getCommand("trade").setExecutor((CommandExecutor)new StubCommand("Trading"));
        this.getCommand("options").setExecutor((CommandExecutor)new StubCommand("Options menu"));
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GrowAPetExpansion(this).register();
            this.getLogger().info("Hooked into PlaceholderAPI.");
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously((Plugin)this, () -> this.playerManager.saveAll(), 6000L, 6000L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.playerManager.load(player);
            if (!this.plotManager.hasPlot(player.getUniqueId())) {
                this.plotManager.createPlot(player.getUniqueId());
            }
            this.wallManager.sendWalls(player);
        }
        this.getLogger().info(this.getDescription().getName() + " has been enabled!");
    }

    public void onDisable() {
        if (this.playerManager != null) {
            this.playerManager.saveAll();
        }
        if (this.mobManager != null) {
            this.mobManager.stop();
        }
        if (this.eggIncubationManager != null) {
            this.eggIncubationManager.stop();
        }
        if (this.hudManager != null) {
            this.hudManager.stop();
        }
        if (this.database != null) {
            this.database.close();
        }
        this.getLogger().info(this.getDescription().getName() + " has been disabled!");
    }

    @Generated
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    @Generated
    public Database getDatabase() {
        return this.database;
    }

    @Generated
    public PlayerManager getPlayerManager() {
        return this.playerManager;
    }

    @Generated
    public PlotManager getPlotManager() {
        return this.plotManager;
    }

    @Generated
    public PetManager getPetManager() {
        return this.petManager;
    }

    @Generated
    public EggManager getEggManager() {
        return this.eggManager;
    }

    @Generated
    public EggIncubationManager getEggIncubationManager() {
        return this.eggIncubationManager;
    }

    @Generated
    public ZoneManager getZoneManager() {
        return this.zoneManager;
    }

    @Generated
    public BossManager getBossManager() {
        return this.bossManager;
    }

    @Generated
    public MobManager getMobManager() {
        return this.mobManager;
    }

    @Generated
    public ShopManager getShopManager() {
        return this.shopManager;
    }

    @Generated
    public HudManager getHudManager() {
        return this.hudManager;
    }

    @Generated
    public SpawnEggManager getSpawnEggManager() {
        return this.spawnEggManager;
    }

    @Generated
    public SpawnManager getSpawnManager() {
        return this.spawnManager;
    }

    @Generated
    public StoreManager getStoreManager() {
        return this.storeManager;
    }

    @Generated
    public WallManager getWallManager() {
        return this.wallManager;
    }
}

