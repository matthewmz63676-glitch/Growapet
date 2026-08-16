package me.growapet.managers;

import me.growapet.GrowAPet;
import me.growapet.database.PlayerDAO;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerManager {
    private final GrowAPet plugin;
    private final PlayerDAO dao;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadTokens = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final AtomicLong tokenSequence = new AtomicLong();

    public PlayerManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.dao = new PlayerDAO(plugin.getDatabase());
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        long token = tokenSequence.incrementAndGet();
        loadTokens.put(uuid, token);
        long coins = plugin.getConfigManager().config().getLong("economy.starting-coins", 100);
        long gems = plugin.getConfigManager().config().getLong("economy.starting-gems", 0);
        long credits = plugin.getConfigManager().config().getLong("economy.starting-credits", 0);
        dao.load(uuid, player.getName(), coins, gems, credits).whenComplete((data, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> finishLoad(uuid, token, data, error)));
    }

    private void finishLoad(UUID uuid, long token, PlayerData data, Throwable error) {
        if (!Long.valueOf(token).equals(loadTokens.get(uuid))) return;
        Player player = Bukkit.getPlayer(uuid);
        if (error != null || data == null) {
            plugin.getLogger().severe("Could not load player " + uuid + ": " + message(error));
            if (player != null) player.kick(net.kyori.adventure.text.Component.text("GrowAPet could not safely load your data. Please try again."));
            return;
        }
        if (player == null || !player.isOnline()) return;
        data.setName(player.getName());
        plugin.getShopManager().applyAll(data);
        data.setDirty(true);
        cache.put(uuid, data);
        joinedAt.put(uuid, System.currentTimeMillis());
        syncExpBar(player, data);
        plugin.getEntitlementService().load(uuid).whenComplete((ignored, entitlementError) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (entitlementError != null) plugin.getLogger().warning("Could not load entitlements for " + uuid + ": " + entitlementError.getMessage());
            if (Bukkit.getPlayer(uuid) == player && isLoaded(uuid)) plugin.onPlayerReady(player);
        }));
    }

    public CompletableFuture<Void> unload(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player unload must begin on the server thread");
        UUID uuid = player.getUniqueId();
        loadTokens.put(uuid, tokenSequence.incrementAndGet());
        addSessionPlaytime(uuid);
        PlayerData data = cache.get(uuid);
        if (data == null) return CompletableFuture.completedFuture(null);
        return saveWhenUnlocked(uuid, data).whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().severe("Could not save quitting player " + uuid + ": " + message(error));
                return;
            }
            if (Bukkit.getPlayer(uuid) == null && cache.get(uuid) == data) cache.remove(uuid);
            });
        });
    }

    private CompletableFuture<Void> saveWhenUnlocked(UUID uuid, PlayerData data) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable attempt = new Runnable() {
            @Override public void run() {
                if (!plugin.isEnabled()) { result.complete(null); return; }
                if (data.isEconomyLocked()) { Bukkit.getScheduler().runTaskLater(plugin, this, 1L); return; }
                dao.save(data).whenComplete((ignored, error) -> {
                    if (error == null) result.complete(null); else result.completeExceptionally(error);
                });
            }
        };
        attempt.run();
        return result;
    }

    public CompletableFuture<Void> save(PlayerData data) { long revision=data.getRevision(); return dao.save(data).thenRun(()->{try{Bukkit.getScheduler().runTask(plugin,()->{if(data.getRevision()==revision)data.setDirty(false);});}catch(org.bukkit.plugin.IllegalPluginAccessException ignored){}}); }
    public CompletableFuture<Void> saveNonEconomic(PlayerData data) { return dao.saveNonEconomic(data); }
    public CompletableFuture<Void> saveTransaction(PlayerData data,String id,String kind,long coins,long gems,long credits){return dao.saveWithReceipt(data,id,kind,coins,gems,credits);}

    public CompletableFuture<Void> saveAll() {
        updateOnlinePlaytime();
        CompletableFuture<?>[] saves = cache.values().stream().filter(data -> data.isDirty() && !data.isEconomyLocked()).map(this::save).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(saves);
    }

    private void updateOnlinePlaytime() {
        for (UUID uuid : joinedAt.keySet()) addSessionPlaytime(uuid);
    }

    private void addSessionPlaytime(UUID uuid) {
        Long since = joinedAt.remove(uuid);
        PlayerData data = cache.get(uuid);
        if (since != null && data != null) data.addPlaytime(Math.max(0, (System.currentTimeMillis() - since) / 1000L));
        if (Bukkit.getPlayer(uuid) != null) joinedAt.put(uuid, System.currentTimeMillis());
    }

    public boolean isLoaded(UUID uuid) { return cache.containsKey(uuid); }
    public PlayerData get(UUID uuid) { return cache.get(uuid); }
    public PlayerData get(Player player) { return cache.get(player.getUniqueId()); }
    public long getLivePlaytimeSeconds(UUID uuid) { PlayerData data=cache.get(uuid);if(data==null)return 0;Long since=joinedAt.get(uuid);long current=since==null?0:Math.max(0,(System.currentTimeMillis()-since)/1000L);return data.getPlaytimeSeconds()+current; }
    public Collection<PlayerData> getAllCached() { return ListCopy.copy(cache.values()); }

    public void syncExpBar(Player player, PlayerData data) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("XP synchronization must run on the server thread");
        player.setLevel(data.getLevel());
        player.setExp((float) data.getExpProgress());
    }

    public void addExp(Player player, long amount) {
        PlayerData data = get(player);
        if (data == null) return;
        int before = data.getLevel();
        double linkBonus = plugin.getEntitlementService().expMultiplier(player.getUniqueId());
        data.addExp(Math.round(amount * plugin.getEventManager().multiplier(me.growapet.events.EventType.DOUBLE_EXP) * linkBonus));
        syncExpBar(player, data);
        if (data.getLevel() > before) player.sendMessage("§a§lLEVEL UP! §7You are now level §e" + data.getLevel());
    }

    private static String message(Throwable error) {
        if (error == null) return "unknown error";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private static final class ListCopy {
        private static <T> Collection<T> copy(Collection<T> source) { return java.util.List.copyOf(source); }
    }
}
