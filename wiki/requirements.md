# Requirements

This document lists the functional and non functional requirements of MCEconomy. It is a
plugin extension for the Bukkit family of Minecraft servers, not a standalone application.

## Runtime requirements

- A **Java 25** runtime on the server that runs the plugin.
- A **SpigotMC**, **PaperMC**, or **Folia** server on `api-version` `1.20` or newer. Deploy the
  platform jar that matches the server.
- The **MCIdentity** plugin (`MCOriax/mcidentity`) installed on the server. It is a hard
  dependency declared as `depend: [MCIdentity]` in every platform `plugin.yml`, so MCIdentity
  always initializes first.
- Runtime database drivers, declared in each platform `plugin.yml` and downloaded by the server
  (`com.zaxxer:HikariCP`, `org.mongodb:mongodb-driver-sync`).
- A database backend. **SQLite** is the default and needs no external service. **MySQL**,
  **PostgreSQL**, **MongoDB**, and the **central** backend are used only when selected.

## Build requirements

- The bundled **Gradle wrapper** (Gradle 9.5.0). No system Gradle is required.
- A **Java 25** toolchain. Gradle auto provisions it through the foojay resolver when it is not
  already installed.
- **GitHub Packages credentials** to resolve the MCIdentity `compileOnly` artifacts and to
  publish the `api`, `common`, and `bukkit` libraries. See [`environment.md`](environment.md)
  for the variables and the resolution order.

## Functional requirements

- **Per identity accounts.** Economy data is bound to the active identity UUID with account
  type `identity` (`AccountTypes.IDENTITY`), never the raw player UUID. Every identity a player
  owns keeps its own wallets and transaction logs. Equipping an identity ensures its account
  exists.
- **Balance queries.** The `/balance` command opens a paged GUI, one custom head per currency,
  with no permission required. The inventory sizes itself to the currencies on the page (one row
  for every nine currencies) and pads leftover slots, and the navigation row when a second page
  exists, with a black glass pane.
- **Transfers.** The `/pay <player> <currency> <amount>` command pays another player's active
  identity. The transfer is atomic and fully logged (`send` and `receive` rows plus a
  transaction receipt). Non transferable currencies are rejected.
- **Currency model.** Currencies are configured per platform `config.yml`, keyed by system code,
  each with a display name, a transferable flag, and an optional head texture. Gold, point, and
  token default to non transferable.
- **Balance operations.** The provider adds, removes, and sets balances, rejecting negative
  amounts and insufficient funds, and logs every change.

## Non functional requirements

- **Multi platform.** One shared implementation targets SpigotMC, PaperMC, and Folia. Each
  platform produces its own shaded plugin jar; only `api`, `common`, and `bukkit` publish as
  libraries.
- **Single source of truth for versions.** `project-version` in
  [`gradle.properties`](../gradle.properties) defines one Semantic Version for every module.
- **Thread safety.** Inventory and player operations run on the thread that owns the player (the
  main thread on Spigot and Paper, the region thread on Folia) through the scheduler abstraction.
- **Auditability.** Every balance change writes log rows so transactions can be reconstructed.
- **Extensibility.** The published libraries and the `MCEconomyProvider`, plus Bukkit events,
  let third party plugins integrate. See [`api.md`](api.md).
