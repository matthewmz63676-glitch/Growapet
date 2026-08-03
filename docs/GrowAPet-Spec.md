# CLAUDE.md --- GrowAPet Master Development Guide

> This document is the authoritative specification for the GrowAPet
> project. Any AI or developer working on this project should follow
> this file before writing code.

# Project Identity

-   Name: GrowAPet
-   Platform: Paper 1.21+
-   Java: 21
-   Build: Maven
-   Package: me.stringclient.growapet

# Vision

GrowAPet is a long-term MMORPG progression server centered around: -
Pets - Eggs - Zones - Bosses - Quests - Trading - Personal Plots -
Leaderboards - Events

Progression should take weeks or months.

# Design Principles

-   Modular architecture
-   Async database operations
-   YAML configurable
-   No pay-to-win
-   Cosmetics only monetization
-   High performance
-   Easy to extend

# Core Gameplay Loop

Join → Unlock Plot → Place Egg → Hatch Pet → Equip Pets → Kill Mobs →
Gain Coins/Gems/EXP → Level Player → Level Pets → Unlock Zones → Fight
Bosses → Complete Quests → Collect Better Pets → Repeat

# Player Stats

-   Coins
-   Gems
-   Credits (store currency)
-   Level
-   Experience
-   EXP Multiplier
-   Coin Multiplier
-   Gem Multiplier
-   Damage Multiplier
-   Critical Chance
-   Critical Damage
-   Pet Power
-   Playtime
-   Mob Kills
-   Boss Kills

Minecraft XP level MUST equal plugin level. Minecraft XP progress bar
MUST equal current EXP percentage.

# PlaceholderAPI

%growapet_level% %growapet_exp% %growapet_exp_bar% %growapet_coins%
%growapet_gems% %growapet_credits% %growapet_damage_multiplier%
%growapet_coin_multiplier% %growapet_gem_multiplier% %growapet_power%

# Plot System

Each player owns one protected plot.

Rules: - Eggs ONLY place inside owner's plot. - Pets ONLY exist inside
owner's plot. - No placement in zones, spawn, shops, arenas or other
plots. - Persistent saved locations. - Upgradeable size. - Upgradeable
pet slots. - Upgradeable egg slots. - Decorations supported (cosmetic
only).

Commands: - /plot - /plot home - /plot settings - /visit
`<player>`{=html}

# Pet System

Pets hatch from eggs and are Minecraft entities.

Supported: Every vanilla mob.

Pet Rarities: Common Uncommon Rare Epic Legendary Mythic Divine Secret
Exclusive

Pet Sizes: Tiny Small Normal Large Huge Massive Titan Colossal Mythical

Pet Stats: - Damage Multiplier - Coin Multiplier - Gem Multiplier -
Level - EXP - Size - Owner - UUID - Skin

Pets gain EXP from mob kills.

# Egg System

Right Click Egg → Place → Incubate → Hatch Animation → Pet Spawn

# Mob System

Everything configurable in mobs.yml.

Each mob: - Name - Entity - HP - Coins - Gems - EXP - Respawn - Zone -
Sounds - Particles

Admin: - /getmob `<mob>`{=html}

# Zones

Spawn Forest Plains Mushroom Desert Badlands Snow Deep Caves Nether End
Ancient Realm

Every zone contains: - Mobs - Eggs - Boss - Shop - Warp

# Warp GUI

/warp

Destinations unlock after buying zones.

# Bosses

-   Massive HP
-   Damage leaderboard
-   Broadcasts
-   Skills
-   Respawn timers

Rewards: Top 1 = 10 Credits Top 2 = 5 Credits Top 3 = 3 Credits

# Quests

Daily Weekly Story

Examples: - Kill mobs - Hatch eggs - Reach zones - Kill bosses

# Trading

/trade `<player>`{=html}

Trade: - Pets - Eggs - Coins - Gems

Credits are NOT tradeable.

# Statistics

Track: - Coins - Gems - Credits - Playtime - Mob Kills - Boss Kills -
Boss Damage - Eggs Hatched - Pets Collected - Trades - Highest Pet -
Highest Level

# Events

-   Double Coins
-   Double Gems
-   Double EXP
-   Lucky Egg
-   Boss Rush
-   Pet Frenzy
-   Coin Rain
-   Meteor Shower
-   Treasure Goblin

# Cosmetics

Only: - Chat Colors - Chat Tags

Never sell gameplay power.

# Commands

Player: /pets /plot /warp /zones /quests /trade /stats /leaderboard
/boss /options

Admin: /growapet reload /growapet give /growapet setlevel /growapet
setcoins /growapet setgems /getmob /getegg

# Config Files

config.yml mobs.yml pets.yml eggs.yml zones.yml quests.yml bosses.yml
menus.yml messages.yml

# Suggested Packages

me.stringclient.growapet - commands - config - database - events - gui -
hooks - listeners - managers - mobs - models - pets - placeholders -
plot - quests - stats - tasks - trade - upgrades - utils - warps - zones

# Database

Tables: players pets pet_levels plots zones quests bosses statistics
settings

# Coding Standards

-   SOLID principles
-   Dependency Injection where appropriate
-   No blocking database calls on main thread
-   Cache player data
-   Use managers instead of god classes
-   Use services for business logic
-   Everything configurable
-   Avoid hardcoded values

# Performance Goals

-   Hundreds of players
-   Thousands of stationary mobs
-   Async IO
-   Efficient entity handling
-   Modular systems

# Future

Guilds Raids Dungeons Battle Pass Seasonal Events Pet Fusion Pet
Enchantments Cross-server progression
