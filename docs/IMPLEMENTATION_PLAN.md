# GrowAPet Rescue and Controlled v2 Evolution

## Executive summary

GrowAPet should be rescued in place rather than replaced. The current repository contains useful gameplay content, configuration, identifiers, and a three-table SQLite schema, but its foundations are not yet safe enough for a live economy or a long-running progression server. The work should begin with a tightly scoped Phase 0 that restores a reproducible build, protects existing SQLite data, establishes correct thread boundaries, and closes inventory and reward exploits. Major gameplay systems should be completed only after those gates pass.

The existing Java package `me.growapet` remains canonical. The current 11-zone progression remains the Core MVP target. Petcore is a design-pattern reference only, and the separate 10-zone shop proposal is a later migration that must not be folded into the rescue.

## Implementation and verification update — 2026-08-03

The rescue, Core MVP, and optional phases through Phase 7 have now been implemented in place. The pre-implementation assessment and acceptance plan below are retained as the audit trail that drove the work; they no longer describe the current source state. Per direct client instruction, GrowAPet's TAB and scoreboard modules were removed. The lightweight configurable action bar remains. The separate Phase 8 ten-zone shop migration and unrelated Petcore-only systems remain intentionally excluded.

| Scope | Implemented result | Verification state |
|---|---|---|
| Phase 0A | Official packaged SQLite JDBC replaced the incomplete vendored CFR copy; compile defects were repaired; JUnit 5 and shading were added. | Clean Java 21 compilation and SQLite driver test pass. |
| Phase 0B | Single-owner SQLite executor, readiness state, ordered async lifecycle, WAL/busy policy, backups, transactional versioned migrations, propagated errors, immutable save snapshots, and legacy-compatible normalized rows. | Migration compatibility and driver tests pass. A production-data staging restore drill is still required. |
| Phase 0C | Exact menu sessions, deny-first click/drag handling, transaction receipts, per-player economy serialization, rollback paths, safe item delivery, and granular admin permissions/auditing. | Compilation and unit tests pass. The full live survival/creative packet-click matrix is still required. |
| Phase 0D | Durable wall-clock incubation, main-thread Bukkit handoffs, trusted PDC mob/boss identities, one-time rewards, stale-entity cleanup, safer reload, and bounded tasks. | Automated persistence/config checks pass. Restart/chunk/crash behavior still needs Paper staging tests. |
| Core MVP (Phases 1–4) | Stats/PAPI aliases/XP sync, plot protection and upgrades, pet hatching/equip/entities/XP, configurable mob damage and rewards, ordered zones/warps/walls, boss timers/skills/rank rewards, leaderboards, current shops, and cosmetic-only Credit store. | Code compiles and pure/database/config tests pass. World coordinates and gameplay balance require client staging. |
| Optional systems (Phases 5–7) | Daily/weekly/story quests, non-Credit two-party trading with confirmation invalidation and durable deliveries, all nine event types, action-bar/trade options, chat colors/tags, and audited admin controls. | Code compiles and persistence schemas are covered. Multi-player live tests remain mandatory before production enablement. |
| Removed/excluded | TAB and scoreboard removed. No Petcore package/code was copied. Petcore-only teams/Discord/heads/milestones and the future ten-zone shop migration were not implemented. | Static source audit confirms no TAB/scoreboard implementation references. |

Latest required test gate: `mvn clean test` using Java 21.0.11 and Maven 3.9.16 compiled 86 main sources and four test sources, then ran six tests with zero failures, errors, or skips (**BUILD SUCCESS**, 8.098 seconds). A successful Maven result verifies compilation and the covered automated behavior only; it is not a substitute for the live-server tests listed per phase.

Latest packaging gate: `mvn clean package` reran all six tests and completed with **BUILD SUCCESS** in 11.456 seconds. The resulting shaded `growapet-1.0.0.jar` is 13,803,394 bytes with SHA-256 `90265E12D4D2A24BF089DF89D42E1E8F1025C2953582CBBC59D514CD264E51A8`. Inspection confirms `plugin.yml`, the SQLite driver, and Windows/Linux/macOS native libraries are present; SLF4J, TAB, scoreboard, and removed HUD classes are not embedded.

## Audit baseline and build result

- Repository: `matthewmz63676-glitch/Growapet`
- Audited branch and commit: `main` at `11625a1`
- Source shape: all 134 Java files contain CFR 0.152 decompiler headers; 60 of those files are a vendored copy of SQLite JDBC under `me/growapet/libs/sqlite`.
- Automated tests: none; there is no `src/test` tree.
- Build command run during the audit: `mvn clean package`
- Environment: Java 21.0.11 and Maven 3.9.9
- Exact result: **BUILD FAILURE**, exit code 1, after 8.432 seconds.
- Compiler result: Maven attempted to compile 134 source files and reported seven errors from `src/main/java/me/growapet/libs/sqlite/nativeimage/SqliteJdbcFeature.java`. The missing types are from `org.graalvm.nativeimage.hosted`, including `Feature`.

This historical result contradicted the premise that the original checked-out `main` built. It is retained as the rescue baseline; the implementation update above records the subsequent clean automated result. No runtime system should be described as production-proven merely because the later build succeeds, so the focused live-server verification below remains required.

## Source reconciliation

The sources are applied in the requested priority order:

1. This rescue brief controls sequencing, safety constraints, and scope. It explicitly prevents a replacement plugin, a package-only rename, premature Petcore expansion, and the 10-zone migration.
2. `GrowAPet-Spec.md` defines the intended core loop and long-term GrowAPet identity. Its old package suggestion, `me.stringclient.growapet`, does not override the current `me.growapet` package.
3. The repository controls compatibility. Existing command names, PDC keys, YAML content, `database.db`, UUIDs, plot locations, balances, unlocks, and pet rows should be retained or migrated explicitly.
4. `Petcore-Reference.md` may inform patterns such as atomic YAML writes, versioned stores, GUI denial/resynchronization, transactional purchases, main-thread handoffs, and bounded entity management. Its `me.petcore` classes and its unrelated piles, teams, Discord, rebirth, moderation, and other systems do not exist here and are not implicit requirements.
5. `Shop-Plan.md` describes a future Plains-to-Mushroom-Island shop progression based on Petcore classes that GrowAPet does not have. The current GrowAPet MVP retains its existing 11 zones: Spawn, Forest, Plains, Mushroom, Desert, Badlands, Snow, Deep Caves, Nether, End, and Ancient Realm. The 10-zone plan requires a separate mapping, content, and economy-balancing milestone.

Two product conflicts require explicit client decisions. First, the current Store converts Credits into coins, gems, or a randomized gameplay crate, while the master specification says monetization must be cosmetic-only and never pay-to-win. Second, the spec describes multiple equipped pets, while the current player row has one `active_pet_uuid` and combat uses only the first pet marked equipped. Neither should be resolved by assumption.

## Current-state assessment

| System | Status | Evidence and gaps |
|---|---|---|
| Player data, stats, and XP | **Incomplete and unsafe** | `PlayerData`, `PlayerDAO`, `/stats`, XP leveling, and Minecraft XP-bar synchronization exist. Setters do not consistently mark data dirty, negative/admin values are not validated, load completion can race gameplay and disconnects, and save futures are not awaited or otherwise durably sequenced before connection close. Playtime is stored but not updated. |
| PlaceholderAPI | **Partial** | An optional expansion serves cached online data. Offline/unloaded players return empty values. Current keys such as `damage`, `coinmulti`, and `gemsmulti` differ from spec names such as `damage_multiplier`, `coin_multiplier`, and `gem_multiplier`; compatible aliases are needed. |
| SQLite persistence | **Unsafe** | A single JDBC connection is passed to a two-thread executor, initialization performs file/schema I/O synchronously, migration errors are silently swallowed, operations return `null` on SQL failure, and shutdown closes without an ordered drain. There is no schema-version table, backup, WAL/busy policy, or migration test. The vendored driver currently blocks the build. |
| Plot ownership and protection | **Partial and unsafe** | A dedicated flat world, grid allocation, persisted plot rows, `/plot home`, `/visit`, containment, and limited block/bucket/hanging protection exist. Plot loading calls `Bukkit.getWorld` from a database thread; startup can create replacement plots before asynchronous loading finishes. World, size, and limits are hardcoded instead of honoring configuration. Upgrades are absent, containment ignores Y, and containers, entities, explosions, pistons, fluids, fire, vehicles, and several interaction paths are unprotected. |
| Eggs and incubation | **Unsafe prototype** | Egg PDC, plot-only placement, slot counts, visual cracking, and hatching exist. Active incubation is memory-only: restart or disable consumes the item, cancels the task, and leaves an untracked turtle-egg block. Persistence is not committed before inventory consumption, and chunk/world lifecycle, invalid durations, explosions, and several block-change paths are not handled. |
| Pets, equipping, progression, entities | **Mostly incomplete** | Pet UUIDs, owners, rarity, numeric size, level/EXP fields, multipliers, and rows exist. Hatching saves a pet, but there is no complete pet inventory/menu, equip operation, equip-limit enforcement, pet entity spawning/reconstruction, plot confinement, pet XP award path, or durable placed state. `pets.yml` weights are not used. |
| Configurable mobs and rewards | **Partial and exploitable** | `mobs.yml`, custom health, no-AI mobs, holograms, damage multipliers, configured rewards, and respawn-after-plugin-kill exist. Any unrelated vanilla mob death can award configured or fallback rewards, disabled entries can still reward, plugin mob identity is not durable, and a restart turns persistent custom mobs into untracked vanilla entities. Configured damage, zone enforcement, spawn locations, and robust cleanup are absent. |
| Zones, warps, and walls | **Partial** | Zones load from YAML, unlocks persist in a CSV player column, warps and fake wall gates exist, and costs/level requirements can be checked. Coordinates are documented placeholders. Players can buy any named zone without predecessor enforcement, reload does not rebuild every dependent cache safely, and protection is limited to configured wall cuboids. |
| Bosses | **Prototype** | Manual configured spawning, damage collection, and reward definitions exist. The decompiled reward switch is damaged. Respawn timers, skills, broadcasts, persistent state, trusted entity identity, offline rewards, and durable damage leaderboards are absent. The configured boss-zone lineup also follows the future 10-zone theme rather than the current zone file. |
| Leaderboards | **Partial** | `/leaderboard` performs an asynchronous database query and merges cached online values on the main thread. There are no persistent holograms, refresh service, offline-name policy, pagination, or failure handling when the database future returns `null`. |
| Menus | **Unsafe** | A holder-based `Menu` and `MenuListener` exist, but `MenuListener` is not registered in `GrowAPet#onEnable`. Menu items can therefore be interacted with as ordinary inventory contents. Even after registration, the full click, drag, shift-click, hotbar swap, double-click, creative, double-open, and close matrix requires explicit tests and session cleanup. |
| Shops and currencies | **Unsafe prototype** | Gear, tools, multipliers, coins, gems, credits, and shop-level persistence exist. Purchases mutate only cached state and have no durable transaction/idempotency boundary or audit record. Configuration costs can be negative, arithmetic is not overflow-safe, and current catalog/balance values are hardcoded. |
| Quests, trading, and options | **Absent** | Their commands are wired to `StubCommand`. `quests.yml` is only an intended schema. Trading being absent is safer than exposing a partial, exploitable implementation. |
| Events and cosmetics | **Absent/minimal** | Scheduled gameplay events do not exist. Cosmetics are limited to chat color and a LuckPerms prefix; chat tags and cosmetic management are absent. |
| Admin commands and permissions | **Incomplete** | Several admin actions check the single `growapet.admin` node. Amount validation, offline targeting, auditing, tab-completion privacy, per-capability permissions, and safe reload orchestration are incomplete. `/growapet give` is a stub. |
| Configuration management | **Partial** | Ten YAML files are copied and defaults are attached in memory. Missing defaults are not necessarily written back, invalid keys are not validated as a complete model, and `/growapet reload` reloads files without consistently rebuilding managers/caches. Useful existing content should be preserved. |
| HUD and chat | **Partial** | Configurable scoreboard, action bar, tab display, rank prefix, and chat color exist. HUD work runs frequently for every player, and deprecated asynchronous chat handling calls a hook that checks Bukkit plugin state; thread-safety and update cost need review. |

## Major file and package classification

| Area | Classification | Direction |
|---|---|---|
| `GrowAPet`, commands, listeners | **Adapt** | Retain composition and command names, but add readiness gates, lifecycle-safe wiring, validation, granular permissions, and complete registration. |
| `database` | **Rewrite** | Keep `database.db` and its existing data. Replace connection ownership, error propagation, migrations, backup, and shutdown sequencing. |
| `models` | **Adapt** | Preserve UUIDs and persisted meanings while adding invariants, overflow/negative guards, and separation from Bukkit objects. |
| `plot/PlotManager` | **Rewrite manager; reuse model/data** | Preserve plot rows and coordinates. Split SQL records from main-thread world resolution and add upgrade/protection services. |
| `eggs`, `pets` | **Rewrite managers; reuse PDC/model concepts** | Add durable incubation, validated definitions, inventory/equip services, entity lifecycle, progression, and idempotent reconstruction. |
| `gui`, `shop`, `store` | **Rewrite security and transaction layer; reuse presentation/content** | Preserve useful item presentation and current tiers while using exact menu sessions, unconditional denial, explicit allowed actions, and atomic durable purchases. |
| `mobs`, `zones`, `bosses` | **Adapt** | Preserve YAML content while adding validation, trusted PDC identity, persistent state where needed, progression gates, and bounded tasks. |
| `hud`, `placeholders`, `integration` | **Adapt** | Preserve the visual content and optional integrations while adding aliases, safe hooks, modern chat handling, and delta-based refreshes. |
| `src/main/resources/*.yml` | **Reuse/adapt** | Never overwrite live values. Merge defaults safely, validate on load, and retain current IDs until an approved migration. |
| `me/growapet/libs/sqlite` | **Remove** | Replace the incomplete CFR copy with the official SQLite JDBC artifact packaged in the plugin JAR. The dependency is justified by native loading, reproducible builds, upstream maintenance, and compatibility with the existing JDBC file format. |
| `managers/PluginManager.java` | **Remove** | Empty unused singleton. |
| `src/main/java/summary.txt` | **Remove** | Decompiler artifact with no build or runtime purpose. |
| `StubCommand` usages | **Remove when replaced** | Do not expose feature commands until their real modules meet acceptance criteria. |
| Tests, migration runner, transaction services | **Missing** | Add during Phase 0 before feature expansion. |
| Quest, trade, event, cosmetic modules | **Missing** | Add only in their optional phases. |

## Phased implementation plan

### Phase 0A — Reproducible foundation

**Goal:** Establish a supportable source and build baseline without changing gameplay or the SQLite file format.

**Acceptance criteria:**

- Replace the vendored SQLite sources with the official `org.xerial:sqlite-jdbc` runtime artifact and package the driver plus its native resources in the plugin JAR. Do not minimize away native resources or assume the server supplies the driver.
- Keep the JDBC URL and `plugins/GrowAPet/database.db` path unchanged.
- Repair only confirmed CFR compile damage and remove decompiler-only artifacts; do not use this ticket for behavior refactors.
- Add JUnit Jupiter as a test-only dependency. Its justification is the absence of any regression suite for persistence and pure business rules.
- Client-run `mvn clean verify` succeeds on Java 21, and a test opens a copied existing database without modifying or dropping its tables.
- The produced JAR starts on the supported Paper baseline with PlaceholderAPI and LuckPerms both present and absent according to their optional status.

**Likely files/areas:** `pom.xml`, `Database.java`, known compile-damaged application sources, `me/growapet/libs/sqlite`, decompiler artifacts, and a new test tree.

**Data-migration impact:** None. This phase changes the driver implementation, not the database format or schema.

**Live-server tests:** Boot a staging server on Windows and the production OS using a copy of `database.db`; verify native driver loading, plugin enable/disable, existing player lookup, and optional-hook behavior.

### Phase 0B — Database and lifecycle safety

**Goal:** Make persistence ordered, observable, non-blocking to the main thread, and safely migratable.

**Acceptance criteria:**

- A single dedicated executor owns and exclusively uses the SQLite connection; no connection is shared concurrently across worker threads.
- File opening, backups, schema work, reads, and writes occur off the Paper main thread. Bukkit/Paper objects and APIs are resolved only on the main thread.
- Initialization exposes an explicit readiness state. Commands/listeners that require data fail closed with a clear loading/unavailable message until initialization completes.
- SQL failures complete futures exceptionally and retain context; no failure is converted silently to `null`.
- Add an idempotent `schema_migrations` table and transaction-based migration runner. Each migration records its version only after successful commit.
- Before the first migration of an existing database, create and verify a consistent backup. If backup or migration fails, disable data-mutating features and leave the original database usable.
- Remove destructive `INSERT OR REPLACE` usage where it can delete/recreate rows; use explicit inserts and `ON CONFLICT DO UPDATE` for intended columns.
- Shutdown stops accepting mutations and queues connection close after all accepted work. It does not perform a main-thread database call or close ahead of queued saves.
- Concurrency tests cover join/quit/rejoin, duplicate load requests, write ordering, initialization failure, and shutdown with pending work.

**Likely files/areas:** `Database`, `PlayerDAO`, `PlayerManager`, `GrowAPet`, and new migration, readiness, and database-record classes.

**Data-migration impact:** Add `schema_migrations` only. Preserve all existing players, pets, plots, columns, UUIDs, balances, unlock strings, and shop strings.

**Live-server tests:** Repeated join/quit/rejoin, restart immediately after mutations, disable with queued activity, corrupt/read-only database, backup/restore drill, and main-thread timing monitoring.

### Phase 0C — Inventory, economy, rewards, and permission hardening

**Goal:** Close extraction and duplication paths before any further shop or trade work.

**Acceptance criteria:**

- Register the shared menu listener before any menu can open.
- Identify menus by exact holder/session and inventory instance, not title text. Clear only the exact session that closed so a stale close from a double-open cannot unprotect the active menu.
- For protected menus, deny and cancel every click before dispatch, including top and bottom inventory interactions, shift-click, number-key/hotbar swap, offhand swap, double-click/collect-to-cursor, drop, creative clone, and unknown actions. Cancel drags that touch the top inventory and resynchronize on the next tick.
- Dispatch only explicitly allowed button/click combinations. A packet sequence cannot invoke the same purchase more than once.
- Currency mutation APIs reject negative values, detect overflow, validate finite multipliers, and keep all related deductions/rewards in one durable transaction.
- Add an idempotent receipt/transaction identifier for shop, store, zone, and admin mutations, plus an audit record sufficient to reconcile balances.
- An inventory-capacity failure leaves both balance and inventory unchanged unless the documented action explicitly uses a safe overflow delivery policy.
- Split `growapet.admin` into granular, default-op child permissions while retaining it as an aggregate compatibility parent.

**Likely files/areas:** `gui`, `shop`, `store`, `ZoneManager`, reward/economy services, admin commands, and `plugin.yml`.

**Data-migration impact:** Add an economy receipt/audit table. Existing balances and shop levels remain unchanged.

**Live-server tests:** Execute the complete click/drag matrix in survival and creative, with latency, double-open/close races, a full inventory, repeated clicks, disconnect during purchase, negative/overflow admin input, and every permission boundary.

### Phase 0D — Secure existing gameplay

**Goal:** Remove restart-loss and reward-exploit behavior from already exposed plots, eggs, mobs, bosses, and reloads.

**Acceptance criteria:**

- Persist an incubation record successfully before consuming the egg item or placing the world block. Restore remaining wall-clock duration after restart and make hatch completion idempotent.
- Handle block break, natural hatch, trampling, explosions, pistons, fluids, chunk unload/load, missing worlds, and administrator cancellation without duplication or silent item loss.
- Load plot SQL records off-thread, then resolve worlds and publish the complete plot snapshot on the main thread before allocating missing plots.
- Mark plugin mobs and bosses with trusted PDC IDs. Rewards require a valid configured ID and an active tracked state; arbitrary vanilla mobs, disabled definitions, or orphaned persistent mobs do not reward.
- A tracked mob or boss can issue its death reward exactly once. Respawn tasks are bounded and cancelled on disable.
- Repair boss ranking/reward logic and define an explicit behavior for players who disconnect before payout.
- `/growapet reload` validates all candidate files first and atomically swaps rebuilt immutable definitions/caches on the main thread. Invalid configuration leaves the last-known-good state active.

**Likely files/areas:** egg/incubation, plot, mob/reward, boss managers and listeners, configuration validation, and lifecycle wiring.

**Data-migration impact:** Add `incubating_eggs`. Existing player, plot, and pet rows remain compatible. Unidentifiable legacy turtle-egg blocks are reported for administrator review rather than assigned or hatched by guesswork.

**Live-server tests:** Restart mid-incubation at several stages, unload chunks/worlds, exercise environmental block changes, start with players online, kill ordinary and tracked mobs, restart with persistent custom entities, and run a multi-player boss with a disconnecting participant.

### Phase 1 — Core player and plot MVP

**Goal:** Complete the durable player/stat/plot foundation needed by the GrowAPet loop.

**Acceptance criteria:**

- All specified stats have validated mutation paths, playtime updates accurately, and Minecraft level/progress remain synchronized after load, reward, admin change, death, world change, and restart.
- Add spec placeholder names while keeping current identifiers as compatibility aliases. Define explicit output for unloaded/offline data without main-thread database queries.
- Plot defaults honor configuration. Ownership and allocation are deterministic and collision-checked.
- Implement persisted plot size, pet-slot, and egg-slot upgrades through atomic purchases; never rebuild or relocate an existing plot implicitly.
- Protect blocks, containers, hanging entities, armor stands, vehicles, entities, fluids, fire, explosions, pistons, and visitor interactions according to explicit plot settings and admin bypass permissions.

**Likely files/areas:** player/stat services, placeholders, plot manager/model, protection listeners, plot settings menu, and messages/config validation.

**Data-migration impact:** Backfill normalized player-zone and shop/upgrade rows from the existing CSV columns. Dual-write legacy columns during the compatibility window so rollback does not erase new unlocks or levels.

**Live-server tests:** Two-player owner/visitor/admin matrix across every protected event, plot upgrades, restart, missing world, allocation near grid boundaries, and PAPI/HUD consistency.

### Phase 2 — Core egg and pet MVP

**Goal:** Deliver the complete Place Egg → Hatch Pet → Equip → Progress loop safely.

**Acceptance criteria:**

- Load egg and pet definitions from validated YAML; invalid entity types, weights, durations, multipliers, or duplicate IDs fail validation without partially replacing live definitions.
- Provide a durable pet inventory/menu with stable pet UUIDs, explicit equip/unequip operations, an enforced equip limit, and no item representation that can be forged from display name/lore alone.
- Apply a client-approved stacking formula consistently to damage/coin/gem rewards and expose the same computed values to stats and placeholders.
- Award pet XP from trusted mob kills, persist level transitions, and prevent replay after restart.
- Spawn/reconstruct tagged pet entities only inside the owner’s plot. Entity creation/removal is idempotent across chunk load, teleport, death, logout, reload, and restart.
- Enforce plot pet limits and safely adapt unsupported/dangerous vanilla types through an approved allowlist or display representation.

**Likely files/areas:** `eggs`, `pets`, pet models/DAO, pet menus/listeners, plot integration, reward service, and `eggs.yml`/`pets.yml` validators.

**Data-migration impact:** Extend pet persistence with the minimum entity/location and progression metadata without changing existing pet UUIDs. Backfill safe defaults for legacy rows.

**Live-server tests:** Hatch while online/offline, restart repeatedly, equip/unequip at limits, gain pet XP, fill the plot, unload chunks, kill/remove pet entities, and verify that reconstruction never duplicates a pet.

### Phase 3 — Core progression world MVP

**Goal:** Make mobs, zones, warps, bosses, rewards, and leaderboards form a trustworthy configurable progression.

**Acceptance criteria:**

- Validate all mob, zone, warp, wall, boss, and reward references as one configuration graph before activation.
- Enforce predecessor/unlock rules on every warp and zone-entry path, not only menus.
- Persist or deterministically rebuild configured spawns and boss timers, with bounded entity/task budgets.
- Apply configured mob damage and reward values only to trusted entities in their intended zones.
- Bosses support configured respawn, broadcast lifecycle, damage ranking, one-time rewards, and a documented offline-participant policy. Skills remain limited to explicitly configured, tested behaviors.
- Leaderboards use durable database values, reconcile cached online mutations, handle query failure, and refresh without blocking the main thread.

**Likely files/areas:** mob, zone, warp, wall, boss, leaderboard services, commands/listeners, and their YAML validators.

**Data-migration impact:** Add spawn/boss state only where restart continuity requires it. Retain every current zone ID through the MVP.

**Live-server tests:** Direct and menu warp-bypass attempts, invalid configuration reload, mob respawn/restart, zone reward isolation, boss logout/offline cases, duplicate death events, and leaderboard comparison against SQL and cached players.

### Phase 4 — Current shop MVP

**Goal:** Retain and secure the existing gear, tool, and multiplier progression without importing Petcore or beginning the 10-zone migration.

**Acceptance criteria:**

- Preserve current owned tiers and command behavior.
- Move catalog/cost definitions to validated configuration only where this improves the existing intended design; retain stable internal IDs and migration aliases.
- Commit currency deductions and tier changes atomically and idempotently.
- Recalculate derived multipliers from owned upgrades rather than accumulating them repeatedly on load/reload.
- Disable or quarantine credit-to-power purchases until the client resolves the cosmetics-only conflict.

**Likely files/areas:** current shop/store/menu/economy services and validated menu/shop configuration.

**Data-migration impact:** Preserve `shop_levels`. Migrate keys only through versioned aliases and dual-write compatibility; do not reinterpret balances.

**Live-server tests:** Full exploit matrix, rapid purchases, reload/reopen, restart immediately after purchase, full inventory, maximum tier, insufficient mixed currencies, and balance reconciliation.

### Phase 5 — Optional quests

**Goal:** Add deterministic daily, weekly, and story objectives only after their source metrics are trustworthy.

**Acceptance criteria:** Generate restart-stable assignments, track monotonic progress from trusted metrics, persist reset/claim state, and grant each reward at most once through the economy transaction service.

**Likely files/areas:** new quest definitions, service, store, menu, command, and `quests.yml` validation.

**Data-migration impact:** New quest tables only.

**Live-server tests:** Reset-time boundary, restart, duplicate claim, temporarily declining currency metrics, offline progress rules, and definition changes.

### Phase 6 — Optional trading

**Goal:** Add an atomic two-party trade for pets, eggs, coins, and gems; Credits are never tradeable.

**Acceptance criteria:** Use a server-owned session/state machine. Any offer mutation revokes both confirmations. Validate ownership, balances, pet locks, inventory capacity, and online/session identity again inside one durable commit. Cancel safely on disconnect, disable, conflicting session, or timeout.

**Likely files/areas:** new trade session/service/store/menu/command and pet/economy lock integration.

**Data-migration impact:** Add trade and audit tables; do not convert existing pets or currencies.

**Live-server tests:** Complete inventory exploit matrix, offer changes after confirmation, disconnect/death/teleport, full inventory, simultaneous trade attempts, restart, repeated confirmation packets, and explicit Credit rejection.

### Phase 7 — Optional events, cosmetics, and admin polish

**Goal:** Add reversible events, cosmetic-only monetization, and auditable administration without creating hidden progression paths.

**Acceptance criteria:** Event modifiers have stable IDs, bounded schedules, restart state, explicit combination rules, and guaranteed rollback. Cosmetics remain chat colors/tags or other approved non-power effects. Admin mutations support offline UUID targeting where safe, granular permissions, validation, confirmation for destructive operations, and audit records.

**Likely files/areas:** new event, cosmetic, settings, and admin services plus commands/configuration.

**Data-migration impact:** Add small settings/event/cosmetic tables only where restart continuity requires them.

**Live-server tests:** Restart during events, overlapping modifiers, duplicate scheduling, permission changes, cosmetic ownership, offline admin targets, destructive confirmation, and audit reconciliation.

### Phase 8 — Later 10-zone shop migration

**Goal:** Treat the Shop Plan as a separate approved project after the current MVP is stable.

**Acceptance criteria:**

- Obtain explicit approval for the Plains → Spruce Forest → Savanna → Desert → Ice Spikes → Cherry Grove → Flower Forest → Cave → Ocean → Mushroom Island mapping.
- Define mappings for every legacy zone, shop ID, command, owned tier, fragment/item, boss, warp, and configuration key.
- Simulate income, prices, time-to-upgrade, and existing-player outcomes before selecting the new economy curve.
- Provide compatibility aliases and a versioned rollback plan; never rename populated IDs in place without migration records.
- Run the migration only on a staged production-data copy before scheduling a live maintenance window.

**Likely files/areas:** future shop/zone definitions, content catalogs, commands, YAML, and a dedicated migration.

**Data-migration impact:** Significant and intentionally undecided until mapping/economy approval. This phase must not be bundled into Phases 0–4.

**Live-server tests:** Production-data staging rehearsal, economy simulation, legacy commands/items/configuration, progression gates, repeat migration, rollback, and post-migration reconciliation.

## First three implementation tickets

### Ticket 1 — Restore the reproducible build foundation

Replace the incomplete vendored SQLite source with the official packaged driver, correct only confirmed compile-blocking CFR artifacts, remove decompiler-only files, introduce JUnit Jupiter, and add a database compatibility smoke test. Do not change gameplay, commands, configuration semantics, package names, or the SQLite schema. Client acceptance is a clean build plus successful opening of a copied existing `database.db` on the target operating system.

### Ticket 2 — Introduce the versioned single-owner database layer

Replace the shared two-thread/single-connection design with one connection-owning database worker, exceptional error propagation, readiness gating, verified backups, and transactional `schema_migrations`. Preserve current DAO-facing behavior and all legacy tables/columns. This ticket ends when migration idempotency, failure rollback, task ordering, and non-blocking initialization are tested.

### Ticket 3 — Make player lifecycle persistence safe

Refactor player load, cache publication, join, quit, autosave, and disable sequencing on top of Ticket 2. Prevent stale load completion from replacing newer sessions, keep Bukkit calls on the main thread, reject gameplay until the correct session is ready, and ensure every accepted mutation is durably ordered before connection close. Cover join/quit/rejoin and shutdown races with automated tests and a live restart checklist.

## Decisions requiring client confirmation

1. Confirm Paper 1.21.11 as the deployment baseline rather than the broader “Paper 1.21+” statement.
2. Provide a representative production `database.db` copy and disclose whether WAL/SHM companion files exist before migration design is finalized.
3. Confirm whether Credits can be purchased with real money. If yes, approve disabling or replacing the current coin/gem/crate offers with cosmetics.
4. Confirm the live plot-world name, existing plot coordinates, grid spacing, and whether any plots overlap or use a prebuilt layout.
5. Choose the number of simultaneously equipped pets and the exact multiplier-stacking rule.
6. Approve a safe pet entity allowlist/adapters instead of assuming every vanilla mob, boss, vehicle, and display-incompatible type can be spawned directly.
7. Decide whether incubation continues by wall-clock while players are offline and the server is stopped; wall-clock continuation is recommended.
8. Choose mob reward attribution and boss participation/offline payout rules.
9. Define whether WorldGuard or GrowAPet owns spawn, zone, arena, shop, and plot protection boundaries.
10. Approve granular permission node names/defaults and whether the current `growapet.admin` remains an aggregate parent.
11. Confirm economy limits, negative-value cleanup, and overflow policy for any existing abnormal balances.
12. Confirm that current placeholder names remain as aliases while spec-compliant names are added.
13. Decide the quest reset timezone and future event scheduling timezone.
14. Approve the 10-zone mapping, item conversion, and economy curve in a separate milestone before any migration code is written.

## Risk register

| Risk | Likelihood/impact | Mitigation and release gate |
|---|---|---|
| Data loss or rollback incompatibility | High / Critical | Versioned transactional migrations, verified backups, legacy-column preservation, dual-write compatibility, no destructive replacement upserts, ordered shutdown, and a restore drill on copied production data. No migration ships without successful repeat and rollback rehearsals. |
| Currency, reward, inventory, or trade duplication | High / Critical | Trusted PDC identities, exact menu sessions, unconditional denial/resync, idempotent transaction IDs, atomic ownership/balance commits, inventory-capacity validation, and adversarial click/disconnect/restart tests. No economy feature ships before the exploit matrix passes. |
| Main-thread stalls or unsafe asynchronous Bukkit access | High / High | One database worker, explicit main-thread handoffs, immutable configuration snapshots, batched/bounded work, entity/task budgets, delta-based HUD updates, and live timing/profiling. No system is accepted solely from unit tests. |
| Entity/task leaks and restart duplication | Medium / High | Persistent logical IDs, idempotent reconstruction, PDC tags, chunk/world lifecycle handling, bounded respawn/tick tasks, and disable cleanup tests. |
| Configuration corruption or unsafe reload | Medium / High | Parse and validate complete candidate snapshots, retain last-known-good state, never overwrite live values silently, and report precise errors. |
| Scope creep from Petcore or the 10-zone plan | High / High | Phase gates, explicit optional milestones, feature flags, no partial command exposure, a written Petcore exclusion list, and separately approved economy/migration work. |

## Recommended first ticket

Restore a reproducible, supportable build without changing gameplay or SQLite data: replace the incomplete decompiled SQLite implementation with the official packaged driver, correct only confirmed CFR compile damage, remove decompiler-only artifacts, add the minimal JUnit/database compatibility harness, and have the client verify that a clean build produces a JAR capable of opening a copied existing `database.db` on the deployment platform.
