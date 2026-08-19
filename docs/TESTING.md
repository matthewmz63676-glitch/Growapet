# GrowAPet testing

GrowAPet keeps the test layers intentionally separate:

- JUnit component tests cover calculations, codecs, state transitions, time/random policies, and SQLite transactions.
- MockBukkit tests cover supported Bukkit inputs, inventory rendering, scheduler behavior, and listener boundaries.
- A real Paper/Mineflayer harness is required for PacketEvents, WorldGuard geometry, native server behavior, packet traffic, and restart recovery.

## Local commands

Run these from the repository root on Windows:

```powershell
.\mvnw.cmd -Dgroups=fast test
.\mvnw.cmd -Dgroups=database test
.\mvnw.cmd -Dgroups=mockbukkit test
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
.\mvnw.cmd clean package
```

The Maven Wrapper is pinned to Maven 3.9.16. `clean verify` produces the JaCoCo report at `target/site/jacoco/index.html`; the critical-package gate remains opt-in for local speed and currently passes with the component suite:

```powershell
.\mvnw.cmd -Dcoverage-gate verify
```

The tags are additive. Existing tests remain represented; the tags only make focused runs possible:

- `fast`: pure component and policy tests
- `database`: SQLite-backed tests using temporary databases
- `mockbukkit`: MockBukkit server, world, player, inventory, and scheduler tests
- `regression`: named historical bug protections

## MockBukkit boundary

`GrowAPetMockTest` starts and tears down a fresh `ServerMock` for every test. It registers named dependency stubs for `packetevents`, `WorldEdit`, and `WorldGuard`, but it does not claim to initialize those plugins. Tests that require PacketEvents internals, WorldGuard's native region implementation, NMS behavior, or complete `GrowAPet` enablement belong in a real Paper harness.

MockBukkit tests must not use arbitrary sleeps or depend on test order. Unsupported MockBukkit operations are recorded as an E2E requirement rather than converted into unexplained skips.

## Paper E2E harness

The real-server harness is implemented in `e2e/`. It uses Gradle Wrapper 8.13, Plugwright 2.0.3, Paper/Minecraft 1.21.11, Node 22.14.0, and the root Maven Wrapper to build and stage the actual shaded GrowAPet JAR. `useExternalPluginsOnly=true` keeps the E2E plugin set explicit; the root Maven dependencies and production runtime behavior are unchanged.

The E2E lock uses official server-ready Bukkit/Spigot distributions rather than thin Maven module JARs. The lock file is the source of truth and verifies SHA-256 before staging:

| Plugin | Version | SHA-256 |
|---|---:|---|
| WorldGuard | 7.0.17 | `3f14562509bf01e7680571b6f56932239157ff938f257c3226df3b4088ae54f2` |
| WorldEdit | 7.3.18 | `712c87c66c053df4570f7405bc14179ee59d78856e26f04acfb2ac3709e01241` |
| PacketEvents | 2.13.0 | `6d9ece0d87ee727a79a20b7ffbd432021609c6f52bafcb654fc2d3e9b6f064c5` |
| PlaceholderAPI | 2.11.6 | `bfea0f235da9e9d576577215acb50c74bb8122e03dcd988315b61145c7b61727` |

WorldGuard `7.0.17` declares WorldEdit `7.3.18`, so the original `7.3.17` planning pin was corrected to the compatible pair. The official bundled artifacts were required because the thin Maven modules did not contain the runtime plugin classes that Paper and GrowAPet load. This change is isolated to E2E staging; no production dependency was weakened or replaced.

The real Paper run also exposed a PacketEvents 2.13.0 serialization mismatch when the `WrapperPlayServerEntityTeleport` wrapper was used on Paper 1.21.11: it emitted the position-sync payload under the teleport packet ID. GrowAPet's packet-only pet, text-display, and tutorial movement paths now use PacketEvents' `WrapperPlayServerEntityPositionSync`, matching Paper's `sync_entity_position` packet while preserving the same viewer-visible movement behavior. The fix leaves the Maven dependency version unchanged and is covered by the real pet/restart run.

Run the lock verification and real-server suites from the repository root:

```powershell
.\e2e\gradlew.bat -p e2e verifyE2EDependencyLock
.\e2e\gradlew.bat -p e2e plugwrightTest -PtestFiles=smoke-
.\e2e\gradlew.bat -p e2e plugwrightTest '-PtestFiles=smoke-,gui-menus,commands-flows'
.\scripts\verify-restart.ps1
```

PowerShell requires quotes around the comma-separated filter. The restart script runs two sequential Paper processes with the plugin/world/database state preserved between them. Every run scans `run/logs/latest.log` for unexpected errors, exceptions, thread violations, SQLite failures, and plugin-disable diagnostics. The allowlist is limited to Paper's explicit EULA flag notice and normal shutdown messages; WorldEdit's documented Bukkit-adapter warning is retained as a warning because this is the official server-ready release for the pinned WorldGuard pair.

Current real-server coverage includes readiness and dependency loading, representative commands, permissions and admin boundaries, PlaceholderAPI, all player menu families, semantic GUI contents, navigation/back/refresh/close, deny-first inventory handling, shift-click, hotbar and offhand swaps, drag, double/collect, drop, stale/repeated clicks, plot home, options, trade request/cancellation, egg hatch, pet equip, quest routing, and clean restart persistence.

Relevant upstream references:

- [Plugwright 2.0.3 example](https://raw.githubusercontent.com/Drownek/plugwright/v2.0.3/example_plugin/build.gradle.kts)
- [Plugwright configuration reference](https://raw.githubusercontent.com/Drownek/plugwright/master/docs/configuration.mdx)
- [WorldGuard official 7.0.17 release file](https://dev.bukkit.org/projects/worldguard/files/8167578)
- [WorldEdit official 7.3.18 release file](https://dev.bukkit.org/projects/worldedit/files/7372036)
- [PacketEvents 2.13.0 release](https://github.com/retrooper/packetevents/releases/tag/v2.13.0)
- [PlaceholderAPI 2.11.6 release](https://hangar.papermc.io/HelpChat/PlaceholderAPI/versions/2.11.6)
