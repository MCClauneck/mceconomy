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
├── tools/                    # MCAgents AI tools (pure Java, not published; shaded by the engine)
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
| `common` | `:common` | Yes (`io.github.mcclauneck:common`) | `MCEconomyProvider` (a facade over `currency/CurrencyRegistry` and `balance/BalanceLedger`) and database backends, the SQL ones sharing `database/sql/AbstractSqlEconomyDatabase`. |
| `bukkit` | `:bukkit` | Yes (`io.github.mcclauneck:bukkit`) | Shared Bukkit core: the `AbstractMCEconomyPlugin` bootstrap, the `/balance` and `/pay` commands and GUI, the scheduler abstraction, events, listeners, and the reflective MCIdentity binding under `host/`. |
| `tools` | `:tools` | No | Pure-Java, read-only MCAgents AI tools (`economy_get_my_balances`, `economy_list_currencies`) and the reflective `AITool` proxy adapter; registered by `bukkit/bridge/mcagents/MCAgentsToolRegistrar` when MCAgents is installed, shaded by the engine. |
| `spigotmc` | `:platforms:spigotmc` | No | SpigotMC entry point; bundled into the engine jar. |
| `papermc` | `:platforms:papermc` | No | PaperMC entry point; bundled into the engine jar. |
| `foliamc` | `:platforms:foliamc` | No | FoliaMC entry point and Folia scheduler; bundled into the engine jar. |
| `engine` | `:platforms:engine` | No | Universal central loader; the single distributed jar that runs on SpigotMC, PaperMC, and Folia, detecting the server at enable time and installing the matching scheduler. |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` } ← `engine`;
`tools` depends only on `api`, and `bukkit` and `engine` depend on it.
Cross-module dependencies are wired with Gradle `project(':...')` references; see each module's
`build.gradle` for the full wiring and [`README.md`](README.md) for the publishing rules.

The `bukkit` module binds economy accounts of type `identity` to equipped **MCIdentity**
identities, but declares **no** MCIdentity artifact: `bukkit/host/MCIdentityBridge` resolves
the provider and the equip event reflectively through the host plugin's classloader at enable
time, against the pure `api/host/IdentitySource` contract. Every platform `plugin.yml`
declares `depend: [MCIdentity]`, which is what guarantees the host is loaded first.
`gradle.properties` records the MCIdentity release the bridge targets.

## Documentation

| Directory / File | Purpose |
|---|---|
| [`wiki/`](wiki/) | Human-facing documentation root. |
| [`wiki/requirements.md`](wiki/requirements.md) | Functional and non functional requirements. |
| [`wiki/api.md`](wiki/api.md) | Developer API guide for third-party plugins integrating with MCEconomy (covers the `api`, `common`, and `bukkit` modules, with a cancel-give-money event example). |
| [`wiki/environment.md`](wiki/environment.md) | Build and publish environment variables and infrastructure examples. |
| [`wiki/system.md`](wiki/system.md) | System architecture and runtime behavior. |
| [`wiki/agent-tools.md`](wiki/agent-tools.md) | The optional MCAgents AI tools: names, what they return, and who can use them. |
| [`wiki/logs/1/2/0/CHANGELOG.md`](wiki/logs/1/2/0/CHANGELOG.md) | Changelog for 1.2.0 — MCAgents AI tools, the Minecraft 26.3 server APIs, and Publish gated on a merged `release/{version}` pull request. |
| [`wiki/logs/1/3/0/CHANGELOG.md`](wiki/logs/1/3/0/CHANGELOG.md) | Changelog for 1.3.0 — the `agents` block (`model`, `token`), defaulting to MCAgents' own model and key. |
| [`wiki/logs/1/4/0/CHANGELOG.md`](wiki/logs/1/4/0/CHANGELOG.md) | Changelog for 1.4.0 — MCIdentity becomes a soft dependency; without it, wallets belong to players. |
| [`wiki/logs/1/5/0/CHANGELOG.md`](wiki/logs/1/5/0/CHANGELOG.md) | Changelog for 1.5.0 — each of the nine `agents` modalities read on its own. |
| [`wiki/logs/1/6/0/CHANGELOG.md`](wiki/logs/1/6/0/CHANGELOG.md) | Changelog for 1.6.0 — permission nodes for `/balance` and `/pay`, and the single-responsibility split. |
| [`.agents/index/memory-index.md`](.agents/index/memory-index.md) | Agent memory: task records that let a later session continue earlier work. |

## Where to look
- Build/version config → `gradle.properties`, `build.gradle`, `settings.gradle`.
- Per-module details → each module's `README.md`.
- Developer API integration → [`wiki/api.md`](wiki/api.md).
- MCAgents AI tools → [`wiki/agent-tools.md`](wiki/agent-tools.md).
- Release history → [`wiki/logs/`](wiki/logs/), one `{Major}/{Minor}/{Patch}/CHANGELOG.md` per version.
- MCIdentity integration → [`bukkit/README.md`](bukkit/README.md) and the root
  [`README.md`](README.md).
- Project overview, build, publishing and requirements → [`README.md`](README.md).
