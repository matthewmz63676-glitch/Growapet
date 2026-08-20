package me.growapet;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("fast")
@Tag("regression")
final class CommandManifestTest {
    private static final Set<String> EXPECTED = Set.of("growapet", "plot", "pets", "stats", "boss", "warp", "setwarp", "warpplayer",
            "zones", "visit", "leaderboard", "getegg", "shop", "spawn", "setspawn", "store", "credits", "unlockzone",
            "quests", "trade", "options", "getmob", "mobspawn", "getpet", "autokill", "daily", "tutorial",
            "link", "checklink", "unlink", "season", "cosmetics", "tag", "tagadmin", "customtag", "testannouncement", "topspent");

    @Test void everyDocumentedCommandIsDeclaredWithUsageText() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationSection commands = yaml.getConfigurationSection("commands");
            assertNotNull(commands);
            assertEquals(EXPECTED, commands.getKeys(false));
            for (String command : EXPECTED) assertNotNull(commands.getString(command + ".usage"), command);
        }
    }
}
