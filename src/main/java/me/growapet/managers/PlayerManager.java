/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.managers;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import me.growapet.database.PlayerDAO;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlayerManager {
    private final GrowAPet plugin;
    private final PlayerDAO dao;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<UUID, PlayerData>();

    public PlayerManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.dao = new PlayerDAO(plugin.getDatabase());
    }

    public void load(Player player) {
        this.dao.load(player.getUniqueId(), player.getName()).thenAccept(data -> {
            this.cache.put(player.getUniqueId(), (PlayerData)data);
            this.plugin.getShopManager().applyAll((PlayerData)data);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                this.syncExpBar(player, (PlayerData)data);
                this.plugin.getHudManager().update(player);
            });
        });
    }

    public void unload(Player player) {
        PlayerData data = this.cache.remove(player.getUniqueId());
        if (data != null) {
            this.dao.save(data);
        }
    }

    public void saveAll() {
        for (PlayerData data : this.cache.values()) {
            this.dao.save(data);
        }
    }

    public PlayerData get(UUID uuid) {
        return this.cache.get(uuid);
    }

    public PlayerData get(Player player) {
        return this.cache.get(player.getUniqueId());
    }

    public Collection<PlayerData> getAllCached() {
        return this.cache.values();
    }

    public void syncExpBar(Player player, PlayerData data) {
        player.setLevel(data.getLevel());
        player.setExp((float)data.getExpProgress());
    }

    public void addExp(Player player, long amount) {
        PlayerData data = this.get(player);
        if (data == null) {
            return;
        }
        int before = data.getLevel();
        data.addExp(amount);
        this.syncExpBar(player, data);
        if (data.getLevel() > before) {
            player.sendMessage("\u00a7a\u00a7lLEVEL UP! \u00a77You are now level \u00a7e" + data.getLevel());
        }
    }
}

