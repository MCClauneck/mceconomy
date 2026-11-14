---
name: shared-accounts
description: Task record for replacing economy_account with the shared accounts table and soft depending on MCMultiverse and MCRealm; closes with release 2.1.0.
---

# Shared accounts

**Status:** in progress

*Opened on project version 2.0.0.*

## Goal

MCMultiverse and MCRealm create a shared `accounts` table that every account-keyed plugin
on the same database can point at. MCEconomy's private `economy_account` has the same
columns under another name, so it is replaced by `accounts`, and MCEconomy loads after
MCMultiverse and MCRealm when they are installed.

## Objective

* Every SQL backend creates `accounts` with the canonical statement for its dialect, byte
  for byte, and a unit test pins each statement's SHA-256.
* `economy_account` is gone; `economy_wallet`, `economy_transactions`, `economy_logs` and
  the PostgreSQL archive tables reference `accounts (account_type, account_uuid)`.
* The account row is inserted if missing before the first write for an account.
* The MongoDB backend keeps account documents in an `accounts` collection.
* The central backend is untouched: the server owns its schema.
* Every `plugin.yml` soft depends on MCMultiverse and MCRealm as well as MCAgents.
* No migration and no legacy table: nothing is in production yet.

## Decisions taken while the owner was away

| Question | Choice |
|---|---|
| Where the canonical DDL lives | `common/src/main/resources/schema/accounts/{sqlite,mysql,postgresql}.sql`, copied byte for byte, read by `SharedAccounts`. |
| `archive_economy_account` | Removed: it archived a table that no longer exists, and `accounts` is shared, not MCEconomy's to archive. |
| Archive tables | `archive_economy_wallet`, `archive_economy_logs` and `archive_economy_transactions` reference `accounts` with `ON DELETE RESTRICT`, so no archived history loses its account. |
| `Account` model | Kept; it now describes a row of `accounts`. |
| Version | `2.1.0`: the schema changes, the public API does not. |

## Tasks

| # | Title | Branch | PR |
|---|---|---|---|
| 1 | Record the plan | `plan/shared-accounts` | |
| 2 | Adopt the shared accounts table | `feat/shared-accounts` | |
| 3 | Soft depend on MCMultiverse and MCRealm | `chore/softdepend-universe` | |
| 4 | Release 2.1.0 | `release/2.1.0` | |

### Task 1 — plan/shared-accounts

Recorded the plan.

### Task 2 — feat/shared-accounts

`SharedAccounts` reads the canonical `accounts` statement of each dialect from
`common/src/main/resources/schema/accounts/` and `AbstractSqlEconomyDatabase` runs it before
the economy schema. `economy_account` and `archive_economy_account` are gone; every
account-keyed table, the PostgreSQL archives included, references `accounts`.
`ensureAccount` inserts the row before every wallet, transaction or log write, and MongoDB
upserts into the `accounts` collection. `SharedAccountsTest` pins the three SHA-256 values.
The system wiki gained a storage section.

### Task 3 — chore/softdepend-universe

Every `plugin.yml` soft depends on `[MCAgents, MCMultiverse, MCRealm]`, and the README
requirements name both new soft dependencies.
