/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.mobs;

import java.util.List;
import me.growapet.GrowAPet;
import me.growapet.utils.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SpawnEggManager {
    private final GrowAPet plugin;
    private final NamespacedKey mobIdKey;

    public SpawnEggManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.mobIdKey = new NamespacedKey((Plugin)plugin, "growapet_mob_id");
    }

    public ItemStack createSpawnEgg(String mobId) {
        Material eggMaterial;
        try {
            eggMaterial = Material.valueOf((String)(mobId.toUpperCase() + "_SPAWN_EGG"));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
        String displayName = this.plugin.getMobManager().getMobConfig(mobId.toUpperCase()) != null ? this.plugin.getMobManager().getMobConfig(mobId.toUpperCase()).getString("display-name", mobId) : mobId;
        ItemStack item = new ItemStack(eggMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Utils.colorize("&a" + displayName + " Spawner"));
        meta.setLore(List.of(Utils.colorize("&7Right click to spawn a"), Utils.colorize("&7custom " + displayName + ".")));
        meta.getPersistentDataContainer().set(this.mobIdKey, PersistentDataType.STRING, (Object)mobId.toUpperCase());
        item.setItemMeta(meta);
        return item;
    }

    public String getMobId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return (String)item.getItemMeta().getPersistentDataContainer().get(this.mobIdKey, PersistentDataType.STRING);
    }

    public boolean isCustomSpawnEgg(ItemStack item) {
        return this.getMobId(item) != null;
    }
}

