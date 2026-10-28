---
name: plugin-refactor
description: Task record for the MCEconomy part of the organisation-wide plugin refactor — command permissions, single-responsibility split, and the 1.6.0 release.
---

# Task record — plugin refactor and command permissions

**Status:** done
**Requested by:** the owner, 2026-10-28, approved in advance (plan, pull requests, merge order, version 1.6.0).

## Goal

MCEconomy is easier to read and change — each source file has one responsibility and the
duplicated SQL plumbing in the database backends lives once — and every command is guarded by a
namespaced permission node declared in all four `plugin.yml` files, so a server owner can
revoke `/balance` or `/pay` from a group.

## Constraints

- No change to the public API in `api`, `common`, or `bukkit` that Market or SlayerRewards can
  reach (they bind `getProvider()`, `MCEconomyProvider`, `Currency`, `LogScope` and the two
  events reflectively), to config keys, to database schemas, or to player-facing messages.
- Module isolation: no platform types in `api` or `common`.

## Tasks

| # | Title | Branch | Files / areas | PR |
|---|---|---|---|---|
| 1 | Require permissions for the economy commands | `refactor/command-permissions` | four `plugin.yml`, `bukkit/commands/*`, README, `wiki/requirements.md`, `bukkit/README.md` | #34 |
| 2 | Split oversized classes into single-responsibility files | `refactor/single-responsibility` | `common/MCEconomyProvider`, `common/database/*`, `bukkit/AbstractMCEconomyPlugin`, docs naming them | #35 |
| 3 | Release 1.6.0 | `release/1.6.0` | `gradle.properties`, `wiki/logs/1/6/0/CHANGELOG.md`, `INDEX.md`, this record | #36 |

## Entries

### Task 1 — refactor/command-permissions

- Audited every command: `plugin.yml` declares only `/balance` and `/pay`; nothing registers a
  command through the `CommandMap`; the balance GUI is display-only; the MCAgents tools read
  only the caller's own balances. No command gives or sets currency, reads another player's
  data, or runs from the console, so no `op` node is needed.
- Added `mcclauneck.mceconomy.command.balance` and `mcclauneck.mceconomy.command.pay` (both
  default `true`) and the `mcclauneck.mceconomy.*` parent (default `op`) to all four
  `plugin.yml` files, with `permission:` on each command.
- `BalanceCommand` and `PayCommand` check their node; `PayTabCompleter` offers nothing without
  the pay node. The nodes live in the new `bukkit/commands/CommandPermissions`.
- Docs: README gained the permission column and a Permissions table; `wiki/requirements.md`
  and `bukkit/README.md` name the nodes.
- Next task depends on: nothing from this one beyond the new `CommandPermissions` class.

### Task 2 — refactor/single-responsibility

- `common/database/{sqlite,mysql,postgresql}`: the pool, async wrapping and every query moved
  into `common/database/sql/AbstractSqlEconomyDatabase`; each dialect keeps only its schema,
  its differing statements, and how it binds UUIDs, booleans and timestamps. Statement text
  and schemas are unchanged. MongoDB and the central backend share no SQL and stay as they
  were. A SQLite round-trip test (`MCEconomySQLiteTest`) now covers the shared queries.
- `common/MCEconomyProvider`: now a facade over `currency/CurrencyRegistry` (cache, managed
  currencies, display order), `currency/CurrencyListParser`, `currency/DefaultCurrencies`, and
  `balance/BalanceLedger` (logged balance changes, with set-then-log written once). Every
  public method and `DEFAULT_CURRENCIES` stay; `getCurrenciesInDisplayOrder()` was added and
  `BalanceCommand.orderedCurrencies` delegates to it.
- `bukkit/AbstractMCEconomyPlugin`: backend selection moved to
  `database/EconomyDatabaseFactory`, the MCIdentity-or-player decision to
  `host/AccountBinding`, and command wiring to `commands/CommandRegistrar`. Public getters
  are unchanged.
- Docs: `common/README.md`, `bukkit/README.md`, `wiki/system.md`, `wiki/api.md`, `INDEX.md`.
- `./gradlew build` green, tests included.

### Task 3 — release/1.6.0

- `project-version` 1.5.0 → 1.6.0 (minor: new permission nodes and a new provider method),
  approved in advance by the owner.
- `wiki/logs/1/6/0/CHANGELOG.md` written; `INDEX.md` lists the 1.5.0 log (missing until now)
  and the 1.6.0 log.
- PR column filled; record closed.
