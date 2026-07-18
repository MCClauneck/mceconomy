# MCEconomy Engine (`engine`)

The universal **central loader** for the **MCEconomy** multi-platform Minecraft economy plugin.
This module builds the **single distributed jar** (`MCEconomy-<version>.jar`) that runs on
SpigotMC, PaperMC, and Folia. It detects the server at enable time and installs the matching
scheduler, so one jar replaces the three per-platform jars.

> The `engine` module of the [MCEconomy monorepo](../../README.md). It produces the single
> terminal distributed jar and is **not published**.

## Runtime flow

The entry point `MCEconomyEnginePlugin` is a thin subclass of the shared
`AbstractMCEconomyPlugin` (from the `bukkit` module). It overrides only the scheduler factory,
choosing the implementation by probing the runtime classpath:

1. If `io.papermc.paper.threadedregions.RegionizedServer` is present → **Folia**: installs the
   region-aware `FoliaPlatformScheduler`.
2. Else if `com.destroystokyo.paper.PaperConfig` is present → **Paper**: installs the classic
   `BukkitPlatformScheduler`.
3. Otherwise → **Spigot**: installs the classic `BukkitPlatformScheduler`.

Everything after scheduler selection — config loading, database wiring, currency caching, the
MCIdentity listener, and the `/balance` and `/pay` commands — is inherited unchanged from the
shared bootstrap, so runtime behavior on each platform is identical to the per-platform jars.

## How the universal jar is assembled

The engine shades `api`, `common`, and `bukkit` exactly once and bundles the thin per-platform
jars (their entry points and the Folia scheduler). Because the platform modules reference the
shared modules `compileOnly`, their jars contain only their own classes, so nothing is
duplicated. Only the engine's own `plugin.yml` is kept; the copies bundled from the platform
jars are dropped, leaving a single authoritative descriptor with `folia-supported: true`. The
`config.yml` shipped by the platform modules is reused, so the engine has no resources of its own
beyond `plugin.yml`.

## Dependencies

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — the shared MCEconomy
  modules, shaded into the jar (via `project(':...')`).
- [`spigotmc`](../spigotmc), [`papermc`](../papermc), [`foliamc`](../foliamc) — the per-platform
  entry points, bundled into the jar (via `project(':...')`).
- `dev.folia:folia-api` — `compileOnly`. The engine compiles against the Folia API **only**;
  it is the superset of the Bukkit, Paper, and Folia types the bundled schedulers reference, and
  spigot-api / paper-api / folia-api are mutually exclusive on one classpath (same Gradle
  capability). Platform detection is reflective, so no Spigot/Paper compile dependency is needed.
- **MCIdentity** — a hard runtime dependency (`depend: [MCIdentity]` in `plugin.yml`); the plugin
  must be installed on the server.

## Building

From the repository root:

```bash
./gradlew :platforms:engine:build          # produces the universal plugin jar
./gradlew :platforms:engine:shadowJar      # the universal jar only
./gradlew :platforms:engine:exportJar      # copies the jar to the root build/ dir (and $ENGINE_PLUGIN_JAR_PATH if set)
```

The build uses the Gradle wrapper and targets a **Java 25** toolchain.

## Configuration

The universal jar reuses the `config.yml` bundled from the platform modules. Set `enable: true`
to activate, choose a `database.type`, and customize the `currencies` list. See any platform
module's [`config.yml`](../spigotmc/src/main/resources/config.yml).

## Related modules

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — shared modules.
- [`spigotmc`](../spigotmc), [`papermc`](../papermc), [`foliamc`](../foliamc) — the per-platform
  entry points bundled into the universal jar.

## License

See [LICENSE](../../LICENSE).
