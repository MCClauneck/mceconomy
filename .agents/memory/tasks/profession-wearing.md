---
name: profession-wearing
description: Task record for keying every wallet by the player UUID now that MCIdentity 5.0.0 drops identities; released as 2.0.0.
---

# Profession wearing

**Status:** in progress

*Opened on project version 1.8.0.*

## Goal

MCIdentity 5.0.0 drops identities: a player has one character, keyed by the player UUID,
and wears one profession at a time. Money no longer depends on what a player wears, so
every wallet belongs to the player, with or without MCIdentity installed.

## Objective

* `AccountBinding` always binds `AccountSource.players()`: account type `player`, account
  UUID the player UUID. The `(account_type, account_uuid)` key is unchanged.
* `IdentityAccountListener`, `MCIdentityBridge`, `IdentitySource`,
  `AccountSource.identities` and `AccountTypes.IDENTITY` are removed; the
  `PlayerAccountListener` creates the account on join.
* MCIdentity is no longer a soft dependency.
* The AI tool port reads the account through `AccountSource` and speaks of the player.
* No migration and no legacy support: nothing is in production yet.

## Decisions taken while the owner was away

| Question | Choice |
|---|---|
| Keep an MCIdentity bridge for later use | No: nothing reads it, and the economy does not depend on the worn profession. |
| Keep `AccountSource` | Yes: it is the published way to resolve an account, now always the player. |
| Version | `2.0.0`: public API types and the `identity` account type are removed. |

## Tasks

| # | Title | Branch | PR |
|---|---|---|---|
| 1 | Record the plan | `plan/profession-wearing` | |
| 2 | Key wallets by the player | `refactor/profession-wearing` | |
| 3 | Release 2.0.0 | `release/2.0.0` | |

### Task 1 — plan/profession-wearing

Recorded the plan.

### Task 2 — refactor/profession-wearing

Removed the MCIdentity integration: `AccountBinding` always binds `AccountSource.players()`
and registers `PlayerAccountListener`; `IdentitySource`, `MCIdentityBridge`,
`IdentityAccountListener` and `AccountTypes.IDENTITY` are deleted, and MCIdentity left
every `softdepend`. The tool port is `isAccountReady()` / `accountUuid(UUID)`. Reworded the
README, module READMEs and wiki.
