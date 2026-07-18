# MCEconomy FoliaMC (`foliamc`)

The **FoliaMC** platform entry point for the **MCEconomy** multi-platform Minecraft economy
plugin. It supplies the region-aware Folia scheduler and builds a thin jar that the universal
[`engine`](../engine) module bundles into the single distributed jar.

> The `foliamc` module of the [MCEconomy monorepo](../../README.md). It builds a thin,
> per-platform jar (entry point + Folia scheduler) that the [`engine`](../engine) module
> bundles, and is **not published**.

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

All database operations are asynchronous and never block server threads. Respect Folia's region
model and schedulers.

## Dependencies

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — the shared MCEconomy modules,
  `compileOnly` (via `project(':...')`); the `engine` module shades them into the distributed jar.
- `dev.folia:folia-api` — `compileOnly`.
- **MCIdentity** — a hard runtime dependency (`depend: [MCIdentity]` in `plugin.yml`); the
  plugin must be installed on the server.

## Building

From the repository root:

```bash
./gradlew :platforms:foliamc:build          # produces the thin per-platform jar
./gradlew :platforms:foliamc:shadowJar      # the thin jar only (bundled by the engine)
./gradlew :platforms:engine:build           # build the single universal jar
```

The build uses the Gradle wrapper and targets a **Java 25** toolchain.

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml). Set `enable: true` to
activate, choose a `database.type`, and customize the `currencies` list.

## Related modules

- [`api`](../../api), [`common`](../../common), [`bukkit`](../../bukkit) — shared modules.
- [`spigotmc`](../spigotmc), [`papermc`](../papermc) — the other platform entry points.

## License

See [LICENSE](../../LICENSE).
