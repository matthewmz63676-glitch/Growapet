package me.growapet.seasons;

import me.growapet.GrowAPet;
import me.growapet.boosts.BoostType;
import me.growapet.quests.QuestType;
import me.growapet.rewards.RewardBundle;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Data-driven seasonal objective and reward-track service. */
public final class SeasonService {
    private final GrowAPet plugin;
    private final Map<String, SeasonDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Long> activeStarts = new ConcurrentHashMap<>();
    private final Map<String, Long> activeEnds = new ConcurrentHashMap<>();

    public SeasonService(GrowAPet plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        Map<String, SeasonDefinition> candidate = new ConcurrentHashMap<>(definitions);
        ConfigurationSection root = plugin.getConfigManager().seasons().getConfigurationSection("seasons");
        if (root == null) { plugin.getLogger().warning("seasons.yml has no seasons section; retaining the last-known-good definitions."); return; }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id); if (section == null) { plugin.getLogger().warning("Invalid season section " + id); continue; }
            Map<String, Objective> objectives = new java.util.LinkedHashMap<>();
            ConfigurationSection objectiveRoot = section.getConfigurationSection("objectives");
            if (objectiveRoot != null) for (String key : objectiveRoot.getKeys(false)) {
                ConfigurationSection objective = objectiveRoot.getConfigurationSection(key); if (objective == null) continue;
                try { objectives.put(key, new Objective(key, objective.getString("type", "MOB_KILLS").toUpperCase(), Math.max(1, objective.getLong("amount", 1)))); } catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Invalid season objective " + id + "." + key); }
            }
            Map<String, Reward> rewards = new java.util.LinkedHashMap<>();
            ConfigurationSection rewardRoot = section.getConfigurationSection("rewards");
            if (rewardRoot != null) for (String key : rewardRoot.getKeys(false)) {
                ConfigurationSection reward = rewardRoot.getConfigurationSection(key); if (reward == null) continue;
                try { rewards.put(key, Reward.from(reward)); } catch (IllegalArgumentException invalid) { plugin.getLogger().warning("Invalid season reward " + id + "." + key + ": " + invalid.getMessage()); }
            }
            if (objectives.isEmpty() || rewards.isEmpty()) { plugin.getLogger().warning("Season " + id + " has no valid objectives or rewards; retaining the previous definition."); continue; }
            candidate.put(id, new SeasonDefinition(id, section.getString("definition-version", "1"), section.getString("display-name", id), Math.max(1, section.getLong("duration-days", 14)), objectives, rewards, section.getBoolean("enabled", false)));
        }
        if (!candidate.isEmpty()) { definitions.clear(); definitions.putAll(candidate); }
    }

    public CompletableFuture<Void> load() {
        return plugin.getDatabase().async(connection -> {
            Map<String, long[]> active = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT season_id,starts_at,ends_at FROM season_state WHERE state='ACTIVE' AND ends_at>?")) {
                statement.setLong(1, System.currentTimeMillis()); try (ResultSet rows = statement.executeQuery()) { while (rows.next()) { SeasonDefinition definition = definitions.get(rows.getString(1)); if (definition != null && definition.enabled()) active.put(rows.getString(1), new long[]{rows.getLong(2), rows.getLong(3)}); } }
            }
            return active;
        }).thenAccept(active -> { activeStarts.clear(); activeEnds.clear(); active.forEach((id, window) -> { activeStarts.put(id, window[0]); activeEnds.put(id, window[1]); }); });
    }

    public CompletableFuture<Boolean> start(String id, long durationDays) {
        SeasonDefinition definition = definitions.get(id); if (definition == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown season"));
        if (!definition.enabled()) return CompletableFuture.failedFuture(new IllegalStateException("Season is disabled in seasons.yml"));
        long start = System.currentTimeMillis(), end = start + Math.max(1, Math.min(90, durationDays)) * 86_400_000L;
        String checksum = checksum(definition);
        return plugin.getDatabase().async(connection -> { try (PreparedStatement statement = connection.prepareStatement("INSERT INTO season_state(season_id,definition_version,starts_at,ends_at,state,checksum,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(season_id) DO UPDATE SET starts_at=excluded.starts_at,ends_at=excluded.ends_at,state='ACTIVE',checksum=excluded.checksum,updated_at=excluded.updated_at")) { statement.setString(1,id);statement.setString(2,definition.version);statement.setLong(3,start);statement.setLong(4,end);statement.setString(5,"ACTIVE");statement.setString(6,checksum);statement.setLong(7,start);statement.executeUpdate(); } return null; }).thenApply(ignored -> { activeStarts.put(id, start); activeEnds.put(id, end); return true; });
    }

    public CompletableFuture<Void> stop(String id) { return plugin.getDatabase().async(connection -> { try (PreparedStatement statement=connection.prepareStatement("UPDATE season_state SET state='ENDED',updated_at=? WHERE season_id=?")){statement.setLong(1,System.currentTimeMillis());statement.setString(2,id);statement.executeUpdate();} return null; }).thenRun(() -> { activeStarts.remove(id); activeEnds.remove(id); }); }
    public boolean isActive(String id) { Long end=activeEnds.get(id); return end!=null&&end>System.currentTimeMillis(); }
    public long startsAt(String id) { return activeStarts.getOrDefault(id, 0L); }
    public long endsAt(String id) { return activeEnds.getOrDefault(id, 0L); }
    public List<String> ids() { return List.copyOf(definitions.keySet()); }
    public SeasonDefinition definition(String id) { return definitions.get(id); }

    public boolean validate(String id) {
        SeasonDefinition season = definitions.get(id);
        if (season == null || season.version().isBlank() || season.durationDays() < 1 || season.objectives().isEmpty() || season.rewards().isEmpty()) return false;
        return season.objectives().values().stream().allMatch(objective -> objective.amount() > 0 && objective.type() != null && !objective.type().isBlank());
    }

    public String preview(String id) {
        SeasonDefinition season = definitions.get(id);
        if (season == null) return "<red>Unknown season.</red>";
        return "<gold><bold>" + safe(season.displayName()) + "</bold></gold> <dark_gray>•</dark_gray> <gray>" + season.objectives().size() + " objectives, " + season.rewards().size() + " rewards, " + season.durationDays() + " days.</gray>";
    }

    public CompletableFuture<Integer> reconcile(String id) {
        if (!definitions.containsKey(id)) return CompletableFuture.completedFuture(0);
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM season_claims WHERE season_id=?")) {
                statement.setString(1, id); try (ResultSet row = statement.executeQuery()) { return row.next() ? row.getInt(1) : 0; }
            }
        });
    }

    public void record(UUID playerId, String type, long amount) {
        if (amount <= 0 || activeEnds.isEmpty()) return;
        String normalized = type.toUpperCase(); long now = System.currentTimeMillis();
        for (String seasonId : activeEnds.keySet()) {
            SeasonDefinition season = definitions.get(seasonId); if (season == null || !isActive(seasonId)) continue;
            for (Objective objective : season.objectives.values()) if (objective.type.equals(normalized)) {
                plugin.getDatabase().async(connection -> { try (PreparedStatement statement=connection.prepareStatement("INSERT INTO season_progress(season_id,player_uuid,objective_id,progress,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(season_id,player_uuid,objective_id) DO UPDATE SET progress=MIN(excluded.progress+season_progress.progress,?),updated_at=excluded.updated_at")){statement.setString(1,seasonId);statement.setString(2,playerId.toString());statement.setString(3,objective.id);statement.setLong(4,amount);statement.setLong(5,now);statement.setLong(6,objective.amount);statement.executeUpdate();} return null; }).exceptionally(error->{plugin.getLogger().warning("Season progress write failed: "+error.getMessage());return null;});
            }
        }
    }

    public CompletableFuture<Boolean> claim(Player player, String seasonId, String rewardId) {
        SeasonDefinition season = definitions.get(seasonId); Reward reward = season == null ? null : season.rewards.get(rewardId);
        if (season == null || reward == null || !isActive(seasonId)) return CompletableFuture.completedFuture(false);
        UUID playerId = player.getUniqueId(); String receipt = "season:" + seasonId + ":" + playerId + ":" + rewardId;
        return readyToClaim(playerId, seasonId, season, rewardId).thenCompose(ready -> {
            if (!ready) return CompletableFuture.completedFuture(false);
            return plugin.getRewardFulfilmentService().fulfilGuarded(receipt, "SEASON:" + seasonId, playerId, reward.bundle(), connection -> {
                try (PreparedStatement claim = connection.prepareStatement("INSERT OR IGNORE INTO season_claims(season_id,player_uuid,reward_id,claimed_at) VALUES(?,?,?,?)")) {
                    claim.setString(1, seasonId); claim.setString(2, playerId.toString()); claim.setString(3, rewardId); claim.setLong(4, System.currentTimeMillis());
                    return claim.executeUpdate() == 1;
                }
            });
        }).whenComplete((claimed,error)->{if(Boolean.TRUE.equals(claimed)&&player.isOnline())Bukkit.getScheduler().runTask(plugin,()->Messages.send(player,"<green>Season reward claimed.</green>"));});
    }

    private CompletableFuture<Boolean> readyToClaim(UUID playerId, String seasonId, SeasonDefinition season, String rewardId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement claimed = connection.prepareStatement("SELECT 1 FROM season_claims WHERE season_id=? AND player_uuid=? AND reward_id=?")) {
                claimed.setString(1, seasonId); claimed.setString(2, playerId.toString()); claimed.setString(3, rewardId);
                try (ResultSet row = claimed.executeQuery()) { if (row.next()) return false; }
            }
            for (Objective objective : season.objectives.values()) { try (PreparedStatement statement=connection.prepareStatement("SELECT progress FROM season_progress WHERE season_id=? AND player_uuid=? AND objective_id=?")){statement.setString(1,seasonId);statement.setString(2,playerId.toString());statement.setString(3,objective.id);try(ResultSet row=statement.executeQuery()){if(!row.next()||row.getLong(1)<objective.amount)return false;}} } return true;
        });
    }

    private static String safe(String value) { return value == null ? "" : value.replace("<", "‹").replace(">", "›"); }

    private static String checksum(SeasonDefinition definition) {
        String canonical = definition.id + "|" + definition.version + "|" + definition.displayName + "|" + definition.durationDays + "|" + definition.objectives + "|" + definition.rewards;
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Could not checksum season definition", error); }
    }

    public CompletableFuture<List<ObjectiveView>> views(UUID playerId, String seasonId) {
        SeasonDefinition season = definitions.get(seasonId); if (season == null) return CompletableFuture.completedFuture(List.of());
        return plugin.getDatabase().async(connection -> { List<ObjectiveView> result=new ArrayList<>(); for(Objective objective:season.objectives.values()){long progress=0;try(PreparedStatement statement=connection.prepareStatement("SELECT progress FROM season_progress WHERE season_id=? AND player_uuid=? AND objective_id=?")){statement.setString(1,seasonId);statement.setString(2,playerId.toString());statement.setString(3,objective.id);try(ResultSet row=statement.executeQuery()){if(row.next())progress=row.getLong(1);}}result.add(new ObjectiveView(objective.id,objective.type,progress,objective.amount));}return result; });
    }

    public CompletableFuture<Boolean> claimed(UUID playerId, String seasonId, String rewardId) {
        return plugin.getDatabase().async(connection -> { try (PreparedStatement statement=connection.prepareStatement("SELECT 1 FROM season_claims WHERE season_id=? AND player_uuid=? AND reward_id=?")){statement.setString(1,seasonId);statement.setString(2,playerId.toString());statement.setString(3,rewardId);try(ResultSet row=statement.executeQuery()){return row.next();}} });
    }

    public record SeasonDefinition(String id,String version,String displayName,long durationDays,Map<String,Objective> objectives,Map<String,Reward> rewards,boolean enabled) { }
    public record Objective(String id,String type,long amount) { }
    public record ObjectiveView(String id,String type,long progress,long amount) { }
    public record Reward(String id,RewardBundle bundle) {
        static Reward from(ConfigurationSection section) { String id=section.getName(),kind=section.getString("kind", "CREDITS").toUpperCase(); return switch(kind){case"COINS"->new Reward(id,RewardBundle.currency(section.getLong("amount",0),0,0));case"GEMS"->new Reward(id,RewardBundle.currency(0,section.getLong("amount",0),0));case"CREDITS"->new Reward(id,RewardBundle.currency(0,0,section.getLong("amount",0)));case"COSMETIC_TAG"->new Reward(id,new RewardBundle("1",0,0,0,List.of(new RewardBundle.Entitlement("cosmetic.tag."+section.getString("value",id),"CHAT_TAG",section.getString("value",id))),List.of()));case"BOOST"->new Reward(id,new RewardBundle("1",0,0,0,List.of(),List.of(new RewardBundle.BoostReward(id,BoostType.valueOf(section.getString("boost-type","MOB_EXP").toUpperCase()),section.getDouble("bonus",0.25),section.getLong("duration-minutes",30)))));default->throw new IllegalArgumentException("Unknown season reward kind "+kind);}; }
    }
}
