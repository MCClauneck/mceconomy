# MCEconomy Bukkit (`bukkit`)

The shared Bukkit module for the **MCEconomy** multi-platform Minecraft economy plugin. It
holds the core event structures and listeners reused by every platform, and compiles against
the SpigotMC API and the MCIdentity integration artifacts only.

> The `bukkit` module of the [MCEconomy monorepo](../README.md). It is an **internal** library
> consumed by the platform modules through a Gradle `project(':bukkit')` reference, and is
> **not published**.

## Contents

| Component | Description |
| --- | --- |
| `event/EconomyWalletUpdateEvent` | Custom Bukkit event fired when a wallet balance changes. |
| `listener/PlayerJoinCurrencyListener` | Re-verifies the configured default currencies on player join. |
| `listener/IdentityAccountListener` | Ensures an `identity` economy account exists (keyed by the identity UUID) whenever a player equips an MCIdentity identity. |

## MCIdentity integration

MCEconomy depends on the **MCIdentity** plugin. Each MCIdentity identity is a fully
separate character, so `IdentityAccountListener` reacts to `MCIdentityEquipEvent` and
creates that identity's economy account with the account type `identity`
(`AccountTypes.IDENTITY`), keyed by the **identity UUID** — never the base player UUID.
This keeps wallets and transaction logs separate per identity.

The MCIdentity artifacts are `compileOnly`: the MCIdentity plugin provides the classes on
the server at runtime, so nothing is shaded into the distributable jars.

## Dependencies

- [`api`](../api) — exposed transitively (`api` configuration, via `project(':api')`).
- [`common`](../common) — `implementation` scope (via `project(':common')`).
- `org.spigotmc:spigot-api` — `compileOnly`.
- `io.github.mcoriax:api` / `io.github.mcoriax:common` / `io.github.mcoriax:bukkit`
  (MCIdentity `1.0.0`) — `compileOnly`, resolved from the MCIdentity GitHub Packages
  registry.

## Building

From the repository root:

```bash
./gradlew :bukkit:build
```

GitHub Packages always requires authentication, even for public packages, so building this
module needs credentials with the `read:packages` scope. They are resolved from
`GITHUB_ACTOR`/`GITHUB_TOKEN` (CI), then `AGENT_GITHUB_NAME`/`AGENT_GITHUB_TOKEN`, then
`USER_GITHUB_NAME`/`USER_GITHUB_TOKEN`.

## Related modules

- [`api`](../api) — pure data contracts.
- [`common`](../common) — database implementations and provider.
- [`papermc`](../papermc) / [`spigotmc`](../spigotmc) / [`foliamc`](../foliamc) — platform entry points.

## License

See [LICENSE](../LICENSE).
