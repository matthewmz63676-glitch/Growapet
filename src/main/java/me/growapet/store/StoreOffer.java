package me.growapet.store;

import me.growapet.boosts.BoostType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Validated immutable store offer loaded from store.yml. */
public record StoreOffer(String id, int slot, Material icon, String displayName, String description,
                         long creditPrice, Type type, String entitlementId, String entitlementKind,
                         String setting, String value, BoostType boostType, double boostBonus,
                         long boostDurationMillis) {
    public enum Type { ENTITLEMENT, SETTING, BOOST }

    public StoreOffer {
        Objects.requireNonNull(id);Objects.requireNonNull(icon);Objects.requireNonNull(displayName);Objects.requireNonNull(type);
        if (description == null) description = "";
        if (entitlementKind == null || entitlementKind.isBlank()) entitlementKind = "COSMETIC";
        if (value == null) value = "";
        if(!id.matches("[a-z0-9_-]{1,48}")||slot<0||slot>=54||creditPrice<0)throw new IllegalArgumentException("Invalid store offer "+id);
        if(type==Type.ENTITLEMENT&&(entitlementId==null||!entitlementId.matches("[A-Za-z0-9._:-]{1,128}")))throw new IllegalArgumentException("Invalid entitlement offer "+id);
        if(type==Type.SETTING&&(setting==null||!setting.matches("[A-Za-z0-9._:-]{1,128}")))throw new IllegalArgumentException("Invalid setting offer "+id);
        if(type==Type.BOOST&&(boostType==null||!Double.isFinite(boostBonus)||boostBonus<=0||boostDurationMillis<=0))throw new IllegalArgumentException("Invalid boost offer "+id);
    }

    public static StoreOffer from(String id,ConfigurationSection section){
        Type type=Type.valueOf(section.getString("type","").toUpperCase(Locale.ROOT));
        Material icon=Material.matchMaterial(section.getString("material","PAPER"));if(icon==null||icon.isAir())icon=Material.PAPER;
        BoostType boost=null;long duration=0;
        if(type==Type.BOOST){boost=BoostType.valueOf(section.getString("boost-type","").toUpperCase(Locale.ROOT));duration=Duration.ofMinutes(section.getLong("duration-minutes",1)).toMillis();}
        return new StoreOffer(id,section.getInt("slot"),icon,section.getString("display-name",id),section.getString("description",""),section.getLong("price"),type,
                section.getString("entitlement-id"),section.getString("entitlement-kind","COSMETIC"),section.getString("setting"),section.getString("value",""),boost,section.getDouble("bonus"),duration);
    }

    public boolean permanent(){return type!=Type.BOOST;}
}
