# GrowAPet Paper E2E

This is a separate Gradle/Plugwright harness. It runs the shaded production JAR on Paper 1.21.11 with Mineflayer clients and does not change the root Maven build.

The external plugin lock intentionally uses:

- PacketEvents 2.13.0
- WorldEdit 7.3.18
- WorldGuard 7.0.17
- PlaceholderAPI 2.11.6

WorldEdit 7.3.18 is deliberate: it is the version declared by WorldGuard 7.0.17. PacketEvents, WorldEdit, and WorldGuard use their official bundled Bukkit/Spigot release downloads rather than thin Maven module JARs. Every downloaded JAR is verified against `dependencies.lock.json` before it is staged into the disposable server.

## Commands

From the repository root:

```powershell
.\e2e\gradlew.bat -p e2e tasks
.\e2e\gradlew.bat -p e2e plugwrightInit
.\e2e\gradlew.bat -p e2e verifyE2EDependencyLock
.\e2e\gradlew.bat -p e2e plugwrightTest -PtestFiles=smoke-
.\e2e\gradlew.bat -p e2e plugwrightTest '-PtestFiles=smoke-,gui-menus,commands-flows'
.\e2e\gradlew.bat -p e2e plugwrightTest
.\scripts\verify-restart.ps1
```

PowerShell needs quotes around a comma-separated `-PtestFiles` value. The default run cleans disposable server state while preserving Paper/cache dependencies. Use `-PpreserveState=true` only for a deliberately sequenced restart test; `scripts/verify-restart.ps1` runs the supported two-process seed/verify sequence. `run/`, staged artifacts, and Node modules are ignored by Git.

The harness stages the root `target/growapet-1.0.0.jar` by invoking the root Maven Wrapper. It does not recompile or shade a second copy of GrowAPet.

The exact E2E-only artifact URLs and SHA-256 values are pinned in `dependencies.lock.json`. WorldGuard 7.0.17 is paired with WorldEdit 7.3.18 because that is the dependency version declared by WorldGuard. WorldEdit, WorldGuard, PacketEvents, and PlaceholderAPI are downloaded from their official server-ready release locations; thin Maven module artifacts are not used for Paper staging.

For Paper 1.21.11, packet-only pet/display/tutorial movement uses PacketEvents' `sync_entity_position` wrapper. This matches the current protocol payload and avoids the extra-payload diagnostic produced by the teleport wrapper in PacketEvents 2.13.0; it does not change the production dependency version or the intended viewer-visible behavior.

The server log scan accepts Paper's explicit EULA command-line notice and normal shutdown messages. It does not accept runtime exceptions, AsyncCatcher/thread violations, SQLite failures, event failures, or unexpected GrowAPet disablement. WorldEdit's Bukkit-adapter compatibility notice remains a documented warning for Paper 1.21.11 and is not treated as a reason to substitute an unsupported artifact.
