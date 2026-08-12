package me.growapet.store;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StoreManager {
    private final GrowAPet plugin;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    public StoreManager(GrowAPet plugin) { this.plugin=plugin; }
    public boolean canAfford(PlayerData data, StoreOffer offer) { return data.getCredits()>=offer.getCreditPrice(); }

    public boolean purchase(Player player, StoreOffer offer) {
        UUID playerId=player.getUniqueId();
        PlayerData data=plugin.getPlayerManager().get(playerId);
        if(data==null||!canAfford(data,offer)||!data.tryLockEconomy()) return false;
        if(!busy.add(playerId)){data.unlockEconomy();return false;}
        data.removeCredits(offer.getCreditPrice());
        String receipt=UUID.randomUUID().toString();
        Long boostExpiry=offer.isBoost()?System.currentTimeMillis()+offer.boostDurationMillis():null;
        plugin.getDatabase().transaction(connection->{
            try(PreparedStatement debit=connection.prepareStatement("UPDATE players SET credits=credits-? WHERE uuid=? AND credits>=?")){
                debit.setLong(1,offer.getCreditPrice());debit.setString(2,playerId.toString());debit.setLong(3,offer.getCreditPrice());
                if(debit.executeUpdate()!=1)throw new IllegalStateException("Insufficient credits");
            }
            if(offer.isBoost()) plugin.getPlotBoostManager().insert(connection,receipt,playerId,offer.getBoostType(),offer.getBoostBonus(),boostExpiry,"CREDIT_STORE");
            else try(PreparedStatement setting=connection.prepareStatement("INSERT INTO settings(player_uuid,setting_key,setting_value)VALUES(?,?,?) ON CONFLICT(player_uuid,setting_key)DO UPDATE SET setting_value=excluded.setting_value")){
                setting.setString(1,playerId.toString());setting.setString(2,offer.getSetting());setting.setString(3,offer.getValue());setting.executeUpdate();
            }
            try(PreparedStatement transaction=connection.prepareStatement("INSERT INTO economy_transactions(id,player_uuid,kind,credits_delta,created_at)VALUES(?,?,?,-?,?)")){
                transaction.setString(1,receipt);transaction.setString(2,playerId.toString());transaction.setString(3,"CREDIT_STORE:"+offer.name());transaction.setLong(4,offer.getCreditPrice());transaction.setLong(5,System.currentTimeMillis());transaction.executeUpdate();
            }
            return null;
        }).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{
            busy.remove(playerId);
            data.unlockEconomy();
            if(error!=null){data.addCredits(offer.getCreditPrice());if(player.isOnline())player.sendMessage("§cPurchase failed safely.");return;}
            if(offer.isBoost())plugin.getPlotBoostManager().cache(receipt,playerId,offer.getBoostType(),offer.getBoostBonus(),boostExpiry,"CREDIT_STORE");
            else plugin.getOptionsManager().cache(playerId,offer.getSetting(),offer.getValue());
            if(player.isOnline())me.growapet.utils.Messages.send(player,"<green>Purchased <yellow><offer></yellow>.</green>",me.growapet.utils.Messages.value("offer",offer.getDisplayName()));
        }));
        return true;
    }
}
