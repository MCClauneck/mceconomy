# Project Structure Index

`mceconomy` is the consolidated monorepo for the **MCEconomy** multi-platform Minecraft economy
plugin. It merges six formerly separate repositories into a single Gradle multi-module build.

## Top-level layout

```text
mceconomy/
├── AGENTS.md                 # Authoritative agent instruction set (read first)
├── CLAUDE.md                 # Thin router to AGENTS.md for Claude Code
├── INDEX.md                  # This file — project structure
├── README.md                 # Project overview, build guide, and requirements
├── LICENSE
├── settings.gradle           # Root build; includes every module
├── build.gradle              # Shared config (group, version) for all modules
├── gradle.properties         # Project identity and version (single source of truth)
├── gradlew / gradlew.bat     # Gradle wrapper
├── gradle/                   # Wrapper distribution metadata
├── .agents/                  # Specialised agent instructions (see .agents/README.md)
│
├── api/                      # io.github.mcclauneck:api      — PUBLISHED library
├── common/                   # io.github.mcclauneck:common   — PUBLISHED library
├── bukkit/                   # shared Bukkit events/listeners (internal, not published)
├── spigotmc/                 # SpigotMC plugin entry point (shaded jar, not published)
├── papermc/                  # PaperMC plugin entry point (shaded jar, not published)
└── foliamc/                  # FoliaMC plugin entry point (shaded jar, not published)
```

## Modules

| Module | Gradle path | Published? | Summary |
| --- | --- | --- | --- |
| `api` | `:api` | Yes (`io.github.mcclauneck:api`) | Pure-Java contracts and immutable models. |
| `common` | `:common` | Yes (`io.github.mcclauneck:common`) | `MCEconomyProvider` and database backends. |
| `bukkit` | `:bukkit` | No | Shared Bukkit events and listeners. |
| `spigotmc` | `:spigotmc` | No | SpigotMC platform entry point (shaded jar). |
| `papermc` | `:papermc` | No | PaperMC platform entry point (shaded jar). |
| `foliamc` | `:foliamc` | No | FoliaMC platform entry point (shaded jar). |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` }.
Cross-module dependencies are wired with Gradle `project(':...')` references; see
[`.agents/architecture/structure.md`](.agents/architecture/structure.md) for the full wiring
and publishing rules.

## Where to look
- Build/version config → `gradle.properties`, `build.gradle`, `settings.gradle`.
- Agent rules → `AGENTS.md` and `.agents/` (start at `.agents/README.md`).
- Per-module platform standards → each platform module's `SKILLS.md`.
