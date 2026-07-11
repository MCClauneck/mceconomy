# MCEconomy API (`api`)

The pure-Java API module for the **MCEconomy** multi-platform Minecraft economy plugin.
It defines the data contracts shared by every other module and carries **no platform or
database dependencies**.

> The `api` module of the [MCEconomy monorepo](../README.md). Its Gradle project name is
> **`api`**, so published coordinates are `io.github.mcclauneck:api`.

## Contents

| Type | Description |
| --- | --- |
| `IEconomyDatabase` | The asynchronous database contract every backend implements. |
| `Account` | An economy holder identified by an `accountType` plus a `UUID`. |
| `Currency` | A currency identified by a unique `systemCode` with a `displayName`. |
| `Wallet` | The balance of one account for one currency. |
| `LogScope` | Audit categories for balance changes (`SEND`, `RECEIVE`, `SPEND`, `DONATE`). |

All asynchronous operations are expressed with `CompletableFuture`. This module contains
**only interfaces and immutable models** — no implementation logic.

## Published coordinates

```
io.github.mcclauneck:api:<project-version>
```

Published to GitHub Packages at `https://maven.pkg.github.com/MCClauneck/mceconomy`.

## Building

From the repository root:

```bash
./gradlew :api:build
./gradlew :api:publish   # publishes to GitHub Packages (requires credentials)
```

## Related modules

- [`common`](../common) — database implementations and provider.
- [`bukkit`](../bukkit) — shared Bukkit events and listeners.
- [`papermc`](../papermc) / [`spigotmc`](../spigotmc) / [`foliamc`](../foliamc) — platform entry points.

## License

See [LICENSE](../LICENSE).
