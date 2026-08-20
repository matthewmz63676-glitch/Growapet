package me.growapet.leaderboards;

import me.growapet.models.PlayerData;
import org.bukkit.Material;

import java.util.Locale;
import java.util.function.ToDoubleFunction;

public enum LeaderboardType {
    COINS("coins", "Coins", Material.GOLD_INGOT, PlayerData::getCoins, false),
    GEMS("gems", "Gems", Material.DIAMOND, PlayerData::getGems, false),
    CREDITS("credits", "Credits", Material.NETHER_STAR, PlayerData::getCredits, false),
    LEVEL("level", "Level", Material.EXPERIENCE_BOTTLE, PlayerData::getLevel, false),
    MOBKILLS("mob_kills", "Mob Kills", Material.IRON_SWORD, PlayerData::getMobKills, false),
    BOSSKILLS("boss_kills", "Boss Kills", Material.NETHERITE_SWORD, PlayerData::getBossKills, false),
    DAMAGE("damage_multiplier", "Damage", Material.DIAMOND_SWORD, PlayerData::getDamageMultiplier, true),
    COINMULTI("coin_multiplier", "Coin Multiplier", Material.GOLD_BLOCK, PlayerData::getCoinMultiplier, true),
    GEMSMULTI("gem_multiplier", "Gem Multiplier", Material.DIAMOND_BLOCK, PlayerData::getGemMultiplier, true),
    POWER("pet_power", "Pet Power", Material.BONE, PlayerData::getPetPower, true),
    PLAYTIME("playtime_seconds", "Playtime", Material.CLOCK, PlayerData::getPlaytimeSeconds, false),
    EGGS("eggs_hatched", "Eggs Hatched", Material.TURTLE_EGG, PlayerData::getEggsHatched, false),
    PETS("pets_collected", "Pets Collected", Material.CAT_SPAWN_EGG, PlayerData::getPetsCollected, false),
    TRADES("trades", "Trades", Material.EMERALD, PlayerData::getTrades, false),
    HIGHESTPET("highest_pet_level", "Highest Pet", Material.ENCHANTED_BOOK, PlayerData::getHighestPetLevel, false),
    TOPMONEYSPENT("top_money_spent", "Top Money Spent", Material.EMERALD, ignored -> 0, true);
    private final String column,label;private final Material icon;private final ToDoubleFunction<PlayerData> live;private final boolean decimal;
    LeaderboardType(String column,String label,Material icon,ToDoubleFunction<PlayerData>live,boolean decimal){this.column=column;this.label=label;this.icon=icon;this.live=live;this.decimal=decimal;}
    public String column(){return column;}public String label(){return label;}public Material icon(){return icon;}public double live(PlayerData data){return live.applyAsDouble(data);}public boolean decimal(){return decimal;}
    public String key(){return name().toLowerCase(Locale.ROOT);}
    public static LeaderboardType from(String key){if(key==null)return null;try{return valueOf(key.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException error){return null;}}
}
