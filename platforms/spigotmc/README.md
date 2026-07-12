# MCEconomy SpigotMC (`spigotmc`)

The **SpigotMC** platform entry point for the **MCEconomy** multi-platform Minecraft economy
plugin. It wires the shared modules together into a single distributable, shaded jar.

> The `spigotmc` module of the [MCEconomy monorepo](../../README.md). It produces a terminal
> distributable jar and is **not published**.

## Runtime flow

The entry point is a thin subclass of the shared `AbstractMCEconomyPlugin` (from the
`bukkit` module) that supplies this platform's scheduler implementation. On `onEnable`,
the shared bootstrap installs that scheduler, then:

1. Loads `config.yml` and bails out early if `enable: false`.
2. Constructs the configured `IEconomyDatabase` (SQLite by default) and calls `initialize()`.
3. Creates the central `MCEconomyProvider` over that database.
4. `loadCurrencies()` — caches all currencies in memory.
5. `ensureDefaultCurrencies()` — creates any configured currencies that are missing, so new
   `currencies` entries appear on the next enable.
6. Registers `IdentityAccountListener` so every equipped MCIdentity identity gets its own
   `identity` economy account, keyed by the identity UUID. The plugin listens to MCIdentity
   events only — there are no raw player listeners.
7. Registers the `/balance` GUI (with its click listener) and the `/pay` command, both
   bound to the sender's active identity.

All database operations are asynchronous and never block the main server thread. Follow the
standard SpigotMC scheduling conventions.

## Dependencies

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — the shared MCEconomy modules
  (via `project(':...')`).
- `org.spigotmc:spigot-api` — `compileOnly`.
- **MCIdentity** — a hard runtime dependency (`depend: [MCIdentity]` in `plugin.yml`); the
  plugin must be installed on the server.

## Building

From the repository root:

```bash
./gradlew :platforms:spigotmc:build          # produces the shaded plugin jar
./gradlew :platforms:spigotmc:shadowJar      # the distributable jar only
./gradlew :platforms:spigotmc:exportJar      # also copies the jar to $SPIGOTMC_PLUGIN_JAR_PATH if set
```

The build uses the Gradle wrapper and targets a **Java 25** toolchain.

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml). Set `enable: true` to
activate, choose a `database.type`, and customize the `currencies` list.

## Related modules

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — shared modules.
- [`papermc`](../papermc), [`foliamc`](../foliamc) — the other platform entry points.

## License

See [LICENSE](../../LICENSE).
