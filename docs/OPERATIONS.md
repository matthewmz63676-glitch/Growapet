# GrowAPet operations checklist

## Runtime dependencies

Install these plugins on the supported Paper/DivineMC 1.21.11 server:

- PacketEvents 2.13.x
- WorldEdit 7.3.x (WorldGuard dependency)
- WorldGuard 7.0.17

PlaceholderAPI and LuckPerms are optional. GrowAPet does not provide its own
TAB or scoreboard module and does not use Armor Stands for holograms.

## First boot

1. Start once with a staging copy of the server and stop cleanly.
2. Configure `plugins/GrowAPet/config.yml`, `zones.yml`, `bosses.yml`,
   `tutorial.yml`, and `hud.yml`. The repository values are examples; zone
   warps and WorldGuard region names must match the actual build.
3. Create every named WorldGuard cuboid (`growapet_spawn`,
   `growapet_forest`, and so on) in the configured world.
4. Run `/setspawn` as an administrator.
5. Run `/growapet doctor` and resolve every red blocker. Yellow warnings are
   safe fallbacks, but should be reviewed before opening the server.
6. Place populations with `/mobspawn set <id> <mob> [max]`. Review them with
   `/mobspawn list`; remove them with `/mobspawn remove <id>`.
7. Configure the tutorial with `/tutorial setpoint start|mob|shop|egg`, then
   verify it with `/tutorial validate` and `/tutorial preview`.

## HUD resource pack

The pack is optional and Unicode icons remain the fallback. Host the generated
`GrowAPet-HUD-1.21.11.zip` over HTTPS, compute its SHA-1, and set the URL and
hash under `hud.yml`:

```yaml
resource-pack:
  enabled: true
  url: "https://your-host.example/growapet/GrowAPet-HUD-1.21.11.zip"
  sha1: "40-character-lowercase-sha1"
  required: false
```

The hash must match the exact bytes served by the URL. Run `/growapet doctor`
after reloading to verify the format.

## Data safety

- Stop the server before copying `plugins/GrowAPet/database.db`.
- Keep the automatically-created `plugins/GrowAPet/backups/` directory with
  each release and test a restore on a separate staging instance.
- Never replace the live database with a blank file. Migrations are additive
  and preserve the existing player tables and identifiers.
- Test restart, forced disable, join/quit, trade cancellation, and a full
  inventory before promoting a release.

## Release gate

Run `mvn clean test` and `mvn clean package`, inspect the shaded JAR, install it
on staging with real worlds and dependencies, and complete the two-player
trade/protection/visibility checklist before publishing. A passing Maven build
does not verify packet rendering or live WorldGuard geometry.
