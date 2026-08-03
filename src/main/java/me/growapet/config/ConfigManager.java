/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.Configuration
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 */
package me.growapet.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import me.growapet.GrowAPet;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {
    private static final String[] FILES = new String[]{"config.yml", "mobs.yml", "pets.yml", "eggs.yml", "zones.yml", "quests.yml", "bosses.yml", "menus.yml", "messages.yml", "hud.yml"};
    private final GrowAPet plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<String, FileConfiguration>();
    private final Map<String, File> files = new HashMap<String, File>();

    public ConfigManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        for (String name : FILES) {
            File file = new File(this.plugin.getDataFolder(), name);
            if (!file.exists()) {
                this.plugin.saveResource(name, false);
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration((File)file);
            this.mergeMissingDefaults(name, file, (FileConfiguration)cfg);
            this.configs.put(name, (FileConfiguration)cfg);
            this.files.put(name, file);
        }
    }

    private void mergeMissingDefaults(String name, File file, FileConfiguration cfg) {
        try (InputStream resource = this.plugin.getResource(name);){
            if (resource == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(resource, StandardCharsets.UTF_8));
            cfg.setDefaults((Configuration)defaults);
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Couldn't attach defaults for " + name + ": " + e.getMessage());
        }
    }

    public void save(String name) {
        FileConfiguration cfg = this.configs.get(name);
        File file = this.files.get(name);
        if (cfg == null || file == null) {
            return;
        }
        try {
            cfg.save(file);
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Couldn't save " + name + ": " + e.getMessage());
        }
    }

    public void reloadAll() {
        for (String name : FILES) {
            File file = this.files.get(name);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration((File)file);
            this.mergeMissingDefaults(name, file, (FileConfiguration)cfg);
            this.configs.put(name, (FileConfiguration)cfg);
        }
    }

    public FileConfiguration get(String name) {
        return this.configs.get(name);
    }

    public FileConfiguration config() {
        return this.get("config.yml");
    }

    public FileConfiguration mobs() {
        return this.get("mobs.yml");
    }

    public FileConfiguration pets() {
        return this.get("pets.yml");
    }

    public FileConfiguration eggs() {
        return this.get("eggs.yml");
    }

    public FileConfiguration zones() {
        return this.get("zones.yml");
    }

    public FileConfiguration quests() {
        return this.get("quests.yml");
    }

    public FileConfiguration bosses() {
        return this.get("bosses.yml");
    }

    public FileConfiguration menus() {
        return this.get("menus.yml");
    }

    public FileConfiguration messages() {
        return this.get("messages.yml");
    }

    public FileConfiguration hud() {
        return this.get("hud.yml");
    }
}

