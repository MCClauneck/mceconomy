# MCEconomy PaperMC (`papermc`)

The **PaperMC** platform entry point for the **MCEconomy** multi-platform Minecraft economy
plugin. It wires the shared modules together into a single distributable, shaded jar.

> The `papermc` module of the [MCEconomy monorepo](../README.md). It produces a terminal
> distributable jar and is **not published**.

## Runtime flow

On `onEnable`, the plugin:

1. Loads `config.yml` and bails out early if `enable: false`.
2. Constructs the configured `IEconomyDatabase` (SQLite by default) and calls `initialize()`.
3. Creates the central `MCEconomyProvider` over that database.
4. `loadCurrencies()` — caches all currencies in memory.
5. `ensureDefaultCurrencies()` — generates the configured default currencies on first run.
6. Registers `PlayerJoinCurrencyListener` so defaults are re-verified on player join.
7. Boots the MCExtension system and registers the `/mceconomyextension` command.

All database operations are asynchronous and never block the main server thread. Follow the
standard PaperMC scheduling and Adventure (`Component.translatable()`) conventions.

## Dependencies

- [`api`](../api), [`common`](../common), [`bukkit`](../bukkit) — the shared MCEconomy modules
  (via `project(':...')`).
- `io.github.mcengine:manager`, `:papermc` — the MCExtension system (relocated into `io.github.mcclauneck`).
- `io.papermc.paper:paper-api` — `compileOnly`.

## Building

From the repository root:

```bash
./gradlew :papermc:build          # produces the shaded plugin jar
./gradlew :papermc:shadowJar      # the distributable jar only
./gradlew :papermc:exportJar      # also copies the jar to $PAPERMC_PLUGIN_JAR_PATH if set
```

The build uses the Gradle wrapper and targets a **Java 25** toolchain.

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml). Set `enable: true` to
activate, choose a `database.type`, and customize the `currencies` list.

## Related modules

- [`api`](../api), [`common`](../common), [`bukkit`](../bukkit) — shared modules.
- [`spigotmc`](../spigotmc), [`foliamc`](../foliamc) — the other platform entry points.

## License

See [LICENSE](../LICENSE).
