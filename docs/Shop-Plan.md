# prompt2.md — The New Shop Lineup (10-Zone Plan)

This file replaces the *zone list* used by the shop system with the final 10 zones you decided on. It is a
**design spec**, not a code change — nothing in the live plugin has been renamed or migrated. It shows exactly
how to slot your 10 zones into the shop architecture that already exists (`ShopType`, `ShopItem`, `ShopManager`,
`ShopCommand`, `ShopFragment`), reusing every current command/function pattern with zero new infrastructure.

Your finalized zone order:

1. Plains
2. Spruce Forest
3. Savanna
4. Desert
5. Ice Spikes
6. Cherry Grove
7. Flower Forest
8. Cave
9. Ocean
10. Mushroom Island

> Current live shops (`ShopType.java`) are Forest → Mushroom → Desert → Badlands → IceSpikes → Stone →
> Deepslate → Beach, plus the standalone Talisman shop. This document is the "if/when we rebuild the shop
> around the new 10 zones" reference — see `prompt.md`'s "Planned future zone lineup" note for the same
> caveat: don't execute this as a migration without a dedicated economy-balancing pass.

---

## 1. Why nothing new has to be built

`ShopType` (`src/main/java/me/petcore/shop/ShopType.java`) is a single enum where **every shop is just one
constant** — the constructor takes `(id, command, title, columns, rows, currencies, fragments, adminSlots,
blankSlots, tierChains, predecessorShopId, defaultItems, texture)`. Adding a shop for a new zone is copy-paste-
and-rename of an existing block, nothing more:

- `ShopManager` (`shop/ShopManager.java`) is generic — it never references a zone by name, only through
  `ShopType`/`ShopFragment`/`ShopItem`, so it needs no changes to serve 10 zones instead of 8.
- `ShopCommand` (`shop/ShopCommand.java`) is one class bound once per `ShopType` — `/plainsshop`,
  `/spruceforestshop`, etc. are all the exact same `ShopCommand` instance pattern, just constructed once per
  new enum constant.
- `ShopItemEditorGUI`, `ShopStore`, `ShopRegistry`, `ShopSlotData`, `CostFormat` all key off `ShopType`/slot
  number, not zone identity — no changes needed there either.
- The only *new* code required per zone is one `XShopItem` enum (mirroring `ForestShopItem.java` /
  `DesertShopItem.java` etc.) that defines the actual armor/tool tiers, lore, and costs for that zone — and a
  matching `ZonePileType` entry if the zone also needs mining piles/fragments (out of scope for this doc; see
  `prompt.md` §"Zones").

---

## 2. The 10 new `ShopType` constants

Each zone gets one shop, in this exact progression order (mirrors the existing predecessor-chain pattern:
each shop's first tier is gated behind the previous shop's final tier).

| # | Zone | Shop `id` / command | Title | Fragment currency | Predecessor shop |
|---|------|----------------------|-------|--------------------|-------------------|
| 1 | Plains | `plainsshop` | `&aPlains Shop` | Wheat Fragment | *(none — start of progression)* |
| 2 | Spruce Forest | `spruceforestshop` | `&2Spruce Forest Shop` | Spruce Fragment | `plainsshop` |
| 3 | Savanna | `savannashop` | `&6Savanna Shop` | Acacia Fragment | `spruceforestshop` |
| 4 | Desert | `desertshop` | `&eDesert Shop` | Desert Fragment | `savannashop` |
| 5 | Ice Spikes | `icespikesshop` | `&bIce Spikes Shop` | Ice Fragment | `desertshop` |
| 6 | Cherry Grove | `cherrygroveshop` | `&dCherry Grove Shop` | Cherry Fragment | `icespikesshop` |
| 7 | Flower Forest | `flowerforestshop` | `&fFlower Forest Shop` | Flower Fragment | `cherrygroveshop` |
| 8 | Cave | `caveshop` | `&8Cave Shop` | Cave Fragment | `flowerforestshop` |
| 9 | Ocean | `oceanshop` | `&3Ocean Shop` | Ocean Fragment | `caveshop` |
| 10 | Mushroom Island | `mushroomislandshop` | `&cMushroom Island Shop` | Mushroom Fragment | `oceanshop` |

Note: `desertshop` and `icespikesshop` reuse the existing `id`s from the live 8-zone shop list (`ShopType`
already has `DESERTSHOP`/`ICESPIKESSHOP`) — if this plan is ever actually executed, those two constants get
re-pointed at the new predecessor chain rather than duplicated, everything else is a brand-new constant.
`Mushroom Island` is the renamed/re-themed successor to the current `MUSHROOMSHOP`.

Every shop below reuses the **exact same GUI geometry** as the current five gear shops (Forest → Ice Spikes in
the live code): a 6-row chest (`columns=9, rows=6`), armor columns `10-13` and tool columns `15-16`, four tier
rows per column (`slot = base + tier*9`), i.e. `ShopType.GearLayout.ADMIN_SLOTS` /
`GearLayout.TIER_CHAINS` verbatim — no new layout code, just reused constants.

### Example: how the enum constants would read (copy-paste pattern, unchanged from today)

```java
PLAINSSHOP(
        "plainsshop",
        "plainsshop",
        "&aPlains Shop",
        9, 6,
        EnumSet.of(ShopCurrency.COINS, ShopCurrency.RUBIES),
        List.of(new ShopFragment(ZonePileType.PLAINS, "setplainsshopfragment", "Wheat Fragment")),
        GearLayout.ADMIN_SLOTS,
        GearLayout.BLANK_SLOTS,
        GearLayout.TIER_CHAINS,
        null, // start of the new progression
        List.of(PlainsShopItem.values()),
        null),

SPRUCEFORESTSHOP(
        "spruceforestshop",
        "spruceforestshop",
        "&2Spruce Forest Shop",
        9, 6,
        EnumSet.of(ShopCurrency.COINS, ShopCurrency.RUBIES),
        List.of(new ShopFragment(ZonePileType.SPRUCE_FOREST, "setspruceforestshopfragment", "Spruce Fragment")),
        GearLayout.ADMIN_SLOTS,
        GearLayout.BLANK_SLOTS,
        GearLayout.TIER_CHAINS,
        "plainsshop", // gated behind the final Plains tier
        List.of(SpruceForestShopItem.values()),
        null),

// ...same shape repeats for Savanna, Desert, IceSpikes, CherryGrove, FlowerForest, Cave, Ocean, MushroomIsland,
// each with predecessorShopId pointing at the zone directly above it in the table.
```

Every other field (`columns`, `rows`, `currencies`, `GearLayout.*`) is copied verbatim from the existing
`FORESTSHOP`/`DESERTSHOP` constants — the only per-zone work is: pick an `id`, a `command`, a `title` color,
one `ShopFragment` (zone + admin "set fragment" command name + display name), the `predecessorShopId`, and
write one `XShopItem` enum of built-in armor/tool items (mirrors `ForestShopItem.java` — 4 armor tiers × 4
pieces + tools, each with lore, coin/ruby cost, and optional fragment cost).

---

## 3. Reusing the existing shop commands & functions — nothing new to write

All of the following are **already generic** and require zero changes, only one more registration line per
new `ShopType` constant (in `Petcore.java`'s command-registration block, the same place the current 8 shops
are wired):

- **`/<shop-id>`** (e.g. `/plainsshop`, `/spruceforestshop`, … `/mushroomislandshop`) — opens that shop's GUI.
  Backed by one `new ShopCommand(shopManager, ShopType.PLAINSSHOP)` per zone, exactly like today's
  `/forestshop`. Players must be OP to run it directly; console/NPC form is `/<shop> <player>`.
- **`/get<shop><role>`** (e.g. `/getplainsshophelmet`, `/getcherrygroveshoptool`) — `ShopType.firstTierItemCommands()`
  auto-derives these from each shop's tier-1 armor/tool slots, same as today; no new code, it's computed from
  the enum data alone.
- **`/set<shop>shopfragment`** (e.g. `/setplainsshopfragment`, `/setoceanshopfragment`) — the admin command
  that assigns which real item represents that zone's fragment currency; comes for free from the
  `ShopFragment` record passed into the constant, same mechanism as `/setforestshopfragment` today.
- **Right-click admin editor** (`ShopItemEditorGUI`) — already keys off `ShopType` + slot number generically;
  opening the editor for any of the 10 new shops needs no new code, just `ShopAdminAccess.isAdmin(p)` +
  right-clicking an admin slot, same as today.
- **Purchase flow** (`ShopManager.handlePurchase`) — the coin/ruby/fragment cost check, tier-prerequisite gate,
  cross-shop predecessor gate, and reward hand-over are all `ShopType`/`ShopSlotData`-driven already; the new
  10-shop progression chain (Plains → … → Mushroom Island) plugs into `prerequisiteSlot`/
  `predecessorPrerequisiteSlot` with no changes, since those methods only ever read `tierChains` and
  `predecessorShopId` off whichever `ShopType` constant is asking.
- **Anti-exploit click handling** — every one of these 10 new shop GUIs is safe by construction the same way
  the current 8 are: `ShopManager.onInventoryClick`/`onInventoryDrag` cancel unconditionally regardless of
  shop identity (see `Gui.md` for the full pattern). Nothing zone-specific to add here.

---

## 4. What *would* still need real work (not covered by "just add an enum constant")

Being upfront about the parts that aren't free, if this plan is ever picked up:

1. **One `XShopItem` enum per zone** (10 total) — the actual armor/tool tiers, lore text, and coin/ruby/
   fragment costs for Plains, Spruce Forest, Savanna, Cherry Grove, Flower Forest, Cave, and Ocean don't exist
   yet (Desert, Ice Spikes, and Mushroom Island can likely start from the current `DesertShopItem`,
   `IceSpikesShopItem`, `MushroomShopItem` and be retuned rather than written from scratch).
2. **A `ZonePileType` entry per new zone** (Plains, Spruce Forest, Savanna, Cherry Grove, Flower Forest, Cave,
   Ocean) — HP tiers, block materials, fragment drop chances, reward multiplier — needed before a
   `ShopFragment` can point at a real, minable fragment currency for that zone.
3. **A full economy re-balance pass** across all 10 tiers' coin/ruby/fragment costs, since the current 8-shop
   costs were tuned zone-by-zone against the current `ZonePileType` reward multipliers (see the `1.2x/1.4x/
   1.6x/1.8x/2.0x/2.2x/2.5x` comments in `ZonePileType.java`) — a straight 8→10 zone stretch needs its own
   curve, not a copy of the old one.
4. **Registration wiring in `Petcore.java`** — one `getCommand("...").setExecutor(new ShopCommand(...))` line
   per new shop, plus adding each new `ShopFragment`'s "set fragment" command to `plugin.yml`.

None of the above is required to validate the *shape* of the plan — it only matters once someone actually
starts building the new zones' content.
