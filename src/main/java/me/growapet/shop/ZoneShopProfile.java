package me.growapet.shop;

import org.bukkit.Material;

import java.util.Map;

/** Presentation identity for each current (10-zone) gear storefront. */
public record ZoneShopProfile(String zoneId, String label, String style, Material icon) {
    private static final Map<String, ZoneShopProfile> PROFILES = Map.ofEntries(
            Map.entry("spawn", new ZoneShopProfile("spawn", "Starter", "aqua", Material.NETHER_STAR)),
            Map.entry("plains", new ZoneShopProfile("plains", "Plains", "yellow", Material.HAY_BLOCK)),
            Map.entry("spruce_forest", new ZoneShopProfile("spruce_forest", "Spruce Forest", "dark_green", Material.SPRUCE_SAPLING)),
            Map.entry("savanna", new ZoneShopProfile("savanna", "Savanna", "green", Material.ACACIA_LOG)),
            Map.entry("desert", new ZoneShopProfile("desert", "Desert", "gold", Material.SANDSTONE)),
            Map.entry("ice_spikes", new ZoneShopProfile("ice_spikes", "Ice Spikes", "aqua", Material.PACKED_ICE)),
            Map.entry("cherry_grove", new ZoneShopProfile("cherry_grove", "Cherry Grove", "light_purple", Material.CHERRY_SAPLING)),
            Map.entry("flower_forest", new ZoneShopProfile("flower_forest", "Flower Forest", "white", Material.POPPY)),
            Map.entry("cave", new ZoneShopProfile("cave", "Cave", "dark_gray", Material.DEEPSLATE)),
            Map.entry("ocean", new ZoneShopProfile("ocean", "Ocean", "dark_aqua", Material.PRISMARINE)),
            Map.entry("mushroom_island", new ZoneShopProfile("mushroom_island", "Mushroom Island", "red", Material.RED_MUSHROOM))
    );

    public static ZoneShopProfile forZone(String zoneId) {
        return PROFILES.getOrDefault(zoneId, PROFILES.get("spawn"));
    }
}
