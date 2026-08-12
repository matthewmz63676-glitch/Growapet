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

import java.util.UUID;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.utils.Messages;
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
    private final NamespacedKey deliveryKey;
    private final NamespacedKey instanceKey;

    public EggManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.eggTypeKey = new NamespacedKey((Plugin)plugin, "egg_entity_type");
        this.incubateSecondsKey = new NamespacedKey((Plugin)plugin, "egg_incubate_seconds");
        this.deliveryKey = new NamespacedKey(plugin, "delivery_id");
        this.instanceKey = new NamespacedKey(plugin, "egg_instance_id");
    }

    public ItemStack createEgg(EntityType entityType, int incubateSeconds) {
        if (entityType == null || !entityType.isAlive() || !entityType.isSpawnable() || entityType == EntityType.PLAYER || entityType == EntityType.ARMOR_STAND) throw new IllegalArgumentException("Unsupported pet entity");
        incubateSeconds = Math.max(1, Math.min(604800, incubateSeconds));
        ItemStack item = new ItemBuilder(Material.TURTLE_EGG)
                .name(Messages.parse("<green><bold><type> EGG</bold></green>", Messages.value("type", entityType.name().replace('_', ' '))))
                .loreComponents(java.util.List.of(
                        Messages.parse("<gray>• Place → inside your own plot</gray>"),
                        Messages.parse("<gray>• Incubation → <white><seconds>s</white></gray>", Messages.value("seconds", incubateSeconds))))
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.eggTypeKey, PersistentDataType.STRING, entityType.name());
        meta.getPersistentDataContainer().set(this.incubateSecondsKey, PersistentDataType.INTEGER, incubateSeconds);
        meta.getPersistentDataContainer().set(this.instanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    public EntityType getEggType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String type = (String)item.getItemMeta().getPersistentDataContainer().get(this.eggTypeKey, PersistentDataType.STRING);
        if (type == null) return null;
        try { return EntityType.valueOf(type); } catch (IllegalArgumentException ignored) { return null; }
    }

    public int getIncubateSeconds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 60;
        }
        Integer seconds = (Integer)item.getItemMeta().getPersistentDataContainer().get(this.incubateSecondsKey, PersistentDataType.INTEGER);
        return seconds != null ? Math.max(1, Math.min(seconds, 604800)) : 60;
    }

    public boolean isEgg(ItemStack item) {
        return this.getEggType(item) != null;
    }

    public String ensureInstanceId(ItemStack item) {
        if (!isEgg(item)) return null;
        ItemMeta meta = item.getItemMeta();
        String existing = meta.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        if (existing != null) {
            try { UUID.fromString(existing); return existing; }
            catch (IllegalArgumentException ignored) { }
        }
        String created = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, created);
        item.setItemMeta(meta);
        return created;
    }

    public String getInstanceId(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
    }

    public void markDelivery(ItemStack item, String deliveryId) { ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(deliveryKey,PersistentDataType.STRING,deliveryId);item.setItemMeta(meta); }
    public boolean hasDelivery(ItemStack item,String deliveryId){return item!=null&&item.hasItemMeta()&&deliveryId.equals(item.getItemMeta().getPersistentDataContainer().get(deliveryKey,PersistentDataType.STRING));}
}
