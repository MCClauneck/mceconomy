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
├── bukkit/                   # shared Bukkit events/listeners (internal, not published)
└── platforms/                # platform entry points (shaded jars, not published)
    ├── spigotmc/             # SpigotMC plugin entry point
    ├── papermc/              # PaperMC plugin entry point
    └── foliamc/              # FoliaMC plugin entry point
```

## Modules

| Module | Gradle path | Published? | Summary |
| --- | --- | --- | --- |
| `api` | `:api` | Yes (`io.github.mcclauneck:api`) | Pure-Java contracts and immutable models. |
| `common` | `:common` | Yes (`io.github.mcclauneck:common`) | `MCEconomyProvider` and database backends. |
| `bukkit` | `:bukkit` | No | Shared Bukkit events and listeners. |
| `spigotmc` | `:platforms:spigotmc` | No | SpigotMC platform entry point (shaded jar). |
| `papermc` | `:platforms:papermc` | No | PaperMC platform entry point (shaded jar). |
| `foliamc` | `:platforms:foliamc` | No | FoliaMC platform entry point (shaded jar). |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` }.
Cross-module dependencies are wired with Gradle `project(':...')` references; see each module's
`build.gradle` for the full wiring and [`README.md`](README.md) for the publishing rules.

## Where to look
- Build/version config → `gradle.properties`, `build.gradle`, `settings.gradle`.
- Per-module details → each module's `README.md`.
- Project overview, build, publishing and requirements → [`README.md`](README.md).
