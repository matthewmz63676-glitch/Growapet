package me.growapet.rewards;

import me.growapet.GrowAPet;
import me.growapet.boosts.BoostType;
import me.growapet.gui.ItemBuilder;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Creates and consumes owner-bound GrowAPet boost consumables on the main thread. */
public final class BoostItemManager {
    private final GrowAPet plugin;
    private final NamespacedKey idKey, typeKey, bonusKey, minutesKey, ownerKey, deliveryKey;
    private final Set<UUID> activating = ConcurrentHashMap.newKeySet();

    public BoostItemManager(GrowAPet plugin) {
        this.plugin = plugin;
        idKey = new NamespacedKey(plugin, "boost_item_id"); typeKey = new NamespacedKey(plugin, "boost_type");
        bonusKey = new NamespacedKey(plugin, "boost_bonus"); minutesKey = new NamespacedKey(plugin, "boost_minutes");
        ownerKey = new NamespacedKey(plugin, "boost_owner"); deliveryKey = new NamespacedKey(plugin, "delivery_id");
    }

    public ItemStack create(UUID owner, String id, BoostType type, double bonus, long minutes, String deliveryId) {
        ItemStack item = new ItemBuilder(Material.POTION)
                .name(Messages.parse("<light_purple><bold>GROWAPET BOOST</bold></light_purple>"))
                .loreComponents(List.of(
                        Messages.parse("<gray>• Effect → <white>" + label(type) + " +" + Math.round(bonus * 100) + "%</white></gray>"),
                        Messages.parse("<gray>• Duration → <white>" + minutes + " minutes</white></gray>"),
                        Messages.parse("<yellow>Right-click → activate</yellow>")
                )).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(bonusKey, PersistentDataType.DOUBLE, bonus);
        meta.getPersistentDataContainer().set(minutesKey, PersistentDataType.LONG, minutes);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(deliveryKey, PersistentDataType.STRING, deliveryId);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBoost(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING); }
    public boolean hasDelivery(ItemStack item, String deliveryId) { return isBoost(item) && deliveryId.equals(item.getItemMeta().getPersistentDataContainer().get(deliveryKey, PersistentDataType.STRING)); }

    public void activate(Player player, ItemStack item) {
        if (!isBoost(item)) return;
        ItemMeta meta = item.getItemMeta(); var pdc = meta.getPersistentDataContainer();
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        if (!player.getUniqueId().toString().equals(owner)) { Messages.send(player, "<red>This boost belongs to another player.</red>"); return; }
        String deliveryId = pdc.get(deliveryKey, PersistentDataType.STRING);
        if (deliveryId == null || deliveryId.isBlank() || !activating.add(player.getUniqueId())) {
            Messages.send(player, "<yellow>That boost is already being activated.</yellow>");
            return;
        }
        try {
            BoostType type = BoostType.valueOf(pdc.get(typeKey, PersistentDataType.STRING));
            double bonus = pdc.getOrDefault(bonusKey, PersistentDataType.DOUBLE, 0.0);
            long minutes = pdc.getOrDefault(minutesKey, PersistentDataType.LONG, 0L);
            if (bonus <= 0 || minutes <= 0) throw new IllegalArgumentException("Invalid boost payload");
            long expiresAt = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(minutes, 60_000L));
            plugin.getPlotBoostManager().grantFromDelivery(player.getUniqueId(), deliveryId, type, bonus, expiresAt,
                    "BOOST_ITEM:" + pdc.get(idKey, PersistentDataType.STRING)).whenComplete((claimed, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                activating.remove(player.getUniqueId());
                if (error != null) {
                    Messages.send(player, "<red>Boost activation failed safely; the item was not consumed.</red>");
                    plugin.getLogger().warning("Boost delivery failed for " + player.getUniqueId() + ": " + error.getMessage());
                    return;
                }
                // A duplicate/copy is harmless: the durable claim has already been used.
                item.setAmount(Math.max(0, item.getAmount() - 1));
                Messages.send(player, Boolean.TRUE.equals(claimed)
                        ? "<green>Boost activated <dark_gray>•</dark_gray> <white>" + label(type) + " +" + Math.round(bonus * 100) + "% for " + minutes + " minutes.</white>"
                        : "<yellow>That boost delivery was already consumed.</yellow>");
            }));
        } catch (Exception error) {
            activating.remove(player.getUniqueId());
            Messages.send(player, "<red>This boost item is invalid and was not consumed.</red>");
            plugin.getLogger().warning("Invalid boost item for " + player.getUniqueId() + ": " + error.getMessage());
        }
    }

    private static String label(BoostType type) { return switch (type) { case MOB_EXP -> "Mob EXP"; case PET_EXP -> "Pet EXP"; case HATCH_SPEED -> "Hatch Speed"; default -> type.name().replace('_', ' '); }; }
}
