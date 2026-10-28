# MCEconomy Common (`common`)

The platform-agnostic logic module for the **MCEconomy** multi-platform Minecraft economy
plugin. It contains the central `MCEconomyProvider` and the database implementations, and is
strictly responsible for **getting and setting data from the database**.

> The `common` module of the [MCEconomy monorepo](../README.md). Its Gradle project name is
> **`common`**, so published coordinates are `io.github.mcclauneck:common`.

## Contents

| Component | Description |
| --- | --- |
| `MCEconomyProvider` | Central provider and the one entry point other plugins call; a facade over the parts below. |
| `currency/CurrencyRegistry` | The currency cache, the configured (managed) currencies, and their display order. |
| `currency/CurrencyListParser`, `currency/DefaultCurrencies` | Parse the `code,Display Name,transferable` entries; the built-in fallback currencies. |
| `balance/BalanceLedger` | Reads, sets, adds, removes, and transfers balances, writing a log row for every change. |
| `database/sql/AbstractSqlEconomyDatabase` | The JDBC plumbing the three SQL backends share: the Hikari pool, the async wrapping, and every query; each dialect supplies only its schema, its differing statements, and how it binds UUIDs, booleans, and timestamps. |
| `database/sqlite` | SQLite dialect of the shared SQL backend. |
| `database/mysql` | MySQL dialect. |
| `database/postgresql` | PostgreSQL dialect. |
| `database/mongodb` | MongoDB implementation. |
| `central` | Remote central server backend (MCEngine server-expressjs) over HTTP. |
| `http/MCEconomyHttpClient` | Lightweight HTTP client used by the central backend. |

All database operations are asynchronous and return `CompletableFuture`s, so they never block
the server thread.

## Dependencies

- [`api`](../api) — the data contracts (`compileOnly`, via `project(':api')`).

Database drivers (HikariCP, MongoDB driver, Gson) are declared `compileOnly`; the consuming
platform supplies them at runtime via its `plugin.yml` `libraries` block.

## Published coordinates

```
io.github.mcclauneck:common:<project-version>
```

Published to GitHub Packages at `https://maven.pkg.github.com/MCClauneck/mceconomy`.

## Building

From the repository root:

```bash
./gradlew :common:build
./gradlew :common:publish   # publishes to GitHub Packages (requires credentials)
```

## Related modules

- [`api`](../api) — pure data contracts.
- [`bukkit`](../bukkit) — shared Bukkit events and listeners.
- [`papermc`](../papermc) / [`spigotmc`](../spigotmc) / [`foliamc`](../foliamc) — platform entry points.

## License

See [LICENSE](../LICENSE).
