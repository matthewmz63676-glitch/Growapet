package me.growapet.trade;

import me.growapet.GrowAPet;
import me.growapet.models.Pet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, single-threaded trade state machine backed by SQLite escrow. */
public final class TradeManager {
    private static final String OPEN = "OPEN";
    private static final String COMMITTING = "COMMITTING";

    private final GrowAPet plugin;
    private final Map<UUID, TradeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, TradeRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> petLocks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingPetLocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRequesters = ConcurrentHashMap.newKeySet();
    private volatile boolean ready;

    public TradeManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> initialize() {
        return plugin.getDatabase().transaction(connection -> {
            recoverAbandoned(connection);
            return null;
        }).thenRun(() -> ready = true);
    }

    public void request(Player from, Player to) {
        if (!ready) {
            Messages.send(from, "<red>Trading is still recovering. Try again shortly.</red>");
            return;
        }
        if (from.equals(to)) {
            Messages.send(from, "<red>You cannot trade with yourself.</red>");
            return;
        }
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(to.getUniqueId())) {
            Messages.send(from, "<red>One of you is already in a trade.</red>");
            return;
        }
        if (!acceptsRequests(to.getUniqueId())) {
            Messages.send(from, "<red>That player is not accepting trade requests.</red>");
            return;
        }
        if (!pendingRequesters.add(from.getUniqueId())) {
            Messages.send(from, "<red>Please wait before sending another trade request.</red>");
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(10L, plugin.getConfigManager().config().getLong("trade.request-cooldown-seconds", 10L)) * 1000L;
        long expiresAt = now + Math.max(5L, plugin.getConfigManager().config().getLong("trade.request-expiry-seconds", 30L)) * 1000L;
        plugin.getDatabase().async(connection -> recordRequestCooldown(connection, from.getUniqueId(), now, cooldownMillis))
                .whenComplete((allowed, error) -> onMain(() -> {
            pendingRequesters.remove(from.getUniqueId());
            if (!from.isOnline()) return;
            if (error != null) {
                Messages.send(from, "<red>Trade request failed safely. Please try again.</red>");
                return;
            }
            if (!Boolean.TRUE.equals(allowed)) {
                Messages.send(from, "<red>Trade requests have a strict <white><seconds>s</white> cooldown.</red>",
                        Messages.value("seconds", cooldownMillis / 1000L));
                return;
            }
            if (!to.isOnline() || !acceptsRequests(to.getUniqueId())) {
                Messages.send(from, "<red>That player is not accepting trade requests.</red>");
                return;
            }
            requests.put(to.getUniqueId(), new TradeRequest(from.getUniqueId(), expiresAt));
            Messages.send(to, "<aqua><bold>TRADE REQUEST</bold></aqua> <dark_gray>•</dark_gray> <white><player></white> <gray>wants to trade.</gray>", Messages.value("player", from.getName()));
            Messages.send(to, "<gray>• Accept <dark_gray>→</dark_gray> <white>/trade accept</white></gray>");
            Messages.send(from, "<green>Trade request sent to <white><player></white>.</green>", Messages.value("player", to.getName()));
        }));
    }

    public void accept(Player player) {
        if (!ready || !acceptsRequests(player.getUniqueId())) {
            requests.remove(player.getUniqueId());
            Messages.send(player, "<red>Trade requests are disabled or unavailable.</red>");
            return;
        }
        TradeRequest request = requests.remove(player.getUniqueId());
        Player other = request == null || request.expiresAt < System.currentTimeMillis()
                ? null : Bukkit.getPlayer(request.from);
        if (other == null || sessions.containsKey(other.getUniqueId()) || sessions.containsKey(player.getUniqueId())) {
            Messages.send(player, "<red>You do not have a valid trade request.</red>");
            return;
        }

        TradeSession session = new TradeSession(other.getUniqueId(), player.getUniqueId());
        session.busy = true;
        sessions.put(session.one, session);
        sessions.put(session.two, session);
        long now = System.currentTimeMillis();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO trade_sessions(trade_id,player_one,player_two,state,created_at,updated_at) VALUES(?,?,?,?,?,?)")) {
                statement.setString(1, session.id.toString());
                statement.setString(2, session.one.toString());
                statement.setString(3, session.two.toString());
                statement.setString(4, OPEN);
                statement.setLong(5, now);
                statement.setLong(6, now);
                statement.executeUpdate();
            }
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (error != null) {
                close(session, error);
                notifyPlayers(other, player, "<red>Could not open that trade safely.</red>");
                return;
            }
            session.busy = false;
            notify(session, "<green>Trade opened.</green> <gray>Offer assets, then both use <white>/trade confirm</white>.</gray>");
        }));
    }

    public void onTradePreferenceChanged(UUID player, boolean enabled) {
        if (!enabled) requests.remove(player);
    }

    public boolean isPetLocked(UUID petId) {
        return petId != null && (petLocks.containsKey(petId) || pendingPetLocks.contains(petId));
    }

    public void offerCurrency(Player player, boolean coins, long amount) {
        TradeSession session = active(player);
        PlayerData data = plugin.getPlayerManager().get(player);
        if (session == null || data == null || amount < 0) {
            Messages.send(player, "<red>Invalid trade offer.</red>");
            return;
        }
        TradeOffer offer = session.offer(player.getUniqueId());
        long previous = coins ? offer.coins : offer.gems;
        long difference = amount - previous;
        long spendable = coins ? data.getCoins() : data.getGems();
        if (difference > 0 && spendable < difference) {
            Messages.send(player, "<red>You do not have enough <currency>.</red>", Messages.value("currency", coins ? "Coins" : "Gems"));
            return;
        }
        if (!begin(session, player)) return;
        if (!data.tryLockEconomy()) {
            session.busy = false;
            Messages.send(player, "<red>Another transaction is already in progress.</red>");
            return;
        }

        String type = coins ? "COINS" : "GEMS";
        plugin.getPlayerManager().save(data).thenCompose(ignored -> plugin.getDatabase().transaction(connection -> {
            if (difference > 0 && !debit(connection, player.getUniqueId(), coins, difference)) {
                throw new IllegalStateException("Balance changed before escrow");
            }
            if (difference < 0) credit(connection, player.getUniqueId(), coins, -difference);
            upsertCurrencyEscrow(connection, session.id, player.getUniqueId(), type, amount);
            resetConfirmations(connection, session.id);
            return null;
        })).whenComplete((ignored, error) -> onMain(() -> {
            data.unlockEconomy();
            if (session.closed) return;
            if (error != null) {
                session.busy = false;
                Messages.send(player, "<red>Offer update failed safely.</red>");
                return;
            }
            if (difference > 0) {
                if (coins) data.removeCoins(difference); else data.removeGems(difference);
            } else if (difference < 0) {
                if (coins) data.addCoinsRaw(-difference); else data.addGemsRaw(-difference);
            }
            if (coins) offer.coins = amount; else offer.gems = amount;
            changed(session);
        }));
    }

    public void offerPet(Player player, UUID petId) {
        TradeSession session = active(player);
        Pet pet = plugin.getPetManager().getPet(player.getUniqueId(), petId);
        if (session == null || pet == null || pet.isEquipped() || isPetLocked(petId)) {
            Messages.send(player, "<red>That pet is unavailable. Unequip it before trading.</red>");
            return;
        }
        if (!begin(session, player)) return;
        pendingPetLocks.add(petId);
        plugin.getPetManager().save(pet).thenCompose(ignored -> plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE pets SET trade_lock_id=? WHERE uuid=? AND owner=? AND equipped=0 AND trade_lock_id IS NULL")) {
                lock.setString(1, session.id.toString());
                lock.setString(2, petId.toString());
                lock.setString(3, player.getUniqueId().toString());
                if (lock.executeUpdate() != 1) throw new IllegalStateException("Pet is already locked");
            }
            upsertEscrow(connection, session.id, player.getUniqueId(), "PET", petId.toString(), 1, null, "ACTIVE");
            resetConfirmations(connection, session.id);
            return null;
        })).whenComplete((ignored, error) -> onMain(() -> {
            pendingPetLocks.remove(petId);
            if (session.closed) return;
            if (error != null) {
                session.busy = false;
                Messages.send(player, "<red>Pet offer failed safely.</red>");
                return;
            }
            petLocks.put(petId, session.id);
            session.offer(player.getUniqueId()).pets.add(petId);
            changed(session);
        }));
    }

    public void offerEgg(Player player) {
        TradeSession session = active(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (session == null || !plugin.getEggManager().isEgg(held)) {
            Messages.send(player, "<red>Hold a GrowAPet egg first.</red>");
            return;
        }
        TradeOffer offer = session.offer(player.getUniqueId());
        if (offer.eggInstanceId != null) {
            Messages.send(player, "<red>You already offered an egg.</red>");
            return;
        }
        if (!begin(session, player)) return;

        String instanceId = plugin.getEggManager().ensureInstanceId(held);
        ItemStack snapshot = held.clone();
        snapshot.setAmount(1);
        String payload = Base64.getEncoder().encodeToString(snapshot.serializeAsBytes());
        plugin.getDatabase().transaction(connection -> {
            upsertEscrow(connection, session.id, player.getUniqueId(), "EGG", instanceId, 1, payload, "PENDING");
            resetConfirmations(connection, session.id);
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (session.closed) return;
            if (error != null) {
                session.busy = false;
                Messages.send(player, "<red>Egg offer failed safely.</red>");
                return;
            }
            ItemStack current = player.getInventory().getItemInMainHand();
            if (!instanceId.equals(plugin.getEggManager().getInstanceId(current))) {
                removeEscrowAsset(session.id, player.getUniqueId(), "EGG", instanceId);
                session.busy = false;
                Messages.send(player, "<red>Keep the offered egg in your hand until it is secured.</red>");
                return;
            }
            if (current.getAmount() <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            else current.setAmount(current.getAmount() - 1);
            offer.eggInstanceId = instanceId;
            plugin.getDatabase().async(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE trade_escrow SET state='ACTIVE' WHERE trade_id=? AND owner_uuid=? AND asset_type='EGG' AND asset_id=?")) {
                    statement.setString(1, session.id.toString());
                    statement.setString(2, player.getUniqueId().toString());
                    statement.setString(3, instanceId);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("Egg escrow disappeared");
                }
                return null;
            }).whenComplete((finalized, finalError) -> onMain(() -> {
                if (session.closed) return;
                if (finalError != null) {
                    session.busy = false;
                    cancelSession(session, "<red>Trade cancelled because egg escrow could not be finalized.</red>");
                    return;
                }
                changed(session);
            }));
        }));
    }

    public void confirm(Player player) {
        TradeSession session = active(player);
        if (session == null) { Messages.send(player,"<red>No active trade.</red>"); return; }
        if (!begin(session, player)) return;
        boolean first = session.one.equals(player.getUniqueId());
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE trade_sessions SET " + (first ? "first_confirmed" : "second_confirmed") + "=1,updated_at=? WHERE trade_id=? AND state='OPEN'")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, session.id.toString());
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Trade is no longer open");
            }
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (session.closed) return;
            session.busy = false;
            if (error != null) {
                Messages.send(player, "<red>Confirmation failed safely.</red>");
                return;
            }
            session.offer(player.getUniqueId()).confirmed = true;
            notify(session, "<green><player> confirmed.</green>", Messages.value("player", player.getName()));
            if (session.first.confirmed && session.second.confirmed) commit(session);
            else finishDeferredCancel(session);
        }));
    }

    public CompletableFuture<Void> cancel(UUID playerId, String reason) {
        TradeSession session = sessions.get(playerId);
        if (session == null) return CompletableFuture.completedFuture(null);
        if (session.committing) return session.settled;
        if (session.busy) {
            session.cancelRequested = true;
            return session.settled;
        }
        cancelSession(session, legacyReason(reason));
        return session.settled;
    }

    public void deliverPending(UUID playerId) {
        plugin.getDatabase().async(connection -> {
            List<Delivery> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id,item_type,item_data FROM item_deliveries WHERE player_uuid=? ORDER BY created_at")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new Delivery(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
            return result;
        }).whenComplete((deliveries, error) -> onMain(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (error != null || player == null) return;
            for (Delivery delivery : deliveries) {
                ItemStack item;
                try {
                    item = decodeDelivery(delivery);
                } catch (RuntimeException invalid) {
                    plugin.getLogger().severe("Invalid item delivery " + delivery.id + ": " + invalid.getMessage());
                    continue;
                }
                String instanceId = plugin.getEggManager().getInstanceId(item);
                if (alreadyReceived(player, delivery.id, instanceId)) {
                    deleteDelivery(delivery.id);
                    continue;
                }
                plugin.getEggManager().markDelivery(item, delivery.id);
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                if (!leftovers.isEmpty()) {
                    Messages.send(player, "<yellow>A pending item is waiting for inventory space.</yellow>");
                    break;
                }
                deleteDelivery(delivery.id);
            }
        }));
    }

    public void stop() {
        ready = false;
        requests.clear();
        for (TradeSession session : new HashSet<>(sessions.values())) {
            if (session.closed) continue;
            if (session.committing) {
                PlayerData first=plugin.getPlayerManager().get(session.one),second=plugin.getPlayerManager().get(session.two);
                if(first!=null){first.tryLockEconomy();plugin.getPlayerManager().saveNonEconomic(first);}
                if(second!=null){second.tryLockEconomy();plugin.getPlayerManager().saveNonEconomic(second);}
                unlockSessionPets(session);session.closed=true;sessions.remove(session.one,session);sessions.remove(session.two,session);
                continue;
            }
            restoreCachedEscrow(session);
            unlockSessionPets(session);
            session.closed = true;
            sessions.remove(session.one, session);
            sessions.remove(session.two, session);
            plugin.getDatabase().transaction(connection -> {
                returnEscrow(connection, session.id, "CANCELLED");
                return null;
            }).whenComplete((ignored, error) -> {
                if (error == null) session.settled.complete(null);
                else session.settled.completeExceptionally(error);
            });
        }
    }

    private void commit(TradeSession session) {
        if (session.closed || session.committing) return;
        PlayerData firstData = plugin.getPlayerManager().get(session.one);
        PlayerData secondData = plugin.getPlayerManager().get(session.two);
        if (firstData != null && !firstData.tryLockEconomy()) {
            session.busy = false;
            Bukkit.getScheduler().runTaskLater(plugin, () -> commit(session), 1L);
            return;
        }
        if (secondData != null && !secondData.tryLockEconomy()) {
            if (firstData != null) firstData.unlockEconomy();
            session.busy = false;
            Bukkit.getScheduler().runTaskLater(plugin, () -> commit(session), 1L);
            return;
        }
        session.busy = true;
        session.committing = true;
        plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement state = connection.prepareStatement(
                    "UPDATE trade_sessions SET state='COMMITTING',updated_at=? WHERE trade_id=? AND state='OPEN' AND first_confirmed=1 AND second_confirmed=1")) {
                state.setLong(1, System.currentTimeMillis());
                state.setString(2, session.id.toString());
                if (state.executeUpdate() != 1) throw new IllegalStateException("Trade confirmations changed");
            }
            settleEscrow(connection,session.id,session.one,session.two);
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (firstData != null) firstData.unlockEconomy();
            if (secondData != null) secondData.unlockEconomy();
            if (session.closed) return;
            if (error != null) {
                session.committing = false;
                session.busy = false;
                cancelSession(session, "<red>Trade settlement failed and all escrow is being returned.</red>");
                return;
            }
            applyCompletedTrade(session);
        }));
    }

    private void applyCompletedTrade(TradeSession session) {
        PlayerData first = plugin.getPlayerManager().get(session.one);
        PlayerData second = plugin.getPlayerManager().get(session.two);
        if (first != null) {
            first.addCoinsRaw(session.second.coins);
            first.addGemsRaw(session.second.gems);
            first.incrementTrades();
        }
        if (second != null) {
            second.addCoinsRaw(session.first.coins);
            second.addGemsRaw(session.first.gems);
            second.incrementTrades();
        }
        unlockSessionPets(session);
        for (UUID pet : session.first.pets) plugin.getPetManager().transfer(session.one, session.two, pet);
        for (UUID pet : session.second.pets) plugin.getPetManager().transfer(session.two, session.one, pet);
        notify(session, "<green><bold>TRADE COMPLETE</bold></green> <dark_gray>•</dark_gray> <white>All assets were transferred safely.</white>");
        UUID one = session.one;
        UUID two = session.two;
        close(session, null);
        deliverPending(one);
        deliverPending(two);
    }

    private void cancelSession(TradeSession session, String reason) {
        if (session.closed || session.busy || session.committing) {
            session.cancelRequested = true;
            return;
        }
        session.busy = true;
        plugin.getDatabase().transaction(connection -> {
            returnEscrow(connection, session.id, "CANCELLED");
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (session.closed) return;
            if (error != null) {
                session.busy = false;
                session.settled.completeExceptionally(error);
                notify(session, "<red>Trade recovery is pending; your escrow remains safely recorded.</red>");
                return;
            }
            restoreCachedEscrow(session);
            unlockSessionPets(session);
            UUID one = session.one;
            UUID two = session.two;
            notify(session, reason);
            close(session, null);
            deliverPending(one);
            deliverPending(two);
        }));
    }

    private void changed(TradeSession session) {
        session.first.confirmed = false;
        session.second.confirmed = false;
        session.busy = false;
        notify(session, "<yellow>Offer changed <dark_gray>•</dark_gray> both confirmations were cleared.</yellow>");
        finishDeferredCancel(session);
    }

    private void finishDeferredCancel(TradeSession session) {
        if (session.cancelRequested && !session.busy && !session.committing) {
            session.cancelRequested = false;
            cancelSession(session, "<red>Trade cancelled.</red>");
        }
    }

    private boolean begin(TradeSession session, Player player) {
        if (session == null || session.closed || session.committing) {
            Messages.send(player, "<red>No active trade.</red>");
            return false;
        }
        if (session.busy) {
            Messages.send(player, "<red>Wait for the current trade update to finish.</red>");
            return false;
        }
        session.busy = true;
        return true;
    }

    private TradeSession active(Player player) {
        return sessions.get(player.getUniqueId());
    }

    private void restoreCachedEscrow(TradeSession session) {
        if (session.cacheRestored) return;
        session.cacheRestored = true;
        PlayerData first = plugin.getPlayerManager().get(session.one);
        PlayerData second = plugin.getPlayerManager().get(session.two);
        if (first != null) {
            first.addCoinsRaw(session.first.coins);
            first.addGemsRaw(session.first.gems);
        }
        if (second != null) {
            second.addCoinsRaw(session.second.coins);
            second.addGemsRaw(session.second.gems);
        }
    }

    private void unlockSessionPets(TradeSession session) {
        for (UUID pet : session.first.pets) petLocks.remove(pet, session.id);
        for (UUID pet : session.second.pets) petLocks.remove(pet, session.id);
    }

    private void close(TradeSession session, Throwable error) {
        session.closed = true;
        session.busy = false;
        sessions.remove(session.one, session);
        sessions.remove(session.two, session);
        if (error == null) session.settled.complete(null);
        else session.settled.completeExceptionally(error);
    }

    private boolean acceptsRequests(UUID playerId) {
        return plugin.getOptionsManager().isLoaded(playerId)
                && plugin.getOptionsManager().enabled(playerId, "trade_requests", true);
    }

    private ItemStack decodeDelivery(Delivery delivery) {
        if ("TRADE_ITEM".equals(delivery.type)) {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(delivery.data));
        }
        if ("EGG".equals(delivery.type)) {
            String[] parts = delivery.data.split(":", 2);
            return plugin.getEggManager().createEgg(EntityType.valueOf(parts[0]), Integer.parseInt(parts[1]));
        }
        throw new IllegalArgumentException("Unsupported item type " + delivery.type);
    }

    private boolean alreadyReceived(Player player, String deliveryId, String instanceId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getEggManager().hasDelivery(item, deliveryId)) return true;
            if (instanceId != null && instanceId.equals(plugin.getEggManager().getInstanceId(item))) return true;
        }
        return false;
    }

    private void deleteDelivery(String id) {
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM item_deliveries WHERE id=?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        }).exceptionally(error -> {
            plugin.getLogger().severe("Could not acknowledge item delivery " + id + ": " + error.getMessage());
            return null;
        });
    }

    private void removeEscrowAsset(UUID tradeId, UUID owner, String type, String assetId) {
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM trade_escrow WHERE trade_id=? AND owner_uuid=? AND asset_type=? AND asset_id=?")) {
                statement.setString(1, tradeId.toString());
                statement.setString(2, owner.toString());
                statement.setString(3, type);
                statement.setString(4, assetId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    static void recoverAbandoned(Connection connection) throws Exception {
        List<UUID> tradeIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT trade_id FROM trade_sessions WHERE state IN ('OPEN','COMMITTING')");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) tradeIds.add(UUID.fromString(rows.getString(1)));
        }
        for (UUID tradeId : tradeIds) returnEscrow(connection, tradeId, "RECOVERED");
        try (PreparedStatement unlock = connection.prepareStatement(
                "UPDATE pets SET trade_lock_id=NULL WHERE trade_lock_id IS NOT NULL AND trade_lock_id NOT IN (SELECT trade_id FROM trade_sessions WHERE state='OPEN')")) {
            unlock.executeUpdate();
        }
    }

    static boolean recordRequestCooldown(Connection connection, UUID player, long now, long cooldownMillis) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trade_request_cooldowns(player_uuid,last_request_at) VALUES(?,?) " +
                        "ON CONFLICT(player_uuid) DO UPDATE SET last_request_at=excluded.last_request_at " +
                        "WHERE trade_request_cooldowns.last_request_at<=?")) {
            statement.setString(1, player.toString());
            statement.setLong(2, now);
            statement.setLong(3, now - cooldownMillis);
            return statement.executeUpdate() == 1;
        }
    }

    static void settleEscrow(Connection connection,UUID tradeId,UUID one,UUID two)throws Exception{
        try(PreparedStatement check=connection.prepareStatement("SELECT state FROM trade_sessions WHERE trade_id=?")){check.setString(1,tradeId.toString());try(ResultSet row=check.executeQuery()){if(!row.next()||!COMMITTING.equals(row.getString(1)))throw new IllegalStateException("Trade is not committing");}}
        List<EscrowRow> rows=escrowRows(connection,tradeId);
        for(EscrowRow row:rows){UUID receiver=row.owner.equals(one)?two:one;switch(row.type){case"COINS"->credit(connection,receiver,true,row.amount);case"GEMS"->credit(connection,receiver,false,row.amount);case"PET"->transferPet(connection,row.assetId,row.owner,receiver,tradeId);case"EGG"->createDelivery(connection,deliveryId(tradeId,receiver,row.assetId),receiver,"TRADE_ITEM",row.payload);default->throw new IllegalStateException("Unknown escrow asset "+row.type);}}
        incrementTrades(connection,one);incrementTrades(connection,two);long now=System.currentTimeMillis();
        try(PreparedStatement history=connection.prepareStatement("INSERT INTO trades(trade_id,player_one,player_two,state,payload,created_at,completed_at) VALUES(?,?,?,?,?,?,?)")){history.setString(1,tradeId.toString());history.setString(2,one.toString());history.setString(3,two.toString());history.setString(4,"COMPLETED");history.setString(5,"durable-escrow");history.setLong(6,now);history.setLong(7,now);history.executeUpdate();}
        try(PreparedStatement state=connection.prepareStatement("UPDATE trade_sessions SET state='COMPLETED',updated_at=? WHERE trade_id=? AND state='COMMITTING'")){state.setLong(1,now);state.setString(2,tradeId.toString());if(state.executeUpdate()!=1)throw new IllegalStateException("Trade state changed during settlement");}
        deleteEscrow(connection,tradeId);
    }

    private static void returnEscrow(Connection connection, UUID tradeId, String state) throws Exception {
        for (EscrowRow row : escrowRows(connection, tradeId)) {
            switch (row.type) {
                case "COINS" -> credit(connection, row.owner, true, row.amount);
                case "GEMS" -> credit(connection, row.owner, false, row.amount);
                case "PET" -> unlockPet(connection, row.assetId, tradeId);
                case "EGG" -> createDelivery(connection, deliveryId(tradeId, row.owner, row.assetId), row.owner, "TRADE_ITEM", row.payload);
                default -> throw new IllegalStateException("Unknown escrow asset " + row.type);
            }
        }
        deleteEscrow(connection, tradeId);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE trade_sessions SET state=?,updated_at=? WHERE trade_id=? AND state IN ('OPEN','COMMITTING')")) {
            statement.setString(1, state);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, tradeId.toString());
            statement.executeUpdate();
        }
    }

    private static List<EscrowRow> escrowRows(Connection connection, UUID tradeId) throws Exception {
        List<EscrowRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid,asset_type,asset_id,amount,payload FROM trade_escrow WHERE trade_id=?")) {
            statement.setString(1, tradeId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new EscrowRow(
                        UUID.fromString(result.getString(1)), result.getString(2), result.getString(3),
                        result.getLong(4), result.getString(5)));
            }
        }
        return rows;
    }

    private static void upsertCurrencyEscrow(Connection connection, UUID tradeId, UUID owner, String type, long amount) throws Exception {
        if (amount == 0L) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM trade_escrow WHERE trade_id=? AND owner_uuid=? AND asset_type=? AND asset_id=?")) {
                statement.setString(1, tradeId.toString());
                statement.setString(2, owner.toString());
                statement.setString(3, type);
                statement.setString(4, type.toLowerCase());
                statement.executeUpdate();
            }
            return;
        }
        upsertEscrow(connection, tradeId, owner, type, type.toLowerCase(), amount, null, "ACTIVE");
    }

    private static void upsertEscrow(Connection connection, UUID tradeId, UUID owner, String type,
                                     String assetId, long amount, String payload, String state) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trade_escrow(trade_id,owner_uuid,asset_type,asset_id,amount,payload,state) VALUES(?,?,?,?,?,?,?) " +
                        "ON CONFLICT(trade_id,owner_uuid,asset_type,asset_id) DO UPDATE SET amount=excluded.amount,payload=excluded.payload,state=excluded.state")) {
            statement.setString(1, tradeId.toString());
            statement.setString(2, owner.toString());
            statement.setString(3, type);
            statement.setString(4, assetId);
            statement.setLong(5, amount);
            statement.setString(6, payload);
            statement.setString(7, state);
            statement.executeUpdate();
        }
    }

    private static void resetConfirmations(Connection connection, UUID tradeId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE trade_sessions SET first_confirmed=0,second_confirmed=0,updated_at=? WHERE trade_id=? AND state='OPEN'")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, tradeId.toString());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Trade is no longer open");
        }
    }

    private static boolean debit(Connection connection, UUID player, boolean coins, long amount) throws Exception {
        String column = coins ? "coins" : "gems";
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET " + column + "=" + column + "-? WHERE uuid=? AND " + column + ">=?")) {
            statement.setLong(1, amount);
            statement.setString(2, player.toString());
            statement.setLong(3, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private static void credit(Connection connection, UUID player, boolean coins, long amount) throws Exception {
        if (amount <= 0) return;
        String column = coins ? "coins" : "gems";
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET " + column + "=CASE WHEN " + column + ">? THEN 9223372036854775807 ELSE " + column + "+? END WHERE uuid=?")) {
            statement.setLong(1, Long.MAX_VALUE - amount);
            statement.setLong(2, amount);
            statement.setString(3, player.toString());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Missing trade player");
        }
    }

    private static void transferPet(Connection connection, String petId, UUID owner, UUID receiver, UUID tradeId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pets SET owner=?,equipped=0,entity_uuid=NULL,trade_lock_id=NULL WHERE uuid=? AND owner=? AND trade_lock_id=?")) {
            statement.setString(1, receiver.toString());
            statement.setString(2, petId);
            statement.setString(3, owner.toString());
            statement.setString(4, tradeId.toString());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Escrowed pet changed owner");
        }
    }

    private static void unlockPet(Connection connection, String petId, UUID tradeId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pets SET trade_lock_id=NULL WHERE uuid=? AND trade_lock_id=?")) {
            statement.setString(1, petId);
            statement.setString(2, tradeId.toString());
            statement.executeUpdate();
        }
    }

    private static void createDelivery(Connection connection, String id, UUID player, String type, String payload) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO item_deliveries(id,player_uuid,item_type,item_data,created_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, id);
            statement.setString(2, player.toString());
            statement.setString(3, type);
            statement.setString(4, payload);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void incrementTrades(Connection connection, UUID player) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE players SET trades=CASE WHEN trades=9223372036854775807 THEN trades ELSE trades+1 END WHERE uuid=?")) {
            statement.setString(1, player.toString());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Missing trade player");
        }
    }

    private static void deleteEscrow(Connection connection, UUID tradeId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM trade_escrow WHERE trade_id=?")) {
            statement.setString(1, tradeId.toString());
            statement.executeUpdate();
        }
    }

    private static String deliveryId(UUID tradeId, UUID owner, String assetId) {
        return UUID.nameUUIDFromBytes(("trade:" + tradeId + ":" + owner + ":" + assetId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private void notify(TradeSession session, String message, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        notifyPlayers(Bukkit.getPlayer(session.one), Bukkit.getPlayer(session.two), message, resolvers);
    }

    private static void notifyPlayers(Player one, Player two, String message,
                                      net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        if (one != null) Messages.send(one, message, resolvers);
        if (two != null) Messages.send(two, message, resolvers);
    }

    private static String legacyReason(String reason) {
        if (reason == null || reason.isBlank()) return "<red>Trade cancelled.</red>";
        String plain=reason.replaceAll("§[0-9A-FK-ORa-fk-or]","").replace("<","‹").replace(">","›");
        return "<red>"+plain+"</red>";
    }

    private void onMain(Runnable action) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private record TradeRequest(UUID from, long expiresAt) {}
    private record Delivery(String id, String type, String data) {}
    private record EscrowRow(UUID owner, String type, String assetId, long amount, String payload) {}
}
