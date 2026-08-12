package me.growapet.shop;

import org.bukkit.Material;

import java.util.Map;

/** Presentation identity for each current (11-zone MVP) gear storefront. */
public record ZoneShopProfile(String zoneId, String label, String style, Material icon) {
    private static final Map<String, ZoneShopProfile> PROFILES = Map.ofEntries(
            Map.entry("spawn", new ZoneShopProfile("spawn", "Starter", "aqua", Material.NETHER_STAR)),
            Map.entry("forest", new ZoneShopProfile("forest", "Forest", "green", Material.OAK_SAPLING)),
            Map.entry("plains", new ZoneShopProfile("plains", "Plains", "yellow", Material.HAY_BLOCK)),
            Map.entry("mushroom", new ZoneShopProfile("mushroom", "Mushroom", "red", Material.RED_MUSHROOM)),
            Map.entry("desert", new ZoneShopProfile("desert", "Desert", "gold", Material.SANDSTONE)),
            Map.entry("badlands", new ZoneShopProfile("badlands", "Badlands", "dark_red", Material.RED_SANDSTONE)),
            Map.entry("snow", new ZoneShopProfile("snow", "Snowlands", "aqua", Material.PACKED_ICE)),
            Map.entry("deep_caves", new ZoneShopProfile("deep_caves", "Deep Caves", "gray", Material.DEEPSLATE)),
            Map.entry("nether", new ZoneShopProfile("nether", "Nether", "red", Material.NETHERRACK)),
            Map.entry("end", new ZoneShopProfile("end", "End", "light_purple", Material.END_STONE)),
            Map.entry("ancient_realm", new ZoneShopProfile("ancient_realm", "Ancient Realm", "dark_purple", Material.ECHO_SHARD))
    );

    public static ZoneShopProfile forZone(String zoneId) {
        return PROFILES.getOrDefault(zoneId, PROFILES.get("spawn"));
    }
}
