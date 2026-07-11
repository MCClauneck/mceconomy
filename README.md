# MCEconomy

**MCEconomy** is a multi-platform Minecraft economy plugin. This repository is the consolidated
**monorepo** that merges the six formerly separate repositories — `plugin-api`, `plugin-common`,
`plugin-bukkit`, `plugin-spigotmc`, `plugin-papermc`, and `plugin-foliamc` — into a single
Gradle multi-module build.

All modules share the group `io.github.mcclauneck` and one Semantic Version
(`Major.Minor.Patch`), defined once in [`gradle.properties`](gradle.properties).

## Modules

| Module | Gradle path | Published | Description |
| --- | --- | --- | --- |
| [`api`](api) | `:api` | **`io.github.mcclauneck:api`** | Pure-Java data contracts and immutable models. No platform or database dependencies. |
| [`common`](common) | `:common` | **`io.github.mcclauneck:common`** | The central `MCEconomyProvider` and the SQLite/MySQL/PostgreSQL/MongoDB/API database backends. |
| [`bukkit`](bukkit) | `:bukkit` | — | Shared Bukkit events and listeners, reused by every platform. |
| [`spigotmc`](platforms/spigotmc) | `:platforms:spigotmc` | — | SpigotMC plugin entry point; produces the shaded distributable jar. |
| [`papermc`](platforms/papermc) | `:platforms:papermc` | — | PaperMC plugin entry point; produces the shaded distributable jar. |
| [`foliamc`](platforms/foliamc) | `:platforms:foliamc` | — | FoliaMC plugin entry point; produces the shaded distributable jar. |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` }.
Modules depend on each other through Gradle `project(':...')` references, so a single build
compiles the whole project — no intermediate publishing step is required.

## Building

The project uses the Gradle wrapper.

```bash
./gradlew build                 # build every module
./gradlew :platforms:papermc:build        # build one platform (also: spigotmc, foliamc)
./gradlew :platforms:papermc:shadowJar    # produce just the shaded PaperMC plugin jar
./gradlew :platforms:papermc:exportJar    # copy the jar to $PAPERMC_PLUGIN_JAR_PATH if set
```

The platform modules (`spigotmc`, `papermc`, `foliamc`) produce the distributable, shaded
plugin jars, and accept `SPIGOTMC_PLUGIN_JAR_PATH` / `PAPERMC_PLUGIN_JAR_PATH` /
`FOLIAMC_PLUGIN_JAR_PATH` to copy the built jar to a local test server.

## Publishing

Only the two reusable libraries publish to GitHub Packages
(`https://maven.pkg.github.com/MCClauneck/mceconomy`):

```bash
./gradlew :api:publish
./gradlew :common:publish
```

The `bukkit`, `spigotmc`, `papermc`, and `foliamc` modules do **not** publish — `bukkit` is an
internal library consumed via a project reference, and the platform modules are terminal
distributable jars.

## Configuration

Each platform module ships a `src/main/resources/config.yml`. Set `enable: true` to activate,
choose a `database.type`, and customize the `currencies` list.

## Versioning

Versions follow Semantic Versioning (`Major.Minor.Patch`, optionally `-{type}` such as
`1.0.0-beta`). `project-version` in [`gradle.properties`](gradle.properties) is the single
source of truth for every module.

## Requirements

- **Build tool:** the bundled Gradle wrapper (Gradle 9.5.0). No system Gradle required.
- **JDK:** a **Java 25** toolchain. Gradle auto-provisions it via the foojay resolver if it is
  not already installed; otherwise install a JDK 25 locally.
- **Minecraft server:** a SpigotMC, PaperMC, or Folia server on `api-version` `1.20` or newer,
  matching the platform module you deploy.
- **Runtime libraries:** the platform `plugin.yml` declares the database drivers the server
  downloads at runtime (`com.zaxxer:HikariCP`, `org.mongodb:mongodb-driver-sync`).
- **Credentials (GitHub Packages):** publishing `api`/`common` and resolving the
  `io.github.mcengine:*` extension artifacts require GitHub Packages credentials, resolved from
  `GITHUB_ACTOR`/`GITHUB_TOKEN` (CI), then `AGENT_GITHUB_NAME`/`AGENT_GITHUB_TOKEN`, then
  `USER_GITHUB_NAME`/`USER_GITHUB_TOKEN`.

## License

See [LICENSE](LICENSE).
