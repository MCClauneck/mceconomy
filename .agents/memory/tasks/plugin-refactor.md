---
name: plugin-refactor
description: Task record for the MCEconomy part of the organisation-wide plugin refactor — command permissions, single-responsibility split, and the 1.6.0 release.
---

# Task record — plugin refactor and command permissions

**Status:** in progress
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
| 1 | Require permissions for the economy commands | `refactor/command-permissions` | four `plugin.yml`, `bukkit/commands/*`, README, `wiki/requirements.md`, `bukkit/README.md` | |
| 2 | Split oversized classes into single-responsibility files | `refactor/single-responsibility` | `common/MCEconomyProvider`, `common/database/*`, `bukkit/AbstractMCEconomyPlugin`, docs naming them | |
| 3 | Release 1.6.0 | `release/1.6.0` | `gradle.properties`, `wiki/logs/1/6/0/CHANGELOG.md`, `INDEX.md`, this record | |

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
