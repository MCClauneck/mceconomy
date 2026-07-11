# MCEconomy Bukkit (`bukkit`)

The shared Bukkit module for the **MCEconomy** multi-platform Minecraft economy plugin. It
holds the core event structures and listeners reused by every platform, and compiles against
the SpigotMC API only.

> The `bukkit` module of the [MCEconomy monorepo](../README.md). It is an **internal** library
> consumed by the platform modules through a Gradle `project(':bukkit')` reference, and is
> **not published**.

## Contents

| Component | Description |
| --- | --- |
| `event/EconomyWalletUpdateEvent` | Custom Bukkit event fired when a wallet balance changes. |
| `listener/PlayerJoinCurrencyListener` | Re-verifies the configured default currencies on player join. |

## Dependencies

- [`api`](../api) — exposed transitively (`api` configuration, via `project(':api')`).
- [`common`](../common) — `implementation` scope (via `project(':common')`).
- `org.spigotmc:spigot-api` — `compileOnly`.

## Building

From the repository root:

```bash
./gradlew :bukkit:build
```

## Related modules

- [`api`](../api) — pure data contracts.
- [`common`](../common) — database implementations and provider.
- [`papermc`](../papermc) / [`spigotmc`](../spigotmc) / [`foliamc`](../foliamc) — platform entry points.

## License

See [LICENSE](../LICENSE).
