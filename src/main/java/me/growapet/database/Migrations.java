package me.growapet.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

final class Migrations {
    private Migrations() {}

    static List<Migration> all() {
        return List.of(
                new Migration(1, Migrations::legacySchema),
                new Migration(2, Migrations::durableGameplaySchema),
                new Migration(3, Migrations::optionalSystemsSchema),
                new Migration(4, Migrations::tradeEscrowSchema),
                new Migration(5, Migrations::creditProgressionSchema),
                new Migration(6, Migrations::durableBossSchema),
                new Migration(7, Migrations::durableMobRespawnSchema),
                new Migration(8, Migrations::tutorialProgressSchema),
                new Migration(9, Migrations::persistentMobSpawnSchema),
                new Migration(10, Migrations::laterIntegrationsSchema),
                new Migration(11, Migrations::laterIntegrationsHardeningSchema),
                new Migration(12, Migrations::discordLinkReuseSchema),
                new Migration(13, Migrations::petcoreModulesSchema)
        );
    }

    private static void legacySchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, coins INTEGER NOT NULL DEFAULT 0, gems INTEGER NOT NULL DEFAULT 0, credits INTEGER NOT NULL DEFAULT 0, level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, exp_multiplier REAL NOT NULL DEFAULT 1.0, coin_multiplier REAL NOT NULL DEFAULT 1.0, gem_multiplier REAL NOT NULL DEFAULT 1.0, damage_multiplier REAL NOT NULL DEFAULT 1.0, critical_chance REAL NOT NULL DEFAULT 0.0, critical_damage REAL NOT NULL DEFAULT 1.5, mob_kills INTEGER NOT NULL DEFAULT 0, boss_kills INTEGER NOT NULL DEFAULT 0, eggs_hatched INTEGER NOT NULL DEFAULT 0, pets_collected INTEGER NOT NULL DEFAULT 0, trades INTEGER NOT NULL DEFAULT 0, quests_completed INTEGER NOT NULL DEFAULT 0, playtime_seconds INTEGER NOT NULL DEFAULT 0, active_pet_uuid TEXT)",
                "CREATE TABLE IF NOT EXISTS pets (uuid TEXT PRIMARY KEY, owner TEXT NOT NULL, entity_type TEXT NOT NULL, display_name TEXT NOT NULL, rarity TEXT NOT NULL, size TEXT, level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, damage_multiplier REAL NOT NULL DEFAULT 1.0, coin_multiplier REAL NOT NULL DEFAULT 1.0, gem_multiplier REAL NOT NULL DEFAULT 1.0, skin TEXT, equipped INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS plots (owner TEXT PRIMARY KEY, id INTEGER UNIQUE NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, size INTEGER NOT NULL DEFAULT 32, pet_limit INTEGER NOT NULL DEFAULT 5, egg_limit INTEGER NOT NULL DEFAULT 3)"
        );
        addColumn(connection, "players", "unlocked_zones", "TEXT NOT NULL DEFAULT ''");
        addColumn(connection, "players", "shop_levels", "TEXT NOT NULL DEFAULT ''");
        addColumn(connection, "pets", "size_int", "INTEGER NOT NULL DEFAULT 1");
    }

    private static void durableGameplaySchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS incubating_eggs (world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, owner TEXT NOT NULL, entity_type TEXT NOT NULL, total_seconds INTEGER NOT NULL, hatch_at INTEGER NOT NULL, PRIMARY KEY(world,x,y,z))",
                "CREATE TABLE IF NOT EXISTS economy_transactions (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, kind TEXT NOT NULL, coins_delta INTEGER NOT NULL DEFAULT 0, gems_delta INTEGER NOT NULL DEFAULT 0, credits_delta INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS player_zones (player_uuid TEXT NOT NULL, zone_id TEXT NOT NULL, unlocked_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, zone_id))",
                "CREATE TABLE IF NOT EXISTS player_shop_upgrades (player_uuid TEXT NOT NULL, upgrade_id TEXT NOT NULL, level INTEGER NOT NULL, PRIMARY KEY(player_uuid, upgrade_id))",
                "CREATE TABLE IF NOT EXISTS settings (player_uuid TEXT NOT NULL, setting_key TEXT NOT NULL, setting_value TEXT NOT NULL, PRIMARY KEY(player_uuid, setting_key))",
                "CREATE TABLE IF NOT EXISTS admin_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, action TEXT NOT NULL, target TEXT, details TEXT, created_at INTEGER NOT NULL)"
        );
        addColumn(connection, "pets", "entity_uuid", "TEXT");
        addColumn(connection, "pets", "world", "TEXT");
        addColumn(connection, "pets", "x", "REAL");
        addColumn(connection, "pets", "y", "REAL");
        addColumn(connection, "pets", "z", "REAL");
    }

    private static void optionalSystemsSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS quest_progress (player_uuid TEXT NOT NULL, quest_key TEXT NOT NULL, period_key TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(player_uuid, quest_key, period_key))",
                "CREATE TABLE IF NOT EXISTS trades (trade_id TEXT PRIMARY KEY, player_one TEXT NOT NULL, player_two TEXT NOT NULL, state TEXT NOT NULL, payload TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, completed_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS event_state (event_id TEXT PRIMARY KEY, event_type TEXT NOT NULL, starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL, multiplier REAL NOT NULL DEFAULT 1.0, enabled INTEGER NOT NULL DEFAULT 1)",
                "CREATE TABLE IF NOT EXISTS boss_history (boss_run_id TEXT PRIMARY KEY, boss_id TEXT NOT NULL, killed_at INTEGER NOT NULL, rankings TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS boss_state (boss_id TEXT PRIMARY KEY, next_spawn_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS item_deliveries (id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, item_type TEXT NOT NULL, item_data TEXT NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_players_coins ON players(coins DESC)",
                "CREATE INDEX IF NOT EXISTS idx_pets_owner ON pets(owner)",
                "CREATE INDEX IF NOT EXISTS idx_quests_player ON quest_progress(player_uuid)"
        );
        addColumn(connection, "players", "pet_power", "REAL NOT NULL DEFAULT 0");
        addColumn(connection, "players", "boss_damage", "REAL NOT NULL DEFAULT 0");
        addColumn(connection, "players", "highest_pet_level", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void tradeEscrowSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS trade_sessions (trade_id TEXT PRIMARY KEY, player_one TEXT NOT NULL, player_two TEXT NOT NULL, state TEXT NOT NULL, first_confirmed INTEGER NOT NULL DEFAULT 0, second_confirmed INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS trade_escrow (trade_id TEXT NOT NULL, owner_uuid TEXT NOT NULL, asset_type TEXT NOT NULL, asset_id TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0, payload TEXT, state TEXT NOT NULL DEFAULT 'ACTIVE', PRIMARY KEY(trade_id, owner_uuid, asset_type, asset_id))",
                "CREATE TABLE IF NOT EXISTS trade_request_cooldowns (player_uuid TEXT PRIMARY KEY, last_request_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_trade_sessions_state ON trade_sessions(state)",
                "CREATE INDEX IF NOT EXISTS idx_trade_escrow_trade ON trade_escrow(trade_id)",
                "CREATE INDEX IF NOT EXISTS idx_deliveries_player ON item_deliveries(player_uuid)"
        );
        addColumn(connection, "pets", "trade_lock_id", "TEXT");
    }

    private static void creditProgressionSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS daily_claims (player_uuid TEXT PRIMARY KEY, last_claim_at INTEGER NOT NULL, claim_count INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS credit_grants (receipt_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, credits INTEGER NOT NULL, source TEXT NOT NULL, granted_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS plot_boosts (boost_id TEXT PRIMARY KEY, owner_uuid TEXT NOT NULL, boost_type TEXT NOT NULL, bonus REAL NOT NULL, starts_at INTEGER NOT NULL, expires_at INTEGER, source TEXT NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_credit_grants_player ON credit_grants(player_uuid)"
                ,"CREATE INDEX IF NOT EXISTS idx_plot_boosts_owner ON plot_boosts(owner_uuid,expires_at)"
        );
    }

    private static void durableBossSchema(Connection connection) throws SQLException {
        addColumn(connection, "boss_state", "active", "INTEGER NOT NULL DEFAULT 0");
        addColumn(connection, "boss_state", "run_id", "TEXT");
        addColumn(connection, "boss_state", "health", "REAL");
        addColumn(connection, "boss_state", "damage_data", "TEXT");
        addColumn(connection, "boss_state", "updated_at", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void durableMobRespawnSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS mob_respawns (respawn_id TEXT PRIMARY KEY, mob_id TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL DEFAULT 0, pitch REAL NOT NULL DEFAULT 0, due_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_mob_respawns_due ON mob_respawns(due_at)"
        );
    }

    private static void tutorialProgressSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS tutorial_progress (player_uuid TEXT PRIMARY KEY, stage TEXT NOT NULL DEFAULT 'MOB', updated_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_tutorial_stage ON tutorial_progress(stage)"
        );
    }

    private static void persistentMobSpawnSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS mob_spawn_points (spawn_id TEXT PRIMARY KEY, mob_id TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL DEFAULT 0, pitch REAL NOT NULL DEFAULT 0, zone_id TEXT NOT NULL DEFAULT '', max_count INTEGER NOT NULL DEFAULT 1, enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_mob_spawn_points_zone ON mob_spawn_points(zone_id)");
        addColumn(connection, "mob_respawns", "spawn_point_id", "TEXT");
        execute(connection, "CREATE INDEX IF NOT EXISTS idx_mob_respawns_spawn_point ON mob_respawns(spawn_point_id)");
    }

    /** Durable boundaries for the later Discord, commerce, seasonal, and shop-migration systems. */
    private static void laterIntegrationsSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS discord_links (player_uuid TEXT PRIMARY KEY, discord_id TEXT NOT NULL UNIQUE, discord_name TEXT NOT NULL DEFAULT '', linked_at INTEGER NOT NULL, unlinked_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS discord_link_tokens (token_hash TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, expires_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, consumed_at INTEGER, created_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_discord_tokens_player ON discord_link_tokens(player_uuid,expires_at)",
                "CREATE TABLE IF NOT EXISTS reward_receipts (receipt_id TEXT PRIMARY KEY, origin TEXT NOT NULL, player_uuid TEXT NOT NULL, bundle_version TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, reversed_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS player_entitlements (player_uuid TEXT NOT NULL, entitlement_id TEXT NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,entitlement_id))",
                "CREATE TABLE IF NOT EXISTS entitlement_sources (receipt_id TEXT NOT NULL, player_uuid TEXT NOT NULL, entitlement_id TEXT NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, created_at INTEGER NOT NULL, revoked_at INTEGER, PRIMARY KEY(receipt_id,entitlement_id))",
                "CREATE INDEX IF NOT EXISTS idx_entitlements_player ON player_entitlements(player_uuid,active)",
                "CREATE TABLE IF NOT EXISTS commerce_receipts (receipt_id TEXT PRIMARY KEY, provider TEXT NOT NULL, transaction_id TEXT NOT NULL, package_id TEXT NOT NULL, player_uuid TEXT NOT NULL, quantity INTEGER NOT NULL, status TEXT NOT NULL, verified_at INTEGER NOT NULL, reversed_at INTEGER)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_commerce_transaction_package ON commerce_receipts(provider,transaction_id,package_id)",
                "CREATE TABLE IF NOT EXISTS commerce_receipt_items (receipt_id TEXT NOT NULL, item_index INTEGER NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0, value TEXT NOT NULL DEFAULT '', PRIMARY KEY(receipt_id,item_index))",
                "CREATE TABLE IF NOT EXISTS commerce_reversals (receipt_id TEXT NOT NULL, reason TEXT NOT NULL, provider_status TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(receipt_id,reason))",
                "CREATE TABLE IF NOT EXISTS commerce_debts (player_uuid TEXT NOT NULL, asset_kind TEXT NOT NULL, amount INTEGER NOT NULL, source_receipt TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN', created_at INTEGER NOT NULL, resolved_at INTEGER, PRIMARY KEY(player_uuid,asset_kind,source_receipt))",
                "CREATE INDEX IF NOT EXISTS idx_commerce_debts_player ON commerce_debts(player_uuid,status)",
                "CREATE TABLE IF NOT EXISTS season_state (season_id TEXT PRIMARY KEY, definition_version TEXT NOT NULL, starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL, state TEXT NOT NULL, checksum TEXT NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS season_progress (season_id TEXT NOT NULL, player_uuid TEXT NOT NULL, objective_id TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, PRIMARY KEY(season_id,player_uuid,objective_id))",
                "CREATE TABLE IF NOT EXISTS season_claims (season_id TEXT NOT NULL, player_uuid TEXT NOT NULL, reward_id TEXT NOT NULL, claimed_at INTEGER NOT NULL, PRIMARY KEY(season_id,player_uuid,reward_id))",
                "CREATE INDEX IF NOT EXISTS idx_season_progress_player ON season_progress(player_uuid,season_id)",
                "CREATE TABLE IF NOT EXISTS shop_catalog_versions (catalog_id TEXT PRIMARY KEY, checksum TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS zone_aliases (catalog_id TEXT NOT NULL, legacy_id TEXT NOT NULL, current_id TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(catalog_id,legacy_id))",
                "CREATE TABLE IF NOT EXISTS shop_purchases (catalog_id TEXT NOT NULL, player_uuid TEXT NOT NULL, zone_id TEXT NOT NULL, chain_id TEXT NOT NULL, tier_id TEXT NOT NULL, source TEXT NOT NULL, purchased_at INTEGER NOT NULL, PRIMARY KEY(catalog_id,player_uuid,zone_id,chain_id,tier_id))",
                "CREATE TABLE IF NOT EXISTS shop_migration_batches (batch_id TEXT PRIMARY KEY, from_catalog TEXT NOT NULL, to_catalog TEXT NOT NULL, checksum TEXT NOT NULL, state TEXT NOT NULL, created_at INTEGER NOT NULL, applied_at INTEGER, rolled_back_at INTEGER)",
                "CREATE TABLE IF NOT EXISTS shop_migration_entries (batch_id TEXT NOT NULL, player_uuid TEXT NOT NULL, legacy_data TEXT NOT NULL, generated_data TEXT NOT NULL, coin_remainder INTEGER NOT NULL DEFAULT 0, gem_remainder INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL, PRIMARY KEY(batch_id,player_uuid))",
                "CREATE INDEX IF NOT EXISTS idx_shop_purchases_player ON shop_purchases(player_uuid,catalog_id)",
                "CREATE INDEX IF NOT EXISTS idx_shop_migration_entries_state ON shop_migration_entries(batch_id,state)"
        );
    }

    /** Idempotency records for reversals and owner-bound consumables added after v10 shipped. */
    private static void laterIntegrationsHardeningSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS reward_reversals (receipt_id TEXT PRIMARY KEY, reason TEXT NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS boost_item_claims (delivery_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, boost_id TEXT NOT NULL, activated_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_boost_item_claims_player ON boost_item_claims(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_reward_receipts_player ON reward_receipts(player_uuid,created_at)");
        addColumn(connection, "event_state", "definition_version", "TEXT NOT NULL DEFAULT '1'");
        addColumn(connection, "event_state", "definition_checksum", "TEXT NOT NULL DEFAULT ''");
        addColumn(connection, "event_state", "stacking_policy", "TEXT NOT NULL DEFAULT 'HIGHEST'");
        addColumn(connection, "event_state", "lifecycle_state", "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumn(connection, "event_state", "updated_at", "INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * The v10 table retained a table-wide UNIQUE constraint on discord_id. Keep
     * historical links for audit, but move inactive IDs to an archival value so
     * a player can safely link that Discord account again after /unlink.
     */
    private static void discordLinkReuseSchema(Connection connection) throws SQLException {
        addColumn(connection, "discord_links", "unlinked_discord_id", "TEXT");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE discord_links SET unlinked_discord_id=discord_id,discord_id='unlinked:'||player_uuid||':'||unlinked_at WHERE unlinked_at IS NOT NULL AND unlinked_discord_id IS NULL");
        }
    }

    private static void petcoreModulesSchema(Connection connection) throws SQLException {
        execute(connection,
                "CREATE TABLE IF NOT EXISTS money_spent (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, amount_cents INTEGER NOT NULL DEFAULT 0 CHECK(amount_cents>=0), updated_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_money_spent_amount ON money_spent(amount_cents DESC,player_name ASC)",
                "CREATE TABLE IF NOT EXISTS custom_tags (tag_id TEXT PRIMARY KEY, display_name TEXT NOT NULL, markup TEXT NOT NULL, created_by TEXT NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS custom_tag_tokens (token TEXT PRIMARY KEY, tag_id TEXT NOT NULL, player_uuid TEXT NOT NULL, redeemed_at INTEGER, created_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_custom_tag_tokens_player ON custom_tag_tokens(player_uuid,redeemed_at)",
                "CREATE TABLE IF NOT EXISTS store_purchases (receipt_id TEXT PRIMARY KEY, buyer_uuid TEXT NOT NULL, recipient_uuid TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 1 CHECK(quantity>0), credits_spent INTEGER NOT NULL DEFAULT 0 CHECK(credits_spent>=0), purchased_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS idx_store_purchases_recipient ON store_purchases(recipient_uuid,purchased_at)");
    }

    private static void execute(Connection connection, String... statements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.executeUpdate(sql);
        }
    }

    private static void addColumn(Connection connection, String table, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
