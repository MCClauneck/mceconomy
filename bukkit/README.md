# MCEconomy Bukkit (`bukkit`)

The shared Bukkit module for the **MCEconomy** multi-platform Minecraft economy plugin. It
holds the core event structures and listeners reused by every platform, and compiles against
the SpigotMC API and the MCIdentity integration artifacts only.

> The `bukkit` module of the [MCEconomy monorepo](../README.md). Its Gradle project name is
> **`bukkit`**, so published coordinates are `io.github.mcclauneck:bukkit`. It is consumed by
> the platform modules through a Gradle `project(':bukkit')` reference, and is **published** to
> GitHub Packages so third-party plugins can compile against its events.

## Contents

| Component | Description |
| --- | --- |
| `AbstractMCEconomyPlugin` | Shared plugin bootstrap (config, database wiring, provider, listener and command registration) extended by every platform entry point. |
| `commands/BalanceCommand` | `/balance` — opens the paged balance GUI for the sender's active identity. |
| `commands/PayCommand`, `commands/PayTabCompleter` | `/pay <player> <currency> <amount>` — fully logged transfer between two active identities. |
| `config/CurrencyConfig` | Parses the `currencies` config section: definitions (display name, transferable) and per-currency head textures. |
| `event/EconomyTransactionEvent` | Cancellable Bukkit event fired before a balance change; the veto point (e.g. cancel giving money to a player). |
| `event/EconomyWalletUpdateEvent` | Notification-only Bukkit event fired after a wallet balance changes. |
| `gui/BalanceGui`, `gui/BalanceGuiHolder`, `gui/BalanceGuiListener` | The paged balance GUI, its inventory holder, and the click listener. |
| `listener/IdentityAccountListener` | Ensures an `identity` economy account exists (keyed by the identity UUID) whenever a player equips an MCIdentity identity. |
| `scheduler/PlatformScheduler`, `scheduler/Schedulers`, `scheduler/BukkitPlatformScheduler` | Platform-agnostic scheduling (async work and entity-thread hops); Folia ships its own implementation in the foliamc module. |
| `util/SkullTextures` | Builds custom player heads from base64 texture values. |

The plugin integrates through MCIdentity events only — it registers no raw player
listeners, so all economy state stays bound to identities rather than player accounts.

## MCIdentity integration

MCEconomy depends on the **MCIdentity** plugin. Each MCIdentity identity is a fully
separate character, so `IdentityAccountListener` reacts to `MCIdentityEquipEvent` and
creates that identity's economy account with the account type `identity`
(`AccountTypes.IDENTITY`), keyed by the **identity UUID** — never the base player UUID.
This keeps wallets and transaction logs separate per identity.

The MCIdentity artifacts are `compileOnly`: the MCIdentity plugin provides the classes on
the server at runtime, so nothing is shaded into the distributed jar.

## Dependencies

All dependencies are `compileOnly`: the `engine` module shades `api`, `common`, and `bukkit`
into the single distributed jar, so this module never bundles them itself.

- [`api`](../api) — `compileOnly` (via `project(':api')`).
- [`common`](../common) — `compileOnly` (via `project(':common')`).
- `org.spigotmc:spigot-api` — `compileOnly` (provided by the server).
- `io.github.mcoriax:api` / `io.github.mcoriax:common` / `io.github.mcoriax:bukkit`
  (MCIdentity `2.0.0`) — `compileOnly`, resolved from the MCIdentity GitHub Packages
  registry (provided by the MCIdentity plugin at runtime).

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
