# MCEconomy Bukkit (`bukkit`)

The shared Bukkit module for the **MCEconomy** multi-platform Minecraft economy plugin. It
holds the core event structures and listeners reused by every platform, and compiles against
the SpigotMC API only.

> The `bukkit` module of the [MCEconomy monorepo](../README.md). Its Gradle project name is
> **`bukkit`**, so published coordinates are `io.github.mcclauneck:bukkit`. It is consumed by
> the platform modules through a Gradle `project(':bukkit')` reference, and is **published** to
> GitHub Packages so third-party plugins can compile against its events.

## Contents

| Component | Description |
| --- | --- |
| `AbstractMCEconomyPlugin` | Shared plugin bootstrap extended by every platform entry point; it sequences the enable steps below and exposes the provider, account binding, and agents settings. |
| `database/EconomyDatabaseFactory` | Builds the backend `database.type` selects (SQLite, MySQL, PostgreSQL, MongoDB, or the central server). |
| `host/AccountBinding` | Binds every wallet to its player at enable time (`AccountSource.players()`) and registers the player account listener. |
| `commands/CommandRegistrar` | Wires the `/balance` and `/pay` executors and the `/pay` tab completer. |
| `commands/BalanceCommand` | `/balance` — opens the paged balance GUI for the sender's own account. |
| `commands/CommandPermissions` | The `mcclauneck.mceconomy.command.*` nodes the commands check. |
| `commands/PayCommand`, `commands/PayTabCompleter` | `/pay <player> <currency> <amount>` — fully logged transfer between two players' accounts. |
| `config/CurrencyConfig` | Parses the `currencies` config section: definitions (display name, transferable) and per-currency head textures. |
| `config/AgentsSettings`, `config/MCAgentsGlobal` | Read the `agents` config block (`model`, `token`, both `global` by default) and resolve `global` from the running MCAgents plugin's `getAgentsModel()` / `getAgentsToken()` by reflection; exposed as `getAgentsModel()` / `getAgentsToken()` on `AbstractMCEconomyPlugin`. |
| `event/EconomyTransactionEvent` | Cancellable Bukkit event fired before a balance change; the veto point (e.g. cancel giving money to a player). |
| `event/EconomyWalletUpdateEvent` | Notification-only Bukkit event fired after a wallet balance changes. |
| `gui/BalanceGui`, `gui/BalanceGuiHolder`, `gui/BalanceGuiListener` | The paged balance GUI, its inventory holder, and the click listener. |
| `listener/PlayerAccountListener` | Ensures a `player` economy account exists (keyed by the player UUID) when a player joins. |
| `scheduler/PlatformScheduler`, `scheduler/Schedulers`, `scheduler/BukkitPlatformScheduler` | Platform-agnostic scheduling (async work and entity-thread hops); Folia ships its own implementation in the foliamc module. |
| `util/SkullTextures` | Builds custom player heads from base64 texture values. |

## Accounts

Every wallet belongs to the player: account type `player` (`AccountTypes.PLAYER`) keyed by
the player UUID. Since MCIdentity 5.0.0 a player has one character whatever profession they
wear, so this module reads nothing from MCIdentity, and consumers of this published library
inherit no MCIdentity dependency.

## Dependencies

All dependencies are `compileOnly`: the `engine` module shades `api`, `common`, and `bukkit`
into the single distributed jar, so this module never bundles them itself.

- [`api`](../api) — `compileOnly` (via `project(':api')`).
- [`common`](../common) — `compileOnly` (via `project(':common')`).
- `org.spigotmc:spigot-api` — `compileOnly` (provided by the server).

## Building

From the repository root:

```bash
./gradlew :bukkit:build
./gradlew :bukkit:publish   # publishes to GitHub Packages (requires credentials)
```

GitHub Packages always requires authentication, even for public packages, so building and
publishing this module needs credentials with the `read:packages` (build) and `write:packages`
(publish) scopes, resolved from `GITHUB_ACTOR`/`GITHUB_TOKEN`.

## Related modules

- [`api`](../api) — pure data contracts.
- [`common`](../common) — database implementations and provider.
- [`papermc`](../papermc) / [`spigotmc`](../spigotmc) / [`foliamc`](../foliamc) — platform entry points.

## License

See [LICENSE](../LICENSE).
