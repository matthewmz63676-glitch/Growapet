# Petcore — Systems Reference / Rebuild Prompt

This document is a deep-dive reference into the Petcore Spigot/Paper plugin's major systems, derived
directly from the source tree at `C:\Users\excee\IdeaProjects\Petcore`. It is meant to be used as an
AI-development prompt/reference: every section names the real classes, methods, config keys, and
storage formats involved, and ends with a numbered recipe for rebuilding that feature from scratch in
a new plugin.

**Plugin basics** (`src/main/resources/plugin.yml`): main class `me.petcore.Petcore`, `api-version:
1.21.11`, `load: POSTWORLD`, hard-depends on `PlaceholderAPI`, soft-depends on `WorldGuard` and
`ProtocolLib`. Bundled libraries: `net.dv8tion:JDA:5.0.0-beta.24` (Discord bot) and
`org.xerial:sqlite-jdbc:3.45.3.0` (every per-feature SQLite store). There are ~250 commands declared,
plus several dozen more registered dynamically at runtime (see "Wiring" below).

**Config files:** `src/main/resources/config.yml` (bundled defaults, ~1900 lines) is copied to the
plugin data folder as `config.yml` on first run (`saveDefaultConfig()`); `Petcore#mergeMissingConfigDefaults()`
then walks every leaf key in the bundled file and copies in any key missing from the live file (so
plugin updates that add new config sections don't silently vanish on existing servers — but a key that
already exists is never overwritten). `config2.yml` at the repo root is an older/duplicate snapshot of
the same file (not read by the plugin at runtime — only `src/main/resources/config.yml` is packaged
into the jar).

---

## Wiring — how `Petcore.java` assembles everything

`src/main/java/me/petcore/Petcore.java` (`onEnable()`, ~1600 lines) is the composition root. Nothing is
dependency-injected — every manager/store is `new`'d in a deliberate order and wired together with
plain setters, then commands are bound with `getCommand(name).setExecutor(...)`. The order matters:

1. `saveDefaultConfig()` + `mergeMissingConfigDefaults()` + one-time config migrations (e.g. stripping
   stale `discord.link_reward_commands` entries), then `disableAdvancementAnnouncements()`.
2. Abort early (disable the whole plugin) if PlaceholderAPI isn't present.
3. `GuiSecurity` listener registered first — a shared "packet-race guard" every read-only GUI relies on.
4. `LinkManager` (Discord-link storage) initialized; `PetcoreExpansion` (PAPI) registered; `DiscordBot`
   started **asynchronously** (`runTaskAsynchronously`) so a slow Discord handshake never blocks server boot.
5. `ScoreboardManager` created and started.
6. `TeamManager` initialized; `/team` executor registration is deliberately delayed 20 ticks to dodge a
   Paper/Brigadier command-tree race.
7. Chat systems: `ChatManager`, `ChatGames`, `MuteManager`, `AutoMute`, `DisabledCommands`.
8. `StoreManager` (ranks/gamepasses/boosters — the premium "credits" currency lives here, not in
   `me.petcore.economy`). Commands are additionally force-registered straight into the server's
   `CommandMap` under a `"petcore"` prefix, because a live Skript script (`store.sk`/`credits.sk`) can
   steal the same command names on its own reload.
9. `/stat` GUI (`me.petcore.chat.PlayerStatsGUI`), Welcome/Spawn/ClearChat/Rules/Minigame managers.
10. Pile system: `PileKeys`, `PileSettings`, `PileManager`, `PilesStore`, `SpawnerItemService`,
    `RotatePileModeManager`, `PileListener`, `PileCommandHandler`, plus 24 dynamically-registered
    `/get<zone>pile<hp>hp` spawner commands (6 zones × 4 HP tiers) generated from `ZonePileType` and
    injected straight into the `CommandMap` (they don't exist as static entries in `plugin.yml`).
11. Shop system: `CoinsStore`, `RubiesStore`, `ShardsStore`, `FragmentStore`, `ShopModule` (see §1).
12. Fragment compression (`/compress`), fragment-item admin commands (`/set<zone>fragment`,
    `/setcomp`), `/fragtest`.
13. Custom pile creation admin tools (`/makepile` etc.), then all 8 `EggModule`s (one per `EggType`
    zone — see §12) plus `EggModule.wireIndex` for the shared Pet Index.
14. PlaceholderAPI expansions for coins/rubies/level/credits/stats.
15. Tags (`/tag`, `/tagadmin`), head-stats floating names, last-location store, Huge Pets, Placed Pets
    (constructed **before** `PileManager` needs a live reference to it, so `PileManager` can call
    `placedPetsManager.giveExpToPlacedPets(...)` at payout time), `LoreStatListener` (gear stat bonuses),
    stat-multiplier PAPI expansions (`%damage%`, `%coinsmulti%`, etc.).
16. Economy admin commands (`EconomyCommandHandler`), `/debugfix`.
17. Boosters/potions (`PotionStore`, `PotionManager`, `BoosterSlotStore`, `BoostersGui`), Breakables,
    Milestones (§9), Quests (§10), Cookie easter-egg hunt, Fragment Exchange (`/salvage`), Vending
    machines.
18. Rebirth system (`RebirthStore`, `RebirthManager`, `RebirthGui`).
19. **Zone walls** (`WallUnlockStore`, `WallManager`, `WallGUI`, `WallListener`) — constructed after
    Rebirth so `RebirthManager` can take a live `WallManager` dependency (a "require all walls broken"
    rebirth gate). Wall block caches are loaded from WorldGuard 1 tick later.
20. Pet Slot Shop (`/slotshop`) — depends on `WallManager` for its unlock gates.
21. `/resetdata` / `/resetplayerdata` — season-wipe commands wired against nearly every store above.
22. Leaderboard holograms (§5), interactive tutorial system, `/checklink`, Daily rewards.
23. `Bukkit.getPluginManager().registerEvents(this, this)` (the main class is itself a `Listener` for
    join/quit), `startActionBarTask()`, `startExpBarSyncTask()` (§4), `PetcoreAPI` service registration.
24. `TopSpent` (§14), misc legacy-Skript ports, Announcements, `/yam`/`/face`, Locked Zone (§16b),
    Maintenance mode, Rebirth/Discord/Store head NPCs (§8), Warp system (§2), Boss-fight system.

---

## 1. Zone Shop

### What it is / purpose
A generic, fully data-driven gear-vending GUI, one per progression zone (Forest, Mushroom, Desert,
Badlands, Ice Spikes, Stone, Deepslate, Beach, plus a standalone "Talisman" shop). Players spend coins,
rubies, and a per-zone "fragment" item (looted from piles) on tiered armor/tools. Adding a new shop is
just a new `ShopType` enum constant — no new Java classes required.

### Key classes & files
- `src/main/java/me/petcore/shop/ShopType.java` — enum of 9 shops. Each constant defines: id, its
  `/command` name, legacy-color title, GUI size (54 slots for the 6 standard gear shops + Beach, 45 for
  Talisman), accepted `ShopCurrency`s (`COINS`, `RUBIES`), `ShopFragment` list (the zone's fragment
  currency; Talisman accepts two), admin/reward slot indices, blank-slot indices, tier-upgrade chains,
  `predecessorShopId` (cross-shop gating), and built-in `defaultItems`.
- `ShopType.GearLayout` (nested) — the shared 24-slot layout used by the 6 standard gear shops: armor
  columns 10-13 (helmet/chest/legs/boots), tool columns 15-16; four tiers via `slot = base + tier*9`
  giving rows `[10,19,28,37]`, `[11,20,29,38]`, `[12,21,30,39]`, `[13,22,31,40]`, and one 8-long tool
  chain `[15,16,24,25,33,34,42,43]`.
- `ShopType.TALISMANSHOP` — 45-slot GUI, one horizontal 5-tier admin chain `[20,21,22,23,24]`, 16 blank
  decorative slots, no built-in items (fully admin-configured), 2 fragment currencies.
- `ShopManager.java` — the single `Listener` that opens every shop GUI (`openShop`), routes clicks
  (`onInventoryClick`), and runs the purchase flow (`handlePurchase`).
- `ShopModule.java` — lifecycle glue: owns `ShopRegistry`/`ShopStore`/`ShopManager`/`SignTextInput`/
  `ShopItemEditorGUI`; registers every `ShopType.values()` at construction; `enable()` registers listeners.
- `ShopRegistry.java` — flat `Map<String, ShopType>`, throws on duplicate id.
- `ShopCommand.java` — bound per shop type to back `/<shopcommand>` (e.g. `/forestshop`); OP-only for
  players, or console form with an explicit target player name (for NPC plugins).
- `ShopStore.java` — YAML persistence (see below).
- `ShopItemEditorGUI.java` — in-game admin item/price/lore editor.
- `ShopItemCommand.java` — backs the dynamically-registered `/get<shop><role>` give-commands.
- `ShopItemFlags.java` — PDC-based per-slot "disabled" flag.
- `ShopAdminAccess.java` — OP or `petcore.admin` gate for the right-click editor.
- `CostFormat.java` — k/M/B/T cost-number compaction for lore.
- `StatType.java` — parses `"+ x<number> <Word>"` lore lines (Damage/Coins/Rubies/EXP/Speed) into a PDC
  stats blob for `LoreStatListener` to read structurally.
- Per-zone item enums implementing `ShopItem`/`GearShopItem`: `ForestShopItem`, `MushroomShopItem`,
  `DesertShopItem`, `BadlandsShopItem`, `IceSpikesShopItem`, `StoneShopItem`, `DeepslateShopItem`,
  `BeachShopItem`.
- `ShopSlotData.java` (record), `ShopCurrency.java` (enum COINS/RUBIES), `ShopFragment.java` (record:
  zone, command name, display name).
- **Unrelated, differently-named system:** `src/main/java/me/petcore/slotshop/` — a "Pet Slot Shop"
  luxury sink for buying extra placed-pet slots (base 5, up to 11), *not* a zone gear shop. See below.

### How it works
**GUI construction (`ShopManager.openShop`):** creates a Bukkit inventory of `type.guiSize()` slots
titled with the shop's legacy title (e.g. `&2Forest Shop`), calls `GuiSecurity.protect(inv)`. For every
slot: admin/reward slots render via `renderShopSlot` (clones the reward `ItemStack`, appends cost lore
lines built from `CostControl` records — one per accepted currency, one per fragment — then a trailing
`"&eClick to buy"` or `"&c&lDISABLED — not for sale"` line); blank slots are left `null`; everything else
is filled with a `GRAY_STAINED_GLASS_PANE` labeled `" "`.

**Click handling (`onInventoryClick`, priority `HIGHEST`):** every click in a tracked shop is cancelled
and the inventory resynced next tick (`denyAndResync`) — the menu is fully read-only. Only admin-slot
clicks do anything: RIGHT/SHIFT_RIGHT by an admin opens `ShopItemEditorGUI`; LEFT/SHIFT_LEFT by anyone
triggers `handlePurchase`.

### Anti-exploit click handling

Every shop GUI follows the canonical GUI click-handling pattern — see `Gui.md` for the full write-up
and a reusable skeleton; this section only records the concrete behaviour actually implemented here.
`ShopManager.onInventoryClick` runs at `EventPriority.HIGHEST` and, for any player with a tracked shop
open, calls `denyAndResync(e, p)` **before** it ever looks at which slot was clicked: this both
`e.setCancelled(true)` and `e.setResult(Event.Result.DENY)`, then schedules
`Bukkit.getScheduler().runTask(plugin, p::updateInventory)` one tick later. The deny+resync happens
unconditionally for every click regardless of slot or click type, so shift-click, hotbar number-key
swap, and double-click collect-to-cursor can never pull a reward item out of the shop before the
purchase logic even decides whether the click landed on a real slot. `onInventoryDrag` cancels +
`Event.Result.DENY` + delayed `updateInventory()` the same way for drag/shift-drag events, which
`InventoryClickEvent` never sees. `onInventoryClose` only clears a player's "shop open" tracking when
the `Inventory` that closed is the exact instance recorded for them (`openShopInv`), so a stale close
from a double-open race (e.g. an NPC double-click re-running the open command) can't leave the actual
open menu unguarded.

### Commands

- `/<shopcommand>` (e.g. `/forestshop`, `/mushroomshop`, `/desertshop`, `/badlandsshop`,
  `/icespikesshop`, `/stoneshop`, `/deepslateshop`, `/beachshop`, plus the Talisman shop's command) —
  backed by `ShopCommand`, one instance per `ShopType`; opens that shop's GUI. OP-only for a player
  invocation; the console form takes an explicit target player name (for NPC plugins to drive).
- `/get<shop><role>` (e.g. `/getforestshophelmet`) — backed by `ShopItemCommand`, dynamically
  registered per `ShopType.firstTierItemCommands()`; grants that shop's first-tier reward item
  directly, either to the invoking player or, via `/<command> <player>`, to a target player.
- `/salvage`-adjacent: `fragment_exchange.ratios.*` in `config.yml` (a separate but adjacent system,
  not part of `ShopCommand`/`ShopItemCommand` themselves).

**Purchase flow (`handlePurchase`):**
1. Reject empty/disabled slots.
2. Tier-chain prerequisite: must already own (tolerant of legacy lore variants / the unbreakable bit,
   via `countMatchingTier`) the previous tier's item; it's consumed (not stacked) on purchase.
3. Cross-shop predecessor gate: a shop's first tiers may require owning + consuming the predecessor
   shop's final-tier item (e.g. Mushroom tier 1 requires Forest's max tier).
4. Affordability check against `CoinsStore`/`RubiesStore` balances and fragment-item inventory counts
   (loose fragments plus compressed items worth `FragmentStore.COMPRESSION_FACTOR` each). Any shortfall
   aborts the whole transaction with itemized failure messages — nothing is deducted.
5. On success: deducts currencies, spends fragments (loose first, breaking compressed items and
   refunding change), consumes prerequisite/predecessor items, gives the reward (drops overflow at the
   player's feet), advances the tutorial (`TutorialActions.BUY_SHOP_ITEM`), and prints a purchase
   summary. Reward items are always `setUnbreakable(true)` with `HIDE_DYE/HIDE_ATTRIBUTES/
   HIDE_UNBREAKABLE` flags; parsed stat values are stashed in a PDC blob (`ShopItem.STATS_KEY`,
   `petlify:shop_stats`).

**Admin editor (`ShopItemEditorGUI`):** 27-slot GUI. `SLOT_PREVIEW=4`; `SLOT_SET_HELD=10` (clone held
item); `SLOT_NAME=11` (chat-prompt rename); `SLOT_LORE=12` (54-slot per-line lore editor, `+ line` at
45, back at 49, editing a line opens an anvil-style sign prompt via `SignTextInput`); `SLOT_PRICE=13`
(27-slot cost editor, `[-]/amount/[+]` triples per currency/fragment starting at slot 9, ±100 per
click / ±1000 shift-click, click the amount to type an exact value); `SLOT_TOGGLE=14` (enable/disable);
`SLOT_GIVE_TEST=15`; `SLOT_CLEAR=16`; `SLOT_DUPLICATE=19`; `SLOT_MOVE=20`; `SLOT_DELETE=21`
(double-click-within-5s confirm); `SLOT_BACK=25`. Every action writes straight to `ShopStore` — no
separate save step.

**Storage:** `ShopStore.java` — a flat YAML file `shops.yml` (atomic write via `AtomicYaml.save`).
Layout: `<shopId>.<slot>.item` (Bukkit ItemStack serialization), `.coins`, `.rubies`,
`.fragments.<ZONE_ENUM_NAME>` (only non-zero costs stored). Unconfigured slots fall back to
`ShopType.defaultSlotData(slot)` so the 8 gear shops sell out of the box; Talisman has no built-in
defaults.

### Per-zone item catalog

All eight standard gear shops share `ShopType.GearLayout`'s 24-slot armor+tool grid and are built from
a six-role × four-tier `GearShopItem` enum (`GearShopItem.java` is the shared interface: it generates
each item's lore from an ordered list of `StatValue`s rather than hand-authored strings, so the
rendered line and the parsed stat — read structurally by `LoreStatListener` via `StatType`/PDC — can
never drift apart). Each row is `header` (`"&8Armor"`/`"&8Tool"`) + a blank + one generated stat line
per `StatValue` + a blank + a rarity tag (`COMMON` white, `UNCOMMON` green, `RARE` blue — see
`GearShopItem`'s constants). Costs below are tier-I → tier-IV coins/rubies/fragment-cost, in
progression order; every shop's first tiers additionally gate behind owning the predecessor shop's
final tier (see `predecessorShopId`/`predecessorPrerequisiteSlot`).

- **`ForestShopItem`** (`&2Oak …`, `COMMON`, no predecessor gate) — the starter shop, leather-tinted
  green (`0x9CCC65` → `0x2E5E1C` across tiers). Helmet/Chestplate/Leggings/Boots plus 8 tools (Oak
  Stick/Digger/Slicer/Miner/Chopper/Damager/Breaker/Sapling — flavor names over vanilla
  stick/shovel/hoe/pickaxe/axe/sword/arrow/sapling materials). Tier I costs 650 coins / 260 rubies / 3
  Forest fragments; tier IV costs 12,100 / 4,900 / 18. Charges `ZonePileType.FOREST` fragments only.
- **`MushroomShopItem`** (`&cMushroom …`, `COMMON`, gated behind Forest's final tier) — red leather tint
  (`0xE57373` → deep red). Tier I 30,600 coins / 12,100 rubies / 5 Mushroom fragments; tier III shown
  at 229,300 / 91,800 / 16 (climbing into tier IV beyond).
- **`DesertShopItem`** (`&eDesert …`, `COMMON`, gated behind Mushroom's final tier) — sandy-tan leather
  tint (`0xEBD9A3` → `0xBF9B54`). Tier I 368,000 / 147,000 / 6 fragments; tier III 1,738,000 / 696,000
  / 33.
- **`BadlandsShopItem`** (`&6Badlands …`, `UNCOMMON`, gated behind Desert's final tier) — orange terracotta
  tint (`0xFFB870` → `0xD2691E`). Tier I 3,366,000 / 1,347,000 / 14; tier III 14,815,000 / 5,925,000 /
  59. First shop to cross into `UNCOMMON` rarity.
- **`IceSpikesShopItem`** (`&bIce Spikes …`, `RARE`, gated behind Badlands' final tier) — pale blue tint
  (`0xCBE7FF` → `0x6FB4F5`). Tier I 37,440,000 / 14,986,000 / 36; tier III 164,790,000 / 65,902,000 /
  146. First shop to reach `RARE` rarity.
- **`StoneShopItem`** (`&7Stone …`, `RARE`, gated behind Ice Spikes' final tier) — gray tint (`0xBFBFBF`
  → `0x6E6E6E`). Tier I 299,520,000 / 119,888,000 / 65; tier III 1,318,320,000 / 527,216,000 / 263.
- **`DeepslateShopItem`** (`&8Deepslate …`, `RARE`, gated behind Stone's final tier) — near-black tint
  (`0x5A5A5A` → `0x333333`). Tier I 305,510,400 / 122,285,760 / 117; tier III 1,344,686,400 /
  537,760,320 / 473. Coin/ruby costs mirror Stone's numbers almost exactly at each tier (same cost
  curve continuing) but with a much steeper fragment cost, since Deepslate fragments are scarcer.
- **`BeachShopItem`** (`&bBeach …`, `RARE`, gated behind Deepslate's final tier, 54-slot GUI like the
  others) — sandy-blue tint (`0xE8D5A0` → `0x4FA8D8`). Tier I coins/rubies numerically match Deepslate's
  tier I (305,510,400 / 122,285,760) but the fragment cost jumps to 304 (vs. Deepslate's 117); tier III
  1,344,686,400 / 537,760,320 / 1,230. The final zone in the current 8-shop progression.
- **`GearShopItem`** (interface, not a shop itself) — the shared boilerplate: `ARMOR`/`TOOL` header
  constants, `COMMON`/`UNCOMMON`/`RARE` rarity constants, the `stat(type, amount)` factory, the
  `GearData` record backing every enum constant (slot, material, display name, header/rarity, stat
  list, optional leather-tint RGB, coin/ruby cost, `ZonePileType`, fragment cost), and
  `legacyStats()`/`legacyLores()` for rebalanced items whose old lore/stat combination must still
  satisfy tier-prerequisite matching for players who bought before the rebalance.
- **Talisman shop** (`ShopType.TALISMANSHOP`) — the one shop not built from a `GearShopItem` enum: a
  45-slot GUI with a single horizontal 5-tier admin chain and no built-in default items (fully
  admin-configured via `ShopItemEditorGUI`), accepting two fragment currencies instead of one.

### The `slotshop` package (Pet Slot Shop — a different feature)
- `SlotShopGUI.java` — 54-slot `&8Pet Slot Shop`, border glass, interior "&7Coming soon" filler. Six
  offer slots (10-15), each a `SlotOffer` record (coins cost, ruby cost, gate zone, gate label): slot
  10 = 25M/10M gated behind `zone2`; 11 = 60M/24M; 12 = 150M/60M (all zone2); 13 = 400M/160M,
  14 = 1B/400M, 15 = 2.5B/1B (all gated behind `zone3`). Locked = gray dye + lock label; already-bought
  = red dye "Purchased"; buyable = yellow dye + cost lore. On purchase: validates gate + not-already-
  bought + balance, subtracts both currencies, dispatches console `petslot add <player> 1`, and calls
  `SlotShopStore.setBought`.
- `SlotShopStore.java` — SQLite `slotshop.db`, table `slotshop_purchases(uuid, slot, PK(uuid, slot))` —
  row presence = purchased.
- `SlotShopCommand.java` — `/slotshop` opens the GUI.
- `ResetSlotsPurchaseCommand.java` — `/resetslotspurchase <player|all>` wipes rows.

### How to recreate from scratch
1. Define an enum of shop "types" carrying: id, command name, GUI size, accepted currencies, accepted
   fragment currencies, a set of admin/reward slot indices, blank-decoration slot indices, and tier
   upgrade chains (ordered lists of slot indices where slot N+1 requires owning slot N's item).
2. Build one YAML-backed store keyed by `<shopId>.<slot>` holding an `ItemStack` reward plus per-
   currency/fragment cost — this is your single source of truth; the enum only supplies *defaults*.
3. Write one `Listener` that opens a plain `Inventory` sized per shop type, fills non-reward slots with
   glass panes, and renders each reward slot's item + generated cost lore.
4. Cancel every click in that inventory unconditionally; only re-enable behavior for reward slots:
   admin-permission right-click opens an editor, left-click by anyone attempts a purchase.
5. On purchase: verify tier/cross-shop prerequisites are owned, verify every currency/fragment cost is
   affordable, then deduct atomically (abort entirely on any shortfall) and hand over the reward,
   consuming prerequisite items.
6. Build an in-GUI admin editor (set item from hand, rename via a fake sign/anvil prompt, per-line lore
   editor, ± cost steppers, enable/disable toggle, duplicate/move/delete) that writes directly back to
   the same YAML store with no separate "save" action.
7. Optionally add a completely separate "slot shop" style feature (a flat list of paid unlocks gated by
   an unrelated progression system) as its own small GUI + SQLite table of purchased flags.

### Config keys involved
No dedicated `shop:` section exists in `config.yml` — all shop item/cost data lives in the runtime-only
`shops.yml`, not the bundled config. Related config: `fragment_exchange.ratios.*` (zone fragment
trade-up rates for `/salvage`, a separate but adjacent system).

### Planned future zone lineup (not yet implemented)

This is a **planning note only** — nothing below should be executed as code changes right now. The
user has finalized a 10-zone lineup for future development, in this order:

1. Plains
2. Spruce Forest
3. Savanna
4. Desert
5. Ice Spikes
6. Cherry Grove
7. Flower Forest
8. Cave (themed)
9. Ocean
10. Mushroom Island

The **currently implemented** zones — live in code today via `ZonePileType` (`FOREST`, `MUSHROOM`,
`DESERT`, `BADLANDS`, `ICESPIKES`, `STONE`, `DEEPSLATE`, `BEACH`, in that progression order, each with
real HP tiers, block materials, and reward multipliers) and their matching shop classes
(`ForestShopItem`, `MushroomShopItem`, `DesertShopItem`, `BadlandsShopItem`, `IceSpikesShopItem`,
`StoneShopItem`, `DeepslateShopItem`, `BeachShopItem`) — are a **different, already-built 8-zone
lineup**. Do not rename or refactor any of that existing code on the basis of this planning list; it's
live, balanced, and tied to real economy numbers players are already progressing through.

Plain naming overlap between the two lists (noted here without guessing any mechanical mapping):

- **Desert** appears in both lists under the same name.
- **Ice Spikes** in the planned list plausibly corresponds to the current `IceSpikesShopItem`/
  `ICESPIKES` zone.
- **Mushroom Island** in the planned list plausibly corresponds to the current `MushroomShopItem`/
  `MUSHROOM` zone.
- **Plains**, **Spruce Forest**, and **Savanna** are new names that appear to precede or replace the
  current `Forest`/`ForestShopItem` slot conceptually as early-progression zones — this document does
  not assert how (or whether) they map onto the existing `FOREST` zone; that's a design decision for
  whoever picks up the migration.
- **Cherry Grove**, **Flower Forest**, **Cave (themed)**, and **Ocean** are wholly new zones with no
  current-code counterpart at all (no existing `ZonePileType` or shop-item enum resembles any of
  them). `Badlands`, `Stone`, and `Deepslate` — all currently implemented — have no counterpart in the
  planned 10-zone list either, so their fate under this plan (kept, merged, retired) is likewise
  undecided here.

Migrating the live `ZonePileType`/zone-shop-item lineup to this new 10-zone list is a **separate,
larger future task** that requires its own economy-balancing pass — new HP tiers, block materials, and
reward multipliers per `ZonePileType` entry, plus new `GearShopItem`-style enums (materials, leather
tints, stat numbers, coin/ruby/fragment cost curves, and predecessor-chain gating) per new shop — and
should not be done casually or as a quick rename. This section is a planning note for whoever picks
that up next, not an instruction to begin that work now.

---

## 2. Warp System

### What it is / purpose
A Java port of the legacy `warp.sk` Skript: `/setwarp`, `/warps` (a 2-page zone-selection GUI gated by
wall-unlock status), `/warp`, and `/warpplayer`. Also exposes a `/plot`-adjacent teleport helper used by
the boss-fight system's "warp ball" ability.

### Key classes & files
- `src/main/java/me/petcore/warp/WarpManager.java` — the entire system in one class (implements
  `CommandExecutor, TabCompleter, Listener`).

### How it works
**Storage:** `warps.yml` in the plugin data folder. In-memory `LinkedHashMap<String, Location> warps`
loaded from `warps.<id>.location` (Bukkit's native Location YAML serialization: world/x/y/z/yaw/pitch);
`save()` rewrites the whole `warps` section on every `/setwarp`.

**Zone-id resolution:** `config.yml` keys `warp.zone-ids.zone1..zone8` and `warp.zone-ids.plot` (default
`Zone1..Zone8`, `Plot`; self-seeded if missing). `resolveWarpId(input)` case/space/underscore-
insensitively maps friendly names (forest, mushroom, desert, badlands, icespikes/"ice spikes", stone,
deepslate, beach, plot, admin) to these configured ids; anything else passes through unchanged so
custom warp names still work.

**Commands (one shared `onCommand`):**
- `/setwarp <name|list>` — OP-only; `list` prints every warp with world + rounded coordinates.
- `/warps` — players only; opens the 2-page GUI.
- `/warp [id|admin]` — no args opens the GUI; `admin` (OP-only) teleports to the `"Admin"` warp;
  otherwise resolves the id, runs `checkZoneUnlocked`, and teleports with a themed message + sound.
- `/warpplayer <player> <warp>` — OP-only, force-teleports another online player.
- `/warp`/`/warps` are both fully blocked (with a boss-fight message) while `isWarpBlocked(player)` is
  true — driven by an externally-installed `Predicate<Player> warpBlocker` that `me.petcore.boss.
  BossManager` sets while that specific player is engaged in a boss fight.
- `teleportToWarp(player, rawId)` — a direct, non-command teleport used by the boss's warp-ball
  ability, bypassing `isWarpBlocked` entirely (since that ability specifically targets players who are
  already in the fight).

**GUI:** `WarpGuiHolder` (`InventoryHolder` record holding `page`). 36-slot inventories titled `&8Warp
Menu &7» &ePage 1`/`2`. Page 1: Forest (slot 10, always unlocked), Mushroom (12, gated
`isZoneUnlocked(p,2)`), Plot (22, always), Desert (14, gated 3), Badlands (16, gated 4), Next-page arrow
(35). Page 2: Ice Spikes (10, gated 5), Stone (12, gated 6), Deepslate (14, gated 7), Beach (16, gated
8), Previous-page arrow (35). Remaining 0-35 slots filled with black glass panes. Every button is PDC-
tagged (`NamespacedKey "warp_gui_zone"`, values like `"forest"`, `"next"`) rather than matched by
display-name text (explicitly chosen for robustness against text-serialization drift). `onGuiClick`
(priority `HIGHEST`) reads that tag to route clicks: unlocked → teleport with a colored success message
and zone-flavored `Sound` (e.g. `BLOCK_GRASS_PLACE` for Forest, `BLOCK_DEEPSLATE_PLACE` for Deepslate);
locked → red "ZONE LOCKED" message + `Sound.ENTITY_VILLAGER_NO`.

**Zone-unlock check:** `isZoneUnlocked(player, statusN)` — `statusN <= 1` is always true (Forest);
otherwise calls `WallManager.isUnlocked(player, "zone" + (statusN - 1))` directly (same numbering as
`%statusN%`, but in-process instead of round-tripping through PlaceholderAPI string parsing).

**Teleport safety:** no explicit cooldown or "safe landing spot" logic — teleports go straight to
whatever `Location` `/setwarp` captured; sounds are purely cosmetic feedback.

**Tab completion:** `/warp`/`/setwarp` first-arg suggestions are OP-only (regular players get none, so
they can't discover hidden/admin warp ids like `"Admin"` via tab); `/warpplayer` suggests online player
names then warp ids, OP-only.

### How to recreate from scratch
1. Persist a flat `Map<String, Location>` to a YAML file keyed by warp id.
2. Build one `/setwarp`, one `/warp [id]`, and a paginated GUI listing every "destination" as an item,
   each button PDC-tagged with a stable machine key (never match by display-name text).
3. Gate GUI entries and direct `/warp <id>` on any prerequisite (progression wall, permission, etc.) by
   querying that system directly rather than through a string-based placeholder API.
4. Expose an injectable `Predicate<Player>` "block teleporting right now" hook so unrelated features
   (a boss fight, a duel, a minigame) can veto `/warp` without this class needing to know about them.
5. Provide a separate "force teleport" method that bypasses your own block-predicate, for systems that
   need to move an already-blocked player deliberately (a forced retreat, a boss ability, etc.).

### Config keys involved
`warp.zone-ids.zone1` .. `warp.zone-ids.zone8`, `warp.zone-ids.plot`. Related: `boss.warp-name: petlify`
(the warp destination players are forced to when caught by a boss's warp-ball ability).

---

## 3. Scoreboard

### What it is / purpose
A per-player sidebar scoreboard (classic Bukkit Scoreboard API, not TextDisplay-based) showing level,
rebirths, piles broken, placed-pet slots, and currency balances, refreshed on a fixed interval.

### Key classes & files
- `src/main/java/me/petcore/ScoreboardManager.java` (top-level package, not under a sub-package).

### How it works
Each player gets a private `Scoreboard` (`Bukkit.getScoreboardManager().getNewScoreboard()`, cached in
`Map<UUID, Scoreboard> playerBoards`). One `Objective` named `"petcore_sb"`, criteria `"dummy"`,
`RenderType.INTEGER`, displayed on `DisplaySlot.SIDEBAR`. Each configured line becomes its own `Team`
(`petcore_0`..`petcore_14`, `MAX_LINES=15`): the visible text goes in `Team#prefix(Component)` (suffix
empty), a unique invisible-color-code string (e.g. `§0§r`) is the team's sole scoreboard "entry", and a
descending integer score preserves row order. The right-side score numbers are hidden via reflection
calling Paper's `NumberFormat.blank()` on the objective (`hideScoreNumbers`; silently no-ops on pre-
1.20.3 servers).

**Text pipeline:** title/lines are resolved through `PlaceholderAPI.setPlaceholders` first, then a
hand-rolled tokenizer (`tokenizeAndParse`/`ColorState`) converts legacy `&` codes and `&#RRGGBB` hex
into Minecraft JSON text components (via `GsonComponentSerializer`), a `SPRITE_PATTERN`
`(sprite:minecraft:ATLAS:PATH)` token becomes an inline `{"type":"object","atlas":...,"sprite":...}`
icon component, and a `[sprite:...]` label token is stripped outright. Numbers ≥1000 are auto-compacted
via `formatNumbers`→`CompactNumber.format` when `scoreboard.format_numbers` is true (default).
`scoreboard.text-shadow` (default true) applies an opaque-black `Component#shadowColor` to every line.

Every tick, after building the board, `TagManager.syncToBoard` and `HeadStatsManager.syncToBoard` are
re-applied — a fresh private scoreboard has no tag-related teams on it yet, so this keeps those features
working across a rebuild.

**Refresh scheduler:** gated by `scoreboard.enabled` (default true); `Bukkit.getScheduler().
runTaskTimer(plugin, () -> {for each online player: updateScoreboard(player)}, interval, interval)`
where `interval = scoreboard.interval` (default 20 ticks = 1s). `updateNow(player)` allows an immediate
out-of-band refresh (e.g. right after `/resetdata`).

### How to recreate from scratch
1. Give each player their own `Scoreboard` instance (don't share the server's main scoreboard) and one
   `Objective` with `criteria="dummy"`, `DisplaySlot.SIDEBAR`.
2. Represent each visible row as its own `Team` whose `prefix` holds the (Adventure Component) line
   text and whose sole `entry` is a unique, otherwise-invisible string (e.g. distinct legacy color-code
   sequences) — this lets you have up to ~15 unique "score rows" without colliding entries.
3. Assign descending integer scores to preserve row order, then hide the number column via the
   platform's `NumberFormat.blank()` API if available (reflectively, so it degrades gracefully on
   older server versions).
4. Resolve `%placeholder%` text through PlaceholderAPI (or your own resolver) before formatting; build
   a small legacy-`&`+hex-color tokenizer if you want compact JSON-component output instead of relying
   on a library.
5. Schedule one repeating task that rebuilds every online player's board on a config-driven interval;
   expose a manual "refresh now" entrypoint for immediate updates after data-mutating admin commands.

### Config keys involved
`scoreboard.enabled`, `scoreboard.interval` (ticks), `scoreboard.text-shadow`, `scoreboard.title`,
`scoreboard.lines` (list), `scoreboard.format_numbers`.

---

## 4. Actionbar

### What it is / purpose
A recurring action-bar HUD (coins, rubies, piles broken, level, walk speed) shown to every online
player. Unlike Scoreboard, there is no separate manager class — it lives directly inside
`Petcore.java`.

### Key classes & files
- `src/main/java/me/petcore/Petcore.java` — `startActionBarTask()` (~line 2609), `buildBar()` (~line
  2688), `formatActionBarNumbers()`. (Other files matching "actionbar" — `ChatManager`,
  `DisabledCommands`, `PileManager`, `PileSettings`, `PlotVisitManager`, `ResetDataCommand`,
  `HeadStatsManager`, `CompactNumber` — only send one-off transient action-bar toasts for their own
  events; they are not the recurring HUD.)

### How it works
`startActionBarTask()` is gated by `actionbar.enabled` (default true); reads `interval =
actionbar.interval` (default 20 ticks = 1s) and schedules `actionBarTask = Bukkit.getScheduler().
runTaskTimer(this, () -> {...}, interval, interval)`. Every tick, for every online player, it re-reads
`actionbar.lines` and `actionbar.separator` fresh from config (so a `/petcore reload` picks up edits
immediately) and calls `player.sendActionBar(buildBar(player, lines, separator))`.

`buildBar()`: for each line — resolve `%placeholders%` via PlaceholderAPI, strip `[sprite:...]` label
tokens, convert `<##RRGGBB>`/`<#RRGGBB>` MiniMessage-style hex into `&#RRGGBB`, optionally compact
numbers ≥1000 (`actionbar.format_numbers`, default true, via `ScoreboardManager.compactFormat`), then
run the same `parseLine`/`ColorState` pipeline used elsewhere (legacy `&` codes, `&#hex`, and the
`OBJECT_PATTERN` sprite/head tokens) — joining all lines with the separator into one JSON array
deserialized via `GsonComponentSerializer`. `actionbar.text-shadow` (default true) applies a shadow
color to the whole bar.

Separately, `startExpBarSyncTask()` runs every 20 ticks (independent of the actionbar-enabled toggle)
calling `pileManager.syncVanillaExpBar(player)` to keep the vanilla XP bar (the green fill + level
number above the hotbar) mirroring this plugin's own leveling stats.

### Config keys involved (`actionbar:` section, `src/main/resources/config.yml` line ~294)
`actionbar.enabled` (true), `actionbar.interval` (20), `actionbar.text-shadow` (true),
`actionbar.format_numbers` (true), `actionbar.separator` (`' &8|| '`), `actionbar.lines` — defaults:
`'&6%coins% (sprite:minecraft:items:item/gold_ingot)'`,
`'&4%rubies% (sprite:minecraft:items:item/redstone)'`,
`'&6%PETCORE_pilesbroken% (sprite:minecraft:items:item/golden_pickaxe)'`,
`'&3%petcore_level% (sprite:minecraft:items:item/experience_bottle)'` (namespaced to
`petcore_level` deliberately, so a bare `%level%` claimed by another plugin's PAPI expansion can't hijack
this line), `'&b%walkspeed% (sprite:minecraft:gui:mob_effect/speed)'`.

### How to recreate from scratch
1. Schedule one repeating task (`runTaskTimer`) at a config-driven interval that iterates online
   players and calls `player.sendActionBar(component)`.
2. Build the bar text fresh every tick from a config-driven list of template lines, resolving
   placeholders and joining with a configurable separator.
3. Support inline "sprite"/custom-icon tokens by regex-matching a custom bracket syntax and emitting
   the platform's native inline-icon component type (Minecraft 1.21.9+'s "object" text component) so
   icons render with no resource pack.
4. Add a numeric-compaction pass (K/M/B/T suffixes) that's careful not to mistake color-code characters
   adjacent to digits for part of the number.
5. Keep this fully decoupled from the sidebar scoreboard system, even though both parse similar syntax —
   duplicate the small parser rather than coupling the two features together.

---

## 5. Leaderboard Hologram

### What it is / purpose
An admin-placed, FancyHolograms-style leaderboard hologram system built entirely from native Paper
`TextDisplay` entities — no ArmorStand, no third-party hologram plugin/API. Supports ranked boards for
Coins, Rubies, Piles Broken, Rebirths, and Level, each rendering real player faces inline via
Minecraft's 1.21.9+ player-head text component.

### Key classes & files
- `src/main/java/me/petcore/leaderboard/Leaderboard.java` — per-board data holder (id, type, base
  `Location`, title, "none" template, ordered line templates, line-entity UUIDs, scale, "you" line,
  per-viewer personal-row entity map).
- `LeaderboardType.java` — enum `COINS, RUBIES, PILES_BROKEN, REBIRTH, LEVEL`.
- `LeaderboardManager.java` — the engine: spawning, refresh, persistence, per-viewer personal rows.
- `LeaderboardCommand.java` — `/leaderboard <create|remove|moveto|movehere|scale|refresh|list|reload|
  purge|reorient>`.
- `me.petcore.Petcore#buildComponent`/`OBJECT_PATTERN` (in `Petcore.java`) — the shared regex/renderer
  that turns `(sprite:minecraft:ATLAS:PATH)` or `(head:NAME:UUID:VALUE:SIGNATURE)` tokens into real
  Minecraft text components.

### How it works
**Entity model:** each board is a vertical stack of `TextDisplay` entities, one per row, spawned via
`world.spawn(loc, TextDisplay.class)` and positioned at `base.subtract(0, lineSpacing*scale*rowIndex,
0)`. Row 0 = title; rows 1-10 = ranks 1-10 (`RANK_ROWS = 10`); row 11 = a blank spacer
(`SPACER_ROW`/`SHARED_ROWS = 11`); row 12 (`PERSONAL_ROW`) is a **per-viewer private** "(YOU)" row.

Every shared display is configured (`configureDisplay`) with `Billboard.FIXED`, `setShadowed`,
`setSeeThrough`, a transparent background (`Color.fromARGB(alpha,0,0,0)`), `TextAlignment.CENTER`,
`setLineWidth`, `setPersistent(true)`, `setInvulnerable(true)`, no gravity, and a PDC tag
(`NamespacedKey "leaderboard_id"` + `"leaderboard_line"`) so orphans can be detected later. Orientation
is a **fixed** yaw rotation (`facingYawDegrees`, default 180°) applied as the display's `Transformation`
via a `Quaternionf` — not billboard face-tracking, so every viewer sees the identical facing.

**Personal row-12:** created lazily per online viewer (`ensurePersonalEntity`) — a second `TextDisplay`
with `setVisibleByDefault(false)` + `viewer.showEntity(plugin, display)` (the only mechanism to show
different text to different players with one entity type). Torn down on `PlayerQuitEvent`.

**Persistence:** everything lives in `plugins/Petcore/leaderboard/leaderboard.yml` — one YAML block per
board (`<id>.type/location/title/none/scale/you-line/lines/entities`) plus a global `settings:` block
(`facing-yaw-degrees`, `line-spacing` default 0.27, `line-width` default 300, `shadowed`, `see-through`,
`background-alpha`, `refresh-interval-ticks` default 100, `head.enabled` default true, legacy
`head.sprite-atlas`/`sprite-path`). Not stored in `config.yml` at all — fully self-contained.

**Data source / sorting:** `fetchTop(LeaderboardType, limit)` dispatches to `coinsStore.top(limit)`,
`rubiesStore.top(limit)`, `rebirthStore.topRebirths(limit)`, `progressStore.topPilesBroken(limit)`, or
`levelStore.topLevels(limit)` — each store owns its own SQL sort/limit query; `LeaderboardManager`
itself does no sorting.

**Refresh scheduler:** `Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L,
settings.refreshIntervalTicks())` — 1s initial delay then every `refresh-interval-ticks` (default 100
ticks / 5s). `refreshOne(lb)` fetches top-N and resolves player-skin data **asynchronously**
(`runTaskAsynchronously`), then hops back to the main thread (`runTask`) to call
`TextDisplay#text(Component)`.

**Head rendering:** a line template may contain `<head:%player%>`. During refresh this is rewritten
into `(head:NAME:UUID:VALUE:SIGNATURE)` where VALUE/SIGNATURE are that player's signed `textures`
profile property, fetched off-thread via Paper's `PlayerProfile` API (`completeFromCache`/`complete`),
cached per-UUID in `headCache` with a 5-minute retry cooldown on failure (`headLookupCooldown`).
`Petcore.OBJECT_PATTERN` matches both the sprite token and this head token;
`Petcore#buildComponent`/`parseLine` turns a head match into the Minecraft `"object"` player-head
component (`{"type":"object","player":{"name":...,"id":...,"properties":[{"name":"textures",
"value":...,"signature":...}]}}`), rendering the real face inline with **no resource pack** — only
1.21.9+ clients render it; older clients show nothing where the head would be. Default line template:
`rankColor(rank) + "(#%rank%) <head:%player%> &f%player% &8- &6%coins%"` where `rankColor` is gold for
rank 1, silver for 2, bronze for 3, gray otherwise.

**Orphan cleanup:** `purgeOrphans()` scans every `Display` entity in every loaded world for the PDC id
tag and removes any not currently tracked by a live board (runs on startup and via `/leaderboard
purge`).

### How to recreate from scratch
1. Define a POJO per board (id, type, base `Location`, title/line templates, entity UUID list, scale)
   and persist boards as blocks in one YAML file, plus a shared top-level `settings:` block.
2. On enable, spawn one `TextDisplay` per row via `world.spawn(...)`: `Billboard.FIXED`, transparent
   background, center alignment, `setPersistent`/`setInvulnerable`/no gravity, and a PDC tag so orphans
   can be found and purged later.
3. Apply a **fixed** yaw rotation via a `Transformation` + `Quaternionf` instead of relying on
   billboard-tracking, so every viewer sees the same facing regardless of where they stand.
4. Schedule a `runTaskTimer` at your chosen interval; inside it, do score-fetching and any
   skin/Mojang-profile lookups asynchronously, then hop back to the main thread to call
   `TextDisplay#text(Component)`.
5. For any "this is different per viewer" line (e.g. "your own rank"), spawn a second per-player
   `TextDisplay`, call `setVisibleByDefault(false)` + `player.showEntity(plugin, entity)`, and clean it
   up on `PlayerQuitEvent`.
6. For head/skin rendering: resolve the player's signed `textures` profile property off-thread, cache
   it per UUID, and build the platform's native player-head text component (name/id/textures) — this
   avoids needing a resource pack or a separate physical head entity.
7. Add admin commands to create/remove/move/rescale/reload/purge boards, and a purge routine that scans
   every `Display` entity in every world for your PDC tag and removes any not currently tracked.

### Config keys involved
None in `config.yml` — entirely self-contained in `plugins/Petcore/leaderboard/leaderboard.yml`
(`settings.*` as listed above, plus one YAML block per board).

---

## 6. Discord Chat / MC-Chat Linking

### What it is / purpose
A JDA-based Discord bot providing (a) bidirectional chat relay between a configured Discord channel and
in-game chat, and (b) an account-linking flow (in-game `/link` generates a code → Discord button opens a
modal to redeem it), backed by SQLite.

### Key classes & files
- `src/main/java/me/petcore/discord/DiscordBot.java` — JDA bootstrap, slash commands, button/modal
  interaction handlers, guild-leave auto-unlink, presence updates.
- `src/main/java/me/petcore/discord/ChatBridge.java` — the sole `AsyncChatEvent` listener; MC→Discord
  embed relay and (as a JDA `ListenerAdapter`) Discord→MC relay.
- `src/main/java/me/petcore/discord/PetcoreExpansion.java` — PAPI expansion `petlifymine`
  (`%petlifymine_linked%`, `%petlifymine_discord_id%`, `%petlifymine_team_tag%`,
  `%petlifymine_prefix%`, `%petlifymine_credits%`, `%petlifymine_pet_tag%`).
- `src/main/java/me/petcore/LinkManager.java` — code generation/expiry + SQLite UUID↔Discord-ID
  mapping.
- `src/main/java/me/petcore/Petcore.java` — hosts the `/link` handler (~line 2052) and
  `runUnlinkCommands` (~line 2384).

**Library:** JDA 5.0.0-beta.24. Intents: `GUILD_MEMBERS`, `GUILD_PRESENCES`, `GUILD_MESSAGES`,
`MESSAGE_CONTENT`.

### How it works
**Linking flow:**
1. Player runs `/link` in-game → `linkManager.generateCode(uuid)` creates a random 6-character code
   (excludes ambiguous chars `0`, `1`, `I`, `O`), stored in an in-memory `ConcurrentHashMap<String,
   PendingLink>` with a 5-minute expiry (`CODE_EXPIRY_MS`); a background task purges expired codes every
   30 seconds.
2. The player is shown the code via `discord.code_message`.
3. An admin posts a linking panel in Discord via a `/sendlinkembed` slash command — an embed with a
   "🔗 Press here to link" button.
4. Clicking the button checks `linkManager.isDiscordLinked(discordId)`; if not linked, opens a Modal
   with a 6-character text input.
5. On modal submit: uppercases/trims the code, calls `linkManager.consumeCode(code, discordId)` (removes
   the pending code either way; returns the UUID or null if invalid/expired).
6. On success: saves `discord_username`/`discord_tag` async, marks the player linked in
   `PetcoreExpansion`, runs `discord.link_reward_commands` via console dispatch (`{player}`/
   `{discord_id}` substitution), broadcasts (if `link_broadcast_enabled`), messages the player in-game,
   grants the configured `linked_role_id` Discord role, and logs an embed to `log_channel_id`.

**Storage:** `LinkManager` opens SQLite at `plugins/Petcore/data.db`, table
`linked_players(uuid PK, player_name, discord_id UNIQUE, discord_username, discord_tag, linked_at)`.
Key lookups: `isLinked(uuid)`, `isDiscordLinked(discordId)`, `getDiscordId(uuid)`,
`getUUIDByDiscordId(discordId)` (used for auto-unlink on guild leave), `loadAllLinked()` (preloads the
whole map into `PetcoreExpansion` at startup), `unlink(uuid)`, `resetAll()` (used by `/resetdata
confirm`).

**Unlink paths:** (a) Discord `/unlink <username>` slash command (requires `MANAGE_SERVER`); (b)
in-game `/unlink <player>` admin command; (c) automatic — `onGuildMemberRemove` fires when a linked
Discord member leaves the guild, reverse-looking-up their UUID and unlinking + revoking the role +
in-game message. All three paths run `discord.unlink_reward_removal_commands` and log an embed.

**Message relay (`ChatBridge`):**
- **MC→Discord:** gated by `chat_bridge.mc_to_discord.enabled`/`channel_id`; skips muted/team-chat-
  toggled/staffchat players and any message containing configured `excluded_markers` (e.g. `[SC]`) or
  `[TEAM]`; sanitizes Discord pings; builds an embed (author = rank-prefix + name, thumbnail from
  `mc-heads.net/avatar/{uuid}/64`, description = sanitized message, footer = online/max count); sent via
  JDA to the channel resolved from `channel_id`. Join/quit embeds are also sent if
  `chat_bridge.join_quit.enabled`.
- **Discord→MC:** gated by `chat_bridge.discord_to_mc.enabled`/`channel_id`; ignores bots; builds an
  Adventure component styled `[Discord] {user}: {message}` (hoverable username showing Discord ID,
  clickable tag if `discord_invite_url` is set) and broadcasts to every online player on the main
  thread.

### How to recreate from scratch
1. Add the JDA library, build a `JDABuilder` with the intents you actually need
   (members/presences/messages/message-content), and start it asynchronously so a slow handshake never
   blocks server boot.
2. Build a SQLite table mapping Minecraft UUID ↔ Discord user ID, one row per link, with a unique
   constraint on the Discord ID (a Discord account can only ever be linked to one Minecraft account).
3. Implement `/link` to mint a short random code with a TTL, stored in an in-memory map (not the
   database — it's transient), and purge expired entries on a periodic timer.
4. On the Discord side, use a persistent embed + button that opens a Modal collecting the code; verify
   and consume it against your pending-code map, then write the permanent link row.
5. On successful link, run a configurable list of console commands (reward grants) with `{player}`
   placeholders substituted, and the mirror list on unlink (removal).
6. Hook `GuildMemberRemoveEvent` to auto-unlink anyone who leaves the Discord guild.
7. For chat relay: listen to your platform's async chat event, build a Discord embed per message (skip
   muted/excluded senders), and post it via the bot to a configured channel; on the Discord side,
   listen for `MessageReceivedEvent` in that same channel and broadcast to online players on your main
   thread.

### Config keys involved (`discord:` section, `config.yml`)
`token`, `guild_id`, `linked_role_id`, `log_channel_id`, `code_message`, `link_message_ingame`,
`already_linked_message`, `link_broadcast_enabled`, `link_broadcast`, `leave_unlink_broadcast`,
`link_reward_commands` (list), `unlink_reward_removal_commands` (list), `messages.*` (per-embed
titles/descriptions), `embed_panel_title/desc`, `embed_rewards_note/title`, `link_rewards_display`
(list), `embed_thumbnail_url`, `embed_success_title/desc`, `embed_color_panel/success/error`,
`embed_footer`. Also `chat_bridge.mc_to_discord.enabled/channel_id/excluded_markers`,
`chat_bridge.discord_to_mc.enabled/channel_id`, `chat_bridge.join_quit.enabled/channel_id/join_format/
quit_format`, `chat_bridge.discord_invite_url`.

---

## 7. Economy

### What it is / purpose
Four currencies (coins, rubies, shards, and a level/EXP system) plus the admin command surface for all
of them. Note: **"credits"** (the premium/store currency) is *not* part of this package — it's owned by
`me.petcore.store.StoreManager` and stored in `store_data.yml` under `store.credits.<uuid>`.

### Key classes & files
- `src/main/java/me/petcore/economy/CoinsStore.java` — SQLite `coins.db`, table `coins(uuid TEXT PK,
  amount REAL)`.
- `RubiesStore.java` — SQLite `rubies.db`, table `rubies(uuid PK, amount REAL)`.
- `ShardsStore.java` — SQLite `shards.db`, tables `shards(uuid PK, amount REAL, lifetime REAL)`
  (`lifetime` never decreases — feeds `%shards_lifetime%`) and `shard_upgrades(uuid, upgrade_id,
  level)`.
- `LevelStore.java` — SQLite `level.db`, tables `level(uuid PK, amount INTEGER)` and `max_level(uuid PK,
  amount INTEGER)` (career high-water mark, never reset by rebirth — feeds Milestone gating).
- `LevelRewards.java` — loads `leveling.rewards.*` once, applies currency-multiplier bumps only at
  `level-interval` boundaries.
- `CoinsPlaceholderExpansion.java` (`%coins%`, `%coins_raw%`), similarly `RubiesPlaceholderExpansion`
  (in `me.petcore.placeholders`), `ShardsPlaceholderExpansion` (`%shards%`, `%shards_raw%`,
  `%shards_lifetime%`), `LevelPlaceholderExpansion` (`%level%`, colored by tier — gold-bold at ≥500).
- `EconomyCommandHandler.java` — backs `/coins`, `/rubies`, `/setstat`/`/addstat`/`/removestat`,
  `/resetmultis`, `/exp`, `/explevel`, `/setpilesbroken`, `/setdamagemulti`, `/setexpmulti`,
  `/addcoinsmulti`, `/luck`.

### How it works
Every currency store follows the identical pattern: its own private `Connection` to a standalone
SQLite file in the plugin data folder, `synchronized` get/set methods, and an
`INSERT ... ON CONFLICT(uuid) DO UPDATE` upsert (no shared "SqliteDatabase" base class — each store owns
its connection independently, matching the pattern used by `PilesStore`, `PlayerStatsStore`, etc.
throughout this plugin).

**Level curve** (`LevelStore`): `expForLevel(level) = expBase * level^expExponent`
(`leveling.exp-base` default 1400.0, `leveling.exp-exponent` default 2.20). A zone-scaled overload:
`expForLevel(level, zoneIndex) = expForLevel(level) * expZoneMultiplier^zoneIndex`
(`leveling.exp-zone-multiplier` default 1.20, Forest = zoneIndex 0) — the same formula both
`EconomyCommandHandler`'s `/exp give` roll-up loop and `PileManager#expNeededForNextLevel` share.

**LevelRewards:** `leveling.rewards.level-interval` (default 8) gates how often the currency-multiplier
bonus lands (not every level — this was deliberately changed from "every level" because it snowballed
multipliers too fast). At each interval crossed: `coins-multi-per-interval` (0.02) added to `coinsmulti`,
`rubies-multi-per-interval` (0.02) to `rubymulti`, `exp-multi-per-interval` (0.02) to `expmulti`, all in
`PlayerStatsStore`. `damage-multi-per-level` (0.0 by default) applies every single level, not gated by
the interval. A parallel `leveling.pet-level-rewards.<rarity>` block (default/common/uncommon/rare/
legendary/secret) drives **placed-pet** leveling separately — damage-multi only, by design (a pet's own
coins/ruby/EXP multiplier contribution is 0, so leveling a pet never snowballs the shared currency
multipliers).

**Earn/sink balance** (`economy:` config section): `pile-exp-multiplier` (0.85) scales exp-per-HP on
every pile break (`exp = maxHealth * pile-exp-multiplier * zone-reward-multiplier * ...` — read/applied
in `PileManager`, not this package). `behind-zone-penalty` (0.5) / `behind-zone-penalty-floor` (0.05) —
an anti-cheese penalty: farming a pile in a zone behind your furthest-unlocked wall multiplies
coins/exp/rubies by `behind-zone-penalty ^ (zones behind)`, floored at `behind-zone-penalty-floor` (so
farming 1 zone behind pays 50%, 2 zones behind 25%, and it never drops below 5% no matter how far
behind). The comment history in `config.yml` documents an explicit removal of a currency-multiplier cap
(2026-08-01, by request) — `PlayerStatsStore#getEffectiveOrDefault` no longer clamps `coinsmulti`/
`expmulti`/`rubymulti` at all.

**Vault:** no Vault economy-API integration exists anywhere in the codebase. The only "vault" hits found
are `PlayerVaultsHook.java` (integration with the unrelated *PlayerVaults* storage/inventory plugin) and
unrelated naming collisions (`ChatBridge`, `RebirthGui`/`RebirthManager`'s "vault" wipe terminology,
`DisabledCommands`, reset commands).

### How to recreate from scratch
1. For each currency, create a tiny standalone SQLite-backed store: one table `(uuid PK, amount)`, an
   upsert-based `set`, an additive `add`/`subtract`, and a `top(limit)` query for leaderboards.
2. Build a level curve as a pure function of level (a power law is simplest: `base * level^exponent`),
   and optionally scale it by a per-player "progress" multiplier (e.g. zones unlocked) so later-game
   leveling costs compound instead of staying linear.
3. Track a separate "career maximum level" column that a prestige/rebirth reset never touches, if any
   other system (milestones, cosmetic unlocks) should gate on lifetime progress rather than current
   level.
4. Gate multiplier-reward grants to a coarse interval (every N levels) rather than every level, to avoid
   runaway compounding; keep a single flat `PlayerStatsStore`-style table for all "multiplier" stats so
   every system (leveling, potions, gear, rebirth, Discord link bonus) reads/writes the same source of
   truth.
5. If you want a "farm the easy zone forever" penalty, compute a ratio between the player's current
   grind location and their furthest unlocked location, and apply an exponential decay with a floor.

### Config keys involved
`economy.pile-exp-multiplier`, `economy.behind-zone-penalty`, `economy.behind-zone-penalty-floor`,
`leveling.exp-base`, `leveling.exp-exponent`, `leveling.exp-zone-multiplier`,
`leveling.rewards.level-interval`, `leveling.rewards.coins-multi-per-interval`,
`leveling.rewards.rubies-multi-per-interval`, `leveling.rewards.exp-multi-per-interval`,
`leveling.rewards.damage-multi-per-level`, `leveling.pet-level-rewards.<rarity>.*`.

---

## 8. Heads (custom head NPCs)

### What it is / purpose
Decorative "NPC" heads placed in the world — giant, slowly-rotating, glowing custom-textured player
heads (an `ItemDisplay` visual plus an invisible `Interaction` hitbox) that show a clickable chat message
when right-clicked. Ported from Skript's `head.sk`/`head2.sk`. Two instances are wired in
`Petcore.java`: `head1` (Discord invite) and `head2` (Store link).

### Key classes & files
- `src/main/java/me/petcore/heads/CustomHeadNpc.java` — one instance per physical head.
- `HeadMessageConfig.java` — loads per-head click-message text from a separate file.

### How it works
Each `CustomHeadNpc` is parameterized (constructor args, see `Petcore.java` ~line 1689) by id
(`"head1"`/`"head2"`), world/coordinates, a base64 Mojang `textures` profile-property value (applied via
`PlayerProfile`/`ProfileProperty("textures", ...)` on a `PLAYER_HEAD` `SkullMeta`), a display scale, a
scoreboard team name + `ChatColor` (drives the entity glow color), fallback message component lines, and
a click `Sound`. Commands: `/spawnhead*`, `/tphead*`, `/removeheads*` (matched by prefix against the
invoking command name so one class instance can back all three per-head commands).

**Rotation:** a 1-tick repeating task rotates every tagged `ItemDisplay` entity ~1.8°/tick via quaternion
transforms, giving the slow continuous spin.

**Clicks:** land as `PlayerInteractAtEntityEvent` (not the plain block/entity interact event, since the
hitbox is a Paper `Interaction` entity), world-name-matched case-insensitively. Static builders
`CustomHeadNpc.discordMessage(url)` / `.storeMessage()` produce the two default message component
lists — the Discord invite URL is sourced from `chat_bridge.discord_invite_url`.

**Per-head message override:** `HeadMessageConfig` reads `<datafolder>/head/msg.yml` (keys `head1`,
`head2`, each a plain YAML string list), **re-read from disk on every click** so edits apply live with
no reload command needed. A `_version` key auto-regenerates the file's defaults if it predates the
current schema (currently version 2). Each line supports a `" -> "` marker: text before the arrow
displays, text after becomes an invisible clickable URL (falls back to detecting a bare `https?://` URL
if no marker is present). Any `discord.gg/...` URL found in a stored line is live-rewritten to the
current `chat_bridge.discord_invite_url` config value on every read, so config changes never require
regenerating `msg.yml` by hand.

Heads are **not** used as decoration inside any GUI (shop, egg, etc.) — this is a standalone,
world-placed feature only.

### How to recreate from scratch
1. Spawn an `ItemDisplay` with a `PLAYER_HEAD` item whose `SkullMeta` carries a Mojang signed `textures`
   profile property (no external image loading needed — the client renders the real skin).
2. Spawn a paired invisible `Interaction` entity at the same location sized to be clickable, and listen
   for `PlayerInteractAtEntityEvent` against it.
3. Optionally add a repeating 1-tick task applying a small incremental yaw rotation to the display for a
   "living statue" effect.
4. Store click-message text in its own small YAML file (not the main config) so it can be hot-edited
   without a full plugin reload, with a version marker that regenerates stale defaults.
5. Support a lightweight "display text / hidden URL" line syntax so admins can write friendly clickable
   text without hand-writing click-event JSON.

### Config keys involved
No dedicated `heads:` block in `config.yml` — head placement/texture/team are hardcoded in
`Petcore.java`'s construction calls; only the message text (`<datafolder>/head/msg.yml`) and the shared
`chat_bridge.discord_invite_url` key are externally configurable.

---

## 9. Milestone

### What it is / purpose
`/milestone` — a GUI with three permanent, never-repeating progression tracks (45 tiers each), each
tier a one-time-claimable reward once its trigger threshold is met.

### Key classes & files
- `src/main/java/me/petcore/milestone/MilestoneCategory.java` — enum `LEVELING`, `MEDAL` (playtime),
  `EGG` (egg hatching).
- `MilestoneManager.java` — trigger metrics + reward computation.
- `MilestoneReward.java` — record (coins, rubies, credits, damagePotion+count, luckPotion+count).
- `MilestoneStore.java` — SQLite persistence.
- `MilestoneGui.java` — category selector + per-track paginated GUI.
- `MilestoneCommand.java`, `ClearMilestoneCommand.java`, `ResetMilestonesCommand.java`.

### How it works
**Trigger metrics:**
- `LEVELING` gates on the player's **highest-ever level** (`max(LevelStore.getMax, LevelStore.get)`,
  never reset by rebirth). Requirement curve: `round(2 + (750-2) * (tier/44)^1.7)` — tiers roughly track
  wall zone-end levels (Forest L20, Mushroom L40, Desert L65, Badlands L95).
- `MEDAL`/Playtime gates on vanilla `Statistic.PLAY_ONE_MINUTE` (the same metric EssentialsX's
  `/playtime` reads). Requirement: `round(10 + (28800-10) * (tier/44)^2.4)` minutes, topping out at
  ~20 days at tier 44.
- `EGG` gates on `PlayerProgressStore.getPlayerEggsHatched`. Requirement:
  `round(5 + (100000-5) * (tier/44)^2.0)`.

**Rewards** (`computeReward(tier, coinCoeff, rubyShare)`): `coins = roundNice(coinCoeff *
scale(tier)^2.2)` where `scale(tier) = 500 + (100000-500) * (tier/44)^2`; `coinCoeff` is 0.0046 for
Leveling/Egg, 0.0003 for Playtime (deliberately nerfed since playtime is AFK-farmable); `rubies =
rubyShare * coins` (40% for Leveling/Egg, 20% for Playtime); `credits` = 0 until tier 15, then
`(tier+1)/5 - 2` on every 5th tier thereafter (tiers 15,20,...,45 → 1..7 credits), granted via console
`credits give`; a damage potion (severity band 0-4, mapped Forest→Ice Spikes) on every tier; a luck
potion only on "big" (every-5th) tiers.

**Storage:** `MilestoneStore` → `milestones.db`, table `milestone_claims(uuid, category, tier,
PK(uuid, category, tier))` — a row's mere existence means claimed.

**GUI (`MilestoneGui`):** category selector (45-slot, black glass outline, one item per category at its
configured slot, close at slot 40) → track view (54-slot: `SLOT_TRACK_INFO=4`, `SLOT_CLAIM_ALL=8`,
`SLOT_PREVIOUS=45`, `SLOT_TRACK_CLOSE=49`, `SLOT_NEXT=53`, 28 reward slots per page at fixed indices —
45 tiers span 2 pages). Reward-item color: LOCKED = gray dye, CLAIMABLE = yellow dye + enchant glint,
CLAIMED = lime dye. "Claim All" batch-claims every currently-claimable tier in that category.

**Commands:** `/milestone` (open GUI), `/clearmilestone` (admin, wipes **all** players unconditionally),
`/resetmilestones <player|all>` (admin, scoped reset).

### How to recreate from scratch
1. Define a small enum of progression "tracks", each backed by a real, monotonically-non-decreasing
   in-game metric (career-high level, vanilla playtime statistic, a lifetime counter).
2. Pick a smooth curve (e.g. a power law between a low and high bound) to generate N tier thresholds
   from that metric, and a matching reward curve so later tiers pay proportionally more.
3. Persist only "claimed" state as a composite-key row per (player, track, tier) — the mere presence of
   a row is the claim flag; everything else (current progress, whether it's claimable) is computed live
   from the live metric plus the claimed-rows set.
4. Build a two-level GUI: a category picker, then a paginated per-category reward grid whose item color/
   glint communicates locked vs claimable vs claimed at a glance, plus a "claim everything currently
   available" button.
5. Add admin commands to force-reset one player's or every player's claim state independently from the
   live metrics themselves (the metrics keep accumulating; only the claim table is wiped).

### Config keys involved
No dedicated `milestone:` block was found in `config.yml` — thresholds/reward curves/coefficients are
hardcoded constants in `MilestoneManager`, not admin-configurable via YAML.

---

## 10. Quests

### What it is / purpose
`/quests` — a daily quest menu offering 3 quests (drawn from a 6-type pool) with personal targets and
rewards scaled by the player's level, resetting at local midnight.

### Key classes & files
- `src/main/java/me/petcore/quests/QuestType.java` — 6 objective types.
- `QuestInstance.java` — one active quest's state.
- `QuestManager.java` — generation, live progress sampling, claim flow, streaks.
- `QuestStore.java` — SQLite persistence.
- `QuestGui.java` — the menu.
- `QuestCommand.java`, `ResetQuestsCommand.java`.

### How it works
**`QuestType` enum** (icon material, display name, unit, verb, and a `target(level)` formula):
- `PLAYTIME` (CLOCK): `max(15, round(20 + level*0.25))` minutes.
- `HATCH_EGGS` (EGG): `max(3, round(5 + level*0.8))`.
- `COLLECT_FRAGMENTS` (PRISMARINE_SHARD): `round(50 + level*8.0)`.
- `BREAK_PILES` (IRON_PICKAXE): `round(70 + level*13.0)`.
- `COLLECT_RUBIES` (RED_DYE): a power-law currency-target curve anchored at level 65
  (`atRef=600_000`, floor 400, exponent 4.5).
- `COLLECT_COINS` (SUNFLOWER): same curve shape, `atRef=1_500_000`, floor 1_000.

**Progress tracking (`QuestInstance`):** each has a `baseline` (a snapshot of the live metric at
generation time) and a `progress` high-water-mark (delta since baseline, never allowed to decrease).
`QuestManager.currentMetric()` reads live values: playtime ticks/1200,
`PlayerProgressStore.getPlayerEggsHatched`/`getFragmentsCollected`/`getPilesBroken`,
`RubiesStore.get`/`CoinsStore.get`. Sampling happens on `getQuests`/`claim`/`sampleOnline`, plus a
1-second `tickSample()` timer polling every online player — because progress only ever increases
(`Math.max(quest.progress(), delta)`), a temporary drop in a live metric (e.g. spending coins) never
rolls quest progress backward.

**Generation:** deterministic per (uuid, day) seed feeding a `Random`, which shuffles the 6 types and
picks 3 distinct ones into fixed slots. Reward: a 100-300 "reward points" roll → `coins = points*30`,
`rubies = points*10`; a separately weighted credit roll (`CREDIT_AMOUNTS = {1..10}`, weights
`{30,25,15,10,7,5,3,2,2,1}` summing to 100, exact percentage chances shown in-GUI via a lore book).
Rewards are frozen at generation time; live personal `coinsmulti`/`rubymulti` and a zone-unlock display
bonus (5/10/15/25/35/45/60% keyed off highest unlocked wall via `WallManager.isUnlocked`) are only
applied at display/claim time (`rewardCoins()`/`rewardRubies()`), not baked into storage.

**Completion/reward flow (`claim()`):** validates not-expired/not-already-claimed/complete, marks
claimed in the database, grants coins+rubies directly to `CoinsStore`/`RubiesStore`, dispatches console
`credits give <player> <amount>` for the credit reward. When all 3 of a day's quests become claimed, a
daily streak counter bumps (only if the previous claim day was exactly yesterday, else resets to 1).

**Daily reset:** expires at the next server-local midnight (`LocalDate`/`ZoneId.systemDefault()`), stored
per-player as a `reset_at` epoch-millis column. `/quests reset [player|all]` and `/resetquests
<player|all>` force immediate regeneration with fresh variance.

**Storage (`QuestStore` → `quests.db`):** table `daily_quests(uuid, slot, day, type, target,
reward_coins, reward_rubies, baseline, progress, claimed, reset_at, reward_credits, PK(uuid, slot))`
(with `addColumnIfMissing` migrations for `reset_at`/`reward_credits`); table `quest_streak(uuid PK,
streak, last_claim_day)`.

**GUI (`QuestGui`):** 45-slot inventory — clock at slot 4 (countdown to reset + zone bonus %), a
credit-chances "book" item at 39, close at 40, three quest slots at fixed indices `{20, 22, 24}`.
Progress bars render as a 14-character `■` bar (`&b` for filled, `&8` for remainder) plus a percentage.
Colors: claimed = green, complete-but-unclaimed = yellow with an enchant glint, in-progress = blue.

### How to recreate from scratch
1. Define a small set of quest "types," each mapping to a live, already-tracked metric in your plugin,
   with a target formula scaled by player level (or another progress axis) so quests stay meaningful at
   any stage of the game.
2. Generate each day's quest set deterministically from a (player, calendar-day) seed so a server
   restart mid-day doesn't reroll anyone's quests, but a genuinely new day always does.
3. Track progress as `max(storedProgress, currentMetric - baselineSnapshot)` so metrics that can
   temporarily decrease (spendable currencies) never regress quest completion.
4. Poll all online players' progress periodically (every second is plenty) so the GUI never shows stale
   numbers even if a player never re-opens it.
5. Reset at local midnight using calendar-date comparisons, not a fixed "24 hours since last reset"
   timer, so everyone resets together regardless of when they first logged in.
6. Track a claim-streak counter that only increments on a truly consecutive day (compare to "yesterday"
   exactly) and resets otherwise, for a "daily login/completion" incentive loop.

### Config keys involved
No dedicated `quests:` block in `config.yml` — target formulas/reward curves are hardcoded constants in
`QuestType`/`QuestManager`. Referenced only via the announcements rotation text: `"Complete your daily
quests for bonus rewards! Check /quests every day."`

---

## 11. Teams

### What it is / purpose
`/team` — cooperative player groups with an owner/member roster, an invite/join/kick/disband flow, a
Trophies currency earned from mining, and a permanent stat-upgrade tree paid for with those trophies.

### Key classes & files
- `src/main/java/me/petcore/TeamManager.java` (top-level package, **not** under `me.petcore.teams`).
- `src/main/java/me/petcore/teams/TeamCommand.java` — the `/team` subcommand router.
- `TeamUpgradeGUI.java` — the upgrade-tree GUI.
- `PileTrophyListener.java` — (see caveat below).
- (The `__MACOSX` folder alongside these is macOS zip-extraction junk — not real source, ignore it.)

### How it works
**Storage:** `Teams/teams.db` (SQLite) — tables `teams(team_name PK, display_name, owner_uuid,
owner_name, trophies, created_at, last_daily_trophy, team_xp)`, `team_members(team_name, player_uuid,
player_name)`, `team_upgrades(team_name, upgrade_id, tier)`, `team_message_prefs(player_uuid,
message_key, enabled)` (absence of a row = enabled by default). Upgrade-tree tuning (curve exponents,
per-upgrade base costs) lives in `Teams/teams.yml`, a config file separate from the main plugin
`config.yml`.

**`/team` subcommands:** create, info, list, disband (2-step confirm within a configurable window),
invite, join, leave, kick, upgrades (opens `TeamUpgradeGUI`), chat / chat-toggle, daily, options; OP-only:
test, givetrophy/settrophy/removetrophy, resetupgrades. `/teamdelete <teamName>` (separate top-level
command, permission `petcore.teamdelete`) force-deletes bypassing the owner-only disband confirmation.

**Pile-trophy mechanic:** `PileTrophyListener` itself listens for a PDC marker
(`custompiles:custom_pile_hitbox`) belonging to an *external, unrelated* plugin's namespace — in this
codebase it never actually fires against Petcore's own piles. The real hook into Petcore's own pile
system is `TeamManager.onPileBroken(Player)`, called directly from `me.petcore.pile.PileManager` at
pile-break payout time: a flat trophy-chance roll (config `team.trophy_chance_per_member`, plus each
team's purchased "Trophy Chance" upgrade tier as a bonus) grants 1-15 trophies, skewed toward the low
end via `pow(random, 4)`.

**`TeamUpgradeGUI`:** 45-slot inventory. Slot 4 = info/summary. Purchasable upgrades at fixed slots:
10/12/14/16 = Coins/EXP/Ruby/Damage multiplier tiers; 29/31/33 = Trophy Amount, Trophy Chance, Walk
Speed; slot 40 = close. Left-click buys 1 tier; right-click buys 10; shift-right-click buys the maximum
number of tiers the team can currently afford. Costs follow roughly 300-500 trophies at tier 1,
multiplied by ~1.12 per additional tier, capped around 50 tiers per upgrade.

### How to recreate from scratch
1. Model a "team" as (name, owner UUID, member UUID set, a currency balance, and a map of
   upgrade-id → purchased tier), persisted in SQLite with normalized member and upgrade tables rather
   than one denormalized blob.
2. Implement invite/join/leave/kick/disband as pure state transitions with simple guardrails (owner-only
   destructive actions, an invite expiry window, a disband confirmation window to prevent misclicks).
3. Earn the team currency from an existing gameplay loop (here: a small per-hit chance on breaking a
   resource node) rather than inventing a new grind — this keeps team progression tied to normal play.
4. Build one upgrade-tree GUI where each purchasable stat has its own slot, its own tier-based cost
   curve, and supports "buy 1 / buy 10 / buy max affordable" click variants for convenience at scale.
5. Keep per-player message-preference toggles (e.g. "mute team join/leave spam") as a sparse table where
   only actual overrides are stored — absence of a row is the default (usually "enabled").

### Config keys involved
No `teams:` block was found scoped inside the main `config.yml` (or none surfaced in this review) —
team-specific tuning (trophy chance, upgrade cost curves) lives in the separate `Teams/teams.yml` file.

---

## 12. Client-Sided Pets (Placed Pets) and Eggs / Pet Hatching

Two closely related systems: **Eggs** produce pet *items* through a hatching gacha, and **Placed Pets**
lets a player place a hatched pet item down in their plot as a real, visible companion entity with its
own leveling curve.

### 12a. Placed Pets

### What it is / purpose
Letting a player place a hatched pet item in their personal plot as a visible mob that passively
contributes small permanent stat-multiplier bonuses and can level up over time.

### Key classes & files
All under `src/main/java/me/petcore/placedpets/`: `PetSlotCommandHandler.java`,
`PickupAllPetsCommandHandler.java`, `PlacedPetGui.java`, `PlacedPetKeys.java`/`PlacedPetsKeys.java`,
`PlacedPetsListener.java`, `PlacedPetsManager.java`, `PlacedPetsMappings.java`, `PlacedPetsStore.java`,
`PlotCommand.java`, `PlotVisitGui.java`, `PlotVisitManager.java`.

### How it works
**Rendering — confirmed to be real server entities, not packets.** Placing a pet spawns an actual
`Mob`-subtype entity of the pet's underlying `EntityType` (`PlacedPetsManager`, ~line 291:
`spawnLoc.getWorld().spawn(spawnLoc, mapping.mob().getEntityClass(), e -> {...})`), then:
`mob.setAI(false)`, `mob.setRemoveWhenFarAway(false)`, `e.setGravity(false)`, `e.setSilent(true)`,
`e.setPersistent(false)` (deliberately — SQLite is the single source of truth, so the entity is never
allowed to also persist via chunk NBT, or a server restart would duplicate it), `setCollidable(false)`,
and its `Attribute.SCALE` attribute set to `0.5` (pets render at half size). A separate `TextDisplay`
entity floats above it as the name/level hologram. Visibility is controlled with plain Bukkit API —
`entity.setVisibleByDefault(false)` plus a per-viewer `showEntity`/`hideEntity` — **not** ProtocolLib:
the only ProtocolLib usage found anywhere in the whole `me.petcore` tree is unrelated packet-based boss
head rotation in `me.petcore.boss.BossFacingTask`. Placed pets, eggs, teams, stats, and TopMoneySpent
are all confirmed free of packet-level manipulation.

**Data carried on the item / synced to the entity's PDC:** owner UUID, pet id, gold/rainbow tier byte
(0/1/2, from the pet-conversion "machine" system), per-multiplier float values (`coinsmulti`,
`rubymulti`, `expmulti`, `damagemulti` — preferring values already saved on the held item's PDC, falling
back to `mapping.<multi>() * bonus` where `bonus` is the gold/rainbow conversion multiplier from
`convertpets.conversion.gold-multiplier`/`convertrainbow.conversion.rainbow-multiplier`, both default
5.0), and `petExp`/`petLevel`.

**Plot binding:** a single shared WorldGuard region literally named `"plot"` — not a per-player
instanced world; every player's placed pets exist in the same physical space, with visibility gating
(via the `showEntity`/`hideEntity` mechanism above) making each player normally only see their own pets.

**Persistence:** SQLite `placedpets.db` — `placed_pets(entry_uuid PK, owner_uuid, pet_id, tier,
coinsmulti_value, rubymulti_value, expmulti_value, damagemulti_value, world, x, y, z, pet_exp,
pet_level)` (indexed on `owner_uuid`), and `placed_pet_player_totals(uuid PK, pet_limit,
applied_coinsmulti_total, applied_rubymulti_total, applied_expmulti_total,
applied_damagemulti_total)` — the running sum of every placed pet's contribution, so the manager never
has to re-sum every pet row just to know a player's current total bonus.

**Leveling:** `expThresholdFn(level) = base-exp * exp-growth-per-level^(level-1)`
(`placed_pets.leveling.base-exp` default 1000, `placed_pets.leveling.exp-growth-per-level` default
1.12). Only the damage-multiplier reward is ever permanently applied to the owning player
(`leveling.pet-level-rewards.<rarity>.damage-multi-per-level`) — coins/ruby/EXP contributions from a
placed pet's *level* stay at 0 by design, to avoid double-counting with the pet's own base multiplier.

**Plot-visit system:** `PlotVisitManager` tracks a visitor→host map, swaps which owner's pets are shown
to that visitor (via the same visibility API), and teleports via the shared plot warp. `PlotVisitGui`:
54-slot, one player-head per player who has pets placed (sorted by pet count), paginated (exit at 49,
next at 53). `/visit <player>` and `/stopvisiting` are friendlier top-level aliases over the same
`/plot visit`/`/plot exit` logic.

### 12b. Eggs / Pet Hatching

### What it is / purpose
A loot-box hatching mechanic, one `EggModule` per progression zone, that spawns a placeable "egg" world
object; clicking/breaking it starts an animation, then rolls a rarity-weighted pet and grants either a
regular placeable pet item or (rarely) a secret "companion"/"Huge" pet.

### Key classes & files
`src/main/java/me/petcore/eggs/` (26 files) — key ones: `EggType.java` (per-zone definitions),
`EggManager.java`/`EggModule.java` (per-zone lifecycle), `EggGui.java`, `EggProgress.java`/
`EggProgressStore.java`, `Pet.java`/`PetData.java`/`RegularPet.java` (physical-item pets),
`CompanionPet.java`/`CompanionPetData.java` (secret Huge pets, no physical item), `HatchAnimationTask.java`,
`HatchRewardStore.java`, `PetIndexGui.java`/`PetIndexListener.java`, `LuckBonusGui.java`,
`GetPetCommandHandler.java`, `HatchX9CommandHandler.java`.

### How it works
**`EggType` enum** — one constant per zone (`FOREST`, `MUSHROOM`, `DESERT`, `BADLANDS`, `ICESPIKES`,
`STONE`, `DEEPSLATE`, `BEACH`), each carrying: zone id/display name, its `hatch_rewards.yml` filename,
command-name prefixes, GUI slot layouts (an 8-slot pet grid e.g. `{20,21,22,23,24,30,31,32}` plus a
second index array for the Pet Index), a zone-specific egg head texture, and an ordered list of
`PetData`/`CompanionPetData` entries. Each `PetData` carries: id, display name, color code, rarity-tier
label/color, three currency-multiplier base values plus a damage-multiplier base value, a cumulative
luck-roll threshold, a display percentage string, a "luck-cap" value, and the underlying vanilla
`EntityType` name used when placed. Forest's roster (as one concrete example): Chicken (0.16 threshold,
"40%" shown, 4000 luck-cap) → Cow → Sheep → Pig → Cat → Wolf → Bee (1.10 threshold, "1.3%" shown, 9950
luck-cap), plus one secret `CompanionPetData` ("donkey" → "Forest Golem", "0.04%" shown, luck-cap 2500).
Rarity/threshold and luck-cap both climb steeply zone-to-zone (Forest's Bee threshold is 1.10; Mushroom's
equivalent-position pet is 10.90) — later zones are dramatically rarer per attempt.

**Hatching flow:** `EggManager.spawnEgg` creates a rotating `ItemDisplay` visual plus an `Interaction`
hitbox in the world. Interacting starts `HatchAnimationTask` — a ~60-tick floating-skull spin animation
— after which `awardPet` rolls: first a low-probability secret-pet lottery (the companion/Huge pet),
then, if that misses, a cumulative-threshold roll across the zone's regular `PetData` list (each pet's
threshold value from the enum defines its relative weight in the cumulative distribution). A player's
"luck" stat (from `PlayerStatsStore`, boosted by Discord-link bonus and gamepasses) grants best-of-N
extra rolls rather than directly biasing the odds. On a regular-pet result, `PlacedPetKeys.tag(...)`
marks the reward item as placeable; a further "Magic Eggs" gamepass check may upgrade the reward's tier
to Gold (1% chance) or Rainbow (0.1% chance) using the same `convertpets`/`convertrainbow` multiplier
constants used by the standalone pet-conversion "machine" feature.

**Egg GUI (`EggGui`):** 54 slots — the zone's pet roster fills its configured slot array; slots
48/49/50 = hatch x1 / x3 / x9 (x9 gated behind a gamepass, toggled per-player via `/hatchx9`).

**Pet Index (`PetIndexGui`):** a per-zone unlocked/locked pet-collection browser plus a cross-zone
"View All Eggs" picker grid (a 4×7 template), backed by `EggModule.wireIndex` once every `EggModule` has
been constructed.

**Luck breakdown (`LuckBonusGui`):** a 27-slot read-only popup (opened by `/luck` with no arguments)
showing the player's effective hatch luck as a base value plus a Discord-link flat bonus plus a
gamepass multiplier, capped by their configured luck-cap.

**Cross-system link:** `EggManager.getPetItem(petId)`/`EggModule.getPetItem(petId)` is the canonical
resolver for "what item represents pet X" — `Petcore.java` iterates every `eggModules` entry calling
this to feed both `HugeManager.setRewardFn` (Huge Pets GUI) and `placedPetsManager.setRewardResolver`
(Placed Pets), so the egg system's `HatchRewardStore` is the single source of truth for a pet's physical
item form everywhere else in the plugin.

### How to recreate from scratch
1. Model each hatch "pool" (zone) as a list of weighted entries (an id, a cumulative threshold or raw
   weight, a resulting reward-item template, and a rarity label/color) plus one very-low-weight "secret"
   entry that grants a different kind of reward entirely (no physical item, just an unlock flag).
2. Implement the roll as: first check the secret-pool chance; if it misses, roll against the cumulative
   thresholds of the regular pool. A "luck" stat should grant *extra attempts* (best-of-N) rather than
   skewing the underlying odds directly — this keeps the math simple and auditable.
3. Play a short placeholder/skull-spin animation between "player interacts" and "reward is granted" for
   feel, using a display entity plus a delayed task rather than blocking anything.
4. Tag reward items with a small PDC marker so a later "place this pet" feature can identify and consume
   them.
5. For "placeable" companions: spawn a real (visually shrunk, AI-disabled, non-solid, non-persistent)
   mob entity server-side, track its owner/stats/position in your own database (never rely on chunk
   persistence), and use native per-player entity visibility toggling (not packet libraries) to make
   pets visible only to their owner (and anyone explicitly "visiting" that owner's space).
6. Give every placed companion its own small EXP/level curve and only let leveling grant one
   restrained stat type (e.g. damage only) to avoid it snowballing every other economy lever at once.

### Config keys involved
`placed_pets.max_per_player` (5), `placed_pets.leveling.base-exp` (1000),
`placed_pets.leveling.exp-growth-per-level` (1.12), `leveling.pet-level-rewards.<rarity>.*` (per-rarity
level-up reward rates), `convertpets.conversion.gold-multiplier` (5.0),
`convertrainbow.conversion.rainbow-multiplier` (5.0).

---

## 13. Stats Command & Right-Click

### What it is / purpose
`/stat [player]` (aliases `/stats`) opens a GUI showing a player's gear and stat summary; right-clicking
another player opens the same GUI for them.

### Key classes & files
- `src/main/java/me/petcore/stats/PlayerStatsGUI.java` — **note the package/file mismatch**: this file
  physically lives under `me/petcore/stats/` but its first line declares `package me.petcore.chat;` —
  it is the *same* class `Petcore.java` wires to `getCommand("stat")` as `me.petcore.chat.PlayerStatsGUI`
  (constructed and registered ~line 590 of `Petcore.java`, with event-listener registration delayed one
  tick to dodge a Paper `PluginEnableEvent` freeze). There is no separate/duplicate class — just a
  file physically misplaced relative to its declared package.
- `src/main/java/me/petcore/stats/PlayerStatsStore.java` — the actual SQLite data layer (package
  `me.petcore.stats` correctly matches its file location), used across eggs/teams/placed-pets/economy.

### How it works
**`PlayerStatsStore`:** SQLite `stats.db`, table `player_stats(uuid PK, coins, coinsmulti, damage,
damagemulti, exp, expmulti, rubymulti, credits, luck, breakdamagemulti, luckcap)` plus an
`applied_item_stats` table (tracks which equipped-gear stat bonuses have already been applied, so
swapping gear doesn't double-count). Constant list `STATS` enumerates every tracked column; `RESET_EXEMPT`
(`credits`, `luckcap`) marks the two columns a full data reset must never touch (credits is store
currency, luckcap is a player preference, neither is "progress").

**`/stat` GUI (`PlayerStatsGUI`):** built from its own small `Stats.yml` template (helmet/chest/legs/
boots/main-hand/off-hand/skull/barrier slots plus a free-form `profile-lore` block), driven mostly by
PlaceholderAPI placeholder resolution rather than reading `PlayerStatsStore` columns directly. 54-slot
inventory (`StatsHolder` marker, built via `Bukkit.createInventory(new StatsHolder(), 54, ...)`): a
player-head skull at a configured slot, a "Close" button, and one slot each for helmet/main-hand/
chestplate/off-hand/leggings/boots — rendering the target's actual equipped item if online, or an
"(offline)" placeholder item if not.

**Right-click handler:** `PlayerStatsGUI.onRightClickPlayer(PlayerInteractEntityEvent)` (priority
`NORMAL`) — right-clicking another player's entity opens this same GUI for that target, exactly like
running `/stat <name>` yourself.

### How to recreate from scratch
1. Keep your stat *data* (a flat per-player row of named numeric columns: currencies, multipliers, a
   luck value, etc.) in one small SQLite table, separate from any GUI code.
2. Build the `/stat` GUI as a template-driven inventory (configurable slot positions + lore) that pulls
   most of its displayed numbers through your placeholder API rather than hardcoding column reads, so
   server admins can reskin the layout without a code change.
3. Add a `PlayerInteractEntityEvent` listener that opens the exact same GUI-building method used by the
   command, keyed off the clicked entity being a `Player`.
4. Mirror equipped-gear slots (helmet/chest/legs/boots/main-hand/off-hand) live from the target's actual
   inventory when online, falling back to placeholder items when they're offline (offline players still
   have a UUID and stat row, just no live `Player`/`PlayerInventory` object).
5. Mark a small "never touch on a season/data reset" exemption list (e.g. purchased currency, saved
   preferences) so a full data wipe command can't accidentally erase things that aren't "progress."

### Config keys involved
No dedicated `config.yml` section — the GUI's slot layout and lore templates live in their own
`Stats.yml` file in the plugin data folder.

---

## 14. Top Money Spent

### What it is / purpose
`/topspent` — a top-3 real-money-spending leaderboard, manually maintained by admins (not
automatically wired to any purchase system), driving three Citizens-style NPC skins plus a
FancyHolograms reload.

### Key classes & files
- `src/main/java/me/petcore/TopMoneySpent/TopSpent.java` — the entire feature, one file (unusual
  capitalized package name, `me.petcore.TopMoneySpent`, matching the folder name literally).

### How it works
**Tracking is entirely manual.** No automatic hook exists anywhere in the codebase from the Shop, Store,
or any other purchase system into this feature — an admin runs `/topspent <player> <amount>`
(permission `petlifymines.admin`) to log real-money spending, or `/topspent clear <player>` /
`/topspent clearall` to wipe records. There is no shared "amount spent" column anywhere else in
`PlayerStatsStore` or elsewhere that this feature reads from.

**Storage:** a flat YAML file `topspent.yml` (`players.<name>: amount`, a running double total per
player name — not UUID-keyed).

**Recalculation (`recalculateTop`):** runs asynchronously (to avoid lag spikes fetching offline
LuckPerms rank data), sorts all recorded spenders descending by amount, keeps the top 3, and for each
resolves a LuckPerms prefix directly via the LuckPerms API (`user.getCachedData().getMetaData()
.getPrefix()`) rather than through PlaceholderAPI's `%luckperms_prefix%` — chosen specifically because
PAPI's placeholder only reliably resolves for currently-online players, while `LuckPerms
.getUserManager().loadUser(uuid).join()` loads straight from LuckPerms' own storage regardless of online
status. The raw LuckPerms prefix (which may be MiniMessage, e.g. `<gradient:#FF0000:#0000FF>Owner
</gradient>`) is rendered into legacy color codes via MiniMessage→legacy serialization, then any
leftover stray/unmatched tag is stripped with a regex cleanup pass — done in that order specifically so
real gradient/hex tags are rendered *before* the "strip stray tags" pass runs (an earlier bug stripped
every `<...>` tag unconditionally, including real color tags, leaving ranks uncolored).

**Result application:** back on the main thread, the top-3 cache (`topCache`, a `ConcurrentHashMap<Integer,
TopSpender>`) is updated, then for each of the 3 ranks a console command `npc skin topspent_<1|2|3>
<playerName>` is dispatched (assumes the Citizens NPC plugin manages NPCs literally named
`topspent_1/2/3`; empty ranks get a fixed fallback skin UUID), followed 10 ticks later by
`fancyholograms reload` if the FancyHolograms plugin is present — i.e. **the leaderboard display itself
is not a GUI or a native hologram this plugin controls**; it's two other plugins (Citizens, FancyHolograms)
reacting to console commands this class dispatches.

**Placeholders:** a nested `TopSpentPlaceholders` PAPI expansion (identifier `topspent`) exposes
`%topspent_name_<1-3>%` (rank-prefixed player name) and `%topspent_amount_<1-3>%` (formatted to 2
decimal places) for use inside those external NPC/hologram plugins' own templates.

### How to recreate from scratch
1. Store manual "amount spent" entries in a simple flat file keyed by player name (or better, UUID) —
   this is intentionally an admin-log, not an automatically-tracked metric, so there's no event
   wiring to replicate unless you choose to add it.
2. Recompute the sorted top-N asynchronously whenever the log changes, to avoid blocking the main thread
   on any slow rank-lookup API calls.
3. If you want a real rank/prefix next to a name, query your permissions plugin's API directly rather
   than through a generic placeholder API, since the generic path often only works for currently-online
   players.
4. If your server already has an NPC plugin and/or a hologram plugin, the simplest "leaderboard display"
   is dispatching their own console commands to update skins/text, rather than reinventing rendering —
   just be careful to gate any of that dispatch behind checking the target plugin is actually installed.
5. Expose a small PlaceholderAPI expansion so other systems (menus, holograms, NPCs) can read the
   current top-N without needing a direct Java dependency on this class.

### Config keys involved
None in `config.yml` — data lives entirely in `topspent.yml`; behavior (which NPC names, whether
FancyHolograms integration runs) is hardcoded, not YAML-configurable.

---

## 15. Walls

### What it is / purpose
Physical zone gates between the plugin's progression zones (Forest → Mushroom → Desert → Badlands →
Ice Spikes → Stone → Deepslate → Beach). Each wall is simultaneously: (a) a WorldGuard region full of
real blocks shown as fake black-stained-glass to any player who hasn't unlocked it, and (b) a
level/coin/ruby/rebirth-gated purchase GUI, with a spectator-mode "break wall" cutscene played on
successful unlock.

### Key classes & files
- `src/main/java/me/petcore/walls/WallConfig.java` — record: `id` ("zone1".."zone7"), `region`
  (WorldGuard region name, "Wall1".."Wall7"), `camX/camY/camZ/camYaw/camPitch` (break-cutscene camera
  pose), `reqLevel`, `reqCoins`, `reqRubies` (doubles — some walls cost in the tens/hundreds of
  billions), `reqRebirth`, `nextZone`.
- `WallManager.java` — owns the hardcoded 7-entry `List<WallConfig>` (see below), WorldGuard region
  block caching, per-player unlock state (backed by `WallUnlockStore`, in-memory-cached per online
  player), fake-block display, and the break-cutscene animation.
- `WallGUI.java` — the unlock-purchase GUI.
- `WallListener.java` — join/quit cache load/unload, move handling (freezes the player during the
  animation; pushes back anyone who somehow crosses into a locked region; force-warps stragglers), block
  interact (right-click a wall opens `WallGUI`; any click resends fake blocks), chunk-load (resend fake
  blocks), block-break (cancelled inside a locked wall's region).
- `WallCommand.java` — `/unlockzone <zoneId>` and `/makewall <zoneId>` (admin re-lock for testing).
- `WallUnlockStore.java` — SQLite `walls.db`, table `wall_unlocks(uuid, zone, PK(uuid, zone))` — row
  presence = unlocked.
- `WallPlaceholderExpansion.java` — one PAPI expansion instance per zone id, returns
  `§a§lUNLOCKED`/`§c§lLOCKED`.
- `WallStatusPlaceholderExpansion.java` — registers `%status1%`..`%status8%` (`MAX=8`), one expansion
  instance per index (PlaceholderAPI splits an identifier at its first underscore, so each numbered
  identifier genuinely needs its own instance) — `status1` is always unlocked (Forest), `status2..8`
  map to `wallManager.isUnlocked(player, "zone" + (index-1))`.

### How it works
**Wall list (`WallManager`'s constructor):** 7 hardcoded `WallConfig` entries (`zone1`.."zone7"),
extensively commented with the economy-balance derivation behind each cost — an explicit "time a player
should spend earning this wall's cost" target chart (Forest 45m, Mushroom 2h30m, Desert 6h, Badlands
12h, Ice Spikes 22h, Stone 48h, Deepslate 168h/7 days), derived from average pile HP per zone × a
calibrated piles-per-hour constant × the target hours, with rubies fixed at ~40% of the coin cost.
Wall7 (Deepslate→Beach) is the most extreme gate: 880 billion coins / 352 billion rubies, `reqLevel`
210, `reqRebirth` 5.

**Block caching (`loadBlocks`):** hardcoded to a world literally named `"test"`. For each configured
wall, looks up its WorldGuard region by name and caches every block coordinate inside its bounding box
into `Map<String, List<Location>> blockCache`.

**Unlock state:** `WallUnlockStore` (SQLite) is the source of truth; `WallManager` keeps an in-memory
`unlockedCache` per online player (`loadPlayer`/`unloadPlayer` on join/quit) so the hot paths (chunk
load, movement) never hit the database.

**Fake-block display (`sendWall`/`sendWallChunk`):** sends `BLACK_STAINED_GLASS` block-changes
(`player.sendBlockChange`) for every cached block of a locked wall — purely client-side, the real blocks
underneath are untouched. Resent at both +1 tick and +20 ticks after the initial send, specifically to
survive a player's chunks finishing loading late (rejoin timing).

**Unlock GUI (`WallGUI.openGUI`):** uses a 9-slot `InventoryType.DISPENSER`-shaped inventory titled
`&8Unlock Zone`, a single `EMERALD` at slot 4 named `&aUnlock Zone` with lore listing every requirement
(level, coins, rubies, rebirth count if >0) and "&eClick to unlock". On click, all four requirements are
validated (level via `LevelStore`, coins/rubies via `CoinsStore`/`RubiesStore`, rebirths via
`RebirthStore`) with itemized failure messages for anything missing. On success: coins and rubies are
subtracted (level/rebirth are pure gates, never consumed), the GUI closes, `WallManager.setUnlocked`
flips the flag, `WallManager.hideWall` plays the break cutscene, and `grantZoneTag` awards a matching
cosmetic tag from `TagManager` (zone1→"mushroom" tag, zone2→"desert", ... zone7→"beach").

**Break cutscene (`hideWall`):** if a wall's camera coordinates are still the unset `(0,0,0,0,0)`
placeholder, the whole cutscene is skipped (this specifically fixes a bug where players were
spectator-teleported into the void for zones whose physical wall/camera position hadn't been configured
yet) and the unlock just applies immediately with a plain chat message. Otherwise: switch to
`GameMode.SPECTATOR`, teleport to the configured camera pose, play beacon/guardian-curse sounds; at
tick 10, play thunder and start an 8-wave block-removal sequence (each wall block randomly assigned a
wave 1-8, removed with a glass-break sound whose pitch rises with wave number); at tick 90, play
level-up/beacon-success sounds; at tick 105, restore the player fully — clear spectator target, restore
their previous game mode, teleport back to their pre-cutscene location, and **explicitly restore their
`allowFlight`/`isFlying` state**, since switching to `SPECTATOR` (which always grants flight) and back
silently strips whatever flight state a player had from an external fly permission/plugin.

### How to recreate from scratch
1. Represent each "gate" as a small config record: an id, a physical region reference, a break-cutscene
   camera pose, and a set of numeric requirements (any combination of level/currencies/prestige count).
2. Cache the region's block coordinates once at startup (or on first use) so per-tick/per-move checks
   never need to re-query your region-plugin API.
3. Render "locked" purely client-side via fake block-change packets sent only to locked players — never
   modify the actual world blocks. Resend the fake state on join/chunk-load and shortly after, to cover
   late-loading chunks.
4. Persist per-player unlock flags in a tiny SQLite table (or equivalent), mirrored in an in-memory cache
   keyed by UUID, populated on join and dropped on quit.
5. Gate a purchase GUI on all configured requirements simultaneously, giving itemized failure feedback
   for whichever ones aren't met, and only deduct consumable costs (currencies) — never destructively
   "consume" a level or a prestige count, those are pure gates.
6. For a cinematic reveal: switch the player to spectator mode, move them to a fixed camera position,
   and run a short scheduled sequence of block removals/sound cues before restoring their original game
   mode, location, and any transient state (flight) that switching modes silently clears.
7. Skip the cinematic gracefully (falling back to an instant, message-only unlock) if a wall's cutscene
   position was never actually configured, so an incomplete admin setup can't strand a player somewhere
   broken.

### Config keys involved
No dedicated `walls:` block exists in `config.yml` — wall definitions (costs, positions, region names)
are hardcoded directly in `WallManager`'s constructor. Related/shared config: `warp.zone-ids.zone1..8`
(shared zone-id naming with `WarpManager`), `boss.warp-name: petlify`, `boss.zones.*` (per-zone boss
tuning), and cosmetic tutorial-hologram prompt text referencing walls (`config.yml` ~line 1900:
`"🚧 NEXT ZONE"` / `"▶ RIGHT CLICK WALL ◀"` / `"💥 Break the Wall"`).

---

## 16. Zones

### 16a. Wall/progression zones
See §15 (Walls) — the tiered Forest→Beach progression zones *are* the wall system; there is no separate
"zone" boundary/detection mechanism for them beyond the WorldGuard regions the walls themselves occupy.

### 16b. Locked-region system (a *different*, simpler feature)

### What it is / purpose
A basic "keep players out of an admin-designated region" system (ported from `lockedzone.sk`), distinct
from the wall/progression zones — meant for out-of-bounds or under-construction areas rather than
tiered game progression.

### Key classes & files
- `src/main/java/me/petcore/zones/LockedZoneListener.java` — `REGION_NAME = "locked"` (a single
  hardcoded WorldGuard region name, not configurable).
- `LockedRegionBypassCommand.java` — `/lockedregionbypass <player>` and `/lockedregionbypasslist`.

### How it works
Detection is plain WorldGuard `RegionQuery.getApplicableRegions` lookups — there is no "entered a
region" event in Bukkit/WorldGuard, so this checks on every `PlayerMoveEvent` (only when the player
crosses a block boundary, to avoid a lookup every tick) and every `PlayerTeleportEvent`, both at
`EventPriority.MONITOR, ignoreCancelled = true`. If WorldGuard isn't installed, the check always returns
false and the whole feature silently no-ops.

**Effect:** if the destination location is inside the `"locked"` region and the player lacks bypass, the
console command `warpplayer <player> petlify` is dispatched — forcing them to the `"petlify"` warp
(defined via `/setwarp` in `WarpManager`'s `warps.yml`) rather than a direct teleport. There is no
block/interaction restriction here (unlike Walls, which cancels block-break/interact) — this is purely a
location-based redirect.

**Bypass:** `Map<UUID, String> bypass` (uuid → name at grant time), persisted to
`locked_region_bypass.yml` under `bypass.<uuid>.name` so bypass status survives restarts.
`/lockedregionbypass <player>` (permission `skript.op`) toggles bypass for a target — works for online
or offline players via `OfflinePlayer`; `/lockedregionbypasslist` prints every current bypass holder.

### How to recreate from scratch
1. Since there is no native "player entered a region" event on most platforms, check on every
   `PlayerMoveEvent` (gated to only fire when the player actually crosses a block boundary, not every
   tiny sub-block movement) and every teleport event.
2. On detecting the player is now inside the restricted region and lacks a bypass flag, redirect them
   (a full teleport, or — as here — dispatching a separate "send this player to warp X" command so the
   redirect logic is shared with your normal warp system) rather than just cancelling their movement.
3. Persist bypass grants to disk (not just memory) so a server restart doesn't silently re-enable the
   restriction for someone who was explicitly exempted.
4. Keep this deliberately simple and separate from any tiered-progression wall system — a basic
   "keep-out zone" doesn't need costs, GUIs, or unlock animations, just a boolean gate and a redirect.

### Config keys involved
None — the region name (`"locked"`) and the redirect warp id (`"petlify"`) are hardcoded constants, not
read from `config.yml`.

---

## 17. Join/Leave Messages (Welcome System)

### What it is / purpose
Java port of `welcome.sk`: formats join/quit broadcast lines with a live online-player-count
placeholder, distinguishes a genuinely first-time join from a returning one, and layers a small
"welcome the new player" mini-game on top — the first player to run `/wc` within a short window after
a new player's first join claims a configurable reward and gets credited in a server-wide broadcast.

### Key classes & files
- `src/main/java/me/petcore/chat/WelcomeManager.java` — the whole feature: `Listener` for
  `PlayerJoinEvent`/`PlayerQuitEvent`, plus `CommandExecutor`/`TabCompleter` for `/welcome` (`/wc`),
  `/removejoin`, `/testjoin`, `/testleave`.
- `join_data.yml` (plugin data folder) — persists the flat `joined: [<uuid>, ...]` list backing
  first-time-join detection across restarts.

### How it works
**New-vs-returning detection:** `hasJoined` is a `Set<UUID>` loaded from `join_data.yml` at
construction. `onJoin` checks `hasJoined.contains(uuid)`: if present, sends `welcome.join-message`; if
absent, adds the uuid, calls `saveJoinData()` immediately, sends `welcome.new-join-message`, and arms a
single-slot `pending` `NewPlayerEntry` (uuid/name/joinTime) — there is only ever one pending new player
at a time, a second first-time join before the first is welcomed simply replaces it.

**Online-count placeholder:** both join and quit messages interpolate `%count%` from
`Bukkit.getOnlinePlayers().size()` (quit subtracts 1 to account for the leaving player still being
counted at the moment the event fires) — no PAPI dependency for this specific placeholder.

**`/wc` (welcome) flow:** requires a `pending` entry; rejects if `windowSeconds()` (config
`welcome.window-seconds`, default 10) has elapsed since `pending.joinTime`; rejects if the same welcomer
already attempted once (`pending.attempted` `Set<UUID>`, one try per player, independent of whether they
won); on the *first* successful `/wc`, marks `pending.claimed = true`, records `pending.welcomer`, runs
every command in `welcome.rewards.commands` via console, plays `welcome.rewards.sound`, and broadcasts
`welcome.welcome-broadcast` (built from the welcomer's level/level-color/LuckPerms prefix); any
*subsequent* `/wc` for the same already-claimed pending entry still re-broadcasts the welcome line but
skips rewards, replying with `welcome.already-welcomed` instead.

**`/testjoin` / `/testleave`:** op-only self-test commands. `/testjoin` previews the new-join message to
the sender AND arms a real `pending` entry for them, so `/wc` can be exercised solo without a second
account. `/testleave` is a pure preview with no side effects.

**`/removejoin <player>`:** op-only, removes a uuid from `hasJoined` and re-saves, so that player's next
join is treated as first-time again (useful after a manual data wipe/testing).

### Known bug (fixed): `%level%` digit-stripping in the welcome broadcast
The `level(Player p)` helper resolves `%level%` via `PlaceholderAPI.setPlaceholders(p, "%level%")` for
the `&8[%levelcolor%%level%&8]` prefix in `welcome-broadcast`. That PAPI expansion returns a
**pre-colored** string — its own legacy color code glued directly onto the level number (e.g. level 30 →
literally `"§230"`). The original implementation stripped every non-digit character
(`v.replaceAll("[^0-9]", "")`) to isolate the number, but several legacy color codes are themselves
digits (`&2`, `&3`, `&4`, `&5`, `&6`, `&7`) — stripping non-digits from `"§230"` leaves `"230"`, not
`"30"`, silently gluing the color code's digit onto the front of the real level. This only *looked*
correct for the tiers whose color code happens to be a letter (`&a`/`&b`/`&c`/`&d`/`&e`), which is why it
went unnoticed for a long time — every numeric-coded tier (roughly half of all levels) displayed a
wildly wrong number in the welcome broadcast. Fixed by anchoring the regex to the trailing digit run
instead of stripping globally: `Pattern.compile("(\\d+)$")` — the real level number is always the
suffix after the color prefix, never the reverse, so matching from the end is unambiguous.

### How to recreate from scratch
1. Track "has this uuid ever joined before" as a persisted `Set<UUID>` (flat YAML list is fine),
   checked and updated at the very top of your join handler, before building any message.
2. Route join/quit message text through per-event config keys (`join-message` vs `new-join-message`)
   rather than a single template, so first-time joins can carry different copy/formatting.
3. Compute any "X/Y online" placeholder directly from `Bukkit.getOnlinePlayers().size()` at event time —
   don't try to cache or derive it from anything else, and remember quit fires *before* the player is
   actually removed from the online list, so subtract 1 there.
4. For a "first responder claims a reward" mini-game: keep a single mutable "pending" slot (not a
   queue) with a timestamp, an armed/claimed flag, and a set of UUIDs who've already attempted it; gate
   every check (window expiry, already-attempted, already-claimed) before running any reward logic, and
   make post-claim attempts still produce *some* feedback (a re-broadcast or an "already claimed by X"
   message) rather than silently no-op'ing.
5. **Never regex-strip all non-digit characters from a PlaceholderAPI (or any pre-formatted/pre-colored)
   string to recover a raw number.** Legacy color codes and other markup can themselves contain digits,
   so a blanket strip silently corrupts the result for whichever codes happen to be numeric. Either
   anchor the match to the position you know the real value occupies (e.g. `(\d+)$` when the number is
   always the trailing run), or better, read the underlying numeric value directly from its actual data
   store instead of round-tripping it through PAPI text at all.

### Config keys involved
`welcome.window-seconds`, `welcome.join-message`, `welcome.new-join-message`, `welcome.quit-message`,
`welcome.welcome-broadcast`, `welcome.already-welcomed`, `welcome.no-pending-message`,
`welcome.expired-message`, `welcome.rewards.commands` (list), `welcome.rewards.message`,
`welcome.rewards.sound`, `welcome.rewards.sound-volume`, `welcome.rewards.sound-pitch` — all
self-healed into `config.yml` via `registerDefaults()`'s `!cfg.contains(...)` guards. One extra
self-heal: if `welcome.rewards.commands` still exactly equals the *old* 2-command default (pre-dating
the `credits give` reward), it's silently upgraded to the new 3-command default even though the key
technically already "exists" — otherwise servers that never touched this key would never receive the
credit reward that was added later.

---

## 18. Chat Tag / Chat Formatting

### What it is / purpose
Java port of `chat.sk` — the core public-chat pipeline: formats every chat message with a level
bracket, LuckPerms rank prefix, and equipped Petcore tag; handles chat cooldowns, ping detection/
highlighting, staffchat routing, mute enforcement, an emoji shorthand replacer, and a per-player
`/options` GUI (ping/boss-music/level-up/egg-hatch/fragment-message mutes plus a hatch-luck cap).

### Key classes & files
- `src/main/java/me/petcore/chat/ChatManager.java` (988 lines) — everything below.
- `chat/chat.yml` (plugin data folder) — externalized format string, cooldowns, chat-games config,
  Discord-bridge role colors, and persisted `/chatbypass` + `/options` toggle sets.

### How it works
**Event hook:** listens on the modern Paper `io.papermc.paper.event.player.AsyncChatEvent` (not the
deprecated Bukkit `AsyncPlayerChatEvent`) at `EventPriority.NORMAL, ignoreCancelled = false` — the
`ignoreCancelled = false` is deliberate so this handler still runs its own checks (team-chat-toggle,
staffchat-toggle) even when an earlier, lower-priority listener already cancelled the event for its own
routing purpose.

**Formatting:** the message is cancelled unconditionally (`event.setCancelled(true)`) and rebroadcast
manually. The rank prefix comes from `%luckperms_prefix%` via PAPI, but is deserialized through
Adventure's `LegacyComponentSerializer` (after normalizing `&#RRGGBB` → `&x&R&R&G&G&B&B`) as its own
independent `Component` rather than concatenated as a raw string — LuckPerms prefixes can carry hex or
MiniMessage-style codes that `plugin.buildComponent()` alone wouldn't parse correctly, and keeping it
separate stops its colors from bleeding into the plain-colored player-name/message segment that follows.
The equipped Petcore tag (`TagManager.equippedMarkup`) is appended after the player's name the same way.
The final template comes from `chat.yml`'s `format` key (`{level_color}`, `{level}`, `{prefix}`,
`{player}`, `{message}` placeholders); a template without `{prefix}` falls back to a legacy inline
concatenation mode for backward compatibility with pre-template configs.

**Cooldown:** `chatCooldown` map (UUID → last-chat epoch ms), gated by `chat.yml`'s
`cooldown_seconds` (default 1.5s) unless the player is in the persisted `bypassPlayers` set
(`/chatbypass`, staff-only).

**Ping detection:** every online player's name is checked against the raw message, longest-name-first
(`sortedPlayers.sort` by descending length) so e.g. "DosenthAlt" is matched before the substring
"Dosenth", with a regex word-boundary guard (`(?<![A-Za-z0-9])name(?![A-Za-z0-9])`) so partial-name
false-positives don't fire. A parallel "scan copy" of the message has already-matched names masked with
digit filler (`"0".repeat(name.length())`) after each replacement so a shorter name can't subsequently
match *inside* an already-highlighted longer name. Matched targets get an action-bar ping notice, a
pickup sound (unless they've muted it via `/options`), and their name is recolored `&e@Name&f` in the
broadcast copy. Separate `pingCooldown` map gates repeat pings the same way as chat cooldown.

**Staffchat:** `plugin.isStaffChatRawToggled(uuid)` reroutes the whole message to `handleStaffChatMessage`
instead of public chat. A message prefixed with `!` inside staffchat mode is treated as "send to
everyone" (broadcast, with the same prefix/level/tag formatting as normal chat); anything else goes
through `sendStaffChatOnly` — visible only to `staffchat.use` holders, prefixed `&8[&5&lSC&8]`.

**Mute enforcement:** `plugin.isPlayerMuted(uuid)` cancels the event, notifies `MuteManager` mute-spy
watchers with the attempted message, and re-shows the mute screen to the muted player on the next tick.

**Emoji shorthand:** a static `LinkedHashMap<String,String>` (`>>`→`»`, `:)`→`☺`, `:fire:`→`🔥`, etc.)
is applied as plain substring replacement before ping detection, so typing `:fire:` in chat renders 🔥.

**`/options` GUI:** a 36-slot bordered inventory (`openOptionsGui`) with five click-to-toggle mute items
(ping sounds, boss-fight music, level-up messages, egg-hatch sounds, fragment-found messages) plus a
"Hatch Luck Cap" item that prompts the player to type a numeric cap into chat next (captured via
`awaitingLuckCapInput`, intercepted at the very top of `onChat` before any other chat logic runs). All
five mute toggles and the bypass list are persisted into `chat.yml` (`options.*` / `bypass_players`
keys) — previously plain in-memory `Set`s that silently reset to "everything enabled" on every
restart/reload.

**LuckPerms tie-in:** ChatManager never calls the LuckPerms API directly — it always goes through PAPI's
`%luckperms_prefix%` expansion (LuckPerms must be installed and its own PAPI expansion registered for
prefixes to render at all); `ShowCommand` (below) additionally falls back to calling the LuckPerms API
directly when PAPI's placeholder comes back empty.

### How to recreate from scratch
1. Hook the modern Paper chat event (`io.papermc.paper.event.player.AsyncChatEvent`), not the deprecated
   Bukkit one — cancel it unconditionally and rebuild/rebroadcast the line yourself so you have full
   control over formatting, cooldowns, and routing.
2. Never string-concatenate a rank prefix that might contain its own hex/gradient color codes into a
   larger string you then parse with a single serializer — deserialize the prefix independently into its
   own `Component` and `.append()` it, so its colors can't leak into the surrounding text.
3. For "@mention" ping detection against player names, always sort candidates longest-name-first and
   require a word-boundary regex match; mask already-matched spans in a separate "scan copy" so a
   shorter name that happens to be a substring of an already-highlighted longer one can't double-match.
4. Any per-player toggle set (mutes, bypasses) needs to be persisted to disk immediately on every
   change, not just held in a `Set` in memory — otherwise a routine restart or `/reload` silently resets
   every player's preferences.
5. Route all format strings and cooldown numbers through an external config file loaded once and merged
   (existing keys never overwritten, missing keys always backfilled) on every startup, so adding a new
   config section later doesn't require deleting the file.

### Config keys involved
`chat/chat.yml`: `format`, `cooldown_actionbar`, `ping_actionbar`, `ping_received`, `staffchat_usage`,
`cooldown_seconds`, `ping_cooldown_seconds`, `show.*` (see §Chat Folder Overview → ShowCommand),
`chat_games.*` (see below), `discord_bridge.discord_to_mc_format`, `discord_bridge.role_colors.*`,
`bypass_players` (persisted list), `options.ping_muted` / `options.boss_music_muted` /
`options.level_messages_muted` / `options.egg_hatch_sounds_muted` / `options.fragment_messages_muted`
(persisted lists).

---

## 19. Chat Folder Overview (remaining classes)

The rest of `src/main/java/me/petcore/chat/` — everything not already covered by §17 (Welcome) or §18
(ChatManager). Each entry below names its real methods/commands/config keys rather than repeating the
full 5-part structure used for the 16 core systems.

### 19a. Daily rewards — `Daily.java` + `DailyDatabase.java`
`/daily` opens a 27-slot GUI (`✦ Daily Reward ✦`) with a single centered claim item (slot 13). Reward
*tier* is resolved from Discord linkage/roles at GUI-open time and cached per-uuid in `pendingTier` until
clicked: **Unlinked** (+1 credit, always available) → **Linked** (+2, has a linked Discord account) →
**[PET] Tag** (+3, detected three ways in order: raw Discord `clan.tag` API field equal to `"PET"`, a
configured `badge.role_id` in `rewards/rewards.yml`, or a `[PET]`/`«PET»`/`<PET>` nickname pattern) →
**Booster** (+5, has the configured `booster.role_id`). Discord lookups (`fetchClanTag`,
`isBoostingRaw`) hit the raw Discord v10 REST API directly (JDA 5.0.0-beta.24 doesn't expose the `clan`
field), always off the main thread. Cooldown is a flat 24h (`COOLDOWN_MS`) stored per-uuid as
`last_claim` (epoch ms) in **`daily_data.db`** (SQLite table `daily_claims`, WAL mode) via
`DailyDatabase` — `migrateFromYml` one-time-imports any pre-existing `daily_data.yml` on construction,
then renames it to `.bak`. Reward is granted via console `credits give <player> <tier>`. Admin:
`/resetdaily <player>` clears their cooldown immediately.

### 19b. Auto-moderation — `AutoMute.java` + `MuteManager.java`
**`AutoMute`** is a spam/banned-word filter listening on `AsyncChatEvent` (HIGH priority): already-muted
players are silently cancelled and shown the mute screen (fixing an old bug where they could still leak
messages through); a banned-word match cancels the message, applies a mute via `MuteManager`, and
**broadcasts to the entire server** (not just staff) that the player was muted (with an extra
staff-only detail line to `petcore.mute`/op holders). Word patterns live in `chat/automute.yml` under
`automute.banned_words`, supporting `word` (whole-word), `*word*` (contains), `word*`/`*word`
(prefix/suffix), and `regex:<pattern>` forms, compiled once per `load()`/`reload()`. A second listener
(`onCommand`, `PlayerCommandPreprocessEvent`) blocks muted players from bypassing chat via commands like
`/t`, `/tc`, `/msg`, etc. (`automute.blocked_commands`).
**`MuteManager`** owns actual mute state: `/mute <player> <reason>`, `/unmute <player>`,
`/togglemutemsgs` (mute-spy toggle), `/mutehistory <player>` (permission `petlifymine.mute`, bypass
`petlifymine.mutebypass`). Duration is reason-based (`Spam`=3m up to `Doxxing`=30d, see the `REASONS`
map) and **doubles per repeat offense**, hard-capped at 30 days (`MAX_MUTE_SECONDS`). Offense counts and
active mutes persist to `mutes/mutes.yml` (`mutes.<uuid>.*` / `history.<uuid>`), reloaded and pruned of
expired entries on every plugin start.

### 19c. Chat mini-games — `ChatGames.java`
Server-wide timed mini-games run entirely through chat: `TYPE_RACE` (type an exact phrase first),
`MATH_QUIZ` (arithmetic, 3 difficulty tiers), `UNSCRAMBLE` (unscramble a shuffled word, retries the
shuffle up to 10 times if it accidentally reproduces the original), `TRIVIA` (15 hardcoded
Minecraft-trivia questions, answer by letter or full text). An auto-loop (`startAutoLoop`) fires a
random game every `chat_games.interval_minutes` (default 45) if no game is currently active and at least
one player is online; each round times out after `chat_games.timeout_seconds` (default 45) if
unanswered. First correct answer in chat (matched case-insensitively, `AsyncChatEvent` at `LOW`
priority) cancels their message, stops the timeout, runs `chat_games.prize_command` via console, and DMs
`chat_games.prize_message`. Admin: `/chatgame <start|stop|forcenow|status> [TYPE]`.

### 19d. `ClearChat.java`
`/clearchat` (op or `clearchat.use`) sends 100 blank `Component`s to every online player followed by a
configurable `clearchat.message` (default `"&8[&e&l⚡&8] &eCHAT CLEARED &8» &7by &f%player%"`).

### 19e. `DisabledCommands.java`
A pure command/tab-complete-hiding filter for non-staff (`is.staff` permission or op bypass it
entirely): strips known third-party plugin commands (Tebex, ExcellentCrates, Axvault, etc.) and vanilla
info commands (`/plugins`, `/version`, `/help`, `/msg`, `/tell`, ...) from tab-complete
(`PlayerCommandSendEvent`), blocks them outright if actually run (`PlayerCommandPreprocessEvent`), and
suppresses/replaces the vanilla "unknown command" message (`UnknownCommandEvent`) with a small-caps
`"ᴛʜɪꜱ ᴄᴏᴍᴍᴀɴᴅ ᴅᴏᴇꜱ ɴᴏᴛ ᴇxɪꜱᴛ."` in chat + action bar + a deny sound. Blocklists are hardcoded static
`List<String>` constants, not config-driven.

### 19f. `MinigameManager.java` (846 lines)
Java port of `minigame.sk` — the parkour / "floor is lava" event system. Manages three WorldGuard
region triggers detected via manual per-move/per-teleport region-set diffing (no native
"entered a region" event exists): a **portal** region (`minigame.regions.portal-keyword`, default
`minigameportal`) that teleports the player to the parkour spawn (subject to a 15-min re-entry
cooldown); a **floor** region (`floor`) that instant-kills/teleports the player back to spawn (a
`YOU DIED` title) unless they've toggled `/floorbypass`; and an **exit** region (`parkourexit`) that
opens a Yes/No confirmation GUI before applying the exit cooldown and warping them out
(`minigame.warp-command`, default `warp zone4`). The broader `parkour` zone keyword continuously strips
Speed potion effects, flight, and the gear/gamepass walk-speed *attribute* bonus (via
`LoreStatListener.suppressWalkSpeed`) from anyone inside it (restored the instant they leave or toggle
bypass) so the course can't be cheesed. `/parkourwin <player>` (console-only, called from an NPC click
elsewhere) grants layered random rewards: guaranteed coins/rubies, a chance-based damage potion (15%)
and luck potion (5%) — both configured once via `/parkoursetitem <damage|luck>` while holding the item —
a 1% chance at bonus credits, and a 0.3% ("per-mille") chance at a secret Camel Husk Companion. All
reward ranges/odds/cooldowns live under `config.yml → minigame.*`.

### 19g. `RulesManager.java`
`/rules` opens a 3-row chest GUI ("rRules") with three `KNOWLEDGE_BOOK` items (Client/General/Chat
rules, slots 10/13/16), each holding hardcoded lore lines; clicks in the GUI are fully cancelled via a
dedicated `RulesGuiHolder` marker class (identified by class, not by inventory title). `/rulesinfo`
(alias `/rinfo`, gated by `rules.permission`, default `rRules.staff`) prints the module's
prefix/version/colors for debugging. All cosmetic strings (`rules.prefix`, `rules.version`,
`rules.permission-message`, `rules.permission`, `rules.colors.client/general/chat`) self-heal into
`config.yml`.

### 19h. `ShowCommand.java`
`/show <levels|coins|rubies|piles_broken|credits|rebirth|speed|coinsmulti|rubymulti|expmulti|
damagemulti|item>` broadcasts a rich, hover-capable stat line to the whole server, 3s per-player
cooldown. Each stat subcommand pulls its value from a PAPI placeholder (`%level%`, `%coins%`, etc.),
formats large numbers into K/M/B (`formatNumber`), and appends a Minecraft item-sprite icon via
`(sprite:minecraft:items:item/<id>)` markup. `item` mode broadcasts the player's held item's real
Adventure display-name/lore as a hover tooltip (built manually via `HoverEvent.showText`, since
`HoverEvent.showItem` only ever renders the vanilla, uncustomized tooltip). The player's rank prefix is
fetched directly from the **LuckPerms API** (`fetchRankPrefix`, falling back to PAPI's
`%luckperms_prefix%` only if LuckPerms itself isn't installed) — a deliberate departure from
ChatManager's PAPI-only approach, added because the previously-referenced `%petcore_prefix%`
placeholder was never actually implemented and always silently resolved to nothing. All verbs/colors
configurable under `chat.yml`'s `show.*` section.

### 19i. `SpawnManager.java`
`/setspawn` (op) saves the sender's current location to `spawn.yml`; `/spawn` (anyone) teleports back
to it. `teleportToSpawnOrDefaultWorld` is the join-time fallback used elsewhere in `Petcore.java` — if
no admin has ever run `/setspawn`, it falls back to the configured world's (`spawn.world`, default
`"test"`) own vanilla world-spawn point instead of leaving a first-time joiner stranded wherever
Bukkit's own default world-join logic happens to drop them. `spawn.teleport-on-join` and
`spawn.teleport-on-respawn` gate whether *every* join/respawn (not just first-timers) auto-teleports to
spawn.

---

## 20. Last Location (return-to-previous-location on join)

### What it is / purpose
A minimal standalone system that remembers exactly where each player was standing when they last
disconnected, so a *returning* player is teleported back there a few ticks into their next join —
overriding whatever default-world-spawn teleport would otherwise happen first.

### Key classes & files
- `src/main/java/me/petcore/location/LastLocationStore.java` — the entire feature: a single SQLite
  table, `save`/`get`/`close`.
- `lastlocation.db` (plugin data folder) — one file, one table.
- Wired from `Petcore.java`: constructed at `onEnable` step "Return-to-previous-location on join"
  (right after `HeadStatsManager` starts, line ~879); the field is `lastLocationStore`.

### How it works
**Schema:** a single table, `location(uuid TEXT PRIMARY KEY, world TEXT, x REAL, y REAL, z REAL,
yaw REAL, pitch REAL)` — one row per player, upserted via `INSERT ... ON CONFLICT(uuid) DO UPDATE`.

**Save (on quit):** `Petcore`'s `onPlayerQuit` handler unconditionally calls
`lastLocationStore.save(event.getPlayer().getUniqueId(), event.getPlayer().getLocation())` for every
quitting player, right alongside removing them from the scoreboard and scheduling a Discord
presence-update — no config gate, every quit is recorded.

**Restore (on join):** `Petcore`'s `onPlayerJoin` handler only applies this for a **returning** player
(`event.getPlayer().hasPlayedBefore()`) — a genuine first-time join is left alone (there's nothing saved
yet, and it would conflict with whatever first-join placement logic already exists). If a saved location
exists (`lastLocationStore.get(uuid)`, which returns `null` if the row is missing *or* its recorded
world isn't currently loaded — e.g. after a world rename), the actual `teleport()` is deliberately
scheduled 5 ticks later (`runTaskLater(this, ..., 5L)`) rather than run immediately, specifically so it
executes *after* the world's own default-spawn teleport rather than racing it and potentially being
overwritten by it.

**Lifecycle:** `close()` is called once, from `onDisable`, right alongside every other per-feature store
teardown — this is a lightweight single-`Connection` store, not pooled, so a clean close matters to
avoid leaving the SQLite file locked.

### How to recreate from scratch
1. One SQLite table keyed by player UUID, storing world name + all six pose components (x/y/z/yaw/
   pitch) — an upsert (`ON CONFLICT DO UPDATE`) means you never need a separate "does a row exist yet"
   check before saving.
2. Save unconditionally on every quit — don't try to special-case "did they move far enough to be worth
   saving," the write is cheap and the alternative (a stale save) is worse.
2. On join, restore **only** for players who have played before (`Player#hasPlayedBefore()`) — never
   apply a saved-location teleport to a genuine first-time join, since there's nothing meaningful saved
   for them yet and doing so would just clobber whatever your dedicated first-join placement does.
3. Resolve the world by name at restore time and treat a missing/unloaded world as "no saved location"
   (return `null`) rather than throwing — worlds do occasionally get renamed, merged, or fail to load.
4. Schedule the actual restore teleport a handful of ticks after join (not immediately in the join
   handler) so it applies *after* the platform's own default-spawn teleport instead of racing it.

### Config keys involved
None — there is no `lastlocation:` config block; the feature is unconditional and has no toggles.

---

## 21. Heads System (Custom Head NPCs)

### What it is / purpose
Java port of `head.sk`/`head2.sk` — decorative, giant, glowing, smoothly-rotating floating player-head
displays (built from `ItemDisplay` entities, not real skull blocks) that show a clickable informational
message (Discord invite, store link, etc.) when right-clicked. Distinct from — and unrelated to — the
in-game "Heads" *stat display* referenced in other sections (e.g. `HeadStatsManager`, floating
combat/level names above players' heads); this system is purely two standalone decorative NPCs.

### Key classes & files
- `src/main/java/me/petcore/heads/CustomHeadNpc.java` — one instance per physical head NPC (the plugin
  constructs two: `head1` for Discord, `head2` for the store), each owning its own scoreboard-tag
  namespace, rotation state, and spawn location so multiple heads never collide.
- `src/main/java/me/petcore/heads/HeadMessageConfig.java` — loads each head's click-message lines from
  `head/msg.yml`, one string list per head id (`head1`, `head2`).
- Commands: `/spawnhead*`, `/tphead*`, `/removeheads*` (prefix-matched against the command name, so
  each `CustomHeadNpc` instance's `onCommand` handles its own dedicated command variant).

### How it works
**What's stored / how the texture works:** there is no custom head *registry* keyed by name — each
`CustomHeadNpc` is handed a single Base64 `texture` string (a Mojang skin-API texture blob) directly in
its constructor, embedded into a `PlayerProfile`/`ProfileProperty("textures", texture)` pair
(`Bukkit.createProfile(UUID.randomUUID())` — a throwaway random UUID, since the profile only exists to
carry the texture), which is then applied to a `PLAYER_HEAD` `ItemStack`'s `SkullMeta` via
`setPlayerProfile`. This is the standard modern (post-`SkullMeta#setOwnerProfile`-deprecation) way to
render an arbitrary custom-textured skull without needing a real Mojang account/UUID behind it.

**Display, not a block:** the head itself is an `ItemDisplay` entity (`ItemDisplay.ItemDisplayTransform.
HEAD`) holding that textured `PLAYER_HEAD` `ItemStack`, not a placed block — this is what allows
continuous smooth Y-axis rotation (`tickRotation`, every tick, `+1.8°`, wrapping at 360°, applied as a
`Quaternionf` left-rotation on the entity's `Transformation`) and glow (via a scoreboard `Team` with
`setColor`/`addEntry`, since `ItemDisplay#setGlowing(true)` alone needs a team to actually pick a glow
color). A separate invisible `Interaction` entity (6×7 hitbox) is spawned at a slightly different Y and
tagged as the click target — `Interaction` entities fire `PlayerInteractAtEntityEvent`, not the more
commonly-handled `PlayerInteractEntityEvent`, which is the exact reason earlier attempts at NPC-style
clickable entities elsewhere in the codebase (see `EggListener`) silently never reacted to clicks: the
wrong event was being listened for.

**Click messages:** `HeadMessageConfig.get(id, fallback)` re-reads `head/msg.yml` fresh from disk on
*every* click (not cached), so an admin editing the file takes effect immediately with no restart. Each
line supports an optional `" -> "` marker: everything before it is displayed, everything after becomes a
`ClickEvent.openUrl(...)` on that displayed text — so `"&6&lCLICK HERE -> https://x"` shows only
`"CLICK HERE"` in chat (bold, gold) but clicking it opens the link; a line with a bare URL and no marker
still gets the URL auto-detected and stripped from display, attached as a click instead. Any line
containing a `discord.gg/...` link additionally has that link live-rewritten to the current
`chat_bridge.discord_invite_url` config value on every read, so a `msg.yml` written long ago (which is
only ever generated once, on first run or on a version bump) doesn't keep pointing at a stale/expired
invite forever just because the file itself is never regenerated.

**Config self-healing for the message file itself:** `HeadMessageConfig` stamps a `_version` integer
into `msg.yml` and regenerates the whole file with `writeDefaults()` if that version is missing or below
`CONFIG_VERSION` (currently 2) — this is how a shape change to the file (e.g. the introduction of the
`" -> "` click-link marker, replacing an earlier format that displayed the raw URL) gets pushed to
existing installs automatically on a jar upgrade, rather than requiring a manual file deletion.

### How to recreate from scratch
1. To render an arbitrary custom skin texture without a real player account behind it: build a
   `PlayerProfile` with a random UUID, attach the Base64 texture as a `"textures"` `ProfileProperty`, and
   apply it to a `PLAYER_HEAD` item's `SkullMeta` via `setPlayerProfile`.
2. For a decorative, animatable "NPC" that isn't a real entity/mob: spawn an `ItemDisplay` holding that
   textured head item (`ItemDisplayTransform.HEAD`), and drive any animation (rotation, scale) by
   mutating its `Transformation` on a repeating scheduler task.
3. Glow requires a scoreboard `Team` — `setGlowing(true)` alone picks no color; register/reuse a team,
   set its color, and add the entity's UUID as an entry.
4. For click interactivity on a display entity (which has no natural hitbox), spawn a separate invisible
   `Interaction` entity at roughly the same location with a generous width/height, and listen for
   **`PlayerInteractAtEntityEvent`** — not `PlayerInteractEntityEvent`, which never fires for
   `Interaction` entities.
5. Tag every spawned entity with a scoreboard tag namespaced per logical NPC instance (not a single
   shared tag) so multiple copies of the same kind of decoration can coexist and be
   cleared/identified/rotated independently.
6. Keep click-message text in an external file re-read on every interaction (not cached at startup) so
   admins can tweak wording without a restart, and version-stamp that file's on-disk shape so future
   format changes to it can self-migrate existing installs instead of silently reading stale defaults.

### Config keys involved
No dedicated `heads:` block — each `CustomHeadNpc`'s world/coordinates/texture/team/scale are passed in
directly at construction time in `Petcore.java` (not read from `config.yml`). The one config tie-in is
`chat_bridge.discord_invite_url`, read by `HeadMessageConfig` to keep the Discord head's click-link in
sync with whatever invite `/discord` and the announcement system are currently using, and by
`CustomHeadNpc.discordMessage(String)`'s static builder as a fallback default (`DEFAULT_DISCORD_INVITE`,
`https://discord.gg/2uCrysdWWK`) if that key is ever missing.

---

## Appendix: Storage format summary

Every feature in this plugin picks one of three storage styles, with no shared ORM/base class:

- **Standalone SQLite** (most currency/progress stores): one file per feature in the plugin data
  folder (`coins.db`, `rubies.db`, `shards.db`, `level.db`, `stats.db`, `placedpets.db`, `walls.db`,
  `slotshop.db`, `milestones.db`, `quests.db`, `Teams/teams.db`, `data.db` for Discord links, etc.),
  each opening its own JDBC `Connection`, `synchronized` accessor methods, and
  `INSERT ... ON CONFLICT DO UPDATE` upserts.
- **Flat YAML** (config-like or small datasets): `shops.yml`, `warps.yml`, `leaderboard/leaderboard.yml`,
  `topspent.yml`, `Teams/teams.yml`, `locked_region_bypass.yml`, `head/msg.yml` — loaded via
  `YamlConfiguration.loadConfiguration` and rewritten wholesale on save.
- **The main `config.yml`** — self-healing via `Petcore#mergeMissingConfigDefaults()`, so a plugin
  update can add whole new sections without ever touching a server's already-customized keys.
