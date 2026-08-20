package me.growapet.store;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic credit-store boundary. Permanent offers cannot be purchased twice. */
public final class StoreManager {
    private final GrowAPet plugin;private final Set<UUID>busy=ConcurrentHashMap.newKeySet();private volatile List<StoreOffer>offers=List.of();
    public StoreManager(GrowAPet plugin){this.plugin=plugin;reload();}
    public void reload(){ConfigurationSection root=plugin.getConfigManager().store().getConfigurationSection("offers");List<StoreOffer>loaded=new ArrayList<>();if(root!=null)for(String id:root.getKeys(false)){ConfigurationSection section=root.getConfigurationSection(id);if(section==null)continue;try{loaded.add(StoreOffer.from(id,section));}catch(RuntimeException error){plugin.getLogger().warning("Skipping invalid store offer '"+id+"': "+error.getMessage());}}offers=loaded.stream().sorted(Comparator.comparingInt(StoreOffer::slot)).toList();}
    public List<StoreOffer>offers(){return offers;}public boolean canAfford(PlayerData data,StoreOffer offer){return data!=null&&offer!=null&&data.getCredits()>=offer.creditPrice();}
    public boolean owns(UUID playerId,StoreOffer offer){if(offer==null||!offer.permanent())return false;return switch(offer.type()){case ENTITLEMENT->plugin.getEntitlementService().has(playerId,offer.entitlementId());case SETTING->offer.value().equals(plugin.getOptionsManager().get(playerId,offer.setting(),""));case BOOST->false;};}

    public boolean purchase(Player player,StoreOffer offer){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Store purchases must begin on the server thread");
        UUID playerId=player.getUniqueId();PlayerData data=plugin.getPlayerManager().get(playerId);
        if(data==null||offer==null||owns(playerId,offer)||!canAfford(data,offer)||!data.tryLockEconomy())return false;
        if(!busy.add(playerId)){data.unlockEconomy();return false;}if(!data.removeCredits(offer.creditPrice())){busy.remove(playerId);data.unlockEconomy();return false;}
        String receipt="credit-store:"+UUID.randomUUID();Long boostExpiry=offer.type()==StoreOffer.Type.BOOST?System.currentTimeMillis()+offer.boostDurationMillis():null;
        plugin.getDatabase().<Long>transaction(connection->{
            if(offer.type()==StoreOffer.Type.ENTITLEMENT){try(PreparedStatement owned=connection.prepareStatement("SELECT active FROM player_entitlements WHERE player_uuid=? AND entitlement_id=?")){owned.setString(1,playerId.toString());owned.setString(2,offer.entitlementId());try(ResultSet row=owned.executeQuery()){if(row.next()&&row.getInt(1)==1)throw new AlreadyOwnedException();}}}
            else if(offer.type()==StoreOffer.Type.SETTING){try(PreparedStatement owned=connection.prepareStatement("SELECT setting_value FROM settings WHERE player_uuid=? AND setting_key=?")){owned.setString(1,playerId.toString());owned.setString(2,offer.setting());try(ResultSet row=owned.executeQuery()){if(row.next()&&offer.value().equals(row.getString(1)))throw new AlreadyOwnedException();}}}
            try(PreparedStatement debit=connection.prepareStatement("UPDATE players SET credits=credits-? WHERE uuid=? AND credits>=?")){debit.setLong(1,offer.creditPrice());debit.setString(2,playerId.toString());debit.setLong(3,offer.creditPrice());if(debit.executeUpdate()!=1)throw new IllegalStateException("Insufficient credits");}
            long now=System.currentTimeMillis();switch(offer.type()){
                case BOOST->plugin.getPlotBoostManager().insert(connection,receipt,playerId,offer.boostType(),offer.boostBonus(),boostExpiry,"CREDIT_STORE");
                case SETTING->{try(PreparedStatement setting=connection.prepareStatement("INSERT INTO settings(player_uuid,setting_key,setting_value)VALUES(?,?,?) ON CONFLICT(player_uuid,setting_key)DO UPDATE SET setting_value=excluded.setting_value")){setting.setString(1,playerId.toString());setting.setString(2,offer.setting());setting.setString(3,offer.value());setting.executeUpdate();}}
                case ENTITLEMENT->{
                    try(PreparedStatement reward=connection.prepareStatement("INSERT INTO reward_receipts(receipt_id,origin,player_uuid,bundle_version,status,created_at)VALUES(?,?,?,?,?,?)")){reward.setString(1,receipt);reward.setString(2,"CREDIT_STORE");reward.setString(3,playerId.toString());reward.setString(4,"store-v1");reward.setString(5,"APPLIED");reward.setLong(6,now);reward.executeUpdate();}
                    try(PreparedStatement source=connection.prepareStatement("INSERT INTO entitlement_sources(receipt_id,player_uuid,entitlement_id,kind,value,created_at)VALUES(?,?,?,?,?,?)")){source.setString(1,receipt);source.setString(2,playerId.toString());source.setString(3,offer.entitlementId());source.setString(4,offer.entitlementKind());source.setString(5,offer.value());source.setLong(6,now);source.executeUpdate();}
                    try(PreparedStatement owned=connection.prepareStatement("INSERT INTO player_entitlements(player_uuid,entitlement_id,kind,value,active,created_at)VALUES(?,?,?,?,1,?) ON CONFLICT(player_uuid,entitlement_id)DO UPDATE SET active=1,value=excluded.value")){owned.setString(1,playerId.toString());owned.setString(2,offer.entitlementId());owned.setString(3,offer.entitlementKind());owned.setString(4,offer.value());owned.setLong(5,now);owned.executeUpdate();}
                }}
            try(PreparedStatement tx=connection.prepareStatement("INSERT INTO economy_transactions(id,player_uuid,kind,credits_delta,created_at)VALUES(?,?,?,-?,?)")){tx.setString(1,receipt);tx.setString(2,playerId.toString());tx.setString(3,"CREDIT_STORE:"+offer.id());tx.setLong(4,offer.creditPrice());tx.setLong(5,now);tx.executeUpdate();}
            try(PreparedStatement audit=connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at)VALUES(?,?,?,?,?)")){audit.setString(1,player.getName());audit.setString(2,"STORE_PURCHASE");audit.setString(3,playerId.toString());audit.setString(4,offer.id());audit.setLong(5,now);audit.executeUpdate();}
            try(PreparedStatement purchase=connection.prepareStatement("INSERT INTO store_purchases(receipt_id,buyer_uuid,recipient_uuid,product_id,quantity,credits_spent,purchased_at)VALUES(?,?,?,?,?,?,?)")){purchase.setString(1,receipt);purchase.setString(2,playerId.toString());purchase.setString(3,playerId.toString());purchase.setString(4,offer.id());purchase.setInt(5,1);purchase.setLong(6,offer.creditPrice());purchase.setLong(7,now);purchase.executeUpdate();}
            try(PreparedStatement balance=connection.prepareStatement("SELECT credits FROM players WHERE uuid=?")){balance.setString(1,playerId.toString());try(ResultSet row=balance.executeQuery()){if(!row.next())throw new IllegalStateException("Player row missing");return row.getLong(1);}}
        }).whenComplete((newBalance,error)->onMain(()->{
            busy.remove(playerId);data.unlockEconomy();Player online=Bukkit.getPlayer(playerId);
            if(error!=null){syncCredits(playerId,data);if(online!=null)Messages.send(online,root(error)instanceof AlreadyOwnedException?"<yellow>You already own that permanent store item.</yellow>":"<red>Purchase failed safely; your Credits were restored.</red>");return;}
            data.setCredits(newBalance == null ? data.getCredits() : Math.max(0, newBalance));
            switch(offer.type()){case BOOST->plugin.getPlotBoostManager().cache(receipt,playerId,offer.boostType(),offer.boostBonus(),boostExpiry,"CREDIT_STORE");case SETTING->plugin.getOptionsManager().cache(playerId,offer.setting(),offer.value());case ENTITLEMENT->plugin.getEntitlementService().cache(playerId,offer.entitlementId());}
            if(online!=null)Messages.send(online,"<green>Purchased <yellow><offer></yellow>.</green>",Messages.value("offer",offer.displayName()));
        }));return true;
    }
    private void syncCredits(UUID id,PlayerData data){plugin.getDatabase().async(connection->{try(PreparedStatement statement=connection.prepareStatement("SELECT credits FROM players WHERE uuid=?")){statement.setString(1,id.toString());try(ResultSet row=statement.executeQuery()){return row.next()?row.getLong(1):data.getCredits();}}}).thenAccept(balance->onMain(()->{if(plugin.getPlayerManager().get(id)==data)data.setCredits(Math.max(0,balance));}));}
    private void onMain(Runnable action){if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,action);}private static Throwable root(Throwable error){Throwable current=error;while(current.getCause()!=null)current=current.getCause();return current;}private static final class AlreadyOwnedException extends RuntimeException{}
}
