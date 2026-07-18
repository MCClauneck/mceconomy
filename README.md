# MCEconomy

**MCEconomy** is a multi-platform Minecraft economy plugin. This repository is the consolidated
**monorepo** that merges the six formerly separate repositories — `plugin-api`, `plugin-common`,
`plugin-bukkit`, `plugin-spigotmc`, `plugin-papermc`, and `plugin-foliamc` — into a single
Gradle multi-module build.

All modules share the group `io.github.mcclauneck` and one Semantic Version
(`Major.Minor.Patch`), defined once in [`gradle.properties`](gradle.properties).

## Project Overview

* **Platform:** [github.com](https://github.com)
* **Organization:** [MCClauneck](https://github.com/MCClauneck)
* **Repository:** [mceconomy](https://github.com/MCClauneck/mceconomy)

## Modules

| Module | Gradle path | Published | Description |
| --- | --- | --- | --- |
| [`api`](api) | `:api` | **`io.github.mcclauneck:api`** | Pure-Java data contracts and immutable models. No platform or database dependencies. |
| [`common`](common) | `:common` | **`io.github.mcclauneck:common`** | The central `MCEconomyProvider` and the SQLite/MySQL/PostgreSQL/MongoDB/central database backends. |
| [`bukkit`](bukkit) | `:bukkit` | **`io.github.mcclauneck:bukkit`** | Shared Bukkit events and listeners, reused by every platform. |
| [`spigotmc`](platforms/spigotmc) | `:platforms:spigotmc` | — | SpigotMC plugin entry point; bundled into the engine jar. |
| [`papermc`](platforms/papermc) | `:platforms:papermc` | — | PaperMC plugin entry point; bundled into the engine jar. |
| [`foliamc`](platforms/foliamc) | `:platforms:foliamc` | — | FoliaMC plugin entry point and Folia scheduler; bundled into the engine jar. |
| [`engine`](platforms/engine) | `:platforms:engine` | — | Universal **central loader** — the single distributed jar that runs on SpigotMC, PaperMC, and Folia, detecting the server at enable time. |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` } ← `engine`.
Modules depend on each other through Gradle `project(':...')` references, so a single build
compiles the whole project — no intermediate publishing step is required.

## Building

The project uses the Gradle wrapper.

```bash
./gradlew build                          # build every module
./gradlew :platforms:engine:build        # build the universal jar
./gradlew :platforms:engine:shadowJar    # produce just the shaded universal jar
./gradlew :platforms:engine:exportJar    # copy the jar to $ENGINE_PLUGIN_JAR_PATH if set
```

The `engine` module produces the single distributable, shaded plugin jar
(`MCEconomy-<version>.jar`) that runs on SpigotMC, PaperMC, and Folia — the server is detected
at enable time. It accepts `ENGINE_PLUGIN_JAR_PATH` to copy the built jar to a local test
server. The `spigotmc`, `papermc`, and `foliamc` modules still build thin per-platform jars,
which the engine bundles.

## Publishing

Only the three reusable libraries publish to GitHub Packages
(`https://maven.pkg.github.com/MCClauneck/mceconomy`):

```bash
./gradlew :api:publish
./gradlew :common:publish
./gradlew :bukkit:publish
```

The `spigotmc`, `papermc`, `foliamc`, and `engine` platform modules do **not** publish — they
are terminal shaded jars. The `engine` module is the single place that shades `api`, `common`,
and `bukkit` into the one universal distributable jar; the per-platform modules ship only their
own entry point classes, which the engine bundles.

## MCIdentity integration

MCEconomy integrates with the **MCIdentity** plugin (`MCOriax/mcidentity`) and declares it as
a hard dependency (`depend: [MCIdentity]` in every platform `plugin.yml`), so MCIdentity must
be installed on the server and always initializes first.

Each MCIdentity identity is a fully separate character, so economy data is bound to the
**identity UUID** — never the base player UUID. When a player equips an identity, the shared
`IdentityAccountListener` ensures an economy account exists for that identity using the
account type `identity` (`AccountTypes.IDENTITY` in the `api` module). Every identity a
player owns therefore keeps its own wallets and transaction logs.

The MCIdentity artifacts (`io.github.mcoriax:api`, `io.github.mcoriax:common`,
`io.github.mcoriax:bukkit`, version `1.1.0`) are consumed `compileOnly` from the MCIdentity
GitHub Packages registry — the plugin provides the classes at runtime, so nothing is shaded.

## Developer API

Third-party plugins integrate with MCEconomy through the published `api`, `common`, and
`bukkit` libraries and the `MCEconomyProvider`. See the developer guide in
[`wiki/api.md`](wiki/api.md) for dependency setup, the provider method reference, and the
Bukkit events (including how to cancel giving money to a player).

## Commands

Both commands are available to every player — no permission is required — and both operate on
the player's **active MCIdentity identity**, never on the raw player account:

| Command | Description |
| --- | --- |
| `/balance` | Opens a paged GUI showing the active identity's balance for every currency, one custom head per currency (texture configurable per currency). The window sizes itself to the currencies on the page, growing one row for every nine currencies and padding leftover slots with a black glass pane. |
| `/pay <player> <currency> <amount>` | Pays another player's active identity. The transfer is atomic and fully logged (`send` and `receive` log rows plus a transaction receipt), and non-transferable currencies are rejected. |

## Configuration

Each platform module ships a `src/main/resources/config.yml`. Set `enable: true` to activate
and choose a `database.type`. Currencies are configured as a section keyed by system code:

```yaml
currencies:
  coin:
    display_name: Coin
    transferable: true       # false locks the currency to the account that earned it
    texture: <base64>        # head texture shown in the /balance GUI (optional)
```

The legacy list format (`- coin,Coin,true`) is still accepted. Gold, point, and token default
to non-transferable.

## Versioning

Versions follow Semantic Versioning (`Major.Minor.Patch`, optionally `-{type}` such as
`1.0.0-beta`). `project-version` in [`gradle.properties`](gradle.properties) is the single
source of truth for every module.

## Requirements

- **Build tool:** the bundled Gradle wrapper (Gradle 9.5.0). No system Gradle required.
- **JDK:** a **Java 25** toolchain. Gradle auto-provisions it via the foojay resolver if it is
  not already installed; otherwise install a JDK 25 locally.
- **Minecraft server:** a SpigotMC, PaperMC, or Folia server on `api-version` `1.20` or newer.
  The single universal jar detects the server automatically, so the same jar runs on all three.
- **MCIdentity plugin:** MCEconomy hard-depends on the MCIdentity plugin
  (`depend: [MCIdentity]`), so it must be installed on the server.
- **Runtime libraries:** the platform `plugin.yml` declares the database drivers the server
  downloads at runtime (`com.zaxxer:HikariCP`, `org.mongodb:mongodb-driver-sync`).
- **Credentials (GitHub Packages):** building the `bukkit` module (resolving the MCIdentity
  artifacts) and publishing `api`/`common` require GitHub Packages credentials with the
  `read:packages`/`write:packages` scopes, resolved from `GITHUB_ACTOR`/`GITHUB_TOKEN`.

## Documentation

Detailed documentation lives in the [`wiki/`](wiki/) directory:

- [`wiki/requirements.md`](wiki/requirements.md) — functional and non functional requirements.
- [`wiki/api.md`](wiki/api.md) — developer API guide for third party plugins.
- [`wiki/environment.md`](wiki/environment.md) — build and publish environment variables and infrastructure examples.
- [`wiki/system.md`](wiki/system.md) — system architecture and runtime behavior.

## License

See [LICENSE](LICENSE).
