package me.growapet.store;

import me.growapet.boosts.BoostType;
import org.bukkit.Material;

public enum StoreOffer {
    GOLD_CHAT("Gold Chat Color", Material.GOLD_INGOT, 10, "chat_color", "&6", "Gold chat messages"),
    AQUA_CHAT("Aqua Chat Color", Material.DIAMOND, 10, "chat_color", "&b", "Aqua chat messages"),
    PET_LOVER_TAG("Pet Lover Tag", Material.NAME_TAG, 15, "chat_tag", "&d[Pet Lover]", "Pet Lover chat tag"),
    COIN_BOOST("Coin Plot Booster", Material.GOLD_BLOCK, 10, BoostType.COINS, .50, 30, "+50% Coins for 30 minutes"),
    GEM_BOOST("Gem Plot Booster", Material.DIAMOND_BLOCK, 12, BoostType.GEMS, .50, 30, "+50% Gems for 30 minutes"),
    MOB_EXP_BOOST("Mob EXP Booster", Material.EXPERIENCE_BOTTLE, 10, BoostType.MOB_EXP, .50, 30, "+50% Mob EXP for 30 minutes"),
    PET_EXP_BOOST("Pet EXP Booster", Material.ENCHANTED_BOOK, 10, BoostType.PET_EXP, .50, 30, "+50% Pet EXP for 30 minutes"),
    HATCH_BOOST("Hatch Speed Booster", Material.TURTLE_EGG, 12, BoostType.HATCH_SPEED, .50, 30, "+50% Hatch Speed for 30 minutes");

    private final String displayName; private final Material icon; private final long price;
    private final String setting, value, label; private final BoostType boostType; private final double boostBonus; private final int minutes;
    StoreOffer(String name, Material icon, long price, String setting, String value, String label) {
        this.displayName=name;this.icon=icon;this.price=price;this.setting=setting;this.value=value;this.label=label;this.boostType=null;this.boostBonus=0;this.minutes=0;
    }
    StoreOffer(String name, Material icon, long price, BoostType type, double bonus, int minutes, String label) {
        this.displayName=name;this.icon=icon;this.price=price;this.setting=null;this.value=null;this.label=label;this.boostType=type;this.boostBonus=bonus;this.minutes=minutes;
    }
    public String getDisplayName(){return displayName;} public Material getIcon(){return icon;} public long getCreditPrice(){return price;}
    public String getRewardLabel(){return label;} public String getSetting(){return setting;} public String getValue(){return value;}
    public boolean isBoost(){return boostType!=null;} public BoostType getBoostType(){return boostType;} public double getBoostBonus(){return boostBonus;}
    public long boostDurationMillis(){return java.time.Duration.ofMinutes(minutes).toMillis();}
}
