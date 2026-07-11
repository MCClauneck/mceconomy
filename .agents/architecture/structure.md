# Architecture & Technical Standards — `mceconomy`

## Repository Scope

This is the **consolidated monorepo** for the MCEconomy multi-platform Minecraft economy
plugin. It replaces the six previously separate repositories (`plugin-api`, `plugin-common`,
`plugin-bukkit`, `plugin-spigotmc`, `plugin-papermc`, `plugin-foliamc`), which are now Gradle
subprojects of a single build.

All modules share `group = io.github.mcclauneck` and one `project-version`, applied from the
root `build.gradle` `allprojects` block.

## Modules

| Module (`project`) | Plugins | Publishes | Role |
| --- | --- | --- | --- |
| `api` | `java-library`, `maven-publish` | **Yes** — `io.github.mcclauneck:api` | Pure-Java data contracts and immutable models. No platform or database dependencies. |
| `common` | `java`, `maven-publish` | **Yes** — `io.github.mcclauneck:common` | `MCEconomyProvider` and database backends. Getting/setting data only; no platform code. |
| `bukkit` | `java-library` | No | Shared Bukkit events and listeners, reused by every platform. |
| `spigotmc` | `java`, `com.gradleup.shadow` | No | SpigotMC plugin entry point; produces the shaded distributable jar. |
| `papermc` | `java`, `com.gradleup.shadow` | No | PaperMC plugin entry point; produces the shaded distributable jar. |
| `foliamc` | `java`, `com.gradleup.shadow` | No | FoliaMC plugin entry point; produces the shaded distributable jar. |

## Publishing Rules

- **Only `api` and `common` publish.** They expose the reusable library artifacts and carry a
  `maven-publish` block that targets GitHub Packages at
  `https://maven.pkg.github.com/${git-org-name}/${git-repository}` (i.e. `MCClauneck/mceconomy`).
- `bukkit`, `spigotmc`, `papermc`, and `foliamc` MUST NOT publish. They are consumed internally
  (`bukkit`) or are terminal distributables (the platform shadow jars). Do not add
  `maven-publish` to these modules.
- Do NOT change any module's `project` name; the published coordinates for `api`/`common` stay
  `io.github.mcclauneck:api` and `io.github.mcclauneck:common`.

## Dependency Wiring

Cross-module dependencies are resolved locally through Gradle project references, not published
artifacts:

```text
api        → (none)
common     → compileOnly project(':api')
bukkit     → api project(':api'), implementation project(':common')
spigotmc   → implementation project(':api'), project(':common'), project(':bukkit')
papermc    → implementation project(':api'), project(':common'), project(':bukkit')
foliamc    → implementation project(':api'), project(':common'), project(':bukkit')
```

External artifacts still resolved from remote repositories:

- `io.github.mcengine:manager` and `io.github.mcengine:{spigotmc,papermc,foliamc}` — from
  `MCEngine/mcextension` GitHub Packages. The shadow jars relocate `io.github.mcengine` to
  `io.github.mcclauneck`; keep this relocation.
- Platform APIs (`org.spigotmc:spigot-api`, `io.papermc.paper:paper-api`, `dev.folia:folia-api`)
  as `compileOnly`.

## Rules

- `api` and `common` MUST stay pure Java using `CompletableFuture` for async results, with no
  platform code. Database drivers in `common` stay `compileOnly`.
- `bukkit` compiles against the SpigotMC API only (`compileOnly`); no Paper/Folia APIs here.
- Platform-specific scheduling and thread-safety rules live under
  [`.agents/platforms/<platform>/concurrency.md`](../platforms) and the module `SKILLS.md`.
