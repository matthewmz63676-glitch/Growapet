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

import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.utils.Messages;
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
        org.bukkit.configuration.ConfigurationSection config = this.plugin.getMobManager().getMobConfig(mobId.toUpperCase());
        if (config == null || !config.getBoolean("enabled", true)) return null;
        String entityName = config.getString("entity", mobId);
        try {
            eggMaterial = Material.valueOf(entityName.toUpperCase() + "_SPAWN_EGG");
        }
        catch (IllegalArgumentException e) {
            return null;
        }
        String displayName = config != null ? config.getString("display-name", mobId) : mobId;
        ItemStack item = new ItemBuilder(eggMaterial)
                .name(Messages.parse("<green><bold><name> SPAWNER</bold></green>", Messages.value("name", displayName)))
                .loreComponents(java.util.List.of(
                        Messages.parse("<gray>• Right-click → spawn this configured mob</gray>"),
                        Messages.parse("<gray>• Mob → <white><name></white></gray>", Messages.value("name", displayName))))
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.mobIdKey, PersistentDataType.STRING, mobId.toUpperCase());
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
