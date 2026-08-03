/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.entity.EntityType
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.eggs;

import java.util.List;
import me.growapet.GrowAPet;
import me.growapet.utils.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class EggManager {
    private final GrowAPet plugin;
    private final NamespacedKey eggTypeKey;
    private final NamespacedKey incubateSecondsKey;

    public EggManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.eggTypeKey = new NamespacedKey((Plugin)plugin, "egg_entity_type");
        this.incubateSecondsKey = new NamespacedKey((Plugin)plugin, "egg_incubate_seconds");
    }

    public ItemStack createEgg(EntityType entityType, int incubateSeconds) {
        ItemStack item = new ItemStack(Material.TURTLE_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.colorize("&a" + entityType.name() + " Egg"));
        meta.setLore(List.of(Utils.colorize("&7Place inside your plot to incubate.")));
        meta.getPersistentDataContainer().set(this.eggTypeKey, PersistentDataType.STRING, (Object)entityType.name());
        meta.getPersistentDataContainer().set(this.incubateSecondsKey, PersistentDataType.INTEGER, (Object)incubateSeconds);
        item.setItemMeta(meta);
        return item;
    }

    public EntityType getEggType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String type = (String)item.getItemMeta().getPersistentDataContainer().get(this.eggTypeKey, PersistentDataType.STRING);
        return type != null ? EntityType.valueOf((String)type) : null;
    }

    public int getIncubateSeconds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 60;
        }
        Integer seconds = (Integer)item.getItemMeta().getPersistentDataContainer().get(this.incubateSecondsKey, PersistentDataType.INTEGER);
        return seconds != null ? seconds : 60;
    }

    public boolean isEgg(ItemStack item) {
        return this.getEggType(item) != null;
    }
}

