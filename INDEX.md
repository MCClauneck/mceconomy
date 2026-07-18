# Project Structure Index

`mceconomy` is the consolidated monorepo for the **MCEconomy** multi-platform Minecraft economy
plugin. It merges six formerly separate repositories into a single Gradle multi-module build.

## Top-level layout

```text
mceconomy/
├── INDEX.md                  # This file — project structure
├── README.md                 # Project overview, build guide, and requirements
├── LICENSE
├── settings.gradle           # Root build; includes every module
├── build.gradle              # Shared config (group, version) for all modules
├── gradle.properties         # Project identity and version (single source of truth)
├── gradlew / gradlew.bat     # Gradle wrapper
├── gradle/                   # Wrapper distribution metadata
│
├── api/                      # io.github.mcclauneck:api      — PUBLISHED library
├── common/                   # io.github.mcclauneck:common   — PUBLISHED library
├── bukkit/                   # io.github.mcclauneck:bukkit   — PUBLISHED library (Bukkit events/listeners)
└── platforms/                # platform entry points + the universal engine (not published)
    ├── spigotmc/             # SpigotMC plugin entry point
    ├── papermc/              # PaperMC plugin entry point
    ├── foliamc/              # FoliaMC plugin entry point + Folia scheduler
    └── engine/               # Universal "central loader" — the single distributed jar
```

## Modules

| Module | Gradle path | Published? | Summary |
| --- | --- | --- | --- |
| `api` | `:api` | Yes (`io.github.mcclauneck:api`) | Pure-Java contracts and immutable models, including the `AccountTypes` constants. |
| `common` | `:common` | Yes (`io.github.mcclauneck:common`) | `MCEconomyProvider` and database backends. |
| `bukkit` | `:bukkit` | Yes (`io.github.mcclauneck:bukkit`) | Shared Bukkit core: the `AbstractMCEconomyPlugin` bootstrap, the `/balance` and `/pay` commands and GUI, the scheduler abstraction, events, and listeners, including the MCIdentity integration. |
| `spigotmc` | `:platforms:spigotmc` | No | SpigotMC entry point; bundled into the engine jar. |
| `papermc` | `:platforms:papermc` | No | PaperMC entry point; bundled into the engine jar. |
| `foliamc` | `:platforms:foliamc` | No | FoliaMC entry point and Folia scheduler; bundled into the engine jar. |
| `engine` | `:platforms:engine` | No | Universal central loader; the single distributed jar that runs on SpigotMC, PaperMC, and Folia, detecting the server at enable time and installing the matching scheduler. |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` } ← `engine`.
Cross-module dependencies are wired with Gradle `project(':...')` references; see each module's
`build.gradle` for the full wiring and [`README.md`](README.md) for the publishing rules.

The `bukkit` module also compiles (`compileOnly`) against the **MCIdentity** plugin's
published artifacts (`io.github.mcoriax:api`/`common`/`bukkit`) to bind economy accounts of
type `identity` to equipped identities; every platform `plugin.yml` declares
`depend: [MCIdentity]`.

## Documentation

| Directory / File | Purpose |
|---|---|
| [`wiki/`](wiki/) | Human-facing documentation root. |
| [`wiki/requirements.md`](wiki/requirements.md) | Functional and non functional requirements. |
| [`wiki/api.md`](wiki/api.md) | Developer API guide for third-party plugins integrating with MCEconomy (covers the `api`, `common`, and `bukkit` modules, with a cancel-give-money event example). |
| [`wiki/environment.md`](wiki/environment.md) | Build and publish environment variables and infrastructure examples. |
| [`wiki/system.md`](wiki/system.md) | System architecture and runtime behavior. |

## Where to look
- Build/version config → `gradle.properties`, `build.gradle`, `settings.gradle`.
- Per-module details → each module's `README.md`.
- Developer API integration → [`wiki/api.md`](wiki/api.md).
- MCIdentity integration → [`bukkit/README.md`](bukkit/README.md) and the root
  [`README.md`](README.md).
- Project overview, build, publishing and requirements → [`README.md`](README.md).
