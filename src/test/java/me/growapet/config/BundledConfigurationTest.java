package me.growapet.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("fast")
@Tag("regression")
final class BundledConfigurationTest {
    private static final List<String> FILES=List.of("config.yml","mobs.yml","pets.yml","eggs.yml","zones.yml","quests.yml","bosses.yml","menus.yml","messages.yml","hud.yml","tutorial.yml","discord.yml","cosmetics.yml","announcements.yml","stats.yml","store.yml","warps.yml","plugin.yml");

    @Test void everyBundledYamlFileParses() throws Exception {
        for(String name:FILES){
            try(InputStream stream=getClass().getClassLoader().getResourceAsStream(name)){
                assertNotNull(stream,"Missing resource "+name);
                String yaml=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                YamlConfiguration parsed=new YamlConfiguration();
                assertDoesNotThrow(()->parsed.loadFromString(yaml),name);
            }
        }
    }

    @Test void tabAndScoreboardAreNotConfigured() throws Exception {
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("hud.yml")){
            assertNotNull(stream);
            YamlConfiguration hud=new YamlConfiguration();hud.loadFromString(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            assertFalse(hud.contains("tab"));assertFalse(hud.contains("scoreboard"));
        }
    }
}
